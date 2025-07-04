package com.example

import com.bilalfazlani.zioMaelstrom.*
import zio.*
import zio.json.*

object Main extends MaelstromNode {

  case class Ping() derives JsonDecoder

  case class Pong() derives JsonEncoder

  val program = receive[Ping](_ => reply(Pong()))

  override val configure: NodeConfig =
    NodeConfig
      .withStaticInput(
        NodeId("A"),                          // (1)!
        Set(NodeId("B"), NodeId("C")),        // (2)!
        "examples" / "echo" / "fileinput.txt" // (3)!
      )
      .withLogLevelDebug
}
