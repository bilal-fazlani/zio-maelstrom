package com.bilalfazlani.zioMaelstrom

import testkit.MaelstromSpec
import zio.*
import zio.test.*

object InitializationTest extends MaelstromSpec {

  val q: ZLayer[Any, Nothing, Queue[String]] = ZLayer(Queue.unbounded[String])
  val is: ZLayer[Any, Nothing, InputStream]  = q >>> InputStream.queue
  val tRuntime                               = testRuntime(Settings(), is, q)

  val sendInit =
    q >>> ZLayer(
      ZIO.serviceWithZIO[Queue[String]](
        _.offer(
          """{"src":"n1","dest":"n2","body":{"type":"init","node_id":"n1","node_ids":["n1","n2"]}}"""
        ).unit
      )
    )

  val spec = suite("Initialization Tests")(
    test("successfully parse init message") {
      for
        _    <- ZIO.debug("starting test")
        myId <- MaelstromRuntime.me
        _    <- ZIO.debug(myId)
      yield assertTrue(true)
    }.provide(sendInit >>> tRuntime)
  ) @@ TestAspect.timeout(10.seconds) @@ TestAspect.sequential
}
