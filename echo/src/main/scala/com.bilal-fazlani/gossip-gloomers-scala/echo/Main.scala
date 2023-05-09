package com.bilalfazlani.gossipGloomersScala
package echo

import zio.json.JsonDecoder
import zio.json.JsonEncoder
import zioMaelstrom.*
import zioMaelstrom.protocol.*
import zio.json.jsonHint
import zio.json.jsonDiscriminator
import zio.json.JsonCodec
import zio.json.jsonField
import zio.Task
import zio.Random

object Main extends MaelstromNode[Echo, EchoOk]:

  override val nodeInput = NodeInput.File("testing.txt")

  def handle(message: Message[Echo]) =
    for {
      _ <- debugMessage(s"handling message: $message")
      newMessageId <- Random.nextIntBetween(1, Int.MaxValue)
      _ <- send(
        Message(
          source = message.destination,
          destination = message.source,
          EchoOk(echo = message.body.echo, msg_id = MessageId(newMessageId), in_reply_to = message.body.msg_id)
        )
      )
    } yield ()

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
