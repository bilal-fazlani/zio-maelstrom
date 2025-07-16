package com.bilalfazlani.zioMaelstrom

import zio.*
import zio.json.{JsonEncoder, JsonDecoder}
import com.bilalfazlani.zioMaelstrom.models.{Body, MsgName}

// ask_error {
type AskError = Error | DecodingFailure | Timeout
// }

case class Timeout(messageId: MessageId, remote: NodeId, timeout: Duration) {
  override def toString: String =
    s"Timeout(messageId=$messageId, remote=$remote, timeout=${timeout.render})"
}
case class DecodingFailure(error: String, message: GenericMessage)

trait MessageSender:
  def send[A: {MsgName, JsonEncoder}](payload: A, to: NodeId): UIO[Unit]

  def reply[A: {MsgName, JsonEncoder}](payload: A): ZIO[MessageContext, Nothing, Unit]

  private[zioMaelstrom] def sendRaw[A: {MsgName, JsonEncoder}](
      body: Body[A],
      to: NodeId
  ): UIO[Unit]

  def ask[Req: {JsonEncoder, MsgName}, Res: JsonDecoder](
      body: Req,
      to: NodeId,
      timeout: Duration
  ): IO[AskError, Res]

private[zioMaelstrom] object MessageSender:
  val live: ZLayer[
    Initialisation & OutputChannel & CallbackRegistry & MessageIdStore,
    Nothing,
    MessageSender
  ] =
    ZLayer
      .derive[MessageSenderLive]

  def send[A: {MsgName, JsonEncoder}](payload: A, to: NodeId): URIO[MessageSender, Unit] = ZIO
    .serviceWithZIO[MessageSender](_.send(payload, to))

  def reply[A: {MsgName, JsonEncoder}](
      payload: A
  ): ZIO[MessageContext & MessageSender, Nothing, Unit] = ZIO
    .serviceWithZIO[MessageSender](_.reply(payload))

  private[zioMaelstrom] def sendRaw[A: {MsgName, JsonEncoder}](
      body: Body[A],
      to: NodeId
  ): URIO[MessageSender, Unit] = ZIO
    .serviceWithZIO[MessageSender](_.sendRaw(body, to))

  def ask[Req: {JsonEncoder, MsgName}, Res: JsonDecoder](
      body: Req,
      to: NodeId,
      timeout: Duration
  ): ZIO[MessageSender, AskError, Res] =
    ZIO.serviceWithZIO[MessageSender](_.ask(body, to, timeout))

private class MessageSenderLive(
    init: Initialisation,
    stdout: OutputChannel,
    callbackRegistry: CallbackRegistry,
    messageIdStore: MessageIdStore
) extends MessageSender:

  def send[A: {MsgName, JsonEncoder}](payload: A, to: NodeId) =
    val body: Body[A] = Body(MsgName[A], payload, msg_id = None, in_reply_to = None)
    sendRaw(body, to)

  def reply[A: {MsgName, JsonEncoder}](
      payload: A
  ): ZIO[MessageContext, Nothing, Unit] = {
    ZIO.serviceWithZIO[MessageContext] { messageContext =>
      messageContext.messageId match {
        case Some(messageId) =>
          val body: Body[A] = Body(MsgName[A], payload, msg_id = None, in_reply_to = Some(messageId))
          sendRaw(body, messageContext.remote)
        case None =>
          ZIO.logWarning(
            "there is no messageId present in the context, " +
              s"cannot reply to node ${messageContext.remote} with message type ${MsgName[A]}"
          )
      }
    }
  }

  private[zioMaelstrom] def sendRaw[A: {MsgName, JsonEncoder}](
      body: Body[A],
      to: NodeId
  ): UIO[Unit] = {
    val message: Message[A] =
      Message[A](source = init.context.me, destination = to, body = body)
    stdout.transport(message)
  }

  private[zioMaelstrom] def sendWithId[A: {MsgName, JsonEncoder}](
      payload: A,
      to: NodeId
  ): ZIO[Any, Nothing, MessageId] = for {
    messageId <- messageIdStore.next
    body: Body[A] = Body(MsgName[A], payload, msg_id = Some(messageId), in_reply_to = None)
    _ <- sendRaw(body, to)
  } yield body.msg_id.get

  def ask[Req: {JsonEncoder, MsgName}, Res: JsonDecoder](
      body: Req,
      to: NodeId,
      timeout: Duration
  ): IO[AskError, Res] = for {
    msg_id         <- sendWithId(body, to)
    _              <- ZIO.logDebug(s"waiting for reply from $to for message id $msg_id...")
    genericMessage <- ZIO.scoped(callbackRegistry.awaitCallback(msg_id, to, timeout))
    decoded        <-
      if genericMessage.isError then {
        val error = JsonDecoder[Message[Error]]
          .fromJsonAST(genericMessage.raw)
          .map(_.body.payload)
          .left
          .map(e => DecodingFailure(e, genericMessage))
        error.fold(ZIO.fail, ZIO.fail).tapError {
          case DecodingFailure(e, _) =>
            ZIO.logError(s"decoding failed for response from ${to} for message id ${msg_id}")
          case e: Error =>
            ZIO.logError(
              s"error response (${e.code}) received from ${to} for message id ${msg_id}"
            )
        }
      } else {
        ZIO
          .fromEither(JsonDecoder[Message[Res]].fromJsonAST(genericMessage.raw))
          .mapError(e => DecodingFailure(e, genericMessage))
          .tapError(e => ZIO.logError(s"decoding failed for response from ${to} for message id ${msg_id}"))
      }
  } yield decoded.body.payload
