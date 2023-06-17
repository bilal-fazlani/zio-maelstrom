package com.bilalfazlani.zioMaelstrom

import protocol.*
import zio.*
import zio.json.*

type Handler[R, I] = (MessageSource, Context) ?=> I => ZIO[MaelstromRuntime & R, Nothing, Unit]

private[zioMaelstrom] trait RequestHandler:
  def handle[R, I: JsonDecoder](handler: Handler[R, I]): ZIO[R & MaelstromRuntime, Nothing, Unit]

private[zioMaelstrom] object RequestHandler:
  val live: ZLayer[Initialisation & Logger & MessageSender & Settings, Nothing, RequestHandlerLive] = ZLayer.fromFunction(RequestHandlerLive.apply)

  def handle[R, I: JsonDecoder](handler: Handler[R, I]): ZIO[R & MaelstromRuntime, Nothing, Unit] =
    ZIO.serviceWithZIO[RequestHandler](_.handle(handler))

private case class RequestHandlerLive(
    initialisation: Initialisation,
    logger: Logger,
    messageSender: MessageSender,
    settings: Settings
) extends RequestHandler:
  private case class InvalidInput(input: GenericMessage, error: String)

  def handle[R, I: JsonDecoder](handler: Handler[R, I]): ZIO[R & MaelstromRuntime, Nothing, Unit] =
    for {
      _ <- initialisation.inputs.messageStream
        // process messages in parallel
        .mapZIOPar(settings.concurrency)(genericMessage =>
          ZIO.fromEither(GenericDecoder[I].decode(genericMessage))
            .mapError(e => InvalidInput(genericMessage, e)).flatMap { message =>
              given MessageSource = MessageSource(message.source)
              given Context       = initialisation.context
              handler apply message.body
            }.catchAll(handleInvalidInput)
        ).runDrain
    } yield ()

  private def handleInvalidInput(invalidInput: InvalidInput): ZIO[Any, Nothing, Unit] =
    val maybeResponse: Option[ErrorMessage] = invalidInput.input.messageId.map { msgId =>
      ErrorMessage(
        in_reply_to = msgId,
        code = ErrorCode.MalformedRequest,
        text = s"invalid input: $invalidInput"
      )
    }
    for {
      _ <- logger.error(s"invalid input: $invalidInput")
      _ <- maybeResponse match {
        case Some(errorMessageBody) => messageSender.send(errorMessageBody, invalidInput.input.src)
            .ignore
        case None => ZIO.unit // if there was no msg id in msg, we can't send a reply
      }
    } yield ()
