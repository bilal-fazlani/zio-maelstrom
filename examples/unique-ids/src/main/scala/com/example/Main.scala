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
      myNodeId <- me
      newId <- ZIO.serviceWithZIO[Ref[Int]](_.updateAndGet(_ + 1))
      _ <- request reply GenerateOk(id = s"${myNodeId}_$newId", in_reply_to = request.body.msg_id)
    } yield ()
  }

  val settings = Settings(NodeInput.FilePath("examples" / "unique-ids" / "simulation.txt"), true)

  val run = MaelstromRuntime.run(app).provideSome[Scope](MaelstromRuntime.live(settings), ZLayer.fromZIO(Ref.make(0)))
