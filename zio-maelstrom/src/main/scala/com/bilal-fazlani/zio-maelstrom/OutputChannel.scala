package com.bilalfazlani.zioMaelstrom

import zio.*
import protocol.*
import zio.json.{JsonEncoder, EncoderOps}

private[zioMaelstrom] trait OutputChannel:
  def transport[A <: Sendable: JsonEncoder](message: Message[A]): UIO[Unit]

private[zioMaelstrom] object OutputChannel:
  val stdOut: ZLayer[Any, Nothing, OutputChannel] = ZLayer.succeed(OutputChannelLive)
  val queue                                       = ZLayer.fromFunction(TestOutputChannel.apply)

private object OutputChannelLive extends OutputChannel:
  def transport[A <: Sendable: JsonEncoder](message: Message[A]): UIO[Unit] = Console
    .printLine(message.toJson)
    .orDie

private case class TestOutputChannel(
    messages: Queue[Message[Sendable]]
) extends OutputChannel:
  def transport[A <: Sendable: JsonEncoder](message: Message[A]): UIO[Unit] =
    messages.offer(message).unit
