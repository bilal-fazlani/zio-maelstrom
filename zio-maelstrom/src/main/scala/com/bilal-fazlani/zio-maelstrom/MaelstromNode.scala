package com.bilalfazlani.zioMaelstrom

import zio.*

enum NodeContext:
  case Maelstrom
  case Static(me: NodeId, others: Set[NodeId])

trait MaelstromNode extends ZIOAppDefault:

  override final val bootstrap =
    Runtime.removeDefaultLoggers >>> ZIOMaelstromLogger.install(logFormat, logLevel)

  def context: NodeContext                     = NodeContext.Maelstrom
  def input: ZLayer[Any, Nothing, InputStream] = InputStream.stdIn

  /** Concurrency level for processing messages. Default is 1024. This means 1024 request messages
    * (receive api) + 1024 response messages (ask api) = 2048 messages can be processed in parallel.
    */
  def concurrency: Int = 1024

  def logLevel: LogLevel   = LogLevel.Info
  def logFormat: LogFormat = LogFormat.Colored

  def program: ZIO[Scope & MaelstromRuntime, ?, Unit]

  final def run =
    val settings = Settings(concurrency = concurrency)

    val c = context match
      case NodeContext.Maelstrom          => None
      case NodeContext.Static(me, others) => Some(Context(me, others))

    program.provideSome[Scope](MaelstromRuntime.live(settings, input, c))
