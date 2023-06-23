package com.example

import zio.*
import com.bilalfazlani.zioMaelstrom.*
import com.bilalfazlani.zioMaelstrom.protocol.*
import zio.json.*

object Main extends ZIOAppDefault {

  case class Ping(msg_id: MessageId) extends NeedsReply derives JsonDecoder

  case class Pong(in_reply_to: MessageId, `type`: String = "pong") extends Sendable, Reply
      derives JsonEncoder

  val program = receive[Ping](ping => reply(Pong(ping.msg_id)))

  val run = program.provide(
    MaelstromRuntime.usingFile(
      "examples" / "echo" / "fileinput.txt",
      Settings(logLevel = NodeLogLevel.Debug)
    )
  )
}
