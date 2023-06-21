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
      inputStream: ZLayer[Logger, Nothing, InputStream]
  ): ZLayer[Any, Nothing, MaelstromRuntime] = {
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
      Initialisation.run,
      ResponseHandler.start
    )
  }

  val live: ZLayer[Any, Nothing, MaelstromRuntime] = live(Settings(), InputStream.stdIn)

  def live(settings: Settings): ZLayer[Any, Nothing, MaelstromRuntime] =
    live(settings, InputStream.stdIn)
  // }

  def usingFile(path: Path)                     = live(Settings(), InputStream.file(path))
  def usingFile(path: Path, settings: Settings) = live(settings, InputStream.file(path))
