package com.bilalfazlani.zioMaelstrom

import protocol.*
import zio.*
import zio.json.JsonDecoder

type Handler[R, I <: MessageBody] = Message[I] => ZIO[MaelstromRuntime & R, Nothing, Unit]

object RequestHandler:
  private[zioMaelstrom] def handleR[R, I <: MessageBody: JsonDecoder](
      handler: Handler[R, I]
  ): ZIO[MaelstromRuntime & R, Nothing, Unit] =
    for {
      initialisation <- ZIO.service[Initialisation]
      _ <- initialisation.inputs.messageStream
        // process messages in parallel
        .mapZIOPar(1024)(genericMessage =>
          ZIO
            .fromEither(GenericDecoder[I].decode(genericMessage))
            .tap(message => Logger.info(s"received ${message.body} from ${message.source}"))
            .mapError(e => InvalidInput(genericMessage, e))
            .flatMap(message => handler apply message)
            .catchAll(e => handleInvalidInput(e))
        )
        .runDrain
    } yield ()

  private[zioMaelstrom] def handle[I <: MessageBody: JsonDecoder](handler: Handler[Any, I]): ZIO[MaelstromRuntime, Nothing, Unit] = handleR(handler)

  private case class InvalidInput(input: GenericMessage, error: String)

  private def handleInvalidInput(invalidInput: InvalidInput): ZIO[Logger & MessageSender, Nothing, Unit] =
    val maybeResponse: Option[MaelstromError] = invalidInput.input.messageId.map { msgId =>
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
