package com.bilalfazlani.zioMaelstrom

import zio.*
import zio.json.*

type Handler[R, I] = RPCHandler[R, I, Unit]

type RPCHandler[R, I, O] =
  (MessageSource, Option[MessageId], Context) ?=> I => ZIO[MaelstromRuntime & R, Nothing, O]

trait RequestHandler:
  def handle[R, I: JsonDecoder](handler: Handler[R, I]): ZIO[R & MaelstromRuntime, Nothing, Unit]

private[zioMaelstrom] object RequestHandler:
  val live: ZLayer[Initialisation & MessageSender & Settings, Nothing, RequestHandler] =
    ZLayer.derive[RequestHandlerLive]

  def handle[R, I: JsonDecoder](handler: Handler[R, I]): ZIO[R & MaelstromRuntime, Nothing, Unit] =
    ZIO.serviceWithZIO[RequestHandler](_.handle(handler))

private class RequestHandlerLive(
    initialisation: Initialisation,
    messageSender: MessageSender,
    settings: Settings
) extends RequestHandler:
  private case class InvalidInput(input: GenericMessage, error: String)

  private def processingMessageLog(message: GenericMessage) =
    Seq(
      Some("processing request"),
      message.messageType,
      message.messageId.fold(None)(id => Some(s"[$id]")),
      Some("from"),
      Some(message.src.toString)
    ).flatten.mkString(" ")

  def handle[R, I: JsonDecoder](handler: Handler[R, I]): ZIO[R & MaelstromRuntime, Nothing, Unit] =
    for {
      _ <- initialisation.inputs.messageStream
        // process messages in parallel
        .mapZIOPar(settings.concurrency)(genericMessage =>
          ZIO.logDebug(processingMessageLog(genericMessage)) *>
            ZIO
              .fromEither(GenericDecoder[I].decode(genericMessage))
              .mapError(e => InvalidInput(genericMessage, e))
              .flatMap { message =>
                given MessageSource = MessageSource(message.source)
                given Context       = initialisation.context
                given Option[MessageId] = genericMessage.messageId
                handler apply message.body
              }
              .catchAll(handleInvalidInput)
        )
        .runDrain
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
      _ <- ZIO.logError(s"invalid input: $invalidInput")
      _ <- maybeResponse match {
        case Some(errorMessageBody) =>
          messageSender.send(errorMessageBody, invalidInput.input.src).ignore
        case None => ZIO.unit // if there was no msg id in msg, we can't send a reply
      }
    } yield ()
