package com.bilalfazlani.zioMaelstrom

import zio.*
import zio.json.{JsonEncoder, JsonDecoder}

// ask_error {
type AskError = ErrorMessage | DecodingFailure | Timeout
// }

case class Timeout(messageId: MessageId, remote: NodeId, timeout: Duration) {
  override def toString(): String =
    s"Timeout(messageId=$messageId, remote=$remote, timeout=${timeout.render})"
}
case class DecodingFailure(error: String, message: GenericMessage)

trait MessageSender:
  def send[A <: Sendable: JsonEncoder](body: A, to: NodeId): UIO[Unit]

  def ask[Req <: Sendable & NeedsReply: JsonEncoder, Res <: Reply: JsonDecoder](
      body: Req,
      to: NodeId,
      timeout: Duration
  ): IO[AskError, Res]

private[zioMaelstrom] object MessageSender:
  val live
      : ZLayer[Initialisation & OutputChannel & CallbackRegistry, Nothing, MessageSender] =
    ZLayer
      .derive[MessageSenderLive]

  def send[A <: Sendable: JsonEncoder](body: A, to: NodeId): URIO[MessageSender, Unit] = ZIO
    .serviceWithZIO[MessageSender](_.send(body, to))

  def ask[Req <: Sendable & NeedsReply: JsonEncoder, Res <: Reply: JsonDecoder](
      body: Req,
      to: NodeId,
      timeout: Duration
  ): ZIO[MessageSender, AskError, Res] = ZIO.serviceWithZIO[MessageSender](_.ask(body, to, timeout))

private class MessageSenderLive(
    init: Initialisation,
    stdout: OutputChannel,
    callbackRegistry: CallbackRegistry,
) extends MessageSender:
  def send[A <: Sendable: JsonEncoder](body: A, to: NodeId) =
    val message: Message[A] =
      Message[A](source = init.context.me, destination = to.nodeId, body = body)
    stdout.transport(message)

  def ask[Req <: Sendable & NeedsReply: JsonEncoder, Res <: Reply: JsonDecoder](
      body: Req,
      to: NodeId,
      timeout: Duration
  ): IO[AskError, Res] = for {
    _              <- send(body, to)
    _              <- ZIO.logDebug(s"waiting for reply from ${to} for message id ${body.msg_id}...")
    genericMessage <- ZIO.scoped(callbackRegistry.awaitCallback(body.msg_id, to, timeout))
    decoded <-
      if genericMessage.isError then {
        val error = JsonDecoder[Message[ErrorMessage]]
          .fromJsonAST(genericMessage.raw)
          .map(_.body)
          .left
          .map(e => DecodingFailure(e, genericMessage))
        error.fold(ZIO.fail, ZIO.fail).tapError {
          case DecodingFailure(e, _) =>
            ZIO.logError(s"decoding failed for response from ${to} for message id ${body.msg_id}")
          case e: ErrorMessage =>
            ZIO.logError(
                s"error response (${e.code}) received from ${to} for message id ${body.msg_id}"
              )
        }
      } else {
        ZIO
          .fromEither(JsonDecoder[Message[Res]].fromJsonAST(genericMessage.raw))
          .mapError(e => DecodingFailure(e, genericMessage))
          .tapError(e =>
            ZIO.logError(s"decoding failed for response from ${to} for message id ${body.msg_id}")
          )
      }
  } yield decoded.body
