package com.bilalfazlani.zioMaelstrom

import zio.{ZIO, Ref, ZLayer, UIO}

trait MessageIdStore:
  def next: ZIO[Any, Nothing, MessageId]

private[zioMaelstrom] object MessageIdStore:
  val next: ZIO[MessageIdStore, Nothing, MessageId] = ZIO.serviceWithZIO(_.next)

  val live: ZLayer[Any, Nothing, MessageIdStore] =
    ZLayer(Ref.make(0)) >>> ZLayer.derive[MessageIdStoreImpl]

  val stub: ZLayer[Any, Nothing, MessageIdStore] =
    ZLayer.fromZIO(Ref.make(1).map(new MessageIdStoreStub(_)))

private class MessageIdStoreImpl(ref: Ref[Int]) extends MessageIdStore:
  def next: ZIO[Any, Nothing, MessageId] =
    ref.updateAndGet(_ + 1).map(MessageId(_))

private[zioMaelstrom] class MessageIdStoreStub(ref: Ref[Int]) extends MessageIdStore:
  def setNext(next: MessageId): UIO[Unit] = ref.set(next.toInt)
  def next: ZIO[Any, Nothing, MessageId]  = ref.getAndIncrement.map(MessageId(_))
