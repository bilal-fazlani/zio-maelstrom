package com.bilalfazlani.zioMaelstrom

import protocol.*
import zio.*
import zio.json.*

type Handler[R, I] = (MessageSource, Context) ?=> I => ZIO[MaelstromRuntime & R, Nothing, Unit]

private[zioMaelstrom] object RequestHandler:
  def handleR[R, I: JsonDecoder](
      handler: Handler[R, I]
  ): ZIO[MaelstromRuntime & R, Nothing, Unit] =
    for {
      initialisation <- ZIO.service[Initialisation]
      settings       <- ZIO.service[Settings]
      _ <- initialisation.inputs.messageStream
        // process messages in parallel
        .mapZIOPar(settings.concurrency)(genericMessage =>
          ZIO
            .fromEither(GenericDecoder[I].decode(genericMessage))
            .mapError(e => InvalidInput(genericMessage, e))
            .flatMap { message =>
              given MessageSource = MessageSource(message.source)
              given Context       = initialisation.context
              handler apply message.body
            }
            .catchAll(e => handleInvalidInput(e))
        )
        .runDrain
    } yield ()

  def handle[I: JsonDecoder](handler: Handler[Any, I]): ZIO[MaelstromRuntime, Nothing, Unit] = handleR(handler)

  private case class InvalidInput(input: GenericMessage, error: String)

  private def handleInvalidInput(invalidInput: InvalidInput): ZIO[Logger & MessageSender, Nothing, Unit] =
    val maybeResponse: Option[ErrorMessage] = invalidInput.input.messageId.map { msgId =>
      ErrorMessage(
        in_reply_to = msgId,
        code = ErrorCode.MalformedRequest,
        text = s"invalid input: $invalidInput"
      )
    }
    for {
      _ <- Logger.error(s"invalid input: $invalidInput")
      _ <- maybeResponse match {
        case Some(errorMessageBody) => MessageSender.send(errorMessageBody, invalidInput.input.src).ignore
        case None                   => ZIO.unit // if there was no msg id in msg, you can't send a reply
      }
    } yield ()
