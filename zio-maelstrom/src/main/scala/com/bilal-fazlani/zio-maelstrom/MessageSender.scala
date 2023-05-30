package com.bilalfazlani.zioMaelstrom

import protocol.*
import zio.json.JsonEncoder
import zio.*
import zio.json.EncoderOps

trait MessageSender:
  def send[A <: MessageBody: JsonEncoder](body: A, to: NodeId): UIO[Unit]

  def reply[I <: MessageWithId, O <: MessageWithReply: JsonEncoder](message: Message[I], reply: O): UIO[Unit]

  def broadcastAll[A <: MessageBody: JsonEncoder](body: A): UIO[Unit]

  def broadcastTo[A <: MessageBody: JsonEncoder](others: Seq[NodeId], body: A): UIO[Unit]

object MessageSender:
  val live: ZLayer[Context & MessageTransport, Nothing, MessageSenderLive] = ZLayer.fromFunction(MessageSenderLive.apply)

  def send[A <: MessageBody: JsonEncoder](body: A, to: NodeId): URIO[MessageSender, Unit] =
    ZIO.serviceWithZIO[MessageSender](_.send(body, to))

  def reply[I <: MessageWithId, O <: MessageWithReply: JsonEncoder](message: Message[I], reply: O): URIO[MessageSender, Unit] =
    ZIO.serviceWithZIO[MessageSender](_.reply(message, reply))

  def broadcastAll[A <: MessageBody: JsonEncoder](body: A): URIO[MessageSender, Unit] =
    ZIO.serviceWithZIO[MessageSender](_.broadcastAll(body))

  def broadcastTo[A <: MessageBody: JsonEncoder](others: Seq[NodeId], body: A): URIO[MessageSender, Unit] =
    ZIO.serviceWithZIO[MessageSender](_.broadcastTo(others, body))

case class MessageSenderLive(context: Context, transporter: MessageTransport) extends MessageSender:
  def send[A <: MessageBody: JsonEncoder](body: A, to: NodeId) =
    val message: Message[A] = Message[A](
      source = context.me,
      destination = to,
      body = body
    )
    transporter.transport(message)

  def reply[I <: MessageWithId, O <: MessageWithReply: JsonEncoder](message: Message[I], reply: O): UIO[Unit] =
    send(reply, message.source)

  def broadcastAll[A <: MessageBody: JsonEncoder](body: A) =
    broadcastTo(context.others, body)

  def broadcastTo[A <: MessageBody: JsonEncoder](others: Seq[NodeId], body: A) =
    ZIO.foreachPar(others)(to => send(body, to)).unit
