package com.bilalfazlani.zioMaelstrom

import zio.*
import zio.json.*

private[zioMaelstrom] trait KvService:

  def read[Key: JsonEncoder, Value: JsonDecoder](
      key: Key,
      timeout: Duration
  ): ZIO[Any, AskError, Value]

  def readOption[Key: JsonEncoder, Value: JsonDecoder](
      key: Key,
      timeout: Duration
  ): ZIO[Any, AskError, Option[Value]]

  def write[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      value: Value,
      timeout: Duration
  ): ZIO[Any, AskError, Unit]

  def writeIfNotExists[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      value: Value,
      timeout: Duration
  ): ZIO[Any, AskError, Unit]

  def cas[Key: JsonEncoder, Value: JsonCodec](
      key: Key,
      newValue: Option[Value] => Value,
      timeout: Duration
  ): ZIO[Any, AskError, Unit]

  // overall time taken by this method can be more than timeout
  // because this method retries infinitely if precondition keeps failing
  def casZIO[Key: JsonEncoder, Value: JsonCodec, R, E](
      key: Key,
      newValue: Option[Value] => ZIO[R, E, Value],
      timeout: Duration
  ): ZIO[R, AskError | E, Unit]

private[zioMaelstrom] class KvImpl(
    private val remote: NodeId,
    private val sender: MessageSender,
    private val messageIdStore: MessageIdStore,
    private val logger: Logger
) extends KvService {

  override def read[Key: JsonEncoder, Value: JsonDecoder](
      key: Key,
      timeout: Duration
  ): ZIO[Any, AskError, Value] =
    messageIdStore.next.flatMap { messageId =>
      sender
        .ask[KvRead[Key], KvReadOk[Value]](KvRead(key, messageId), remote, timeout)
        .map(_.value)
    }

  override def readOption[Key: JsonEncoder, Value: JsonDecoder](
      key: Key,
      timeout: Duration
  ): ZIO[Any, AskError, Option[Value]] =
    read[Key, Value](key, timeout).map(Some(_)).catchSome {
      case ErrorMessage(_, ErrorCode.KeyDoesNotExist, _, _) => ZIO.succeed(None)
    }

  override def write[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      value: Value,
      timeout: Duration
  ): ZIO[Any, AskError, Unit] =
    messageIdStore.next.flatMap { messageId =>
      sender
        .ask[KvWrite[Key, Value], KvWriteOk](KvWrite(key, value, messageId), remote, timeout)
        .unit
    }

  override def writeIfNotExists[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      value: Value,
      timeout: Duration
  ): ZIO[Any, AskError, Unit] =
    messageIdStore.next.flatMap { messageId =>
      sender
        .ask[CompareAndSwap[Key, Value], CompareAndSwapOk](
          CompareAndSwap(key, None, value, true, messageId),
          remote,
          timeout
        )
        .unit
    }

  private def casError(key: Any) = logger.debug(
    s"an attempt to replace value for key $key failed due to a race condition. will attempt again with the new value"
  )

  override def cas[Key: JsonEncoder, Value: JsonCodec](
      key: Key,
      newValue: Option[Value] => Value,
      timeout: Duration
  ): ZIO[Any, AskError, Unit] =
    for {
      current <- readOption[Key, Value](key, timeout)
      newVal = newValue(current)
      _ <- current match
        case None =>
          writeIfNotExists(key, newVal, timeout)
            .catchSome { case ErrorMessage(_, ErrorCode.KeyAlreadyExists, _, _) =>
              casError(key) *> cas(key, newValue, timeout)
            }
        case Some(value) =>
          cas(key, value, newVal, false, timeout)
            .catchSome {
              case ErrorMessage(_, ErrorCode.PreconditionFailed, _, _) =>
                casError(key) *> cas(key, newValue, timeout)
              case ErrorMessage(_, ErrorCode.KeyDoesNotExist, _, _) =>
                casError(key) *> cas(key, newValue, timeout)
            }
    } yield ()

  override def casZIO[Key: JsonEncoder, Value: JsonCodec, R, E](
      key: Key,
      newValue: Option[Value] => ZIO[R, E, Value],
      timeout: Duration
  ): ZIO[R, AskError | E, Unit] =
    for {
      current <- readOption[Key, Value](key, timeout)
      newVal  <- newValue(current)
      _ <- current match
        case None =>
          writeIfNotExists(key, newVal, timeout)
            .catchSome { case ErrorMessage(_, ErrorCode.KeyAlreadyExists, _, _) =>
              casError(key) *> casZIO(key, newValue, timeout)
            }
        case Some(value) =>
          cas(key, value, newVal, false, timeout)
            .catchSome {
              case ErrorMessage(_, ErrorCode.PreconditionFailed, _, _) =>
                casError(key) *> casZIO(key, newValue, timeout)
              case ErrorMessage(_, ErrorCode.KeyDoesNotExist, _, _) =>
                casError(key) *> casZIO(key, newValue, timeout)
            }
    } yield ()

  private def cas[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      from: Value,
      to: Value,
      createIfNotExists: Boolean,
      timeout: Duration
  ): ZIO[Any, AskError, Unit] =
    messageIdStore.next.flatMap { messageId =>
      sender
        .ask[CompareAndSwap[Key, Value], CompareAndSwapOk](
          CompareAndSwap(key, Some(from), to, createIfNotExists, messageId),
          remote,
          timeout
        )
        .unit
    }
}

class PartiallyAppliedKvRead[Kv <: KvService: Tag, Value] {
  def apply[Key: JsonEncoder](key: Key, timeout: Duration)(using
      JsonDecoder[Value]
  ): ZIO[Kv, AskError, Value] =
    ZIO.serviceWithZIO[Kv](_.read[Key, Value](key, timeout))
}

case class PartiallyAppliedCas[Kv <: KvService: Tag, Key, Value](key: Key, timeout: Duration):
  def apply(
      newValue: Option[Value] => Value
  )(using JsonEncoder[Key], JsonCodec[Value]): ZIO[Kv, AskError, Unit] =
    ZIO.serviceWithZIO[Kv](_.cas(key, newValue, timeout))

case class PartiallyAppliedCasZIO[Kv <: KvService: Tag, Key, Value](key: Key, timeout: Duration):
  def apply[R, E](
      newValue: Option[Value] => ZIO[R, E, Value]
  )(using JsonEncoder[Key], JsonCodec[Value]): ZIO[Kv & R, AskError | E, Unit] =
    ZIO.serviceWithZIO[Kv](_.casZIO(key, newValue, timeout))
