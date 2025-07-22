package com.bilalfazlani.zioMaelstrom.services

import com.bilalfazlani.zioMaelstrom.{AskError, MessageSender, NodeId}
import zio.*
import zio.json.*

trait LinKv extends KvService

object LinKv:
  def read[Value] = PartiallyAppliedKvRead[LinKv, Value]

  def readOption[Key: JsonEncoder, Value: JsonDecoder](
      key: Key
  ) = ZIO.serviceWithZIO[LinKv](_.readOption(key, None))

  def readOption[Key: JsonEncoder, Value: JsonDecoder](
      key: Key,
      timeout: Duration
  ) = ZIO.serviceWithZIO[LinKv](_.readOption(key, Some(timeout)))

  def write[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      value: Value
  ): ZIO[LinKv, AskError, Unit] =
    ZIO.serviceWithZIO[LinKv](_.write(key, value, None))

  def write[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      value: Value,
      timeout: Duration
  ): ZIO[LinKv, AskError, Unit] =
    ZIO.serviceWithZIO[LinKv](_.write(key, value, Some(timeout)))

  def cas[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      from: Value,
      to: Value,
      createIfNotExists: Boolean
  ): ZIO[LinKv, AskError, Unit] =
    ZIO.serviceWithZIO[LinKv](_.cas(key, from, to, createIfNotExists, None))

  def cas[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      from: Value,
      to: Value,
      createIfNotExists: Boolean,
      timeout: Duration
  ): ZIO[LinKv, AskError, Unit] =
    ZIO.serviceWithZIO[LinKv](_.cas(key, from, to, createIfNotExists, Some(timeout)))

  def update[Key, Value](
      key: Key
  ) = PartiallyAppliedUpdate[LinKv, Key, Value](key, None)

  def update[Key, Value](
      key: Key,
      timeout: Duration
  ) = PartiallyAppliedUpdate[LinKv, Key, Value](key, Some(timeout))

  def updateZIO[Key, Value](
      key: Key
  ) = PartiallyAppliedUpdateZIO[LinKv, Key, Value](key, None)

  def updateZIO[Key, Value](
      key: Key,
      timeout: Duration
  ) = PartiallyAppliedUpdateZIO[LinKv, Key, Value](key, Some(timeout))

  def writeIfNotExists[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      value: Value
  ) = ZIO.serviceWithZIO[LinKv](_.writeIfNotExists(key, value, None))

  def writeIfNotExists[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      value: Value,
      timeout: Duration
  ) = ZIO.serviceWithZIO[LinKv](_.writeIfNotExists(key, value, Some(timeout)))

  private[zioMaelstrom] val live: ZLayer[MessageSender, Nothing, LinKv] =
    ZLayer(
      for
        sender <- ZIO.service[MessageSender]
        kvImpl = KvImpl(NodeId("lin-kv"), sender)
      yield new LinKv:
        export kvImpl.*
    )
