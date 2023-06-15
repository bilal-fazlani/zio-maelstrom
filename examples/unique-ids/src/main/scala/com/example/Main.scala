package com.example.uniqueIds

import com.bilalfazlani.zioMaelstrom.protocol.*
import com.bilalfazlani.zioMaelstrom.*
import zio.json.{JsonDecoder, JsonEncoder}
import zio.*

// Define input message
case class Generate(msg_id: MessageId) extends NeedsReply derives JsonDecoder

// Define reply message
case class GenerateOk(id: String, in_reply_to: MessageId, `type`: String = "generate_ok") extends Sendable, Reply
    derives JsonEncoder

object Main extends ZIOAppDefault:

  // Define a handler for the message
  val handler = receive[Generate] { case request =>
    for {
      generated <- ZIO.serviceWithZIO[Ref[Int]](_.updateAndGet(_ + 1))
      combinedId = s"${me}_${generated}"
      _ <- request reply GenerateOk(id = combinedId, in_reply_to = request.msg_id)
    } yield ()
  }

  // Run the handler
  val settings = Settings(nodeInput = NodeInput.FilePath("examples" / "unique-ids" / "simulation.txt"))
  val run      = handler.provide(MaelstromRuntime.live(settings), ZLayer.fromZIO(Ref.make(0)))
