package com.bilalfazlani.zioMaelstrom

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

  private[zioMaelstrom] val live: ZLayer[MessageSender & MessageIdStore, Nothing, SeqKv] =
    ZLayer(
      for
        sender         <- ZIO.service[MessageSender]
        messageIdStore <- ZIO.service[MessageIdStore]
        kvImpl = KvImpl(NodeId("seq-kv"), sender, messageIdStore)
      yield new SeqKv:
        export kvImpl.*
    )
