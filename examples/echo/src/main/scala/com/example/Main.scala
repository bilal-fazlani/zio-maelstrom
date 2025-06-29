package com.example.echo

//imports {
import zio.json.{JsonEncoder, JsonDecoder}
import zio.ZIO
import com.bilalfazlani.zioMaelstrom.*
//}

//messages {
case class Echo(echo: String) derives JsonDecoder   // (1)!
case class EchoOk(echo: String) derives JsonEncoder // (2)!
//}


object Main extends MaelstromNode { // (1)!
  val program =
    receive[Echo](msg => reply(EchoOk(msg.echo))) // (2)!
}
