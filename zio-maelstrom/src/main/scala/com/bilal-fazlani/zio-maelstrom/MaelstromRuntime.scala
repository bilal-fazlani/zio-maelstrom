package com.bilalfazlani.zioMaelstrom

import zio.*
import protocol.MessageBody
import zio.json.JsonDecoder
import protocol.*
import zio.stream.*

private type MessageStream = ZStream[Any, Nothing, GenericMessage]

type MaelstromRuntime = Initialisation & MessageSender & Logger & ResponseHandler

object MaelstromRuntime:

  val live = ZLayer.makeSome[Settings & Scope, MaelstromRuntime](
    Initialisation.live,
    MessageSender.live,
    Logger.live,
    Initializer.live,
    ResponseHandler.live,
    MessageTransport.live,
    Hooks.live
  )

  def run[R: Tag, I <: MessageBody: JsonDecoder](app: MaelstromAppR[R, I]): ZIO[MaelstromRuntime & R & Settings, Nothing, Unit] =
    // val loggerLayer = Logger.live
    // val hooksLayer = Hooks.live
    // val messageTransportLayer = loggerLayer >>> MessageTransport.live
    // val initializerLayer = (loggerLayer ++ messageTransportLayer) >>> Initializer.live
    // def requestHandlerLayers(context: Context) = {
    //   val contextLayer = ZLayer.succeed(context)
    //   val messageSenderLayer = (contextLayer ++ messageTransportLayer ++ hooksLayer) >>> MessageSender.live
    //   messageSenderLayer ++ contextLayer
    // }
    // def responseHandlerLayers = (loggerLayer ++ hooksLayer) >>> ResponseHandler.live

    // val fakeContext: Context = ???
    // val contextLayer: ULayer[Context] = ZLayer.succeed(fakeContext)

    // val dd = ZLayer.makeSome[Context & Settings, MessageSender](MessageSender.live, MessageTransport.live, Hooks.live, Logger.live)

    for {
      init <- ZIO.service[Initialisation]
      Initialisation(context, Inputs(responseStream, messageStream)) = init
      _ <- RequestHandler.handle(app) race ResponseHandler.handle
    } yield ()
    // .provideSome[MaelstromRuntime & R & Settings](
    // )
    // .provideSomeLayer[R & Settings](Logger.live ++ (Logger.live >>> MessageTransport.live) ++ initializerLayer)
