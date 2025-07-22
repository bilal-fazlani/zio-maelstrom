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

  // ReadCustomTimeout {
  // Custom timeout overrides the default timeout
  val counterValueCustom: ZIO[SeqKv, AskError, Int] = SeqKv.read("counter", 30.millis)
  // }

  // WriteCustomTimeout {
  val _: ZIO[SeqKv, AskError, Unit] = SeqKv.write("counter", 1, 50.millis)
  // }

  // CasCustomTimeout {
  val _: ZIO[SeqKv, AskError, Unit] = SeqKv.cas("counter", 1, 3, createIfNotExists = true, 75.millis)
  // }
}
