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
  //customize log level
  def withLogLevelDebug                 = copy(logLevel = LogLevel.Debug)
  def withLogLevelInfo                  = copy(logLevel = LogLevel.Info)
  def withLogLevelWarn                  = copy(logLevel = LogLevel.Warning)
  def withLogLevelError                 = copy(logLevel = LogLevel.Error)
  def withLogLevel(level: LogLevel)     = copy(logLevel = level)
  
  //customize log format
  def withPlaintextLog                  = copy(logFormat = LogFormat.Plain)
  def withColoredLog                    = copy(logFormat = LogFormat.Colored)
  
  //customize concurrency
  def withConcurrency(concurrency: Int) = copy(concurrency = concurrency)
  
  //customize mocking
  def withStaticContext(me: NodeId, others: NodeId*) =
    copy(context = Some(Context(me, Set.from(others))))
  def withFileInput(path: Path) = copy(input = InputStream.file(path))
  def withStdInInput = copy(input = InputStream.stdIn)
  def withInlineInput(input: String) = copy(input = InputStream.inline(input))
}

object NodeConfig:
  //default configuration
  val default                           = NodeConfig()
  
  //customize log level
  def withLogLevelDebug                 = default.withLogLevelDebug
  def withLogLevelWarn                  = default.withLogLevelWarn
  def withLogLevelError                 = default.withLogLevelError
  def withLogLevel(level: LogLevel)     = default.withLogLevel(level)
  
  //customize log format
  def withPlaintextLog                  = default.withPlaintextLog
  
  //customize concurrency
  def withConcurrency(concurrency: Int) = default.withConcurrency(concurrency)
  
  //customize mocking
  def withFileInput(path: Path)         = default.withFileInput(path)
  def withStaticContext(me: NodeId, others: NodeId*) =
    default.withStaticContext(me, others*)

trait MaelstromNode extends ZIOAppDefault:

  val configure: NodeConfig = NodeConfig.default

  override final val bootstrap =
    Runtime.removeDefaultLoggers >>>
      ZIOMaelstromLogger.install(configure.logFormat, configure.logLevel)

  def program: ZIO[Scope & MaelstromRuntime, Any, Any]

  final def run =
    val settings = Settings(concurrency = configure.concurrency)
    program.provideSome[Scope](MaelstromRuntime.live(settings, configure.input, configure.context))
