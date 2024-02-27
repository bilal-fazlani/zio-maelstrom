package com.example.kvstore

import com.bilalfazlani.zioMaelstrom.*
import zio.*

object SeqKvExample {
  // read {
  val counterValue: ZIO[SeqKv, AskError, Int] = SeqKv.read("counter", 5.seconds)
  // }

  // ReadOption {
  val counterMaybe: ZIO[SeqKv, AskError, Option[Int]] = SeqKv.readOption("counter", 5.seconds)
  // }

  // write {
  val _: ZIO[LwwKv, AskError, Unit] = LwwKv.write("counter", 1, 5.seconds)
  // }

  // WriteIfNotExists {
  val _: ZIO[LwwKv, AskError, Unit] = LwwKv.writeIfNotExists("counter", 1, 5.seconds)
  // }

  // cas {
  val _: ZIO[LinKv, AskError, Unit] =
    LinKv.cas(key = "counter", from = 1, to = 3, createIfNotExists = false, timeout = 5.seconds)
  // }

  // update {
  val increasedValue: ZIO[SeqKv, AskError, Int] = SeqKv.update("counter", 5.seconds) {
    case Some(oldValue) => oldValue + 1
    case None           => 1
  }
  // }

  // UpdateZIO {
  def getNewNumber(oldValue: Option[Int]): ZIO[Any, Nothing, Int] = ???

  val increasedValueZIO: ZIO[SeqKv, AskError, Int] =
    SeqKv.updateZIO("counter", 5.seconds)(getNewNumber)
  // }
}
