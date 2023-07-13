package com.bilalfazlani.zioMaelstrom

import zio.*
import zio.json.*

private[zioMaelstrom] trait KvService:

  def read[Key: JsonEncoder, Value: JsonDecoder](
      key: Key,
      timeout: Duration
  ): ZIO[Any, AskError, Value]

  def write[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      value: Value,
      timeout: Duration
  ): ZIO[Any, AskError, Unit]

  def cas[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      from: Value,
      to: Value,
      createIfNotExists: Boolean,
      timeout: Duration
  ): ZIO[Any, AskError, Unit]

private[zioMaelstrom] case class KvImpl(
    private val remote: NodeId,
    private val sender: MessageSender,
    private val messageIdStore: MessageIdStore
) extends KvService {

  override def read[Key: JsonEncoder, Value: JsonDecoder](
      key: Key,
      timeout: Duration
  ): ZIO[Any, AskError, Value] =
    messageIdStore.next.flatMap { messageId =>
      sender
        .ask[KvRead[Key], KvReadOk[Value]](KvRead(key, messageId), remote, timeout)
        .map(_.value)
    }

  override def write[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      value: Value,
      timeout: Duration
  ): ZIO[Any, AskError, Unit] =
    messageIdStore.next.flatMap { messageId =>
      sender
        .ask[KvWrite[Key, Value], KvWriteOk](KvWrite(key, value, messageId), remote, timeout)
        .unit
    }

  override def cas[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      from: Value,
      to: Value,
      createIfNotExists: Boolean,
      timeout: Duration
  ): ZIO[Any, AskError, Unit] =
    messageIdStore.next.flatMap { messageId =>
      sender
        .ask[CompareAndSwap[Key, Value], CompareAndSwapOk](
          CompareAndSwap(key, from, to, createIfNotExists, messageId),
          remote,
          timeout
        )
        .unit
    }
}

class PartiallyAppliedKvRead[Kv <: KvService: Tag, Value] {
  def apply[Key: JsonEncoder](key: Key, timeout: Duration)(using
      JsonDecoder[Value]
  ): ZIO[Kv, AskError, Value] =
    ZIO.serviceWithZIO[Kv](_.read[Key, Value](key, timeout))
}
