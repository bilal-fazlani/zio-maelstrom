package com.bilalfazlani.zioMaelstrom

import zio.{Tag, ZIO, *}
import zio.json.*

case class MessageContext(remote: NodeId, messageId: Option[MessageId])

type Handler[R, I] = I => ZIO[MaelstromRuntime & Scope & R & MessageContext, Nothing, Unit]

trait RequestHandler:
  def handle[R: Tag, I: JsonDecoder](handler: Handler[R, I]): ZIO[R & MaelstromRuntime, Nothing, Unit]

private[zioMaelstrom] object RequestHandler:
  val live: ZLayer[Initialisation & MessageSender & Settings, Nothing, RequestHandler] =
    ZLayer.derive[RequestHandlerLive]

  def handle[R: Tag, I: JsonDecoder](handler: Handler[R, I]): ZIO[R & MaelstromRuntime, Nothing, Unit] =
    given Tag[MaelstromRuntime & R] = Tag[MaelstromRuntime & R]
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
      Some(message.src)
    ).flatten.mkString(" ")

  def handle[R: Tag, I: JsonDecoder](handler: Handler[R, I]): ZIO[R & MaelstromRuntime, Nothing, Unit] =
    for {
      _ <- initialisation.inputs.messageStream
        // process messages in parallel
        .mapZIOPar(settings.concurrency)(genericMessage =>
          ZIO.logDebug(processingMessageLog(genericMessage)) *>
            ZIO
              .fromEither(GenericDecoder[I].decode(genericMessage))
              .mapError(e => InvalidInput(genericMessage, e))
              .flatMap { message =>
                given messageContext: MessageContext = MessageContext(genericMessage.src, genericMessage.messageId)
                val messageContextLayer              = ZLayer.succeed(messageContext)
                ZIO.scoped(
                  ZIO
                    .attempt(
                      handler(message.body.payload).provideSome[R & MaelstromRuntime & Scope](messageContextLayer)
                    )
                    .flatten
                )
              }
              .catchAll {
                case t: Throwable    => handleThrowable(t, genericMessage)
                case i: InvalidInput => handleInvalidInput(i)
              }
        )
        .runDrain
    } yield ()

  private def handleThrowable(t: Throwable, input: GenericMessage): ZIO[Any, Nothing, Unit] =
    val maybeResponse: Option[Error] = input.messageId.map { _ =>
      Error(
        code = ErrorCode.Crash,
        text = s"handler crashed due to unhandled error: ${t.getMessage}"
      )
    }
    for {
      _ <- ZIO.logError(s"handler crashed: ${t.getMessage}")
      _ <- ZIO.logError(t.getStackTrace.mkString("\n"))
      _ <- maybeResponse match {
        case Some(errorMessageBody) =>
          messageSender
            .reply(errorMessageBody, input.src, input.messageId.get)
            .ignore
        case None => ZIO.unit // if there was no msg id in msg, we can't send a reply
      }
      _ <- ZIO.die(t)
    } yield ()

  private def handleInvalidInput(invalidInput: InvalidInput): ZIO[Any, Nothing, Unit] =
    val maybeResponse: Option[Error] = invalidInput.input.messageId.map { _ =>
      Error(
        code = ErrorCode.MalformedRequest,
        text = s"invalid input: $invalidInput"
      )
    }
    for {
      _ <- ZIO.logError(s"invalid input: $invalidInput")
      _ <- maybeResponse match {
        case Some(errorMessageBody) =>
          messageSender
            .reply(errorMessageBody, invalidInput.input.src, invalidInput.input.messageId.get)
            .ignore
        case None => ZIO.unit // if there was no msg id in msg, we can't send a reply
      }
    } yield ()
