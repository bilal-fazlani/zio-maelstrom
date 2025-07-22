package com.bilalfazlani.zioMaelstrom
package testkit

import com.bilalfazlani.zioMaelstrom.services.*
import zio.*
import zio.json.*

case class KvFake(ref: Ref.Synchronized[Map[Any, Any]]) extends KvService:
  override def read[Key: JsonEncoder, Value: JsonDecoder](key: Key, timeout: Option[Duration]): ZIO[Any, AskError, Value] =
    ref.get.map(_(key).asInstanceOf[Value])

  override def readOption[Key: JsonEncoder, Value: JsonDecoder](
      key: Key,
      timeout: Option[Duration]
  ): ZIO[Any, AskError, Option[Value]] =
    ref.get.map(_.get(key).map(_.asInstanceOf[Value]))

  override def write[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      value: Value,
      timeout: Option[Duration]
  ): ZIO[Any, AskError, Unit] =
    ref.update(_ + (key -> value)).unit

  override def writeIfNotExists[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      value: Value,
      timeout: Option[Duration]
  ): ZIO[Any, AskError, Unit] =
    ref.updateZIO { map =>
      map.get(key) match {
        case Some(_) =>
          ZIO.fail(
            Error(
              ErrorCode.PreconditionFailed,
              s"Value for key $key already exists"
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
      timeout: Option[Duration]
  ): ZIO[Any, AskError, Unit] =
    ref.updateZIO { map =>
      map.get(key) match {
        case Some(`from`)              => ZIO.succeed(map + (key -> to))
        case None if createIfNotExists => ZIO.succeed(map + (key -> to))
        case None                      =>
          ZIO.fail(Error(ErrorCode.KeyDoesNotExist, s"Key $key does not exist"))
        case Some(other) =>
          ZIO.fail(
            Error(
              ErrorCode.PreconditionFailed,
              s"Expected $from but found $other"
            )
          )
      }
    }

  override def update[Key: JsonEncoder, Value: JsonCodec](
      key: Key,
      newValue: Option[Value] => Value,
      timeout: Option[Duration]
  ): ZIO[Any, AskError, Value] =
    ref.modify { map =>
      val current = map.get(key).map(_.asInstanceOf[Value])
      val newVal  = newValue(current)
      (newVal, map + (key -> newVal))
    }

  override def updateZIO[Key: JsonEncoder, Value: JsonCodec, R, E](
      key: Key,
      newValue: Option[Value] => ZIO[R, E, Value],
      timeout: Option[Duration]
  ): ZIO[R, AskError | E, Value] =
    ref.modifyZIO { map =>
      val current = map.get(key).map(_.asInstanceOf[Value])
      for {
        newVal <- newValue(current)
      } yield (newVal, map + (key -> newVal))
    }

object KvFake:

  private val mapLayer = ZLayer(Ref.Synchronized.make(Map.empty[Any, Any]))

  val linKv: ZLayer[Any, Nothing, LinKv] = ZLayer.make[LinKv](
    ZLayer.fromFunction(KvFake.apply),
    mapLayer,
    ZLayer.fromFunction((fake: KvFake) =>
      new LinKv {
        export fake.*
      }
    )
  )

  val seqKv: ZLayer[Any, Nothing, SeqKv] = ZLayer.make[SeqKv](
    ZLayer.fromFunction(KvFake.apply),
    mapLayer,
    ZLayer.fromFunction((fake: KvFake) =>
      new SeqKv {
        export fake.*
      }
    )
  )

  val lwwKv: ZLayer[Any, Nothing, LwwKv] = ZLayer.make[LwwKv](
    ZLayer.fromFunction(KvFake.apply),
    mapLayer,
    ZLayer.fromFunction((fake: KvFake) =>
      new LwwKv {
        export fake.*
      }
    )
  )
