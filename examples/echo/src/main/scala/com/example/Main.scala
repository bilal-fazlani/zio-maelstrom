package com.example.echo

import zio.json.*
import zio.*
import com.bilalfazlani.zioMaelstrom.protocol.*
import com.bilalfazlani.zioMaelstrom.*

case class Echo(echo: String, msg_id: MessageId, `type`: String) extends MessageWithId derives JsonDecoder

case class EchoOk(echo: String, in_reply_to: MessageId, `type`: String = "echo_ok") extends MessageWithReply derives JsonEncoder

case class Ping(msg_id: MessageId, `type`: String = "ping") extends MessageWithId derives JsonEncoder
case class Pong(in_reply_to: MessageId, `type`: String = "pong") extends MessageWithReply derives JsonDecoder

object Main extends ZIOAppDefault:
  // val app = MaelstromRuntime.run(MaelstromApp.make[Echo](in => in reply EchoOk(echo = in.body.echo, in_reply_to = in.body.msg_id)))

  val ping = NodeId("c4")
    .ask[Ping, Pong](Ping(MessageId(6)), 1.minute)
    .flatMap(_ => logInfo(s"PONG RECEIVED"))
    .tapError(err => logError(s"ERROR: $err"))

  // (NodeId("c5") ask Ping(MessageId(123))).delay(2.seconds).forever

  val run = ping.provideSome[Scope](MaelstromRuntime.live, Settings.custom(NodeInput.StdIn, true))
