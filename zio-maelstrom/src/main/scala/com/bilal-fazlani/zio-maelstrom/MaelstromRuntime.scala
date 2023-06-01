package com.bilalfazlani.zioMaelstrom

import zio.{Scope, ZLayer}

type MaelstromRuntime = Initialisation & MessageSender & Logger & ResponseHandler

object MaelstromRuntime:

  def live(settings: Settings): ZLayer[Scope, Nothing, MaelstromRuntime] =
    ZLayer.succeed(settings) >>> ZLayer.makeSome[Scope & Settings, MaelstromRuntime](
      Initialisation.live,
      MessageSender.live,
      Logger.live,
      Initializer.live,
      ResponseHandler.live,
      MessageTransport.live,
      Hooks.live
    )

  val live: ZLayer[Scope, Nothing, MaelstromRuntime] = live(Settings.default)
