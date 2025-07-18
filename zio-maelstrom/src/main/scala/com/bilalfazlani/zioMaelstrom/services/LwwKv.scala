package com.bilalfazlani.zioMaelstrom.services

import com.bilalfazlani.zioMaelstrom.{AskError, MessageSender, NodeId}
import zio.*
import zio.json.*

trait LwwKv extends KvService

object LwwKv:
  def read[Value] = PartiallyAppliedKvRead[LwwKv, Value]()

  def readOption[Key: JsonEncoder, Value: JsonDecoder](
      key: Key
  ) = ZIO.serviceWithZIO[LwwKv](_.readOption(key))

  def write[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      value: Value
  ): ZIO[LwwKv, AskError, Unit] =
    ZIO.serviceWithZIO[LwwKv](_.write(key, value))

  def cas[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      from: Value,
      to: Value,
      createIfNotExists: Boolean
  ): ZIO[LwwKv, AskError, Unit] =
    ZIO.serviceWithZIO[LwwKv](_.cas(key, from, to, createIfNotExists))

  def update[Key, Value](key: Key) = PartiallyAppliedUpdate[LwwKv, Key, Value](key)

  def updateZIO[Key, Value](key: Key) = PartiallyAppliedUpdateZIO[LwwKv, Key, Value](key)

  def writeIfNotExists[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      value: Value
  ) = ZIO.serviceWithZIO[LwwKv](_.writeIfNotExists(key, value))

  private[zioMaelstrom] val live: ZLayer[MessageSender, Nothing, LwwKv] =
    ZLayer(
      for
        sender <- ZIO.service[MessageSender]
        kvImpl = KvImpl(NodeId("lww-kv"), sender)
      yield new LwwKv:
        export kvImpl.*
    )
