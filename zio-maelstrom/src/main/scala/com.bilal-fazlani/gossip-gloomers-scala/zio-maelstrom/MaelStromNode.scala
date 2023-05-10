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
import zio.json.ast.Json
import zio.json.ast.JsonCursor
import zio.Ref.Synchronized

enum NodeInput:
  case StdIn
  case FilePath(path: Path)

trait MessageHandler[I <: MessageBody: JsonDecoder, S: Tag]:
  def handle(message: Message[I]): ZIO[Context & Ref[S], Throwable, Unit]

trait Debugger:
  def debugMessage(line: String): Task[Unit] =
    printLineError(line)

  def errorMessage(line: String): Task[Unit] =
    printLineError(line)

case class Context(me: NodeId, others: Seq[NodeId])
object Context:
  def apply(init: Message[MaelstromInit]): Context =
    Context(init.body.node_id, init.body.node_ids)

sealed trait NodeState[+S]
object NodeState:
  case object Initialising extends NodeState[Nothing]
  case class Initialised[S](me: NodeId, others: Seq[NodeId], state: S) extends NodeState[S]

case class InvalidInput(input: String, error: String) extends Exception
case class UnsupportedOperationWhileInitializing(operationName: String) extends Exception

private def nodeState[S: EmptyState: Tag] = ZLayer.fromZIO(Ref.Synchronized.make[S](EmptyState[S]))

type MaelstromNode[I <: MessageBody, O <: MessageBody] = StatefulMaelstromNode[I, O, Unit]

trait StatefulMaelstromNode[I <: MessageBody: JsonDecoder, O <: MessageBody: JsonEncoder, S: Tag: EmptyState]
    extends MessageHandler[I, S],
      Debugger,
      InitHandler,
      MessageSender,
      ZIOAppDefault:

  // private def withState[R, E, A](operationName: String)(f: NodeState.Initialised[S] => ZIO[R, E, A]): ZIO[R & Ref.Synchronized[S], E | Throwable, A] =
  //   for {
  //     stateRef <- ZIO.service[Ref[S]]
  //     state <- stateRef.get
  //     a <- state match {
  //       case NodeState.Initialising      => ZIO.fail(UnsupportedOperationWhileInitializing(operationName))
  //       case s: NodeState.Initialised[_] => f(s.asInstanceOf[NodeState.Initialised[S]])
  //     }
  //   } yield a

  def nodeInput = NodeInput.StdIn

  extension (to: NodeId)
    def send(body: O): ZIO[Context, Throwable, Unit] =
      ZIO.serviceWithZIO[Context](context => sendInternal(Message(context.me, to, body)))

    @targetName("sendInfix")
    infix def !(body: O): ZIO[Context, Throwable, Unit] = send(body)

  extension (message: Message[I])
    def reply(body: O): ZIO[Context, Throwable, Unit] =
      ZIO.serviceWithZIO[Context](context => sendInternal(Message(context.me, message.source, body)))

    @targetName("replyInfix")
    infix def :<-(body: O): ZIO[Context, Throwable, Unit] = reply(body)

  def broadcastAll(body: O) =
    ZIO.serviceWithZIO[Context](context => ZIO.foreachPar(context.others)(_ ! body).unit)

  def broadcastTo(others: Seq[NodeId], body: O) =
    ZIO.foreachPar(others)(_ ! body).unit

  // def me: ZIO[Ref[S], UnsupportedOperationWhileInitializing, NodeId] = ZIO.serviceWithZIO[Ref[S]](state =>
  //   state.get.flatMap {
  //     case NodeState.Initialising      => ZIO.fail(UnsupportedOperationWhileInitializing("me"))
  //     case s: NodeState.Initialised[_] => ZIO.succeed(s.me)
  //   }
  // )

  // def others: ZIO[Ref[S], UnsupportedOperationWhileInitializing, Seq[NodeId]] = ZIO.serviceWithZIO[Ref[S]](state =>
  //   state.get.flatMap {
  //     case NodeState.Initialising         => ZIO.fail(UnsupportedOperationWhileInitializing("others"))
  //     case NodeState.Initialised(_, o, _) => ZIO.succeed(o)
  //   }
  // )

  // private def init(msg: Message[MaelstromInit]) =
  //   for {
  //     _ <- debugMessage(s"handling message: $msg")
  //     state <- ZIO.service[Ref[S]]
  //     initialized <- state.modify[Boolean] {
  //       case NodeState.Initialising =>
  //         (true, NodeState.Initialised[S](msg.body.node_id, msg.body.node_ids.filter(_ != msg.body.node_id), EmptyState[S]))
  //       case s @ NodeState.Initialised(me, others, state) => (false, s)
  //     }
  //     _ <-
  //       if initialized
  //       then
  //         val message = Message(msg.body.node_id, msg.source, MaelstromInitOk(msg.body.msg_id))
  //         debugMessage("initialised") *> sendInternal(message)
  //       else debugMessage("already initialised")
  //   } yield ()

  private def inputStream = nodeInput match
    case NodeInput.StdIn => ZStream.fromInputStream(java.lang.System.in).via(ZPipeline.utfDecode)
    case NodeInput.FilePath(path) =>
      ZStream.fromFile(path.toFile(), 4096).via(ZPipeline.utfDecode).via(ZPipeline.splitLines)

  val initializingProgram = inputStream
    .filter(line => line.trim != "")
    .takeWhile(line => line.trim != "q" && line.trim != "quit")
    .map(str => (str, JsonDecoder[GenericMessage].decodeJson(str)))
    .collectZIO {
      // right means a valid json of a generic message
      case (str, Right(genericMessage)) => 
        if genericMessage isOfType "init"
        then //if init, then decode it as init message
          val initMessage = JsonDecoder[Message[MaelstromInit]].decodeJson(str)
          initMessage match
            //when decoded, send it to init handler
            case Right(initMessage) => handleInitV2(initMessage) as Some(initMessage)
            //if decoding failed, send an error message to sender and log error
            case Left(error)    => handleInitDecoding(genericMessage) as None
        else // else send an error message to sender and log error 
          handleMessageOtherThanInit(genericMessage) as None

      // left means invalid json or missing minimum fields
      // cant reply to anyone. just log the error
      // in this case, this stream will never end and node will be in initialising state forever
      case (input, Left(error)) => 
        debugMessage(s"expected init message. recieved invalid input. error: $error, input: $input") as None
    }
    .
    .takeUntil(_.isDefined)
    .collectSome // keep only init message
    .runHead
    //stream is now completed.
    //if we don't have a sum yet, it means we didn't get any init message
    //since we can't proceed without init message, its safe to fail the program
    .collect(Exception("no init message received")){
      case Some(initMessage) => initMessage
    }
    .map(Context.apply)

  def messageHandlingProgram(context: Context) =
    

  def run =
    inputStream
      .filter(line => line.trim != "")
      .takeWhile(line => line.trim != "q" && line.trim != "quit")
      .mapZIO { s =>
        val either: Either[String, Message[MaelstromInit] | Message[I]] = for {
          ast <- JsonDecoder[Json].decodeJson(s)
          body <- ast.get(JsonCursor.field("body"))
          typeStr <- body.get(JsonCursor.field("type")).flatMap(_.asString.toRight("type is not a string"))
          a <- if typeStr == "init" then ast.`as`[Message[MaelstromInit]] else ast.`as`[Message[I]]
        } yield a

        ZIO
          .fromEither(either)
          .mapError(e => InvalidInput(s, e))
      }
      .collectZIO {
        case m @ Message(_, _, i @ MaelstromInit(msg_id, node_id, node_ids, _)) =>
          handleInit(m.asInstanceOf[Message[MaelstromInit]])
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

trait MessageSender extends Debugger:
  private[zioMaelstrom] def sendInternal[A <: MessageBody: JsonEncoder](message: Message[A]): Task[Unit] =
    printLine(message.toJson) *> debugMessage(s"sent message: $message")

trait InitHandler extends MessageSender:
  // def handleInit[S: EmptyState: Tag](message: Message[MaelstromInit]) =
  //   val replyMessage = Message[MaelstromInitOk](message.destination, message.source, MaelstromInitOk(message.body.msg_id))
  //   for {
  //     _ <- debugMessage(s"handling init message: $message")
  //     _ <- sendInternal(replyMessage)
  //     updated <- ZIO.serviceWithZIO[Ref[S]](stateRef =>
  //       stateRef.modifySome(false) { case NodeState.Initialising =>
  //         (true, NodeState.Initialised[S](message.body.node_id, message.body.node_ids.filter(_ != message.body.node_id), EmptyState[S]))
  //       }
  //     )
  //     _ <- if updated then debugMessage("initialised") else debugMessage("already initialised")
  //     _ <-
  //       if !updated
  //       then
  //         val errorMessage = message.makeError(StandardErrorCode.PreconditionFailed, s"node ${message.destination} is already initialised")
  //         sendInternal(errorMessage) *> debugMessage("sent error message")
  //       else ZIO.unit
  //   } yield ()

  def handleInitV2(message: Message[MaelstromInit]) =
    val replyMessage = Message[MaelstromInitOk](message.destination, message.source, MaelstromInitOk(message.body.msg_id))
    for {
      _ <- debugMessage(s"handling init message: $message")
      _ <- sendInternal(replyMessage)
      _ <- debugMessage("initialised")
    } yield ()

  def handleInitDecoding(genericMessage: GenericMessage) = 
    debugMessage(s"could not decode init message $genericMessage") *>
      genericMessage
        .makeError(StandardErrorCode.MalformedRequest, "init message is malformed")
        .fold(ZIO.unit)(sendInternal(_))

  def handleMessageOtherThanInit(message: GenericMessage) =
    debugMessage(s"could not process message $message because node ${message.dest} is not initialised yet") *> 
      message
        .makeError(StandardErrorCode.TemporarilyUnavailable, s"node ${message.dest} is not initialised yet")
        .fold(ZIO.unit)(sendInternal(_))    