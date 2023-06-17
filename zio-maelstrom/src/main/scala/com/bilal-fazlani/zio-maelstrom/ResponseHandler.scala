package com.bilalfazlani.zioMaelstrom

import zio.*

private[zioMaelstrom] trait ResponseHandler:
  val handle: ZIO[Any, Nothing, Unit]

private[zioMaelstrom] object ResponseHandler:
  val live: ZLayer[Initialisation & Hooks & Settings, Nothing, ResponseHandler] = ZLayer
    .fromFunction(ResponseHandlerLive.apply)

  val start: ZLayer[ResponseHandler & Scope, Nothing, Unit] = ZLayer
    .fromZIO(ZIO.serviceWithZIO[ResponseHandler](_.handle).forkScoped.unit)

private case class ResponseHandlerLive(hooks: Hooks, init: Initialisation, settings: Settings)
    extends ResponseHandler:
  val handle: ZIO[Any, Nothing, Unit] = init.inputs.responseStream
    // process responses in parallel
    .mapZIOPar(settings.concurrency)(hooks.complete).runDrain
