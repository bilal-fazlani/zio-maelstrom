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

object KvFake:

  private val mapLayer = ZLayer.fromZIO(Ref.Synchronized.make(Map.empty[Any, Any]))

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
