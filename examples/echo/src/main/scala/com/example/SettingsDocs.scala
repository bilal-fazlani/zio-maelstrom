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

    override val configure = NodeConfig
      .withConcurrency(100)
      .withLogLevelDebug
      .withPlaintextLog

    val program = ZIO.logDebug("Starting node...")
  }
}
