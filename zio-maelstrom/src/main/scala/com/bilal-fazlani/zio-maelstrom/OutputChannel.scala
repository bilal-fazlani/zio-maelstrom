package com.bilalfazlani.zioMaelstrom

import zio.*
import protocol.*
import zio.json.{JsonEncoder, EncoderOps}

private[zioMaelstrom] trait OutputChannel:
  def transport[A <: Sendable: JsonEncoder](message: Message[A]): UIO[Unit]

private[zioMaelstrom] object OutputChannel:
  val live: ZLayer[Any, Nothing, OutputChannel] = ZLayer.succeed(OutputChannelLive)

private object OutputChannelLive extends OutputChannel:
  def transport[A <: Sendable: JsonEncoder](message: Message[A]): UIO[Unit] = Console
    .printLine(message.toJson).orDie
