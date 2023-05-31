package com.bilalfazlani.zioMaelstrom

import zio.*

trait ResponseHandler:
  def handle(messageStream: MessageStream): ZIO[Any, Nothing, Unit]

object ResponseHandler:
  val live: ZLayer[Logger, Nothing, ResponseHandlerLive] = ZLayer.fromFunction(ResponseHandlerLive.apply)

  def handle(messageStream: MessageStream): ZIO[ResponseHandler, Nothing, Unit] =
    ZIO.serviceWithZIO[ResponseHandler](_.handle(messageStream))

case class ResponseHandlerLive(logger: Logger) extends ResponseHandler:
  def handle(messageStream: MessageStream): ZIO[Any, Nothing, Unit] = ???
