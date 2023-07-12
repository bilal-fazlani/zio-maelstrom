package com.example.kvstore

import com.bilalfazlani.zioMaelstrom.*
import com.bilalfazlani.zioMaelstrom.protocol.*
import zio.*

object SeqKvExample {
  val newMessageId = Random.nextInt.map(MessageId(_))

  val program: ZIO[MaelstromRuntime, AskError, Unit] = for
    msgId1        <- newMessageId
    _             <- SeqKv.write("counter", 1, msgId1, 5.seconds)
    msgId2        <- newMessageId
    counterValue1 <- SeqKv.read[Int]("counter", msgId2, 5.seconds)
    _             <- logInfo(s"counter value is $counterValue1")
    msgId3        <- newMessageId
    _             <- SeqKv.cas("counter", 1, 3, false, msgId3, 5.seconds)
    msgId4        <- newMessageId
    counterValue2 <- SeqKv.read[Int]("counter", msgId4, 5.seconds)
    _             <- logInfo(s"counter value is $counterValue2")
  yield ()
}
