package com.bilalfazlani.zioMaelstrom

case class Settings(nodeInput: NodeInput, enableColoredOutput: Boolean)

object Settings:
  val default = Settings(
    nodeInput = NodeInput.StdIn,
    enableColoredOutput = false
  )
