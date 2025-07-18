package com.bilalfazlani.zioMaelstrom
package testkit

import com.bilalfazlani.zioMaelstrom.services.LinTso
import zio.*

case class LinTsoFake(ref: Ref[Int]) extends LinTso:
  override def ts: ZIO[Any, AskError, Int] =
    ref.updateAndGet(_ + 1)

object LinTsoFake:
  val make = ZLayer(Ref.make(0).map { r =>
    val impl = LinTsoFake(r)
    new LinTso:
      export impl.*
  })
