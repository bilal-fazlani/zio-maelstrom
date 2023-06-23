package com.example

import zio.*
import com.bilalfazlani.zioMaelstrom.*

object Main extends ZIOAppDefault {
  val program = for
    _ <- logDebug("Starting node")
    _ <- logInfo("Received message")
    _ <- logWarn("Something is wrong")
    _ <- logError("Something is really wrong")
  yield ()

  val run = program.provide(
    MaelstromRuntime.live(Settings(logLevel = NodeLogLevel.Debug))
  )
}
