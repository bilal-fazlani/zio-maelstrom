package com.bilalfazlani.zioMaelstrom

import zio.*

private[zioMaelstrom] trait ResponseHandler:
  val handle: ZIO[Any, Nothing, Unit]

private[zioMaelstrom] object ResponseHandler:
  private val live: ZLayer[Initialisation & CallbackRegistry & Settings, Nothing, ResponseHandler] =
    ZLayer.derive[ResponseHandlerLive]

  val start: ZLayer[Scope & Initialisation & CallbackRegistry & Settings, Nothing, Unit] =
    live >>> ZLayer(ZIO.serviceWithZIO[ResponseHandler](_.handle).forkScoped.unit)

private class ResponseHandlerLive(
    callbackRegistry: CallbackRegistry,
    init: Initialisation,
    settings: Settings
) extends ResponseHandler:
  val handle: ZIO[Any, Nothing, Unit] = init.inputs.responseStream
    // process responses in parallel
    .mapZIOPar(settings.concurrency)(callbackRegistry.completeCallback)
    .runDrain
