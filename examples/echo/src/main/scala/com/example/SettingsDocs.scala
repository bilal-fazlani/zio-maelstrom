package com.example

import com.bilalfazlani.zioMaelstrom.*
import com.bilalfazlani.zioMaelstrom.protocol.*
import zio.*

object DefaultSettingsDocs {
  object MainApp extends ZIOAppDefault {

    val nodeProgram: ZIO[MaelstromRuntime, Nothing, Unit] = ???

    val run = nodeProgram.provide(MaelstromRuntime.live)
  }
}

object CustomSettingsDocs {
  object MainApp extends ZIOAppDefault {

    val nodeProgram: ZIO[MaelstromRuntime, Nothing, Unit] = ???

    val settings = Settings(
      logLevel = NodeLogLevel.Debug,
      logFormat = LogFormat.Plain,
      concurrency = 1
    )

    val run = nodeProgram.provide(MaelstromRuntime.live(settings))
  }
}
