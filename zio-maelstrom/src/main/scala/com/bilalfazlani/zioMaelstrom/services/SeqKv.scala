package com.bilalfazlani.zioMaelstrom.services

import com.bilalfazlani.zioMaelstrom.{AskError, MessageSender, NodeId}
import zio.*
import zio.json.*

trait SeqKv extends KvService

object SeqKv:
  def read[Value] = PartiallyAppliedKvRead[SeqKv, Value]

  def readOption[Key: JsonEncoder, Value: JsonDecoder](
      key: Key
  ) = ZIO.serviceWithZIO[SeqKv](_.readOption(key, None))

  def readOption[Key: JsonEncoder, Value: JsonDecoder](
      key: Key,
      timeout: Duration
  ) = ZIO.serviceWithZIO[SeqKv](_.readOption(key, Some(timeout)))

  def write[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      value: Value
  ): ZIO[SeqKv, AskError, Unit] =
    ZIO.serviceWithZIO[SeqKv](_.write(key, value, None))

  def write[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      value: Value,
      timeout: Duration
  ): ZIO[SeqKv, AskError, Unit] =
    ZIO.serviceWithZIO[SeqKv](_.write(key, value, Some(timeout)))

  def cas[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      from: Value,
      to: Value,
      createIfNotExists: Boolean
  ): ZIO[SeqKv, AskError, Unit] =
    ZIO.serviceWithZIO[SeqKv](_.cas(key, from, to, createIfNotExists, None))

  def cas[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      from: Value,
      to: Value,
      createIfNotExists: Boolean,
      timeout: Duration
  ): ZIO[SeqKv, AskError, Unit] =
    ZIO.serviceWithZIO[SeqKv](_.cas(key, from, to, createIfNotExists, Some(timeout)))

  def update[Key, Value](key: Key) = PartiallyAppliedUpdate[SeqKv, Key, Value](key, None)

  def update[Key, Value](
      key: Key,
      timeout: Duration
  ) = PartiallyAppliedUpdate[SeqKv, Key, Value](key, Some(timeout))

  def updateZIO[Key, Value](key: Key) = PartiallyAppliedUpdateZIO[SeqKv, Key, Value](key, None)

  def updateZIO[Key, Value](
      key: Key,
      timeout: Duration
  ) = PartiallyAppliedUpdateZIO[SeqKv, Key, Value](key, Some(timeout))

  def writeIfNotExists[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      value: Value
  ) = ZIO.serviceWithZIO[SeqKv](_.writeIfNotExists(key, value, None))

  def writeIfNotExists[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      value: Value,
      timeout: Duration
  ) = ZIO.serviceWithZIO[SeqKv](_.writeIfNotExists(key, value, Some(timeout)))

  private[zioMaelstrom] val live: ZLayer[MessageSender, Nothing, SeqKv] =
    ZLayer(
      for
        sender <- ZIO.service[MessageSender]
        kvImpl = KvImpl(NodeId("seq-kv"), sender)
      yield new SeqKv:
        export kvImpl.*
    )
