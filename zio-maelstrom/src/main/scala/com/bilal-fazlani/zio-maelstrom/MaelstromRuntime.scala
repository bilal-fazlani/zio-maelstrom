package com.bilalfazlani.zioMaelstrom

import zio.{Scope, ZLayer}

type MaelstromRuntime = Initialisation & MessageSender & Logger & Settings

object MaelstromRuntime:
  def live(settings: Settings): ZLayer[Any, Nothing, MaelstromRuntime] = {
    Scope.default >>> {
      ZLayer.makeSome[Scope, MaelstromRuntime](
        // pure layers
        ZLayer.succeed(settings),
        MessageSender.live,
        Logger.live,
        Initializer.live,
        ResponseHandler.live,
        OutputChannel.live,
        InputChannel.live,
        Hooks.live,

        // effectful layers
        Initialisation.run,
        ResponseHandler.start
      )
    }
  }

  val live: ZLayer[Any, Nothing, MaelstromRuntime] = Scope.default >>> live(Settings())
