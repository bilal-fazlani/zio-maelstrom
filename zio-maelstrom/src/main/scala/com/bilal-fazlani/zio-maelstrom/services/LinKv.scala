package com.bilalfazlani.zioMaelstrom

import protocol.*
import zio.*
import zio.json.*

trait LinKv extends KvService

object LinKv:
  def read[Value] = PartiallyAppliedKvRead[LinKv, Value]()

  def write[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      value: Value,
      messageId: MessageId,
      timeout: Duration
  ): ZIO[LinKv, AskError, Unit] =
    ZIO.serviceWithZIO[LinKv](_.write(key, value, messageId, timeout))

  def cas[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      from: Value,
      to: Value,
      createIfNotExists: Boolean,
      messageId: MessageId,
      timeout: Duration
  ): ZIO[LinKv, AskError, Unit] =
    ZIO.serviceWithZIO[LinKv](_.cas(key, from, to, createIfNotExists, messageId, timeout))

  private[zioMaelstrom] val live: ZLayer[MessageSender, Nothing, LinKv] = ZLayer.fromZIO(
    for
      sender <- ZIO.service[MessageSender]
      kvImpl = KvImpl(NodeId("lin-kv"), sender)
    yield new LinKv:
      export kvImpl.*
  )
