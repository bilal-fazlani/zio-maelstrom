package com.example

import zio.*
import com.bilalfazlani.zioMaelstrom.*

object ContextDocs extends MaelstromNode {

  override val program: ZIO[MaelstromRuntime, Nothing, Unit] = ???

  override val context =
    NodeContext.Static(NodeId("node1"), Set(NodeId("node2"), NodeId("node3"), NodeId("node4")))
}
