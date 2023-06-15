package com.example.echo

//imports {
import zio.json.{JsonEncoder, JsonDecoder}
import zio.{ZIOAppDefault, ZIO}
import com.bilalfazlani.zioMaelstrom.protocol.*
import com.bilalfazlani.zioMaelstrom.*
//}

//messages {
case class Echo(echo: String, msg_id: MessageId) extends NeedsReply derives JsonDecoder
case class EchoOk(echo: String, in_reply_to: MessageId, `type`: String = "echo_ok") extends Sendable, Reply
    derives JsonEncoder
//}

object Main extends ZIOAppDefault {

  val echoHandler: ZIO[MaelstromRuntime, Nothing, Unit] =
    receive[Echo](msg => msg reply EchoOk(echo = msg.echo, in_reply_to = msg.msg_id))

  val run = echoHandler.provide(MaelstromRuntime.live)
}
