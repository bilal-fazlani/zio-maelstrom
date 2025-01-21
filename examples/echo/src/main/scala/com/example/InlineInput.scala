package com.example

import zio.*
import zio.json.*
import com.bilalfazlani.zioMaelstrom.*
import com.bilalfazlani.zioMaelstrom.InputStream.InlineMessage

object InlineInput extends MaelstromNode {

  case class Ping(msg_id: MessageId) extends NeedsReply derives JsonCodec // (1)!

  case class Pong(in_reply_to: MessageId, `type`: String = "pong") extends Sendable, Reply
      derives JsonEncoder

  val program = receive[Ping](ping => reply(Pong(ping.msg_id)))

  override val configure: NodeConfig =
    NodeConfig
      .withStaticInput(
        NodeId("A"),                                    // (2)!
        Set(NodeId("B"), NodeId("C")),                  // (3)!
        InlineMessage(NodeId("B"), Ping(MessageId(1))), // (4)!
        InlineMessage(NodeId("C"), Ping(MessageId(2)))
      )
}
