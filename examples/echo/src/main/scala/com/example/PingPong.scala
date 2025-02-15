package com.example.pingpong

import zio.json.*
import zio.*
import com.bilalfazlani.zioMaelstrom.*

case class Ping(msg_id: MessageId, `type`: String = "ping") extends Sendable, NeedsReply
    derives JsonEncoder
case class Pong(in_reply_to: MessageId) extends Reply derives JsonDecoder

object PingPong extends MaelstromNode:

  val timeout = (for {
    _ <- NodeId("c4")
      .ask[Pong](Ping(MessageId(6)), 3.seconds)
    _ <- ZIO.logInfo(s"PONG RECEIVED")
  } yield ())
    .catchAll { (e: AskError) =>
      ZIO.logError(s"pong error: $e") *>
        exit(ExitCode.failure)
    }

  val program = for {
    _ <- NodeId("c4")
      .ask[Pong](Ping(MessageId(6)), 5.seconds)
      .disconnect raceFirst (ZIO.fail("boom").delay(1.second).disconnect)
    _ <- ZIO.logInfo(s"PONG RECEIVED")
  } yield ()
