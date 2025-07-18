package com.bilalfazlani.zioMaelstrom.services

import com.bilalfazlani.zioMaelstrom.*
import zio.*
import zio.json.*

private[zioMaelstrom] trait KvService:

  def read[Key: JsonEncoder, Value: JsonDecoder](key: Key): ZIO[Any, AskError, Value]

  def readOption[Key: JsonEncoder, Value: JsonDecoder](key: Key): ZIO[Any, AskError, Option[Value]]

  def write[Key: JsonEncoder, Value: JsonEncoder](key: Key, value: Value): ZIO[Any, AskError, Unit]

  def writeIfNotExists[Key: JsonEncoder, Value: JsonEncoder](key: Key, value: Value): ZIO[Any, AskError, Unit]

  def cas[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      from: Value,
      to: Value,
      createIfNotExists: Boolean
  ): ZIO[Any, AskError, Unit]

  def update[Key: JsonEncoder, Value: JsonCodec](key: Key, newValue: Option[Value] => Value): ZIO[Any, AskError, Value]

  // overall time taken by this method can be more than timeout
  // because this method retries infinitely if precondition keeps failing
  def updateZIO[Key: JsonEncoder, Value: JsonCodec, R, E](
      key: Key,
      newValue: Option[Value] => ZIO[R, E, Value]
  ): ZIO[R, AskError | E, Value]

private[zioMaelstrom] class KvImpl(
    private val remote: NodeId,
    private val sender: MessageSender
) extends KvService {

  override def read[Key: JsonEncoder, Value: JsonDecoder](
      key: Key
  ): ZIO[Any, AskError, Value] =
    sender
      .ask[Read[Key], ReadOk[Value]](Read(key), remote)
      .map(_.value)

  override def readOption[Key: JsonEncoder, Value: JsonDecoder](
      key: Key
  ): ZIO[Any, AskError, Option[Value]] =
    read[Key, Value](key).map(Some(_)).catchSome { case Error(ErrorCode.KeyDoesNotExist, _) =>
      ZIO.none
    }

  override def write[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      value: Value
  ): ZIO[Any, AskError, Unit] =
    sender
      .ask[Write[Key, Value], WriteOk](Write(key, value), remote)
      .unit

  override def writeIfNotExists[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      value: Value
  ): ZIO[Any, AskError, Unit] =
    sender
      .ask[Cas[Key, Value], CasOk](
        Cas(key, None, value, true),
        remote
      )
      .unit

  private def updateError(key: Any) = ZIO.logDebug(
    s"an attempt to replace value for key $key failed due to a race condition. will attempt again with the new value"
  )

  override def update[Key: JsonEncoder, Value: JsonCodec](
      key: Key,
      newValue: Option[Value] => Value
  ): ZIO[Any, AskError, Value] =
    for {
      current <- readOption[Key, Value](key)
      newVal = newValue(current)
      _ <- current match
        case None =>
          writeIfNotExists(key, newVal)
            .catchSome { case Error(ErrorCode.KeyAlreadyExists, _) =>
              updateError(key) *> update(key, newValue)
            }
        case Some(value) =>
          cas(key, value, newVal, false)
            .catchSome {
              case Error(ErrorCode.PreconditionFailed, _) =>
                updateError(key) *> update(key, newValue)
              case Error(ErrorCode.KeyDoesNotExist, _) =>
                updateError(key) *> update(key, newValue)
            }
    } yield newVal

  override def updateZIO[Key: JsonEncoder, Value: JsonCodec, R, E](
      key: Key,
      newValue: Option[Value] => ZIO[R, E, Value]
  ): ZIO[R, AskError | E, Value] =
    for {
      current <- readOption[Key, Value](key)
      newVal  <- newValue(current)
      _       <- current match
        case None =>
          writeIfNotExists(key, newVal)
            .catchSome { case Error(ErrorCode.KeyAlreadyExists, _) =>
              updateError(key) *> updateZIO(key, newValue)
            }
        case Some(value) =>
          cas(key, value, newVal, false)
            .catchSome {
              case Error(ErrorCode.PreconditionFailed, _) =>
                updateError(key) *> updateZIO(key, newValue)
              case Error(ErrorCode.KeyDoesNotExist, _) =>
                updateError(key) *> updateZIO(key, newValue)
            }
    } yield newVal

  override def cas[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      from: Value,
      to: Value,
      createIfNotExists: Boolean
  ): ZIO[Any, AskError, Unit] =
    sender
      .ask[Cas[Key, Value], CasOk](
        Cas(key, Some(from), to, createIfNotExists),
        remote
      )
      .unit
}

class PartiallyAppliedKvRead[Kv <: KvService: Tag, Value] {
  def apply[Key: JsonEncoder](key: Key)(using
      JsonDecoder[Value]
  ): ZIO[Kv, AskError, Value] =
    ZIO.serviceWithZIO[Kv](_.read[Key, Value](key))
}

case class PartiallyAppliedUpdate[Kv <: KvService: Tag, Key, Value](key: Key):
  def apply(
      newValue: Option[Value] => Value
  )(using JsonEncoder[Key], JsonCodec[Value]): ZIO[Kv, AskError, Value] =
    ZIO.serviceWithZIO[Kv](_.update(key, newValue))

case class PartiallyAppliedUpdateZIO[Kv <: KvService: Tag, Key, Value](key: Key):
  def apply[R, E](
      newValue: Option[Value] => ZIO[R, E, Value]
  )(using JsonEncoder[Key], JsonCodec[Value]): ZIO[Kv & R, AskError | E, Value] =
    ZIO.serviceWithZIO[Kv](_.updateZIO(key, newValue))
