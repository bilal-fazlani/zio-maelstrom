package com.example.echo

import zio.json.*
import zio.*
import com.bilalfazlani.zioMaelstrom.protocol.*
import com.bilalfazlani.zioMaelstrom.*

case class Echo(echo: String, msg_id: MessageId, `type`: String) extends MessageWithId derives JsonDecoder

case class EchoOk(echo: String, in_reply_to: MessageId, `type`: String = "echo_ok") extends MessageWithReply derives JsonEncoder

case class Ping(msg_id: MessageId, `type`: String = "ping") extends MessageWithId derives JsonEncoder
case class Pong(in_reply_to: MessageId, `type`: String = "pong") extends MessageWithReply derives JsonDecoder

object PingPong extends ZIOAppDefault:
  val ping = NodeId("c4")
    .ask[Pong](Ping(MessageId(6)), 5.seconds)
    .flatMap(_ => logInfo(s"PONG RECEIVED"))
    .tapError(err => logError(s"ERROR: $err"))
    .catchAll(_ => ZIO.unit)

  val run = ping.provideSome[Scope](MaelstromRuntime.live)

object Main extends ZIOAppDefault:
  val handler = receive[Echo] { msg =>
    msg reply EchoOk(echo = msg.body.echo, in_reply_to = msg.body.msg_id)
  }
  val run = handler.provideSome[Scope](MaelstromRuntime.live)
