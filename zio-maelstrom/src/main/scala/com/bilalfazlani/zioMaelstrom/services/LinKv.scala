package com.bilalfazlani.zioMaelstrom.services

import com.bilalfazlani.zioMaelstrom.{AskError, MessageSender, NodeId}
import zio.*
import zio.json.*

trait LinKv extends KvService

object LinKv:
  def read[Value] = PartiallyAppliedKvRead[LinKv, Value]()

  def readOption[Key: JsonEncoder, Value: JsonDecoder](
      key: Key
  ) = ZIO.serviceWithZIO[LinKv](_.readOption(key))

  def write[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      value: Value
  ): ZIO[LinKv, AskError, Unit] =
    ZIO.serviceWithZIO[LinKv](_.write(key, value))

  def cas[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      from: Value,
      to: Value,
      createIfNotExists: Boolean
  ): ZIO[LinKv, AskError, Unit] =
    ZIO.serviceWithZIO[LinKv](_.cas(key, from, to, createIfNotExists))

  def update[Key, Value](
      key: Key
  ) = PartiallyAppliedUpdate[LinKv, Key, Value](key)

  def updateZIO[Key, Value](
      key: Key
  ) = PartiallyAppliedUpdateZIO[LinKv, Key, Value](key)

  def writeIfNotExists[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      value: Value
  ) = ZIO.serviceWithZIO[LinKv](_.writeIfNotExists(key, value))

  private[zioMaelstrom] val live: ZLayer[MessageSender, Nothing, LinKv] =
    ZLayer(
      for
        sender <- ZIO.service[MessageSender]
        kvImpl = KvImpl(NodeId("lin-kv"), sender)
      yield new LinKv:
        export kvImpl.*
    )
