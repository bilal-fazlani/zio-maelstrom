package com.example.kvstore

import com.bilalfazlani.zioMaelstrom.*
import zio.*

object SeqKvExample extends MaelstromNode {
  val newMessageId = Random.nextInt.map(MessageId(_))

  val program: ZIO[MaelstromRuntime, AskError, Unit] = for
    _             <- SeqKv.write("counter", 1, 5.seconds)
    counterValue1 <- SeqKv.read[Int]("counter", 5.seconds)
    _             <- logInfo(s"counter value is $counterValue1")
    _             <- SeqKv.cas("counter", 1, 3, false, 5.seconds)
    counterValue2 <- SeqKv.read[Int]("counter", 5.seconds)
    _             <- logInfo(s"counter value is $counterValue2")
  yield ()
}
