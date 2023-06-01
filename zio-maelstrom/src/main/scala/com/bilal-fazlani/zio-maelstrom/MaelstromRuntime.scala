package com.bilalfazlani.zioMaelstrom

import zio.*
import protocol.MessageBody
import zio.json.JsonDecoder
import protocol.*
import zio.stream.*

private type MessageStream = ZStream[Any, Nothing, GenericMessage]

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
