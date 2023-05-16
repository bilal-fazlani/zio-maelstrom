package com.bilalfazlani.gossipGloomersScala
package zioMaelstrom

import zio.{ZIO, ZLayer, Tag, Scope}
import protocol.MessageBody
import zio.json.JsonDecoder
import protocol.*
import zio.stream.*
import com.bilalfazlani.gossipGloomersScala.zioMaelstrom.Initializer
import zio.ZEnvironment

trait MaelstromRuntime:
  def run[R: Tag, I <: MessageBody: JsonDecoder](
      app: MaelstromAppR[R, I],
      nodeInput: NodeInput
  ): ZIO[Scope & R, Throwable, Unit]

object MaelstromRuntime:
  private case class InvalidInput(input: GenericMessage, error: String) extends Exception
  def run[R: Tag, I <: MessageBody: JsonDecoder](
      app: MaelstromAppR[R, I],
      nodeInput: NodeInput = NodeInput.StdIn
  ) =

    val inputStream = (nodeInput match
      case NodeInput.StdIn =>
        ZStream.fromInputStream(java.lang.System.in).via(ZPipeline.utfDecode)
      case NodeInput.FilePath(path) =>
        ZStream.fromFile(path.toFile(), 4096).via(ZPipeline.utfDecode).via(ZPipeline.splitLines)
    )
    .filter(line => line.trim != "")
      .takeWhile(line => line.trim != "q" && line.trim != "quit")

    val initialized = ZIO.serviceWithZIO[Initializer](_.initialize(inputStream))

    val layer: ZLayer[Scope & Initializer, Throwable, Context & ZStream[Any, Throwable, GenericMessage]] =
      ZLayer
        .fromZIO(initialized)
        .map(x => ZEnvironment(x.get._1) ++ ZEnvironment(x.get._2))

    val cLayer: ZLayer[Scope & Initializer, Throwable, Context] = layer.map(x => ZEnvironment(x.get[Context]))

    val remLayer = layer.map(x => ZEnvironment(x.get[ZStream[Any, Throwable, GenericMessage]]))

    val transportLayer = Debugger.live >>> MessageTransport.live

    def contextLayer = (Debugger.live ++ transportLayer) >>> (Initializer.live >>> cLayer)

    val initializerLayer = (Debugger.live ++ transportLayer) >>> Initializer.live

    def remainderLayer = initializerLayer >>> remLayer

    def messageSenderLayer = (Debugger.live ++ contextLayer) >>> (transportLayer >>> MessageSender.live)

    val finalLayer: ZLayer[Scope & R, Nothing, ZLayer[Scope, Throwable, Debugger & MessageSender & (ZStream[Any, Throwable, GenericMessage] & Context & R)]] = 
      ZLayer.fromFunction((scope: Scope, r: R) => Debugger.live ++ messageSenderLayer ++ remainderLayer ++ contextLayer ++ ZLayer.succeed(r)) 

    consumeMessages(app).provideSomeLayer[R & Scope](finalLayer ++ Debugger.live ++ messageSenderLayer ++ contextLayer ++ remainderLayer)

  private def consumeMessages[R: Tag, I <: MessageBody: JsonDecoder](
      app: MaelstromAppR[R, I]
  ) =
    for {
      context <- ZIO.service[Context]
      genericMessageStream <- ZIO.service[ZStream[Any, Throwable, GenericMessage]]
      result <- genericMessageStream
        .mapZIO(genericMessage =>
          ZIO
            .fromEither(GenericDecoder[I].decode(genericMessage))
            .mapError(e => InvalidInput(genericMessage, e))
            .tapError(e => handleInvalidInput(e, context))
        )
        .mapZIO(message => app.handleMessage(message))
        .runDrain
    } yield result

  private def handleInvalidInput(invalidInput: InvalidInput, context: Context): ZIO[Debugger & MessageSender, Throwable, Unit] =
    val maybeResponse: Option[MaelstromError] = invalidInput.input.details.msg_id.map { msgId =>
      val errorCode = StandardErrorCode.MalformedRequest
      MaelstromError(
        in_reply_to = msgId,
        code = StandardErrorCode.MalformedRequest.code,
        text = s"invalid input: $invalidInput"
      )
    }
    for {
      debugger <- ZIO.service[Debugger]
      _ <- debugger.debugMessage(s"invalid input from ${invalidInput.input.src}")
      sender <- ZIO.service[MessageSender]
      _ <- maybeResponse match {
        case Some(errorMessageBody) => sender.send(errorMessageBody, invalidInput.input.src)
        case None                   => ZIO.unit // if there was no msg id in msg, you can send a reply
      }
    } yield ()
