package com.example.calculator

import zio.json.*
import zio.*
import com.bilalfazlani.zioMaelstrom.protocol.*
import com.bilalfazlani.zioMaelstrom.*

// IN MESSAGES
@jsonDiscriminator("type") sealed trait CalculatorMessage                    extends NeedsReply derives JsonDecoder
@jsonHint("add") case class Add(a: Int, b: Int, msg_id: MessageId)           extends CalculatorMessage derives JsonDecoder
@jsonHint("subtract") case class Subtract(a: Int, b: Int, msg_id: MessageId) extends CalculatorMessage derives JsonDecoder
@jsonHint("multiply") case class Multiply(a: Int, b: Int, msg_id: MessageId) extends CalculatorMessage derives JsonDecoder
@jsonHint("divide") case class Divide(a: Int, b: Int, msg_id: MessageId)     extends CalculatorMessage derives JsonDecoder

// OUT MESSAGES
case class AddOk(result: Int, in_reply_to: MessageId, `type`: String = "add_ok")           extends Sendable, Reply derives JsonEncoder
case class SubtractOk(result: Int, in_reply_to: MessageId, `type`: String = "subtract_ok") extends Sendable, Reply derives JsonEncoder
case class MultiplyOk(result: Int, in_reply_to: MessageId, `type`: String = "multiply_ok") extends Sendable, Reply derives JsonEncoder
case class DivideOk(result: Int, in_reply_to: MessageId, `type`: String = "divide_ok")     extends Sendable, Reply derives JsonEncoder

object Calculator extends ZIOAppDefault:
  val run = receive[CalculatorMessage] {
    case add: Add           => add reply AddOk(add.a + add.b, add.msg_id)
    case subtract: Subtract => subtract reply SubtractOk(subtract.a - subtract.b, subtract.msg_id)
    case multiply: Multiply => multiply reply MultiplyOk(multiply.a * multiply.b, multiply.msg_id)
    case divide: Divide =>
      if divide.b == 0 then divide reply ErrorMessage(divide.msg_id, ErrorCode.Custom(55), "divide by zero")
      else divide reply DivideOk(divide.a / divide.b, divide.msg_id)
  }.provideSome[Scope](MaelstromRuntime.live)
