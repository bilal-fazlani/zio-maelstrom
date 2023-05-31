package com.bilalfazlani.zioMaelstrom

import zio.*

trait ResponseHandler:
  def handle(responseStream: MessageStream): ZIO[Any, Nothing, Unit]

object ResponseHandler:
  val live: ZLayer[Logger & Hooks, Nothing, ResponseHandlerLive] = ZLayer.fromFunction(ResponseHandlerLive.apply)

  def handle(responseStream: MessageStream): ZIO[ResponseHandler, Nothing, Unit] =
    ZIO.serviceWithZIO[ResponseHandler](_.handle(responseStream))

case class ResponseHandlerLive(logger: Logger, hooks: Hooks) extends ResponseHandler:
  def handle(responseStream: MessageStream): ZIO[Any, Nothing, Unit] =
    responseStream
      // process responses in parallel
      .mapZIOPar(1024)(message => hooks.complete(message))
      .runDrain
