package com.example.pingpong

import zio.json.*
import zio.*
import com.bilalfazlani.zioMaelstrom.protocol.*
import com.bilalfazlani.zioMaelstrom.*

case class Ping(msg_id: MessageId, `type`: String = "ping") extends Sendable, ReplyableTo derives JsonEncoder
case class Pong(in_reply_to: MessageId)                     extends Reply derives JsonDecoder

object PingPong extends ZIOAppDefault:
  val ping = NodeId("c4")
    .ask[Pong](Ping(MessageId(6)), 5.seconds)
    .flatMap(_ => logInfo(s"PONG RECEIVED"))
    .tapError(err => logError(s"ERROR: $err"))
    .catchAll(_ => ZIO.unit)

  val run = ping.provideSome[Scope](MaelstromRuntime.live)
