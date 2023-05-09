package com.bilalfazlani.gossipGloomersScala
package echo

import zio.json.*
import zioMaelstrom.*
import zioMaelstrom.protocol.*
import zio.*

object Main extends MaelstromNode[Echo, EchoOk]:

  def handle(in_msg: Message[Echo]) =
    for {
      newMessageId <- Random.nextIntBetween(1, Int.MaxValue).map(MessageId.apply)
      _ <- in_msg reply EchoOk(echo = in_msg.body.echo, msg_id = newMessageId, in_reply_to = in_msg.body.msg_id)
    } yield ()

@jsonDiscriminator("type")
sealed trait EchoProtocol extends MessageBody

@jsonHint("echo")
case class Echo(echo: String, msg_id: MessageId, `type`: String = "echo") extends EchoProtocol, MessageWithId derives JsonDecoder

@jsonHint("echo_ok")
case class EchoOk(echo: String, msg_id: MessageId, in_reply_to: MessageId, `type`: String = "echo_ok") extends MessageWithId, MessageWithReply
    derives JsonEncoder
