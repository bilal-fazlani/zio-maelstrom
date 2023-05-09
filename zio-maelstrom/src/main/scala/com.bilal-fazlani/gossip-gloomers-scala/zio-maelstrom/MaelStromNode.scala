package com.bilalfazlani.gossipGloomersScala
package zioMaelstrom

import protocol.{given, *}
import zio.Console.*
import zio.*
import zio.json.*
import zio.stream.{ZStream, ZPipeline}
import scala.io.StdIn
import scala.annotation.targetName
import java.nio.file.Path
import zio.internal.ansi.AnsiStringOps

enum NodeInput:
  case StdIn
  case FilePath(path: Path)

trait MessageHandler[I <: MessageBody: JsonDecoder]:
  def handle(message: Message[I]): ZIO[Ref[NodeState], Throwable, Unit]

trait Debugger:
  def debugMessage(line: String): Task[Unit] =
    printLineError(line.yellow)

  def errorMessage(line: String): Task[Unit] =
    printLineError(line.red)

enum NodeState:
  case Initialising
  case Initialised[A](me: NodeId, others: Seq[NodeId], state: A)

case class InvalidInput(input: String, error: String) extends Exception
case class InitializationNotFinished() extends Exception

private val nodeState = ZLayer.fromZIO(Ref.make[NodeState](NodeState.Initialising))

trait MaelstromNode[I <: MessageBody: JsonDecoder, O <: MessageBody: JsonEncoder]
    extends MessageHandler[I],
      Debugger,
      ZIOAppDefault:

  def nodeInput = NodeInput.StdIn

  extension (to: NodeId)
    def send(body: O): ZIO[Ref[NodeState], Throwable, Unit] =
      for {
        myNodeId <- me
        _ <- sendInternal(Message(myNodeId, to, body))
      } yield ()

    @targetName("sendInfix")
    infix def !(body: O): ZIO[Ref[NodeState], Throwable, Unit] = send(body)

  extension (message: Message[I])
    def reply(body: O): ZIO[Ref[NodeState], Throwable, Unit] =
      for {
        myNodeId <- me
        _ <- sendInternal(Message(myNodeId, message.source, body))
      } yield ()

    @targetName("replyInfix")
    infix def :<-(body: O): ZIO[Ref[NodeState], Throwable, Unit] = reply(body)

  protected def broadcastAll(body: O): ZIO[Ref[NodeState], Throwable, Unit] =
    for {
      myNodeId <- me
      others <- others
      _ <- ZIO.foreachPar(others)(_ ! body)
    } yield ()

  protected def broadcastTo(others: Seq[NodeId], body: O) =
    for {
      myNodeId <- me
      _ <- ZIO.foreachPar(others)(_ ! body)
    } yield ()

  private def sendInternal(message: Message[O]): Task[Unit] =
    printLine(message.toJson.green) *> debugMessage(s"sent message: $message")

  private def sendInitOk(me: NodeId, to: NodeId, in_reply_to: MessageId): Task[Unit] =
    val message = Message(me, to, MaelstromInitOk(in_reply_to))
    printLine(message.toJson.green) *> debugMessage(s"sent message: $message")

  def me: ZIO[Ref[NodeState], InitializationNotFinished, NodeId] = ZIO.serviceWithZIO[Ref[NodeState]](state =>
    state.get.flatMap {
      case NodeState.Initialising          => ZIO.fail(InitializationNotFinished())
      case NodeState.Initialised(me, _, _) => ZIO.succeed(me)
    }
  )

  def others: ZIO[Ref[NodeState], InitializationNotFinished, Seq[NodeId]] = ZIO.serviceWithZIO[Ref[NodeState]](state =>
    state.get.flatMap {
      case NodeState.Initialising         => ZIO.fail(InitializationNotFinished())
      case NodeState.Initialised(_, o, _) => ZIO.succeed(o)
    }
  )

  private def init(msg: Message[MaelstromInit]): RIO[Ref[NodeState], Unit] =
    for {
      _ <- debugMessage(s"handling message: $msg")
      state <- ZIO.service[Ref[NodeState]]
      initialized <- state.modify[Boolean] {
        case NodeState.Initialising =>
          (true, NodeState.Initialised[Unit](msg.body.node_id, msg.body.node_ids.filter(_ != msg.body.node_id), ()))
        case s @ NodeState.Initialised(me, others, state) => (false, s)
      }
      _ <-
        if initialized
        then
          debugMessage("initialised")
            *> sendInitOk(me = msg.body.node_id, to = msg.source, in_reply_to = msg.body.msg_id)
        else debugMessage("already initialised")
    } yield ()

  private def inputStream = nodeInput match
    case NodeInput.StdIn => ZStream.fromInputStream(java.lang.System.in).via(ZPipeline.utfDecode)
    case NodeInput.FilePath(path) =>
      ZStream.fromFile(path.toFile(), 4096).via(ZPipeline.utfDecode).via(ZPipeline.splitLines)

  def run =
    inputStream
      .takeWhile(line => line.trim != "" && line.trim != "q" && line.trim != "quit")
      .mapZIO { s =>
        val either: Either[String, Message[I | MaelstromInit]] = JsonDecoder[Message[I]]
          .decodeJson(s)
          .orElse(JsonDecoder[Message[MaelstromInit]].decodeJson(s))
        ZIO
          .fromEither(either)
          .mapError(e => InvalidInput(s, e))
      }
      .collectZIO {
        case m @ Message(_, _, i @ MaelstromInit(msg_id, node_id, node_ids, _)) =>
          init(m.asInstanceOf[Message[MaelstromInit]])
        case m =>
          handle(m.asInstanceOf[Message[I]])
      }
      .runCollect
      .tapError {
        case InvalidInput(input, error) =>
          val msg = s"error: $error, input: $input"
          errorMessage(msg) *> ZIO.fail(Exception(msg))
        case InitializationNotFinished() =>
          errorMessage("operation unavailable when initializing") *> ZIO.fail(InitializationNotFinished())
        case e =>
          errorMessage(e.toString) *> ZIO.fail(e)
      }
      .foldZIO(e => exit(ExitCode.failure), _ => exit(ExitCode.success))
      .provide(nodeState)
