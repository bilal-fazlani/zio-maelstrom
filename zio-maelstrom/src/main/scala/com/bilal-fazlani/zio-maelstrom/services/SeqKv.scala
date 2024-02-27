package com.bilalfazlani.zioMaelstrom

import zio.*
import zio.json.*

trait SeqKv extends KvService

object SeqKv:
  def read[Value] = PartiallyAppliedKvRead[SeqKv, Value]()

  def readOption[Key: JsonEncoder, Value: JsonDecoder](
      key: Key,
      timeout: Duration
  ) = ZIO.serviceWithZIO[SeqKv](_.readOption(key, timeout))

  def write[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      value: Value,
      timeout: Duration
  ): ZIO[SeqKv, AskError, Unit] =
    ZIO.serviceWithZIO[SeqKv](_.write(key, value, timeout))

  def cas[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      from: Value,
      to: Value,
      createIfNotExists: Boolean,
      timeout: Duration
  ): ZIO[SeqKv, AskError, Unit] =
    ZIO.serviceWithZIO[SeqKv](_.cas(key, from, to, createIfNotExists, timeout))

  def update[Key, Value](
      key: Key,
      timeout: Duration
  ) = PartiallyAppliedUpdate[SeqKv, Key, Value](key, timeout)

  def updateZIO[Key, Value](
      key: Key,
      timeout: Duration
  ) = PartiallyAppliedUpdateZIO[SeqKv, Key, Value](key, timeout)

  def writeIfNotExists[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      value: Value,
      timeout: Duration
  ) = ZIO.serviceWithZIO[SeqKv](_.writeIfNotExists(key, value, timeout))

  private[zioMaelstrom] val live: ZLayer[MessageSender & MessageIdStore & Logger, Nothing, SeqKv] =
    ZLayer(
      for
        sender         <- ZIO.service[MessageSender]
        messageIdStore <- ZIO.service[MessageIdStore]
        logger         <- ZIO.service[Logger]
        kvImpl = KvImpl(NodeId("seq-kv"), sender, messageIdStore, logger)
      yield new SeqKv:
        export kvImpl.*
    )
