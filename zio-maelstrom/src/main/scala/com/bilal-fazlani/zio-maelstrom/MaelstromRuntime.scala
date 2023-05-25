package com.bilalfazlani.zioMaelstrom

import zio.{ZIO, ZLayer, Tag}
import protocol.MessageBody
import zio.json.JsonDecoder
import protocol.*
import zio.stream.*
import com.bilalfazlani.zioMaelstrom.Initializer
import zio.ZEnvironment

trait MaelstromRuntime:
  def run[R: Tag, I <: MessageBody: JsonDecoder](
      app: MaelstromAppR[R, I],
      nodeInput: NodeInput
  ): ZIO[R, Throwable, Unit]

object MaelstromRuntime:
  private type Remainder = ZStream[Any, Throwable, GenericMessage]

  private case class InvalidInput(input: GenericMessage, error: String) extends Exception
  def run[R: Tag, I <: MessageBody: JsonDecoder](
      app: MaelstromAppR[R, I],
      nodeInput: NodeInput = NodeInput.StdIn
  ): ZIO[R, Throwable, Unit] =
    val inputStream = (nodeInput match
      case NodeInput.StdIn =>
        ZStream.fromInputStream(java.lang.System.in).via(ZPipeline.utfDecode)
      case NodeInput.FilePath(path) =>
        ZStream.fromFile(path.toFile(), 4096).via(ZPipeline.utfDecode).via(ZPipeline.splitLines)
    )
    .filter(line => line.trim != "")
      .takeWhile(line => line.trim != "q")

    val debuggerLayer = Debugger.live
    val messageTransportLayer = debuggerLayer >>> MessageTransport.live
    val initializerLayer = (debuggerLayer ++ messageTransportLayer) >>> Initializer.live
    def layers(context: Context) = {
      val contextLayer = ZLayer.succeed(context)
      val messageSenderLayer = (contextLayer ++ debuggerLayer ++ messageTransportLayer) >>> MessageSender.live
      messageSenderLayer ++ contextLayer
    }

    ZIO
      .scoped(for {
        initResult <- Initializer.initialize(inputStream)
        remainder = initResult._2
        _ <- remainder.runCollect.flatMap(remaining => zio.Console.printLineError(s"remaining: ${remaining.toList}"))
        context = initResult._1
        _ <- consumeMessages(context, remainder, app).provideSomeLayer[R & Debugger & MessageTransport](layers(context))
      } yield ())
      .provideSomeLayer[R](Debugger.live ++ (Debugger.live >>> MessageTransport.live) ++ initializerLayer)

  private def consumeMessages[R: Tag, I <: MessageBody: JsonDecoder](
      context: Context,
      remainder: Remainder,
      app: MaelstromAppR[R, I]
  ): ZIO[Debugger & MessageSender & Context & R, Throwable, Unit] =
    remainder
      // .tap(g => ZIO.serviceWithZIO[Debugger](_.debugMessage(s"received: $g")))
      .mapZIO(genericMessage =>
        ZIO
          .fromEither(GenericDecoder[I].decode(genericMessage))
          .mapError(e => InvalidInput(genericMessage, e))
          .tapError(e => handleInvalidInput(e, context))
      )
      .mapZIO(message => app handle message)
      .runDrain

  private def handleInvalidInput(invalidInput: InvalidInput, context: Context): ZIO[Debugger & MessageSender, Throwable, Unit] =
    val maybeResponse: Option[MaelstromError] = invalidInput.input.details.msg_id.map { msgId =>
      val errorCode = StandardErrorCode.MalformedRequest
      MaelstromError(
        in_reply_to = msgId,
        code = StandardErrorCode.MalformedRequest.code,
        text = s"invalid input: $invalidInput"
      )
    }
    for {
      debugger <- ZIO.service[Debugger]
      _ <- debugger.debugMessage(s"invalid input from ${invalidInput.input.src}")
      sender <- ZIO.service[MessageSender]
      _ <- maybeResponse match {
        case Some(errorMessageBody) => sender.send(errorMessageBody, invalidInput.input.src)
        case None                   => ZIO.unit // if there was no msg id in msg, you can send a reply
      }
    } yield ()
