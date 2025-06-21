package com.example.pingpong

import zio.json.*
import zio.*
import com.bilalfazlani.zioMaelstrom.*

case class Ping() derives JsonEncoder
case class Pong() derives JsonDecoder

object PingPong extends MaelstromNode:

  val timeout = (for {
    pong: Pong <- NodeId("c4").ask[Pong](Ping(), 3.seconds)
    _ <- ZIO.logInfo(s"PONG RECEIVED")
  } yield ())
    .catchAll { (e: AskError) =>
      ZIO.logError(s"pong error: $e") *>
        exit(ExitCode.failure)
    }

  val program = for {
    _ <- NodeId("c4")
      .ask[Pong](Ping(), 5.seconds)
      .disconnect raceFirst (ZIO.fail("boom").delay(1.second).disconnect)
    _ <- ZIO.logInfo(s"PONG RECEIVED")
  } yield ()
