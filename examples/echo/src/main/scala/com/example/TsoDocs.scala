package com.example.tso

import com.bilalfazlani.zioMaelstrom.*
import zio.*

object TsoExample {
  val program: ZIO[MaelstromRuntime, AskError, Unit] = for
    msgId1         <- Random.nextIntBounded(Int.MaxValue).map(MessageId(_))
    timestamp: Int <- LinTso.ts(5.seconds)
    _              <- logInfo(s"timestamp is $timestamp")
  yield ()
}
