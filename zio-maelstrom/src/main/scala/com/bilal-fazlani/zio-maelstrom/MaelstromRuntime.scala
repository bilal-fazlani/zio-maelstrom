package com.bilalfazlani.zioMaelstrom

import zio.{Scope, ZLayer}

type MaelstromRuntime = Initialisation & RequestHandler & MessageSender & Logger & Settings

object MaelstromRuntime:
  def live(settings: Settings): ZLayer[Any, Nothing, MaelstromRuntime] = {
    ZLayer.make[MaelstromRuntime](
      // pure layers
      Scope.default,
      ZLayer.succeed(settings),
      MessageSender.live,
      Logger.live,
      RequestHandler.live,
      InputChannel.live,
      InputStream.stdIn,
      OutputChannel.stdOut,
      Hooks.live,

      // effectful layers
      Initialisation.run,
      ResponseHandler.start
    )
  }

  val live: ZLayer[Any, Nothing, MaelstromRuntime] = Scope.default >>> live(Settings())
