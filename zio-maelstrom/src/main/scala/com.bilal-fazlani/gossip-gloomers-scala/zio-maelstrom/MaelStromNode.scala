package com.bilalfazlani.gossipGloomersScala
package zioMaelstrom

import protocol.*
import zio.Console.*
import zio.*
import zio.json.*
import zio.stream.{ZStream, ZPipeline}
import scala.io.StdIn
import scala.annotation.targetName
import java.nio.file.Path

enum NodeInput:
  case StdIn
  case FilePath(path: Path)

trait MessageHandler[I <: MessageBody: JsonDecoder, S: Tag]:
  def handle(message: Message[I]): ZIO[Ref[NodeState[S]], Throwable, Unit]

trait Debugger:
  def debugMessage(line: String): Task[Unit] =
    printLineError(line)

  def errorMessage(line: String): Task[Unit] =
    printLineError(line)

sealed trait NodeState[+S]
object NodeState:
  case object Initialising extends NodeState[Nothing]
  case class Initialised[S](me: NodeId, others: Seq[NodeId], state: S) extends NodeState[S]

case class InvalidInput(input: String, error: String) extends Exception
case class UnsupportedOperationWhileInitializing(operationName: String) extends Exception

private def nodeState[S: Tag] = ZLayer.fromZIO(Ref.make[NodeState[S]](NodeState.Initialising))

type MaelstromNode[I <: MessageBody, O <: MessageBody] = StatefulMaelstromNode[I, O, Unit]

trait StatefulMaelstromNode[I <: MessageBody: JsonDecoder, O <: MessageBody: JsonEncoder, S: Tag: EmptyState]
    extends MessageHandler[I, S],
      Debugger,
      ZIOAppDefault:

  private def withState[R, E, A](operationName: String)(f: NodeState.Initialised[S] => ZIO[R, E, A]): ZIO[R & Ref[NodeState[S]], E | Throwable, A] =
    for {
      stateRef <- ZIO.service[Ref[NodeState[S]]]
      state <- stateRef.get
      a <- state match {
        case NodeState.Initialising      => ZIO.fail(UnsupportedOperationWhileInitializing(operationName))
        case s: NodeState.Initialised[_] => f(s.asInstanceOf[NodeState.Initialised[S]])
      }
    } yield a

  def nodeInput = NodeInput.StdIn

  extension (to: NodeId)
    def send(body: O): ZIO[Ref[NodeState[S]], Throwable, Unit] =
      withState("send") { state =>
        sendInternal(Message(state.me, to, body))
      }

    @targetName("sendInfix")
    infix def !(body: O): ZIO[Ref[NodeState[S]], Throwable, Unit] = send(body)

  extension (message: Message[I])
    def reply(body: O): ZIO[Ref[NodeState[S]], Throwable, Unit] =
      withState("reply") { state =>
        sendInternal(Message(state.me, message.source, body))
      }

    @targetName("replyInfix")
    infix def :<-(body: O): ZIO[Ref[NodeState[S]], Throwable, Unit] = reply(body)

  protected def broadcastAll(body: O): ZIO[Ref[NodeState[S]], Throwable, Unit] =
    withState("broadcastAll") { state =>
      ZIO.foreachPar(state.others)(_ ! body).unit
    }

  protected def broadcastTo(others: Seq[NodeId], body: O) =
    withState("broadcastTo") { state =>
      ZIO.foreachPar(others)(_ ! body).unit
    }

  private def sendInternal(message: Message[O]): Task[Unit] =
    printLine(message.toJson) *> debugMessage(s"sent message: $message")

  private def sendInitOk(me: NodeId, to: NodeId, in_reply_to: MessageId): Task[Unit] =
    val message = Message(me, to, MaelstromInitOk(in_reply_to))
    printLine(message.toJson) *> debugMessage(s"sent message: $message")

  def me: ZIO[Ref[NodeState[S]], UnsupportedOperationWhileInitializing, NodeId] = ZIO.serviceWithZIO[Ref[NodeState[S]]](state =>
    state.get.flatMap {
      case NodeState.Initialising      => ZIO.fail(UnsupportedOperationWhileInitializing("me"))
      case s: NodeState.Initialised[_] => ZIO.succeed(s.me)
    }
  )

  def others: ZIO[Ref[NodeState[S]], UnsupportedOperationWhileInitializing, Seq[NodeId]] = ZIO.serviceWithZIO[Ref[NodeState[S]]](state =>
    state.get.flatMap {
      case NodeState.Initialising         => ZIO.fail(UnsupportedOperationWhileInitializing("others"))
      case NodeState.Initialised(_, o, _) => ZIO.succeed(o)
    }
  )

  private def init(msg: Message[MaelstromInit]): RIO[Ref[NodeState[S]], Unit] =
    for {
      _ <- debugMessage(s"handling message: $msg")
      state <- ZIO.service[Ref[NodeState[S]]]
      initialized <- state.modify[Boolean] {
        case NodeState.Initialising =>
          (true, NodeState.Initialised[S](msg.body.node_id, msg.body.node_ids.filter(_ != msg.body.node_id), EmptyState[S]))
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
      .filter(line => line.trim != "")
      .takeWhile(line => line.trim != "q" && line.trim != "quit")
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
        case e @ UnsupportedOperationWhileInitializing(operationName) =>
          errorMessage(s"'$operationName' operation is not supported while node is initializing") *> ZIO.fail(e)
        case e =>
          errorMessage(e.toString) *> ZIO.fail(e)
      }
      .foldZIO(e => exit(ExitCode.failure), _ => exit(ExitCode.success))
      .provide(nodeState)
