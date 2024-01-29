package com.example

import zio.*
import com.bilalfazlani.zioMaelstrom.*

object ContextDocs extends ZIOAppDefault {

  val program: ZIO[MaelstromRuntime, Nothing, Unit] = ???

  val run = program.provide(
    MaelstromRuntime.live(
      _.context(NodeId("node1"), Set(NodeId("node2"), NodeId("node3"), NodeId("node4")))
    )
  )
}
