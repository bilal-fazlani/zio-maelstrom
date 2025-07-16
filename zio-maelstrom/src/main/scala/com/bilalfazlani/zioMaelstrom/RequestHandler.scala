package com.bilalfazlani.zioMaelstrom

import zio.*
import zio.json.*

case class MessageContext(remote: NodeId, messageId: Option[MessageId])

type Handler[R, I] = I => ZIO[MaelstromRuntime & Scope & R & MessageContext, Error, Unit]

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
        .mapZIOPar(settings.concurrency) { genericMessage =>
          val messageContext: MessageContext = MessageContext(genericMessage.src, genericMessage.messageId)
          val messageContextLayer            = ZLayer.succeed(messageContext)
          ZIO.logDebug(processingMessageLog(genericMessage)) *>
            ZIO
              .fromEither(GenericDecoder[I].decode(genericMessage))
              .mapError(e => InvalidInput(genericMessage, e))
              .flatMap(message => ZIO.scoped(ZIO.attempt(handler(message.body.payload)).flatten))
              .catchAll {
                case e: Error        => handleError(e)
                case t: Throwable    => handleThrowable(t, genericMessage)
                case i: InvalidInput => handleInvalidInput(i)
              }
              .provideSome[R & MaelstromRuntime](messageContextLayer)
        }
        .runDrain
    } yield ()

  private def handleError(error: Error): URIO[MessageContext, Unit] = messageSender.reply(error)

  private def handleThrowable(t: Throwable, input: GenericMessage): ZIO[MessageContext, Nothing, Unit] =
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

  private def handleInvalidInput(invalidInput: InvalidInput): ZIO[MessageContext, Nothing, Unit] =
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
