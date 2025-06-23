package com.bilalfazlani.zioMaelstrom

import zio.*
import zio.json.{JsonEncoder, EncoderOps}

private[zioMaelstrom] trait OutputChannel:
  def transport[A: JsonEncoder](message: Message[A]): UIO[Unit]

private[zioMaelstrom] object OutputChannel:
  val stdOut: ZLayer[Any, Nothing, OutputChannel] = ZLayer.succeed(OutputChannelLive)
  val queue: ZLayer[Queue[Message[Any]], Nothing, TestOutputChannel] =
    ZLayer.fromFunction(TestOutputChannel.apply)

private object OutputChannelLive extends OutputChannel:
  def transport[A: JsonEncoder](message: Message[A]): UIO[Unit] =
    val json = message.toJson
    ZIO.logDebug(s"write: $json") *> Console.printLine(json).orDie

private case class TestOutputChannel(
    messages: Queue[Message[Any]]
) extends OutputChannel:
  def transport[A: JsonEncoder](message: Message[A]): UIO[Unit] =
    ZIO.logDebug(s"out: ${message.toJson}") zipPar messages.offer(message).unit
