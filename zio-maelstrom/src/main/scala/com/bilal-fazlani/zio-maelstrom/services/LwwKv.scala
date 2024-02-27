package com.bilalfazlani.zioMaelstrom

import zio.*
import zio.json.*

trait LwwKv extends KvService

object LwwKv:
  def read[Value] = PartiallyAppliedKvRead[LwwKv, Value]()

  def readOption[Key: JsonEncoder, Value: JsonDecoder](
      key: Key,
      timeout: Duration
  ) = ZIO.serviceWithZIO[LwwKv](_.readOption(key, timeout))

  def write[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      value: Value,
      timeout: Duration
  ): ZIO[LwwKv, AskError, Unit] =
    ZIO.serviceWithZIO[LwwKv](_.write(key, value, timeout))

  def cas[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      from: Value,
      to: Value,
      createIfNotExists: Boolean,
      timeout: Duration
  ): ZIO[LwwKv, AskError, Unit] =
    ZIO.serviceWithZIO[LwwKv](_.cas(key, from, to, createIfNotExists, timeout))

  def cas[Key, Value](
      key: Key,
      timeout: Duration
  ) = PartiallyAppliedCas[LwwKv, Key, Value](key, timeout)

  def casZIO[Key, Value](
      key: Key,
      timeout: Duration
  ) = PartiallyAppliedCasZIO[LwwKv, Key, Value](key, timeout)

  def writeIfNotExists[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      value: Value,
      timeout: Duration
  ) = ZIO.serviceWithZIO[LwwKv](_.writeIfNotExists(key, value, timeout))

  private[zioMaelstrom] val live: ZLayer[MessageSender & MessageIdStore & Logger, Nothing, LwwKv] =
    ZLayer(
      for
        sender         <- ZIO.service[MessageSender]
        messageIdStore <- ZIO.service[MessageIdStore]
        logger         <- ZIO.service[Logger]
        kvImpl = KvImpl(NodeId("lww-kv"), sender, messageIdStore, logger)
      yield new LwwKv:
        export kvImpl.*
    )
