## zio-maelstrom

A framework for building and running nodes for maelstrom simulations in Scala.

Uses [ZIO](https://zio.dev)

## Echo example

```scala
//Define the message types
case class Echo(echo: String, msg_id: MessageId, `type`: String) extends MessageWithId derives JsonDecoder

case class EchoOk(echo: String, in_reply_to: MessageId, `type`: String = "echo_ok") extends MessageWithReply derives JsonEncoder

object EchoProgram extends ZIOAppDefault:
  //Define the node behaviour
  val handler = receive[Echo] { msg => msg reply EchoOk(echo = msg.body.echo, in_reply_to = msg.body.msg_id) }

  //Run the node
  val run = handler.provideSome[Scope](MaelstromRuntime.live)  
```