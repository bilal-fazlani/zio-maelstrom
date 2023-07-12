package com.bilalfazlani.zioMaelstrom
package testkit

import zio.*
import protocol.*

case class LinTsoFake(ref: Ref[Int]) extends LinTso:
  override def ts(messageId: MessageId, timeout: Duration): ZIO[Any, AskError, Int] =
    ref.updateAndGet(_ + 1)

object LinTsoFake:
  val make = ZLayer.fromZIO(Ref.make(0).map { r =>
    val impl = LinTsoFake(r)
    new LinTso:
      export impl.*
  })