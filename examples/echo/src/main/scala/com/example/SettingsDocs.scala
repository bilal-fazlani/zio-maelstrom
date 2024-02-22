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

    override val context =
      NodeContext.Static(NodeId("node1"), Set(NodeId("node2"), NodeId("node3"), NodeId("node4")))

    override val concurrency = 1               // default: 1024
    override val logLevel    = LogLevel.Debug  // default: LogLevel.Info
    override val logFormat   = LogFormat.Plain // default: LogFormat.Colored
  }
}
