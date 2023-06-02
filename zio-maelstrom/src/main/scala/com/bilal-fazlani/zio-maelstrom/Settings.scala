package com.bilalfazlani.zioMaelstrom

enum NodeLogLevel(val level: Int):
  case Disabled extends NodeLogLevel(0)
  case Debug    extends NodeLogLevel(1)
  case Info     extends NodeLogLevel(2)
  case Error    extends NodeLogLevel(3)

  def >(that: NodeLogLevel): Boolean  = this.level > that.level
  def <(that: NodeLogLevel): Boolean  = this.level < that.level
  def >=(that: NodeLogLevel): Boolean = this.level >= that.level
  def <=(that: NodeLogLevel): Boolean = this.level <= that.level

enum LogFormat:
  case Plain, Colored

case class Settings(nodeInput: NodeInput, logLevel: NodeLogLevel, logFormat: LogFormat)

object Settings:
  val default = Settings(nodeInput = NodeInput.StdIn, logLevel = NodeLogLevel.Info, logFormat = LogFormat.Plain)
