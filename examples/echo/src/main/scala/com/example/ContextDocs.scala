package com.example

import zio.*
import com.bilalfazlani.zioMaelstrom.*

object ContextDocs extends MaelstromNode {

  override val program: ZIO[MaelstromRuntime, Nothing, Unit] = ???

  override val configure =
    NodeConfig.withStaticContext(NodeId("node1"), NodeId("node2"), NodeId("node3"), NodeId("node4"))
}
