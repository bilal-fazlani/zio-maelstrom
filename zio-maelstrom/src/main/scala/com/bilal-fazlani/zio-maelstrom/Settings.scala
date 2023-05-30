package com.bilalfazlani.zioMaelstrom

import zio.ZLayer

case class Settings(nodeInput: NodeInput, enableColoredOutput: Boolean)

object Settings:
  val default = ZLayer.succeed(
    Settings(
      nodeInput = NodeInput.StdIn,
      enableColoredOutput = false
    )
  )

  def custom(nodeInput: NodeInput, enableColoredOutput: Boolean) =
    ZLayer.succeed(
      Settings(
        nodeInput = nodeInput,
        enableColoredOutput = enableColoredOutput
      )
    )
