package com.example.uniqueIds

import com.bilalfazlani.zioMaelstrom.protocol.*
import com.bilalfazlani.zioMaelstrom.*
import zio.json.{JsonDecoder, JsonEncoder}
import zio.*

// Define a message that needs a reply
case class Generate(msg_id: MessageId) extends NeedsReply derives JsonDecoder

// Define reply message
case class GenerateOk(id: String, in_reply_to: MessageId, `type`: String = "generate_ok") 
  extends Sendable, Reply derives JsonEncoder

object Main extends ZIOAppDefault:

  // Define a handler for the message
  val handler = receiveR[Ref[Int], Generate] { case request =>
    for {
      newId <- ZIO.serviceWithZIO[Ref[Int]](_.updateAndGet(_ + 1))
      combinedId = s"${myNodeId}_${newId}"
      _     <- request reply GenerateOk(id = combinedId, in_reply_to = request.msg_id)
    } yield ()
  }

  // Run the handler
  val run = handler.provideSome[Scope](MaelstromRuntime.live, ZLayer.fromZIO(Ref.make(0)))
