package com.bilalfazlani.zioMaelstrom

import zio.*
import java.nio.file.Path

case class NodeConfig private (
    context: Option[Context] = None,
    input: ZLayer[Any, Nothing, InputStream] = InputStream.stdIn,
    concurrency: Int = 1024,
    logLevel: LogLevel = LogLevel.Info,
    logFormat: LogFormat = LogFormat.Colored
) {
  def withLogLevelDebug                 = copy(logLevel = LogLevel.Debug)
  def withLogLevelInfo                  = copy(logLevel = LogLevel.Info)
  def withLogLevelWarn                  = copy(logLevel = LogLevel.Warning)
  def withLogLevelError                 = copy(logLevel = LogLevel.Error)
  def withPlaintextLog                  = copy(logFormat = LogFormat.Plain)
  def withConcurrency(concurrency: Int) = copy(concurrency = concurrency)
  def withFileInput(path: Path)         = copy(input = InputStream.file(path))
  def withStaticContext(me: NodeId, others: NodeId*) =
    copy(context = Some(Context(me, Set.from(others))))
}

object NodeConfig:
  val default                           = NodeConfig()
  def withLogLevelDebug                 = default.withLogLevelDebug
  def withLogLevelInfo                  = default.withLogLevelInfo
  def withLogLevelWarn                  = default.withLogLevelWarn
  def withLogLevelError                 = default.withLogLevelError
  def withPlaintextLog                  = default.withPlaintextLog
  def withConcurrency(concurrency: Int) = default.withConcurrency(concurrency)
  def withFileInput(path: Path)         = default.withFileInput(path)
  def withStaticContext(me: NodeId, others: NodeId*) =
    default.withStaticContext(me, others*)

trait MaelstromNode extends ZIOAppDefault:

  val configure: NodeConfig = NodeConfig.default

  override final val bootstrap =
    Runtime.removeDefaultLoggers >>>
      ZIOMaelstromLogger.install(configure.logFormat, configure.logLevel)

  def program: ZIO[Scope & MaelstromRuntime, ?, Unit] // todo: what should be error type?

  final def run =
    val settings = Settings(concurrency = configure.concurrency)
    program.provideSome[Scope](MaelstromRuntime.live(settings, configure.input, configure.context))
