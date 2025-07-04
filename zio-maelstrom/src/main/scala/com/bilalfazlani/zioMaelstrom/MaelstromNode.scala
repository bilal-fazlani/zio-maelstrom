package com.bilalfazlani.zioMaelstrom

import com.bilalfazlani.zioMaelstrom.InputStream.InlineMessage
import zio.*
import zio.json.JsonEncoder

import java.nio.file.Path

enum InputContext:
  case Static(context: Context, input: ZLayer[Any, Nothing, InputStream])
  case Real

case class NodeConfig private (
    inputContext: InputContext = InputContext.Real,
    concurrency: Int = 1024,
    logLevel: LogLevel = LogLevel.Info,
    logFormat: LogFormat = LogFormat.Colored
) {
  // customize log level
  def withLogLevelDebug             = copy(logLevel = LogLevel.Debug)
  def withLogLevelInfo              = copy(logLevel = LogLevel.Info)
  def withLogLevelWarn              = copy(logLevel = LogLevel.Warning)
  def withLogLevelError             = copy(logLevel = LogLevel.Error)
  def withLogLevel(level: LogLevel) = copy(logLevel = level)

  // customize log format
  def withPlaintextLog = copy(logFormat = LogFormat.Plain)
  def withColoredLog   = copy(logFormat = LogFormat.Colored)

  // customize concurrency
  def withConcurrency(concurrency: Int) = copy(concurrency = concurrency)

  // customize mocking
  def withStaticInput[A: JsonEncoder: Tag](
      me: NodeId,
      others: Set[NodeId],
      messages: InlineMessage[A]*
  ) =
    val context = Context(me, others)
    copy(inputContext = InputContext.Static(context, InputStream.inline(messages, context)))

  def withStaticInput(me: NodeId, others: Set[NodeId], path: Path) =
    val context     = Context(me, others)
    val inputStream = InputStream.file(path)
    copy(inputContext = InputContext.Static(context, inputStream))
}

object NodeConfig:
  // default configuration
  val default = NodeConfig()

  // customize log level
  def withLogLevelDebug             = default.withLogLevelDebug
  def withLogLevelWarn              = default.withLogLevelWarn
  def withLogLevelError             = default.withLogLevelError
  def withLogLevel(level: LogLevel) = default.withLogLevel(level)

  // customize log format
  def withPlaintextLog = default.withPlaintextLog

  // customize concurrency
  def withConcurrency(concurrency: Int) = default.withConcurrency(concurrency)

  // customize mocking
  def withStaticInput[A: JsonEncoder: Tag](
      me: NodeId,
      others: Set[NodeId],
      messages: InlineMessage[A]*
  ) =
    default.withStaticInput(me, others, messages*)

  def withStaticInput(me: NodeId, others: Set[NodeId], path: Path) =
    default.withStaticInput(me, others, path)

trait MaelstromNode extends ZIOAppDefault:

  val configure: NodeConfig = NodeConfig.default

  protected given Tag[MessageSender & MessageIdStore & Services & Initialisation & RequestHandler & Settings] =
    Tag[MessageSender & MessageIdStore & Services & Initialisation & RequestHandler & Settings]

  override final val bootstrap =
    Runtime.removeDefaultLoggers >>>
      ZIOMaelstromLogger.install(configure.logFormat, configure.logLevel)

  def program: ZIO[MaelstromRuntime, Any, Any]

  final def run =
    val settings = Settings(concurrency = configure.concurrency)
    configure.inputContext match
      case InputContext.Static(context, input) =>
        program.provide(MaelstromRuntime.static(settings, input, context))
      case InputContext.Real                   => program.provide(MaelstromRuntime.live(settings))
