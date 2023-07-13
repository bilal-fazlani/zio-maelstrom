package com.bilalfazlani.zioMaelstrom

import zio.*
import zio.json.*

trait LinKv extends KvService

object LinKv:
  def read[Value] = PartiallyAppliedKvRead[LinKv, Value]()

  def write[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      value: Value,
      timeout: Duration
  ): ZIO[LinKv, AskError, Unit] =
    ZIO.serviceWithZIO[LinKv](_.write(key, value, timeout))

  def cas[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      from: Value,
      to: Value,
      createIfNotExists: Boolean,
      timeout: Duration
  ): ZIO[LinKv, AskError, Unit] =
    ZIO.serviceWithZIO[LinKv](_.cas(key, from, to, createIfNotExists, timeout))

  private[zioMaelstrom] val live: ZLayer[MessageSender & MessageIdStore, Nothing, LinKv] =
    ZLayer.fromZIO(
      for
        sender         <- ZIO.service[MessageSender]
        messageIdStore <- ZIO.service[MessageIdStore]
        kvImpl = KvImpl(NodeId("lin-kv"), sender, messageIdStore)
      yield new LinKv:
        export kvImpl.*
    )
