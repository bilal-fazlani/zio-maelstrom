package com.bilalfazlani.zioMaelstrom

import protocol.*
import zio.json.JsonEncoder
import zio.json.EncoderOps
import zio.*

trait MessageTransport:
  def transport[A <: MessageBody: JsonEncoder](message: Message[A]): UIO[Unit]

object MessageTransport:
  val live: ZLayer[Logger & Settings, Nothing, MessageTransportLive] = ZLayer.fromFunction(MessageTransportLive.apply)

case class MessageTransportLive(logger: Logger, settings: Settings) extends MessageTransport:
  def transport[A <: MessageBody: JsonEncoder](message: Message[A]): UIO[Unit] =
    import com.bilalfazlani.rainbowcli.*
    given colorContext: ColorContext = ColorContext(settings.enableColoredOutput)
    (Console.printLine(message.toJson.bold)
      *> logger.info(s"sent message: ${message.body} to ${message.destination}")).orDie
