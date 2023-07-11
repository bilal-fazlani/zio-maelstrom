package com.bilalfazlani.zioMaelstrom

import zio.*
import zio.json.*
import protocol.*

private[zioMaelstrom] trait KvService:
  def read[Key: JsonEncoder, Value: JsonDecoder](
      key: Key,
      messageId: MessageId,
      timeout: Duration
  ): ZIO[Any, AskError, Value]
  def write[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      value: Value,
      messageId: MessageId,
      timeout: Duration
  ): ZIO[Any, AskError, Unit]
  def cas[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      from: Value,
      to: Value,
      createIfNotExists: Boolean,
      messageId: MessageId,
      timeout: Duration
  ): ZIO[Any, AskError, Unit]

private[zioMaelstrom] case class KvImpl(
    remote: NodeId,
    sender: MessageSender
) extends KvService {
  override def read[Key: JsonEncoder, Value: JsonDecoder](
      key: Key,
      messageId: MessageId,
      timeout: Duration
  ): ZIO[Any, AskError, Value] =
    sender
      .ask[KvRead[Key], KvReadOk[Value]](KvRead(key, messageId), remote, timeout)
      .map(_.value)

  override def write[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      value: Value,
      messageId: MessageId,
      timeout: Duration
  ): ZIO[Any, AskError, Unit] =
    sender
      .ask[KvWrite[Key, Value], KvWriteOk](KvWrite(key, value, messageId), remote, timeout)
      .unit

  override def cas[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      from: Value,
      to: Value,
      createIfNotExists: Boolean,
      messageId: MessageId,
      timeout: Duration
  ): ZIO[Any, AskError, Unit] =
    sender
      .ask[CompareAndSwap[Key, Value], CompareAndSwapOk](
        CompareAndSwap(key, from, to, createIfNotExists, messageId),
        remote,
        timeout
      )
      .unit
}
