package com.example.echo

import zio.json.*
import zio.*
import com.bilalfazlani.zioMaelstrom.protocol.*
import com.bilalfazlani.zioMaelstrom.*

case class Echo(echo: String, msg_id: MessageId) extends Replyable derives JsonDecoder

case class EchoOk(echo: String, in_reply_to: MessageId, `type`: String = "echo_ok") extends Sendable, Reply derives JsonEncoder

object Main extends ZIOAppDefault:
  val echoHandler = receive[Echo](msg => msg reply EchoOk(echo = msg.echo, in_reply_to = msg.msg_id))
  val run         = echoHandler.provideSome[Scope](MaelstromRuntime.live)
