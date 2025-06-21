package com.example.calculator

import com.bilalfazlani.zioMaelstrom.*
import zio.json.*
import zio.*

// format: off

// in_messages {
@jsonDiscriminator("type")
sealed trait CalculatorMessage derives JsonDecoder

@jsonHint("add") case class Add(a: Int, b: Int) extends CalculatorMessage

@jsonHint("subtract") case class Subtract(a: Int, b: Int) extends CalculatorMessage

@jsonHint("multiply") case class Multiply(a: Int, b: Int) extends CalculatorMessage

@jsonHint("divide") case class Divide(a: Int, b: Int) extends CalculatorMessage
//}

// out_messages {
case class AddOk(result: Int) derives JsonEncoder

case class SubtractOk(result: Int) derives JsonEncoder

case class MultiplyOk(result: Int) derives JsonEncoder

case class DivideOk(result: Int) derives JsonEncoder
//}

// format: on

object Calculator extends MaelstromNode:

  override val configure: NodeConfig =
    NodeConfig
      .withConcurrency(1)
      .withStaticInput(NodeId("B"), Set(), "examples" / "echo" / "calculator.txt")

  val program = receive[CalculatorMessage] {
    case add: Add           => reply(AddOk(add.a + add.b))
    case subtract: Subtract => reply(SubtractOk(subtract.a - subtract.b))
    case multiply: Multiply => reply(MultiplyOk(multiply.a * multiply.b))
    case divide: Divide =>
      if divide.b == 0 then
        reply(ErrorMessage(summon[Option[MessageId]].get, ErrorCode.Custom(55), "divide by zero"))
      else reply(DivideOk(divide.a / divide.b))
  }
