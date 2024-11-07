package com.example.echo

//imports {
import zio.json.{JsonEncoder, JsonDecoder}
import zio.ZIO
import com.bilalfazlani.zioMaelstrom.*
//}

//messages {
case class Echo(echo: String, msg_id: MessageId) extends NeedsReply // (1)!
    derives JsonDecoder // (2)!

case class EchoOk(echo: String, in_reply_to: MessageId, `type`: String = "echo_ok")
    extends Sendable,
      Reply             // (3)!
    derives JsonEncoder // (4)!
//}

object Main extends MaelstromNode { // (1)!
  val program: ZIO[MaelstromRuntime, Nothing, Unit] =
    receive[Echo](msg => reply(EchoOk(echo = msg.echo, in_reply_to = msg.msg_id))) // (2)!
}
