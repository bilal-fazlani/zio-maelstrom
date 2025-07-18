package com.bilalfazlani.zioMaelstrom.services

import com.bilalfazlani.zioMaelstrom.{AskError, MessageSender, NodeId}
import zio.*
import zio.json.*

private case class Ts() derives JsonEncoder
private case class TsOk(ts: Int) derives JsonDecoder

trait LinTso:
  def ts: ZIO[Any, AskError, Int]

object LinTso:
  def ts: ZIO[LinTso, AskError, Int] =
    ZIO.serviceWithZIO(_.ts)

  private[zioMaelstrom] val live: ZLayer[MessageSender, Nothing, LinTso] =
    ZLayer.derive[LinTsoImpl]

private class LinTsoImpl(sender: MessageSender) extends LinTso:
  def ts: ZIO[Any, AskError, Int] =
    sender.ask[Ts, TsOk](Ts(), NodeId("lin-tso")).map(_.ts)
