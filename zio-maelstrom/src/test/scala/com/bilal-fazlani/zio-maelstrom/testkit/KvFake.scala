package com.bilalfazlani.zioMaelstrom
package testkit

import zio.*
import zio.json.*
import protocol.*

case class KvFake(ref: Ref.Synchronized[Map[Any, Any]]) extends KvService:
  override def read[Key: JsonEncoder, Value: JsonDecoder](
      key: Key,
      timeout: Duration
  ): ZIO[Any, AskError, Value] =
    ref.get.map(_.get(key).get.asInstanceOf[Value])

  override def write[Key: JsonEncoder, Value: JsonEncoder](
      key: Key,
      value: Value,
      timeout: Duration
  ): ZIO[Any, AskError, Unit] =
    ref.update(_ + (key -> value)).unit

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
          MessageId.random.flatMap(messageId =>
            ZIO.fail(ErrorMessage(messageId, ErrorCode.KeyDoesNotExist, s"Key $key does not exist"))
          )
        case Some(other) =>
          MessageId.random.flatMap(messageId =>
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

object KvFake:
  val linKv = ZLayer.fromZIO(Ref.Synchronized.make(Map.empty[Any, Any]).map { r =>
    val impl = KvFake(r)
    new LinKv {
      export impl.*
    }
  })

  val seqKv = ZLayer.fromZIO(Ref.Synchronized.make(Map.empty[Any, Any]).map { r =>
    val impl = KvFake(r)
    new SeqKv {
      export impl.*
    }
  })

  val lwwKv = ZLayer.fromZIO(Ref.Synchronized.make(Map.empty[Any, Any]).map { r =>
    val impl = KvFake(r)
    new LwwKv {
      export impl.*
    }
  })
