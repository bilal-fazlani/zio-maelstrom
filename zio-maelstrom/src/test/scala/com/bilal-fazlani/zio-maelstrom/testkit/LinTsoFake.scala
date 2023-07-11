package com.bilalfazlani.zioMaelstrom
package testkit

import zio.*
import protocol.*

case class LinTsoFake(ref: Ref[Long]) extends LinTso:
  override def ts(messageId: MessageId, timeout: Duration): ZIO[Any, AskError, Long] =
    ref.updateAndGet(_ + 1)

object LinTsoFake:
  val make = ZLayer.fromZIO(Ref.make(0L).map { r =>
    val impl = LinTsoFake(r)
    new LinTso:
      export impl.*
  })