package com.bilalfazlani.zioMaelstrom

enum DebugLogs:
  case Plain, Colored, Disabled

case class Settings(nodeInput: NodeInput, debugLogs: DebugLogs)

object Settings:
  val default = Settings(nodeInput = NodeInput.StdIn, debugLogs = DebugLogs.Disabled)
