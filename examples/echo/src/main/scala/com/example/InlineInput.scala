package com.example

import zio.*
import zio.json.*
import com.bilalfazlani.zioMaelstrom.*
import com.bilalfazlani.zioMaelstrom.InputStream.InlineMessage
import com.bilalfazlani.zioMaelstrom.models.Body
import com.bilalfazlani.zioMaelstrom.models.MsgName

object InlineInput extends MaelstromNode {

  case class Ping() derives JsonCodec // (1)!

  case class Pong() derives JsonEncoder

  val program = receive[Ping](_ => reply(Pong()))

  override val configure: NodeConfig =
    NodeConfig
      .withStaticInput(
        NodeId("A"),                                                                       // (2)!
        Set(NodeId("B"), NodeId("C")),                                                     // (3)!
        InlineMessage(NodeId("B"), Body(MsgName[Ping], Ping(), Some(MessageId(1)), None)), // (4)!
        InlineMessage(NodeId("C"), Body(MsgName[Ping], Ping(), Some(MessageId(2)), None))
      )
}
