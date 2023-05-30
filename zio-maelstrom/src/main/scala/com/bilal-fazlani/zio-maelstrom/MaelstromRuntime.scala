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

    val inputStream = ZStream
      .unwrap(for {
        settings <- ZIO.service[Settings]
        logger <- ZIO.service[Logger]
        nodeInput = settings.nodeInput
        _ <-
          if nodeInput == NodeInput.StdIn then logger.info("using StdIn")
          else logger.info(s"using FilePath(${nodeInput.asInstanceOf[NodeInput.FilePath].path})")
        strm = (if nodeInput == NodeInput.StdIn then ZStream.fromInputStream(java.lang.System.in).via(ZPipeline.utfDecode)
                else
                  ZStream.fromFile(nodeInput.asInstanceOf[NodeInput.FilePath].path.toFile(), 4096).via(ZPipeline.utfDecode).via(ZPipeline.splitLines)
        )
          .filter(line => line.trim != "")
          .takeWhile(line => line.trim != "q")
      } yield strm)
      .orDie

    ZIO
      .scoped(for {
        initResult <- Initializer.initialize(inputStream)
        remainder = initResult._2
        context = initResult._1
        _ <- MessageHandler.handle(remainder, app).provideSomeLayer[R & Settings & Logger & MessageTransport](layers(context))
      } yield ())
      .provideSomeLayer[R & Settings](Logger.live ++ (Logger.live >>> MessageTransport.live) ++ initializerLayer)

