package com.example

import zio.*
import com.bilalfazlani.zioMaelstrom.*

object MainApplication extends MaelstromNode {

  override val logLevel = LogLevel.Debug

  def program = for
    _ <- ZIO.logDebug("Starting node")
    _ <- ZIO.logInfo("Received message")
    _ <- ZIO.logWarning("Something is wrong")
    _ <- ZIO.logError("Something is really wrong")
  yield ()
}
