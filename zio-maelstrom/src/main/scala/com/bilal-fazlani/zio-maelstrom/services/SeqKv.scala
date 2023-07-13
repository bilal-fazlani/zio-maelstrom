package com.bilalfazlani.zioMaelstrom

import protocol.*
import zio.*
import zio.json.*

trait SeqKv extends KvService

object SeqKv:
  def read[Value] = PartiallyAppliedKvRead[SeqKv, Value]()

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

  private[zioMaelstrom] val live: ZLayer[MessageSender, Nothing, SeqKv] = ZLayer.fromZIO(
    for
      sender <- ZIO.service[MessageSender]
      kvImpl = KvImpl(NodeId("seq-kv"), sender)
    yield new SeqKv:
      export kvImpl.*
  )
