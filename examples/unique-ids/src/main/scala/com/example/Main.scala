package com.example.uniqueIds

import com.bilalfazlani.zioMaelstrom.protocol.*
import com.bilalfazlani.zioMaelstrom.*
import zio.json.{JsonDecoder, JsonEncoder}
import zio.*

case class Generate(msg_id: MessageId, `type`: String) extends NeedsReply derives JsonDecoder

case class GenerateOk(id: String, in_reply_to: MessageId, `type`: String = "generate_ok") extends Sendable, Reply derives JsonEncoder

object Main extends ZIOAppDefault:

  val handler = receiveR[Ref[Int], Generate] { case request =>
    for {
      newId <- ZIO.serviceWithZIO[Ref[Int]](_.updateAndGet(_ + 1))
      _     <- request reply GenerateOk(id = s"${myNodeId}_$newId", in_reply_to = request.msg_id)
    } yield ()
  }

  val run = handler.provideSome[Scope](MaelstromRuntime.live(Settings.default), ZLayer.fromZIO(Ref.make(0)))
