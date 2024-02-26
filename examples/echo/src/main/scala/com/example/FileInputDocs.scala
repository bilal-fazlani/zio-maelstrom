package com.example

import zio.*
import com.bilalfazlani.zioMaelstrom.*
import zio.json.*

object Main extends MaelstromNode {

  case class Ping(msg_id: MessageId) extends NeedsReply derives JsonDecoder

  case class Pong(in_reply_to: MessageId, `type`: String = "pong") extends Sendable, Reply
      derives JsonEncoder

  val program = receive[Ping](ping => reply(Pong(ping.msg_id)))

  override val configure: NodeConfig =
    NodeConfig.withFileInput("examples" / "echo" / "fileinput.txt").withLogLevelDebug
}
