package com.bilalfazlani.zioMaelstrom

import zio.{Scope, ZLayer}

type MaelstromRuntime = Initialisation & MessageSender & Logger & ResponseHandler

object MaelstromRuntime:

  def live(settings: Settings): ZLayer[Scope, Nothing, MaelstromRuntime] =
    ZLayer.succeed(settings) >>> {
      val logger: ZLayer[Settings, Nothing, Logger] = if settings.logLevel == NodeLogLevel.Disabled then Logger.disabled else Logger.active
      ZLayer.makeSome[Scope & Settings, MaelstromRuntime](
        Initialisation.live,
        MessageSender.live,
        logger,
        Initializer.live,
        ResponseHandler.live,
        MessageTransport.live,
        Hooks.live
      )
    }

  val live: ZLayer[Scope, Nothing, MaelstromRuntime] = live(Settings.default)
