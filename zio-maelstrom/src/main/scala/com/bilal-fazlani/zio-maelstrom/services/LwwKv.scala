package com.bilalfazlani.zioMaelstrom

import protocol.*
import zio.*
import zio.json.*

trait LwwKv extends KvService

object LwwKv:
  def read[Value] = PartiallyAppliedKvRead[LwwKv, Value]()

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

  private[zioMaelstrom] val live: ZLayer[MessageSender, Nothing, LwwKv] = ZLayer.fromZIO(
    for
      sender <- ZIO.service[MessageSender]
      kvImpl = KvImpl(NodeId("lww-kv"), sender)
    yield new LwwKv:
      export kvImpl.*
  )
