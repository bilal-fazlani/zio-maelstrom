package com.bilalfazlani.zioMaelstrom

import zio.*

enum NodeContext:
  case Maelstrom
  case Static(me: NodeId, others: Set[NodeId])

trait MaelstromNode extends ZIOAppDefault:
  extension [R: Tag, E: Tag, A: Tag](zio: ZIO[R & MaelstromRuntime & Scope, E, A]) {
    protected def provideRemaining[R2, E2](layer: ZLayer[R2, E2, R]) =
      zio.provideSome[MaelstromRuntime & Scope & R2](layer)
  }

  override final val bootstrap =
    Runtime.removeDefaultLoggers >>> ZIOMaelstromLogger.install(logFormat, logLevel)

  def context: NodeContext                     = NodeContext.Maelstrom
  def input: ZLayer[Any, Nothing, InputStream] = InputStream.stdIn

  /** Concurrency level for processing messages. Default is 1024. This means 1024 request messages
    * (receive api) + 1024 response messages (ask api) = 2048 messages can be processed in parallel.
    */
  def concurrency: Int = 1024

  /** Default log level is Info. This means all debug level logs will not be logged. If you want
    * logs lower than Info, override this value
    */
  def logLevel: LogLevel = LogLevel.Info

  /** There two log formats - Colored and Plain. Default is Colored and uses Ansi colors. If you
    * want Plain logs, override this value.
    */
  def logFormat: LogFormat = LogFormat.Colored

  def program: ZIO[Scope & MaelstromRuntime, ?, Unit] // todo: what should be error type?

  final def run =
    val settings = Settings(concurrency = concurrency)

    val ctx = context match
      case NodeContext.Maelstrom          => None
      case NodeContext.Static(me, others) => Some(Context(me, others))

    program.provideSome[Scope](MaelstromRuntime.live(settings, input, ctx))
