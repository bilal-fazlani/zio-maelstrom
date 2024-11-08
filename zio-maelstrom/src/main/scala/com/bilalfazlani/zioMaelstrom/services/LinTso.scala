package com.bilalfazlani.zioMaelstrom.services

import com.bilalfazlani.zioMaelstrom.{AskError, MessageId, MessageIdStore, MessageSender, NeedsReply, NodeId, Reply, Sendable}
import zio.*
import zio.json.*

private case class Ts(msg_id: MessageId, `type`: String = "ts") extends Sendable, NeedsReply
    derives JsonEncoder
private case class TsOk(in_reply_to: MessageId, ts: Int) extends Reply derives JsonDecoder

trait LinTso:
  def ts(timeout: Duration): ZIO[Any, AskError, Int]

object LinTso:
  def ts(timeout: Duration): ZIO[LinTso, AskError, Int] =
    ZIO.serviceWithZIO(_.ts(timeout))

  private[zioMaelstrom] val live: ZLayer[MessageSender & MessageIdStore, Nothing, LinTso] =
    ZLayer.derive[LinTsoImpl]

private class LinTsoImpl(sender: MessageSender, messageIdStore: MessageIdStore) extends LinTso:
  def ts(timeout: Duration): ZIO[Any, AskError, Int] =
    messageIdStore.next.flatMap { messageId =>
      sender.ask[Ts, TsOk](Ts(messageId), NodeId("lin-tso"), timeout).map(_.ts)
    }
