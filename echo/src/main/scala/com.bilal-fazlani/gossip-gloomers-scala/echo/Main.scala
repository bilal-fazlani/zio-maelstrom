package com.bilalfazlani.gossipGloomersScala
package echo

import zio.json.*
import zioMaelstrom.*
import zioMaelstrom.protocol.*
import zio.*

object Main extends MaelstromNode[Echo, EchoOk]:
  def handle(in: Message[Echo]) =
    for {
      newId <- Random.nextIntBetween(1, Int.MaxValue).map(MessageId.apply)
      out = EchoOk(echo = in.body.echo, msg_id = newId, in_reply_to = in.body.msg_id)
      _ <- in reply out
    } yield ()

case class Echo(echo: String, msg_id: MessageId, `type`: String = "echo") extends MessageWithId derives JsonDecoder

case class EchoOk(echo: String, msg_id: MessageId, in_reply_to: MessageId, `type`: String = "echo_ok") extends MessageWithId, MessageWithReply
    derives JsonEncoder
