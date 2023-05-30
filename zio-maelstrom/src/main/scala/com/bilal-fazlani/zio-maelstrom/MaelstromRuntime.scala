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
    def layers(context: Context) = {
      val contextLayer = ZLayer.succeed(context)
      val messageSenderLayer = (contextLayer ++ messageTransportLayer) >>> MessageSender.live
      messageSenderLayer ++ contextLayer
    }

    ZIO
      .scoped(for {
        initResult <- Initializer.initialize(MessageTransport.readInput)
        remainder = initResult._2
        context = initResult._1
        _ <- MessageHandler.handle(remainder, app).provideSomeLayer[R & Settings & Logger & MessageTransport](layers(context))
      } yield ())
      .provideSomeLayer[R & Settings](Logger.live ++ (Logger.live >>> MessageTransport.live) ++ initializerLayer)
