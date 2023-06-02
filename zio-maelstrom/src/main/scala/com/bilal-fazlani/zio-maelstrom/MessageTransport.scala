package com.bilalfazlani.zioMaelstrom

import zio.*
import protocol.*
import zio.json.{JsonEncoder, EncoderOps}
import zio.stream.{ZStream, ZPipeline}
import zio.json.JsonDecoder

trait MessageTransport:
  def transport[A <: Sendable: JsonEncoder](message: Message[A]): UIO[Unit]
  def readInputs: ZIO[Scope, Nothing, Inputs]

private[zioMaelstrom] object MessageTransport:
  val live: ZLayer[Logger & Settings, Nothing, MessageTransportLive] = ZLayer.fromFunction(MessageTransportLive.apply)
  val readInputs                                                     = ZIO.serviceWithZIO[MessageTransport](_.readInputs)

private case class MessageTransportLive(logger: Logger, settings: Settings) extends MessageTransport:

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
          .tap(logger.logInMessage)
          .takeWhile(line => line.trim != "q")
      } yield strm)
      .orDie

  val readInputs = strings
    .map(str => JsonDecoder[GenericMessage].decodeJson(str).left.map(e => InvalidInput(str, e)))
    .tap {
      case Left(errorMessage)    => logger.error(s"could not read `${errorMessage.input}`, error: ${errorMessage.error}")
      case Right(genericMessage) => ZIO.unit
    }
    .collectRight
    .partition(_.isResponse, 1024)
    .map(x => Inputs(x._1, x._2))

  def transport[A <: Sendable: JsonEncoder](message: Message[A]): UIO[Unit] =
    import com.bilalfazlani.rainbowcli.*
    given colorContext: ColorContext = ColorContext(settings.logFormat == LogFormat.Colored)

    Console.printLine(message.toJson.blue.onCyan.bold).orDie
      *> logger.debug(s"sent ${message.body} to ${message.destination}")
