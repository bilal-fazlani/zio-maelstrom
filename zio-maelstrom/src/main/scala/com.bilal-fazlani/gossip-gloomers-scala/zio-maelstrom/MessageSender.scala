package com.bilalfazlani.zioMaelstrom

import protocol.*
import zio.json.JsonEncoder
import zio.Task
import zio.Console.printLine
import zio.json.EncoderOps
import zio.ZIO
import zio.ZLayer

trait MessageSender:
  def send[A <: MessageBody: JsonEncoder](body: A, to: NodeId): Task[Unit]

  def reply[I <: MessageWithId, O <: MessageWithReply: JsonEncoder](message: Message[I], reply: O): Task[Unit]

  def broadcastAll[A <: MessageBody: JsonEncoder](body: A): Task[Unit]

  def broadcastTo[A <: MessageBody: JsonEncoder](others: Seq[NodeId], body: A): Task[Unit]

object MessageSender:
  val live: ZLayer[Debugger & Context & MessageTransport, Nothing, MessageSenderLive] = ZLayer.fromFunction(MessageSenderLive.apply)

case class MessageSenderLive(debugger: Debugger, context: Context, transporter: MessageTransport) extends MessageSender:
  def send[A <: MessageBody: JsonEncoder](body: A, to: NodeId) =
    val message: Message[A] = Message[A](
      source = context.me,
      destination = to,
      body = body
    )
    transporter.transport(message)

  def reply[I <: MessageWithId, O <: MessageWithReply: JsonEncoder](message: Message[I], reply: O): Task[Unit] =
    send(reply, message.source)

  def broadcastAll[A <: MessageBody: JsonEncoder](body: A) =
    broadcastTo(context.others, body)

  def broadcastTo[A <: MessageBody: JsonEncoder](others: Seq[NodeId], body: A) =
    ZIO.foreachPar(others)(to => send(body, to)).unit
