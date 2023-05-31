package com.bilalfazlani.zioMaelstrom

import zio.*
import protocol.MessageBody
import zio.json.JsonDecoder
import protocol.*
import zio.stream.*
import com.bilalfazlani.zioMaelstrom.MessageHandler

private type MessageStream = ZStream[Any, Nothing, GenericMessage]

object MaelstromRuntime:

  def run[R: Tag, I <: MessageBody: JsonDecoder](
      app: MaelstromAppR[R, I]
  ): ZIO[R & Settings, Nothing, Unit] =

    val loggerLayer = Logger.live
    val messageTransportLayer = loggerLayer >>> MessageTransport.live
    val initializerLayer = (loggerLayer ++ messageTransportLayer) >>> Initializer.live
    def messageHandlerLayers(context: Context) = {
      val contextLayer = ZLayer.succeed(context)
      val messageSenderLayer = (contextLayer ++ messageTransportLayer) >>> MessageSender.live
      messageSenderLayer ++ contextLayer
    }
    def responseHandlerLayers = {
      loggerLayer >>> ResponseHandler.live
    }

    ZIO
      .scoped(for {
        initResult <- Initializer.initialize(MessageTransport.readInput)
        InitResult(context, Inputs(responseStream, messageStream)) = initResult
        _ <- 
            MessageHandler.handle(messageStream, app)
              .provideSomeLayer[R & Settings & Logger & MessageTransport](messageHandlerLayers(context))
              .race(ResponseHandler.handle(responseStream)
              .provideSomeLayer[Settings](responseHandlerLayers))
      } yield ())
      .provideSomeLayer[R & Settings](Logger.live ++ (Logger.live >>> MessageTransport.live) ++ initializerLayer)
