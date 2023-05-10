package com.bilalfazlani.gossipGloomersScala
package uniqueIds

import zio.json.*
import zioMaelstrom.*
import zioMaelstrom.protocol.*
import zio.*
import com.bilalfazlani.gossipGloomersScala.zioMaelstrom.NodeState.Initialised.state

object Main extends StatefulMaelstromNode[Generate, GenerateOk, Int]:

  // override def nodeInput: NodeInput = NodeInput.FilePath("unique-ids" / "testing.txt")

  // val state = ZIO.serviceWithZIO[Ref[NodeState[Int]]](_)

  enum StateUpdateResult:
    case Updated
    case Initializing

  // private def newNumber = state(s => s.modify[Int]{ //prevState =>
  //   //prevState.fold(ZIO.fail("???"))(s => ZIO.succeed((s.state + 1, s.copy(state = s.state + 1))))
  // })

  def handle(in: Message[Generate]) =
    for {
      myId <- me
      currentCount <- ZIO.service[Ref.Synchronized[NodeState[Int]]].map(_.get.flatMap(_.))
      out = GenerateOk(id = newId.toString, in_reply_to = in.body.msg_id)
      _ <- debugMessage(s"generate message: $in, out: $out")
      _ <- in reply out
    } yield ()

case class Generate(msg_id: MessageId, `type`: String = "generate") extends MessageWithId derives JsonDecoder

case class GenerateOk(id: String, in_reply_to: MessageId, `type`: String = "generate_ok") extends MessageWithReply derives JsonEncoder
