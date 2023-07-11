package com.bilalfazlani.zioMaelstrom

import protocol.*
import zio.*
import zio.json.*

trait LwwKv extends KvService

object LwwKv:
  def read[Key: JsonEncoder, Value: JsonDecoder](
      key: Key,
      messageId: MessageId,
      timeout: Duration
  ): ZIO[LwwKv, AskError, Value] =
    ZIO.serviceWithZIO[LwwKv](_.read(key, messageId, timeout))
  
  def write[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      value: Value,
      messageId: MessageId,
      timeout: Duration
  ): ZIO[LwwKv, AskError, Unit] =
    ZIO.serviceWithZIO[LwwKv](_.write(key, value, messageId, timeout))
  
  def cas[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      from: Value,
      to: Value,
      messageId: MessageId,
      createIfNotExists: Boolean,
      timeout: Duration
  ): ZIO[LwwKv, AskError, Unit] =
    ZIO.serviceWithZIO[LwwKv](_.cas(key, from, to, createIfNotExists, messageId, timeout))

  private[zioMaelstrom] val live: ZLayer[MessageSender, Nothing, LwwKv] = ZLayer.fromZIO(
    for
      sender <- ZIO.service[MessageSender]
      kvImpl = KvImpl(NodeId("lww-kv"), sender)
    yield new LwwKv:
      export kvImpl.*
  )
