package com.bilalfazlani.zioMaelstrom
package testkit

import zio.*
import zio.json.*

case class KvFake(ref: Ref.Synchronized[Map[Any, Any]], messageIdStore: MessageIdStore)
    extends KvService:
  override def read[Key: JsonEncoder, Value: JsonDecoder](
      key: Key,
      timeout: Duration
  ): ZIO[Any, AskError, Value] =
    ref.get.map(_.get(key).get.asInstanceOf[Value])

  override def readOption[Key: JsonEncoder, Value: JsonDecoder](
      key: Key,
      timeout: zio.Duration
  ): ZIO[Any, AskError, Option[Value]] =
    ref.get.map(_.get(key).map(_.asInstanceOf[Value]))

  override def write[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      value: Value,
      timeout: Duration
  ): ZIO[Any, AskError, Unit] =
    ref.update(_ + (key -> value)).unit

  override def writeIfNotExists[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      value: Value,
      timeout: zio.Duration
  ): ZIO[Any, AskError, Unit] =
    ref.updateZIO { map =>
      map.get(key) match {
        case Some(_) =>
          messageIdStore.next.flatMap(messageId =>
            ZIO.fail(
              ErrorMessage(
                messageId,
                ErrorCode.PreconditionFailed,
                s"Value for key $key already exists"
              )
            )
          )
        case None => ZIO.succeed(map + (key -> value))
      }
    }

  override def cas[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      from: Value,
      to: Value,
      createIfNotExists: Boolean,
      timeout: Duration
  ): ZIO[Any, AskError, Unit] =
    ref.updateZIO { map =>
      map.get(key) match {
        case Some(`from`)              => ZIO.succeed(map + (key -> to))
        case None if createIfNotExists => ZIO.succeed(map + (key -> to))
        case None =>
          messageIdStore.next.flatMap(messageId =>
            ZIO.fail(ErrorMessage(messageId, ErrorCode.KeyDoesNotExist, s"Key $key does not exist"))
          )
        case Some(other) =>
          messageIdStore.next.flatMap(messageId =>
            ZIO.fail(
              ErrorMessage(
                messageId,
                ErrorCode.PreconditionFailed,
                s"Expected $from but found $other"
              )
            )
          )
      }
    }

  override def cas[Key: JsonEncoder, Value: JsonCodec](
      key: Key,
      newValue: Option[Value] => Value,
      timeout: zio.Duration
  ): ZIO[Any, AskError, Unit] =
    ref.update { map =>
      val current = map.get(key).map(_.asInstanceOf[Value])
      val newVal  = newValue(current)
      map + (key -> newVal)
    }

  override def casZIO[Key: JsonEncoder, Value: JsonCodec, R, E](key: Key, newValue: Option[Value] => ZIO[R, E, Value], timeout: zio.Duration): ZIO[R, AskError | E, Unit] = 
    ref.updateZIO { map =>
      val current = map.get(key).map(_.asInstanceOf[Value])
      newValue(current).map(newVal => map + (key -> newVal))
    }


object KvFake:

  private val mapLayer = ZLayer(Ref.Synchronized.make(Map.empty[Any, Any]))

  val linKv: ZLayer[Any, Nothing, LinKv] = ZLayer.make[LinKv](
    ZLayer.fromFunction(KvFake.apply),
    mapLayer,
    MessageIdStore.live,
    ZLayer.fromFunction((fake: KvFake) =>
      new LinKv {
        export fake.*
      }
    )
  )

  val seqKv: ZLayer[Any, Nothing, SeqKv] = ZLayer.make[SeqKv](
    ZLayer.fromFunction(KvFake.apply),
    mapLayer,
    MessageIdStore.live,
    ZLayer.fromFunction((fake: KvFake) =>
      new SeqKv {
        export fake.*
      }
    )
  )

  val lwwKv: ZLayer[Any, Nothing, LwwKv] = ZLayer.make[LwwKv](
    ZLayer.fromFunction(KvFake.apply),
    mapLayer,
    MessageIdStore.live,
    ZLayer.fromFunction((fake: KvFake) =>
      new LwwKv {
        export fake.*
      }
    )
  )
