package com.bilalfazlani.zioMaelstrom.services

import com.bilalfazlani.zioMaelstrom.{AskError, MessageSender, NodeId}
import zio.*
import zio.json.*

trait SeqKv extends KvService

object SeqKv:
  def read[Value] = PartiallyAppliedKvRead[SeqKv, Value]()

  def readOption[Key: JsonEncoder, Value: JsonDecoder](
      key: Key
  ) = ZIO.serviceWithZIO[SeqKv](_.readOption(key))

  def write[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      value: Value
  ): ZIO[SeqKv, AskError, Unit] =
    ZIO.serviceWithZIO[SeqKv](_.write(key, value))

  def cas[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      from: Value,
      to: Value,
      createIfNotExists: Boolean
  ): ZIO[SeqKv, AskError, Unit] =
    ZIO.serviceWithZIO[SeqKv](_.cas(key, from, to, createIfNotExists))

  def update[Key, Value](key: Key) = PartiallyAppliedUpdate[SeqKv, Key, Value](key)

  def updateZIO[Key, Value](key: Key) = PartiallyAppliedUpdateZIO[SeqKv, Key, Value](key)

  def writeIfNotExists[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      value: Value
  ) = ZIO.serviceWithZIO[SeqKv](_.writeIfNotExists(key, value))

  private[zioMaelstrom] val live: ZLayer[MessageSender, Nothing, SeqKv] =
    ZLayer(
      for
        sender <- ZIO.service[MessageSender]
        kvImpl = KvImpl(NodeId("seq-kv"), sender)
      yield new SeqKv:
        export kvImpl.*
    )
