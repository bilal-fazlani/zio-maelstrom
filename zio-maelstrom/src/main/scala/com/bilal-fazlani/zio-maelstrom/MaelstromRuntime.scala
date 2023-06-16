package com.bilalfazlani.zioMaelstrom

import zio.{Scope, ZLayer}

type MaelstromRuntime = Initialisation & MessageSender & Logger & Settings

object MaelstromRuntime:
  def live(settings: Settings): ZLayer[Any, Nothing, MaelstromRuntime] = Scope.default ++
    ZLayer.succeed(settings) >>> {
      val logger: ZLayer[Settings, Nothing, Logger] =
        if settings.logLevel == NodeLogLevel.Disabled then Logger.disabled else Logger.active
      ZLayer.makeSome[Scope & Settings, MaelstromRuntime](
        Initialisation.live,
        MessageSender.live,
        logger,
        Initializer.live,
        ResponseHandler.live,
        MessageTransport.live,
        Hooks.live,
        ZLayer.fromZIO(ResponseHandler.handle.forkScoped.unit)
      )
    }

  val live: ZLayer[Any, Nothing, MaelstromRuntime] = Scope.default >>> live(Settings())
