package com.example.uniqueIds

import com.bilalfazlani.zioMaelstrom.protocol.*
import com.bilalfazlani.zioMaelstrom.*
import zio.json.{JsonDecoder, JsonEncoder}
import zio.*

case class Generate(msg_id: MessageId, `type`: String) extends MessageWithId derives JsonDecoder

case class GenerateOk(id: String, in_reply_to: MessageId, `type`: String = "generate_ok") extends MessageWithReply derives JsonEncoder

object Main extends ZIOAppDefault:
  val app = MaelstromAppR.make[Ref[Int], Generate] { case request =>
    for {
      newId <- ZIO.serviceWithZIO[Ref[Int]](_.updateAndGet(_ + 1))
      context <- ZIO.service[Context]
      _ <- request reply GenerateOk(id = s"${context.me}_$newId", in_reply_to = request.body.msg_id)
    } yield ()
  }
  val run = (MaelstromRuntime run app).provide(ZLayer.fromZIO(Ref.make(0)))
