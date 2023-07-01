package com.bilalfazlani.zioMaelstrom

import zio.{Scope, ZLayer}
import java.nio.file.Path

// definition {
type MaelstromRuntime = Initialisation & RequestHandler & MessageSender & Logger & Settings
// }

object MaelstromRuntime:
  // doc_incluide {
  def live(
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

      // effectful layers
      contextLayer,
      ResponseHandler.start
    )
  }

  val live: ZLayer[Any, Nothing, MaelstromRuntime] = live(Settings(), InputStream.stdIn, None)

  def live(settings: Settings): ZLayer[Any, Nothing, MaelstromRuntime] =
    live(settings, InputStream.stdIn, None)
  // }

  def usingFile(path: Path)                     = live(Settings(), InputStream.file(path), None)
  def usingFile(path: Path, settings: Settings) = live(settings, InputStream.file(path), None)
  def usingFile(path: Path, context: Context) =
    live(Settings(), InputStream.file(path), Some(context))
  def usingFile(path: Path, settings: Settings, context: Context) =
    live(settings, InputStream.file(path), Some(context))
