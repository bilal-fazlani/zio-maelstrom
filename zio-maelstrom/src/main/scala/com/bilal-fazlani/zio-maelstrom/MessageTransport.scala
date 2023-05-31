package com.bilalfazlani.zioMaelstrom

import zio.*
import protocol.*
import zio.json.{JsonEncoder, EncoderOps}
import zio.stream.{ZStream, ZPipeline}
import com.bilalfazlani.rainbowcli.*
import zio.json.JsonDecoder

trait MessageTransport:
  def transport[A <: MessageBody: JsonEncoder](message: Message[A]): UIO[Unit]
  def readInput: ZIO[Scope, Nothing, Inputs]

object MessageTransport:
  val live: ZLayer[Logger & Settings, Nothing, MessageTransportLive] = ZLayer.fromFunction(MessageTransportLive.apply)
  val readInput = ZIO.serviceWithZIO[MessageTransport](_.readInput)

case class MessageTransportLive(logger: Logger, settings: Settings) extends MessageTransport:
  private given ColorContext = ColorContext(settings.enableColoredOutput)

  case class InvalidInput(input: String, error: String)

  private val strings =
    val nodeInput = settings.nodeInput
    ZStream
      .unwrap(for {
        _ <- logger.info(s"using $nodeInput")
        strm = (nodeInput match {
          case NodeInput.StdIn          => ZStream.fromInputStream(java.lang.System.in).via(ZPipeline.utfDecode)
          case NodeInput.FilePath(path) => ZStream.fromFile(path.toFile, 128).via(ZPipeline.utfDecode).via(ZPipeline.splitLines)
        })
          .filter(line => line.trim != "")
          .tap(line => Console.printLineError(line.blue.onGreen.bold))
          .takeWhile(line => line.trim != "q")
      } yield strm)
      .orDie

  val readInput = strings
    .map(str => JsonDecoder[GenericMessage].decodeJson(str).left.map(e => InvalidInput(str, e)))
    .tap {
      case Left(errorMessage)    => logger.error(s"could not read `${errorMessage.input}`, error: ${errorMessage.error}")
      case Right(genericMessage) => ZIO.unit
    }
    .collectRight
    .partition(_.isResponse, 1024)
    .map(x => Inputs(x._1, x._2))

  def transport[A <: MessageBody: JsonEncoder](message: Message[A]): UIO[Unit] =
    given colorContext: ColorContext = ColorContext(settings.enableColoredOutput)
    (Console.printLine(message.toJson.blue.onCyan.bold)
      *> logger.info(s"sent ${message.body} to ${message.destination}")).orDie
