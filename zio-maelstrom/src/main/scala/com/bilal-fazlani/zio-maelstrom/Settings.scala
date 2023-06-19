package com.bilalfazlani.zioMaelstrom

enum NodeLogLevel(val level: Int):
  case Debug    extends NodeLogLevel(1)
  case Info     extends NodeLogLevel(2)
  case Warning  extends NodeLogLevel(3)
  case Error    extends NodeLogLevel(4)
  case Disabled extends NodeLogLevel(5)

  private[zioMaelstrom] def <=(that: NodeLogLevel): Boolean = this.level <= that.level

enum LogFormat:
  case Plain, Colored

/** Settings for the node
  *
  * @param nodeInput
  *   Input can be taken from stdin or a file. Maelstrom will feed to stdin but when debugging it is
  *   easier to use a file. Default is stdin
  * @param logLevel
  *   You can log messages using logInfo or logError. Output logs can be filtered by setting this
  *   value to either Debug, Info, Warning, Error or Disabled. Default is Info
  * @param logFormat
  *   You can choose between colored or plain logs. Default is colored
  * @param concurrency
  *   Concurrency level for processing messages. Default is 1024. This means 1024 request messages
  *   (receive api) + 1024 response messages (ask api) = 2048 messages can be processed in parallel.
  */
case class Settings(
    nodeInput: NodeInput = NodeInput.StdIn,
    logLevel: NodeLogLevel = NodeLogLevel.Info,
    logFormat: LogFormat = LogFormat.Colored,
    concurrency: Int = 1024
)
