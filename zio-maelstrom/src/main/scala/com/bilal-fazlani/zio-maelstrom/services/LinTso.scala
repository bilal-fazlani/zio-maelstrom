package com.bilalfazlani.zioMaelstrom

import protocol.*
import zio.*
import zio.json.*

private case class Ts(msg_id: MessageId, `type`: String = "ts") extends Sendable, NeedsReply
    derives JsonEncoder
private case class TsOk(in_reply_to: MessageId, ts: Long) extends Reply derives JsonDecoder

trait LinTso:
  def ts(messageId: MessageId, timeout: Duration): ZIO[Any, AskError, Long]

object LinTso:
  def ts(messageId: MessageId, timeout: Duration): ZIO[LinTso, AskError, Long] =
    ZIO.serviceWithZIO(_.ts(messageId, timeout))

  private[zioMaelstrom] val live: ZLayer[MessageSender, Nothing, LinTso] = 
    ZLayer.fromFunction(LinTsoImpl.apply)

private case class LinTsoImpl(sender: MessageSender) extends LinTso:
  def ts(messageId: MessageId, timeout: Duration): ZIO[Any, AskError, Long] =
    sender.ask[Ts, TsOk](Ts(messageId), NodeId("lin-tso"), timeout).map(_.ts)
