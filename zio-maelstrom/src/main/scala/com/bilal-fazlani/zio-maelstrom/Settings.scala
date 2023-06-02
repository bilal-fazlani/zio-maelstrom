package com.bilalfazlani.zioMaelstrom

enum NodeLogLevel(val level: Int):
  case Disabled extends NodeLogLevel(0)
  case Info     extends NodeLogLevel(1)
  case Error    extends NodeLogLevel(2)

  def <=(that: NodeLogLevel): Boolean = this.level <= that.level

enum LogFormat:
  case Plain, Colored

case class Settings(nodeInput: NodeInput = NodeInput.StdIn, logLevel: NodeLogLevel = NodeLogLevel.Info, logFormat: LogFormat = LogFormat.Plain)
