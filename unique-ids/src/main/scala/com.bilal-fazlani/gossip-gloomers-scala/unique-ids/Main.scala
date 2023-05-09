package com.bilalfazlani.gossipGloomersScala
package uniqueIds

import zio.json.*
import zioMaelstrom.*
import zioMaelstrom.protocol.*
import zio.*
import java.nio.file.Path

object Main extends MaelstromNode[Generate, GenerateOk]:

  override def nodeInput: NodeInput = NodeInput.FilePath(Path.of("unique-ids", "testing.txt"))

  def handle(in: Message[Generate]) =
    for {
      newId <- Random.nextIntBetween(1, Int.MaxValue).map(MessageId.apply)
      out = GenerateOk(id = newId.toString, msg_id = newId, in_reply_to = in.body.msg_id)
      _ <- debugMessage(s"generate message: $in, out: $out")
      _ <- in reply out
    } yield ()

case class Generate(msg_id: MessageId, `type`: String = "generate") extends MessageWithId derives JsonDecoder

case class GenerateOk(id: String, msg_id: MessageId, in_reply_to: MessageId, `type`: String = "generate_ok") extends MessageWithId, MessageWithReply
    derives JsonEncoder
