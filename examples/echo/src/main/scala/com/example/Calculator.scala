package com.example.calculator

import zio.json.*
import zio.*
import com.bilalfazlani.zioMaelstrom.protocol.*
import com.bilalfazlani.zioMaelstrom.*

// format: off

// in_messages {
@jsonDiscriminator("type")
sealed trait CalculatorMessage extends NeedsReply derives JsonDecoder

@jsonHint("add") case class Add(a: Int, b: Int, msg_id: MessageId) extends CalculatorMessage

@jsonHint("subtract") case class Subtract(a: Int, b: Int, msg_id: MessageId) extends CalculatorMessage

@jsonHint("multiply") case class Multiply(a: Int, b: Int, msg_id: MessageId) extends CalculatorMessage

@jsonHint("divide") case class Divide(a: Int, b: Int, msg_id: MessageId) extends CalculatorMessage
//}

// out_messages {
case class AddOk(result: Int, in_reply_to: MessageId, `type`: String = "add_ok") 
  extends Sendable, Reply derives JsonEncoder

case class SubtractOk(result: Int, in_reply_to: MessageId, `type`: String = "subtract_ok") 
  extends Sendable, Reply derives JsonEncoder

case class MultiplyOk(result: Int, in_reply_to: MessageId, `type`: String = "multiply_ok") 
  extends Sendable, Reply derives JsonEncoder

case class DivideOk(result: Int, in_reply_to: MessageId, `type`: String = "divide_ok") 
  extends Sendable, Reply derives JsonEncoder
//}

// format: on

object Calculator extends ZIOAppDefault:
  val run = receive[CalculatorMessage] {
    case add: Add           => reply(AddOk(add.a + add.b, add.msg_id))
    case subtract: Subtract => reply(SubtractOk(subtract.a - subtract.b, subtract.msg_id))
    case multiply: Multiply => reply(MultiplyOk(multiply.a * multiply.b, multiply.msg_id))
    case divide: Divide =>
      if divide.b == 0 then
        reply(ErrorMessage(divide.msg_id, ErrorCode.Custom(55), "divide by zero"))
      else reply(DivideOk(divide.a / divide.b, divide.msg_id))
  }.provide(
    MaelstromRuntime.usingFile("examples" / "echo" / "calculator.txt", Settings(concurrency = 1))
  )
