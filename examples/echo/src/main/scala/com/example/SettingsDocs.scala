package com.example

import zio.*
import com.bilalfazlani.zioMaelstrom.*

object DefaultSettingsDocs {
  object MainApp extends MaelstromNode {
    val program = ???
  }
}

object CustomSettingsDocs {
  object MainApp extends MaelstromNode {

    override val configure = NodeConfig
      .withConcurrency(100)
      .withLogLevelDebug
      .withPlaintextLog
      .withAskTimeout(1.second)

    val program = ZIO.logDebug("Starting node...")
  }
}
