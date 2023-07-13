package com.bilalfazlani.zioMaelstrom

import zio.{ZIO, Ref, ZLayer}
import protocol.MessageId

trait MessageIdStore:
  def next: ZIO[Any, Nothing, MessageId]

private[zioMaelstrom] object MessageIdStore:
  val next: ZIO[MessageIdStore, Nothing, MessageId] = ZIO.serviceWithZIO(_.next)

  val live: ZLayer[Any, Nothing, MessageIdStore] =
    ZLayer.fromZIO(Ref.make(0)) >>> ZLayer.fromFunction(MessageIdStoreImpl.apply)

private case class MessageIdStoreImpl(ref: Ref[Int]) extends MessageIdStore:
  def next: ZIO[Any, Nothing, MessageId] =
    ref.updateAndGet(_ + 1).map(MessageId(_))
