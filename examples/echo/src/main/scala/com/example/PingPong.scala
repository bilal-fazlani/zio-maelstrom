package com.example.pingpong

import zio.json.*
import zio.*
import com.bilalfazlani.zioMaelstrom.protocol.*
import com.bilalfazlani.zioMaelstrom.*

case class Ping(msg_id: MessageId, `type`: String = "ping") extends Sendable, NeedsReply
    derives JsonEncoder
case class Pong(in_reply_to: MessageId) extends Reply derives JsonDecoder

object PingPong extends ZIOAppDefault:

  val timeout = (for {
    _ <- NodeId("c4")
      .ask[Pong](Ping(MessageId(6)), 3.seconds)
    _ <- logInfo(s"PONG RECEIVED")
  } yield ())
    .catchAll { (e: AskError) =>
      logError(s"pong error: $e") *>
        exit(ExitCode.failure)
    }

  val interruption = for {
    _ <- NodeId("c4")
      .ask[Pong](Ping(MessageId(6)), 5.seconds)
      .disconnect raceFirst (ZIO.fail("boom").delay(1.second).disconnect)
    _ <- logInfo(s"PONG RECEIVED")
  } yield ()

  val run = interruption.provide(
    MaelstromRuntime.live
  ) *> exit(ExitCode.success)
