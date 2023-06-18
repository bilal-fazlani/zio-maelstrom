package com.example.pingpong

import zio.json.*
import zio.*
import com.bilalfazlani.zioMaelstrom.protocol.*
import com.bilalfazlani.zioMaelstrom.*

case class Ping(msg_id: MessageId, `type`: String = "ping") extends Sendable, NeedsReply
    derives JsonEncoder
case class Pong(in_reply_to: MessageId) extends Reply derives JsonDecoder

object PingPong extends ZIOAppDefault:

  val ping = NodeId("c4")
    .ask[Pong](Ping(MessageId(6)), 4.seconds)
    .flatMap(_ => logInfo(s"PONG RECEIVED"))
    .catchAll(_ => ZIO.unit)
    .flatMap(_ => exit(ExitCode.success))

  val run = ping.provide(
    MaelstromRuntime.live(
      Settings(
        nodeInput = NodeInput.FilePath("examples" / "echo" / "ping-pong.txt"),
        logLevel = NodeLogLevel.Debug
      )
    )
  ) *> exit(ExitCode.success)
