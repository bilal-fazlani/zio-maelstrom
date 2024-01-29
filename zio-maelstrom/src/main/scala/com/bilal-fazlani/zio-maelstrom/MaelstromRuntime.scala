package com.bilalfazlani.zioMaelstrom

import zio.{Scope, ZLayer}
import java.nio.file.Path

type Services = LinKv & SeqKv & LwwKv & LinTso

// definition {
type MaelstromRuntime = Initialisation & RequestHandler & MessageSender & MessageIdStore &
  Services & Logger & Settings
// }

object MaelstromRuntime:
  // doc_incluide {
  private def live(
      settings: Settings,
      inputStream: ZLayer[Logger, Nothing, InputStream],
      initContext: Option[Context]
  ): ZLayer[Any, Nothing, MaelstromRuntime] = {
    val contextLayer = initContext.fold(Initialisation.run)(Initialisation.fake)
    ZLayer.make[MaelstromRuntime](
      // pure layers
      Scope.default,
      ZLayer.succeed(settings),
      MessageSender.live,
      Logger.live,
      RequestHandler.live,
      InputChannel.live,
      inputStream,
      OutputChannel.stdOut,
      CallbackRegistry.live,
      MessageIdStore.live,

      // Services
      LinKv.live,
      SeqKv.live,
      LwwKv.live,
      LinTso.live,

      // effectful layers
      contextLayer,
      ResponseHandler.start
    )
  }
  // }

  val live: ZLayer[Any, Nothing, MaelstromRuntime] = live(Settings(), InputStream.stdIn, None)

  def live(build: RuntimeBuilder => RuntimeBuilder): ZLayer[Any, Nothing, MaelstromRuntime] =
    val builder = build(RuntimeBuilder())
    live(builder.settings, builder.inputStream, builder.initContext)

case class RuntimeBuilder(
    private[zioMaelstrom] val settings: Settings = Settings(),
    private[zioMaelstrom] val inputStream: ZLayer[Logger, Nothing, InputStream] = InputStream.stdIn,
    private[zioMaelstrom] val initContext: Option[Context] = None
):
  def inputFile(filePath: Path)                = copy(inputStream = InputStream.file(filePath))
  def context(me: NodeId, others: Set[NodeId]) = copy(initContext = Some(Context(me, others)))
  def logLevel(logLevel: NodeLogLevel)         = copy(settings = settings.copy(logLevel = logLevel))
  def logFormat(logFormat: LogFormat) = copy(settings = settings.copy(logFormat = logFormat))
  def concurrency(concurrency: Int)   = copy(settings = settings.copy(concurrency = concurrency))
