package com.bilalfazlani.zioMaelstrom

import zio.*
import protocol.MessageBody
import zio.json.JsonDecoder
import protocol.*
import zio.stream.*

object MaelstromRuntime:
  private type Remainder = ZStream[Any, Nothing, GenericMessage]

  private case class InvalidInput(input: GenericMessage, error: String) extends Exception
  def run[R: Tag, I <: MessageBody: JsonDecoder](
      app: MaelstromAppR[R, I]
  ): ZIO[R & Settings, Nothing, Unit] =
    val inputStream = ZStream
      .unwrap(for {
        settings <- ZIO.service[Settings]
        logger <- ZIO.service[Logger]
        nodeInput = settings.nodeInput
        _ <-
          if nodeInput == NodeInput.StdIn then logger.info("using StdIn")
          else logger.info(s"using FilePath(${nodeInput.asInstanceOf[NodeInput.FilePath].path})")
        strm = (if nodeInput == NodeInput.StdIn then ZStream.fromInputStream(java.lang.System.in).via(ZPipeline.utfDecode)
                else
                  ZStream.fromFile(nodeInput.asInstanceOf[NodeInput.FilePath].path.toFile(), 4096).via(ZPipeline.utfDecode).via(ZPipeline.splitLines)
        )
          .filter(line => line.trim != "")
          .takeWhile(line => line.trim != "q")
      } yield strm)
      .orDie

    val loggerLayer = Logger.live
    val messageTransportLayer = loggerLayer >>> MessageTransport.live
    val initializerLayer = (loggerLayer ++ messageTransportLayer) >>> Initializer.live
    def layers(context: Context) = {
      val contextLayer = ZLayer.succeed(context)
      val messageSenderLayer = (contextLayer ++ messageTransportLayer) >>> MessageSender.live
      messageSenderLayer ++ contextLayer
    }

    ZIO
      .scoped(for {
        initResult <- Initializer.initialize(inputStream)
        remainder = initResult._2
        context = initResult._1
        _ <- consumeMessages(context, remainder, app).provideSomeLayer[R & Settings & Logger & MessageTransport](layers(context))
      } yield ())
      .provideSomeLayer[R & Settings](Logger.live ++ (Logger.live >>> MessageTransport.live) ++ initializerLayer)

  private def consumeMessages[R: Tag, I <: MessageBody: JsonDecoder](
      context: Context,
      remainder: Remainder,
      app: MaelstromAppR[R, I]
  ): ZIO[Logger & MessageSender & Context & R, Nothing, Unit] =
    remainder
      .mapZIO(genericMessage =>
        ZIO
          .fromEither(GenericDecoder[I].decode(genericMessage))
          .mapError(e => InvalidInput(genericMessage, e))
          .flatMap(message => app handle message)
          .catchAll(e => handleInvalidInput(e, context))
      )
      .runDrain

  private def handleInvalidInput(invalidInput: InvalidInput, context: Context): ZIO[Logger & MessageSender, Nothing, Unit] =
    val maybeResponse: Option[MaelstromError] = invalidInput.input.details.msg_id.map { msgId =>
      val errorCode = StandardErrorCode.MalformedRequest
      MaelstromError(
        in_reply_to = msgId,
        code = StandardErrorCode.MalformedRequest.code,
        text = s"invalid input: $invalidInput"
      )
    }
    for {
      logger <- ZIO.service[Logger]
      _ <- logger.error(s"invalid input from ${invalidInput.input.src}")
      sender <- ZIO.service[MessageSender]
      _ <- maybeResponse match {
        case Some(errorMessageBody) => sender.send(errorMessageBody, invalidInput.input.src).ignore
        case None                   => ZIO.unit // if there was no msg id in msg, you can't send a reply
      }
    } yield ()
