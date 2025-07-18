package com.example.kvstore

import com.bilalfazlani.zioMaelstrom.*
import com.bilalfazlani.zioMaelstrom.services.{LinKv, LwwKv, SeqKv}
import zio.*

object SeqKvExample {
  // read {
  val counterValue: ZIO[SeqKv, AskError, Int] = SeqKv.read("counter")
  // }

  // ReadOption {
  val counterMaybe: ZIO[SeqKv, AskError, Option[Int]] = SeqKv.readOption("counter")
  // }

  // write {
  val _: ZIO[LwwKv, AskError, Unit] = LwwKv.write("counter", 1)
  // }

  // WriteIfNotExists {
  val _: ZIO[LwwKv, AskError, Unit] = LwwKv.writeIfNotExists("counter", 1)
  // }

  // cas {
  val _: ZIO[LinKv, AskError, Unit] =
    LinKv.cas(key = "counter", from = 1, to = 3, createIfNotExists = false)
  // }

  // update {
  val increasedValue: ZIO[SeqKv, AskError, Int] = SeqKv.update("counter") {
    case Some(oldValue) => oldValue + 1
    case None           => 1
  }
  // }

  // UpdateZIO {
  def getNewNumber(oldValue: Option[Int]): ZIO[Any, Nothing, Int] = ???

  val increasedValueZIO: ZIO[SeqKv, AskError, Int] =
    SeqKv.updateZIO("counter")(getNewNumber)
  // }
}
