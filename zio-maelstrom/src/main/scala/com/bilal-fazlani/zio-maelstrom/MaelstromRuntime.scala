package com.bilalfazlani.zioMaelstrom

import zio.{Scope, ZLayer}
import java.nio.file.Path

type MaelstromRuntime = Initialisation & RequestHandler & MessageSender & Logger & Settings

object MaelstromRuntime:
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
      Hooks.live,

      // effectful layers
      Initialisation.run,
      ResponseHandler.start
    )
  }

  val live: ZLayer[Any, Nothing, MaelstromRuntime] = live(Settings(), InputStream.stdIn)
  def usingFile(path: Path)                        = live(Settings(), InputStream.file(path))
  def usingFile(path: Path, settings: Settings)    = live(settings, InputStream.file(path))
