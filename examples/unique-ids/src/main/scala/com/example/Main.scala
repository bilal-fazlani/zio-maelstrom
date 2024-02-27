package com.example.uniqueIds

//imports {
import com.bilalfazlani.zioMaelstrom.*
import zio.json.{JsonDecoder, JsonEncoder}
import zio.{ZIO, Ref, ZLayer}
//}

//messages{
case class Generate(msg_id: MessageId) extends NeedsReply derives JsonDecoder
case class GenerateOk(id: String, in_reply_to: MessageId, `type`: String = "generate_ok")
    extends Sendable,
      Reply derives JsonEncoder
//}

object Main extends MaelstromNode {

  val program = receive[Generate] { case request =>
    for {
      generated <- ZIO.serviceWithZIO[Ref[Int]](_.updateAndGet(_ + 1))
      combinedId = s"${me}_${generated}" // (1)!
      _ <- reply(GenerateOk(id = combinedId, in_reply_to = request.msg_id))
    } yield ()
  }.provideSome[MaelstromRuntime](ZLayer(Ref.make(0)))
}
