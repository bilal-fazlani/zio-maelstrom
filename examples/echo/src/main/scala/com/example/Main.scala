package com.example.echo

import zio.json.*
import zio.*
import com.bilalfazlani.zioMaelstrom.protocol.*
import com.bilalfazlani.zioMaelstrom.*

//------ echo -------

case class Echo(echo: String, msg_id: MessageId, `type`: String) extends MessageWithId derives JsonDecoder

case class EchoOk(echo: String, in_reply_to: MessageId, `type`: String = "echo_ok") extends MessageWithReply derives JsonEncoder

object Main extends ZIOAppDefault:
  val echoHandler = receive[Echo](msg => msg reply EchoOk(echo = msg.echo, in_reply_to = msg.msg_id))
  val run         = echoHandler.provideSome[Scope](MaelstromRuntime.live)

//------ ping pong -------

case class Ping(msg_id: MessageId, `type`: String = "ping")      extends MessageWithId derives JsonEncoder
case class Pong(in_reply_to: MessageId, `type`: String = "pong") extends MessageWithReply derives JsonDecoder

object PingPong extends ZIOAppDefault:
  val ping = NodeId("c4")
    .ask[Pong](Ping(MessageId(6)), 5.seconds)
    .flatMap(_ => logInfo(s"PONG RECEIVED"))
    .tapError(err => logError(s"ERROR: $err"))
    .catchAll(_ => ZIO.unit)

  val run = ping.provideSome[Scope](MaelstromRuntime.live)

//------ calculator -------

@jsonDiscriminator("type")
sealed trait CalculatorMessage extends MessageBody derives JsonDecoder

@jsonHint("add")
case class Add(a: Int, b: Int, msg_id: MessageId, `type`: String) extends CalculatorMessage, MessageWithId derives JsonDecoder

@jsonHint("subtract")
case class Subtract(a: Int, b: Int, msg_id: MessageId, `type`: String) extends CalculatorMessage, MessageWithId derives JsonDecoder

case class AddOk(result: Int, in_reply_to: MessageId, `type`: String = "add_ok") extends MessageWithReply derives JsonEncoder

case class SubtractOk(result: Int, in_reply_to: MessageId, `type`: String = "subtract_ok") extends MessageWithReply derives JsonEncoder

object Calculator extends ZIOAppDefault:
  val run = receive[CalculatorMessage] {
    case add: Add           => add reply AddOk(add.a + add.b, add.msg_id)
    case subtract: Subtract => subtract reply SubtractOk(subtract.a - subtract.b, subtract.msg_id)
  }.provideSome[Scope](MaelstromRuntime.live)
