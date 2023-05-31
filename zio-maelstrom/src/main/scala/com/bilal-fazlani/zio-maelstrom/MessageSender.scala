package com.bilalfazlani.zioMaelstrom

import protocol.*
import zio.*
import zio.json.{EncoderOps, JsonEncoder, JsonDecoder}

enum ResponseError:
  case MessageTimeoutError(messageId: MessageId, remote: NodeId, timeout: Duration)
  case DecodingError(error: String, message: GenericMessage)

trait MessageSender:
  def send[A <: MessageBody: JsonEncoder](body: A, to: NodeId): UIO[Unit]

  def ask[I <: MessageWithId: JsonEncoder, O <: MessageWithReply: JsonDecoder](
      body: I,
      to: NodeId,
      timeout: Duration
  ): IO[ResponseError, O]

  def reply[I <: MessageWithId, O <: MessageWithReply: JsonEncoder](message: Message[I], reply: O): UIO[Unit]

  def broadcastAll[A <: MessageBody: JsonEncoder](body: A): UIO[Unit]

  def broadcastTo[A <: MessageBody: JsonEncoder](others: Seq[NodeId], body: A): UIO[Unit]

object MessageSender:
  val live: ZLayer[Context & MessageTransport & Hooks, Nothing, MessageSenderLive] = ZLayer.fromFunction(MessageSenderLive.apply)

  def send[A <: MessageBody: JsonEncoder](body: A, to: NodeId): URIO[MessageSender, Unit] =
    ZIO.serviceWithZIO[MessageSender](_.send(body, to))

  def reply[I <: MessageWithId, O <: MessageWithReply: JsonEncoder](message: Message[I], reply: O): URIO[MessageSender, Unit] =
    ZIO.serviceWithZIO[MessageSender](_.reply(message, reply))

  def broadcastAll[A <: MessageBody: JsonEncoder](body: A): URIO[MessageSender, Unit] =
    ZIO.serviceWithZIO[MessageSender](_.broadcastAll(body))

  def broadcastTo[A <: MessageBody: JsonEncoder](others: Seq[NodeId], body: A): URIO[MessageSender, Unit] =
    ZIO.serviceWithZIO[MessageSender](_.broadcastTo(others, body))

case class MessageSenderLive(context: Context, transport: MessageTransport, hooks: Hooks) extends MessageSender:
  def send[A <: MessageBody: JsonEncoder](body: A, to: NodeId) =
    val message: Message[A] = Message[A](
      source = context.me,
      destination = to,
      body = body
    )
    transport.transport(message)

  def ask[I <: MessageWithId: JsonEncoder, O <: MessageWithReply: JsonDecoder](
      body: I,
      to: NodeId,
      timeout: Duration
  ): IO[ResponseError, O] =
    send(body, to).flatMap(_ => hooks.awaitMessage(body.msg_id, to, timeout).map(_.body))

  def reply[I <: MessageWithId, O <: MessageWithReply: JsonEncoder](message: Message[I], reply: O): UIO[Unit] =
    send(reply, message.source)

  def broadcastAll[A <: MessageBody: JsonEncoder](body: A) =
    broadcastTo(context.others, body)

  def broadcastTo[A <: MessageBody: JsonEncoder](others: Seq[NodeId], body: A) =
    ZIO.foreachPar(others)(to => send(body, to)).unit
