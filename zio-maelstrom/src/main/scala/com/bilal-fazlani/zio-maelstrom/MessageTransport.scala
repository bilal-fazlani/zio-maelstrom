package com.bilalfazlani.zioMaelstrom

import protocol.*
import zio.json.JsonEncoder
import zio.json.EncoderOps
import zio.*
import zio.stream.ZStream
import zio.stream.ZPipeline
import com.bilalfazlani.rainbowcli.*

trait MessageTransport:
  def transport[A <: MessageBody: JsonEncoder](message: Message[A]): UIO[Unit]
  val readInput: ZStream[Settings, Nothing, String]

object MessageTransport:
  val live: ZLayer[Logger & Settings, Nothing, MessageTransportLive] = ZLayer.fromFunction(MessageTransportLive.apply)

  val readInput = ZStream.serviceWithStream[MessageTransport](_.readInput)

case class MessageTransportLive(logger: Logger, settings: Settings) extends MessageTransport:
  val readInput = ZStream
    .unwrap(for {
      settings <- ZIO.service[Settings]
      given ColorContext = ColorContext(settings.enableColoredOutput)
      nodeInput = settings.nodeInput
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

  def transport[A <: MessageBody: JsonEncoder](message: Message[A]): UIO[Unit] =
    given colorContext: ColorContext = ColorContext(settings.enableColoredOutput)
    (Console.printLine(message.toJson.blue.onCyan.bold)
      *> logger.info(s"sent ${message.body} to ${message.destination}")).orDie
