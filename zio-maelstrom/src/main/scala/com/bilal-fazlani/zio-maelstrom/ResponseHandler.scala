package com.bilalfazlani.zioMaelstrom

import zio.*

trait ResponseHandler:
  val handle: ZIO[Logger, Nothing, Unit]

private[zioMaelstrom] object ResponseHandler:
  val live: ZLayer[Initialisation & Logger & Hooks & Settings, Nothing, ResponseHandler] = ZLayer.fromFunction(ResponseHandlerLive.apply)

  val handle: ZIO[ResponseHandler & Logger, Nothing, Unit] = ZIO.serviceWithZIO[ResponseHandler](_.handle)

private case class ResponseHandlerLive(logger: Logger, hooks: Hooks, init: Initialisation, settings: Settings) extends ResponseHandler:
  val handle: ZIO[Logger, Nothing, Unit] =
    init.inputs.responseStream
      // process responses in parallel
      .mapZIOPar(settings.concurrency)(hooks.complete)
      .runDrain
