package example
package echo

import zio.json.*
import zio.*
import com.bilalfazlani.gossipGloomersScala.zioMaelstrom.protocol.*
import com.bilalfazlani.gossipGloomersScala.zioMaelstrom.*

case class Echo(echo: String, msg_id: MessageId, `type`: String) extends MessageWithId derives JsonDecoder

case class EchoOk(echo: String, in_reply_to: MessageId, `type`: String = "echo_ok") extends MessageWithReply derives JsonEncoder

object Main extends ZIOAppDefault:
  val app = MaelstromApp.make[Echo](in => in reply EchoOk(echo = in.body.echo, in_reply_to = in.body.msg_id))
  val run = MaelstromRuntime run (app, NodeInput.FilePath("echo" / "simulation.txt"))
