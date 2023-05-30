package com.bilalfazlani.zioMaelstrom

import protocol.*
import zio.ZIO
import zio.Tag
import zio.json.JsonDecoder

object MessageHandler:
  def handle[R: Tag, I <: MessageBody: JsonDecoder](
      messageStream: MessageStream,
      app: MaelstromAppR[R, I]
  ): ZIO[Context & MessageSender & Logger & R, Nothing, Unit] =
    messageStream
      .mapZIO(genericMessage =>
        ZIO
          .fromEither(GenericDecoder[I].decode(genericMessage))
          .mapError(e => InvalidInput(genericMessage, e))
          .flatMap { message =>
            Logger.info(s"received message: ${message.body} from ${message.source}") *> (app handle message)
          }
          .catchAll(e => handleInvalidInput(e))
      )
      .runDrain

  private case class InvalidInput(input: GenericMessage, error: String)

  private def handleInvalidInput(invalidInput: InvalidInput): ZIO[Logger & MessageSender, Nothing, Unit] =
    val maybeResponse: Option[MaelstromError] = invalidInput.input.details.msg_id.map { msgId =>
      val errorCode = StandardErrorCode.MalformedRequest
      MaelstromError(
        in_reply_to = msgId,
        code = StandardErrorCode.MalformedRequest.code,
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
