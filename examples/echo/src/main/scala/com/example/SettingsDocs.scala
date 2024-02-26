package com.example

import com.bilalfazlani.zioMaelstrom.*
import zio.*

object DefaultSettingsDocs {
  object MainApp extends MaelstromNode {
    val program = ???
  }
}

object CustomSettingsDocs {
  object MainApp extends MaelstromNode {

    val program = ZIO.logDebug("Starting node")

    override val configure = NodeConfig
      .withStaticContext(NodeId("node1"), NodeId("node2"), NodeId("node3"), NodeId("node4"))
      .withConcurrency(1)
      .withLogLevelDebug
      .withPlaintextLog
  }
}
