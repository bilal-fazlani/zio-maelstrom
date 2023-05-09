package com.bilalfazlani.gossipGloomersScala
package echo

import zio.json.JsonDecoder
import zio.json.JsonEncoder
import zioMaelstrom.*
import zioMaelstrom.protocol.*
import zio.json.jsonHint
import zio.json.jsonDiscriminator
import scala.util.Random
import zio.json.JsonCodec
import zio.json.jsonField

object Main extends MaelstromNode[Echo, EchoOk]:
  def handle(message: Message[Echo]) =
    debugMessage(s"handling message: $message") *>
      send(
        Message(
          source = NodeId("A"),
          destination = NodeId("B"),
          EchoOk(echo = message.body.echo, msg_id = MessageId(Random.nextInt), in_reply_to = message.body.msg_id)
        )
      )

@jsonDiscriminator("type")
sealed trait MyProtocol extends MessageBody

@jsonHint("echo")
case class Echo(
    echo: String,
    msg_id: MessageId,
    `type`: String = "echo"
) extends MyProtocol,
      MessageWithId
    derives JsonEncoder,
      JsonDecoder

@jsonHint("echo_ok")
case class EchoOk(
    echo: String,
    msg_id: MessageId,
    in_reply_to: MessageId,
    `type`: String = "echo_ok"
) extends MessageBody,
      MessageWithId,
      MessageWithReply
    derives JsonCodec
