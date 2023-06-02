## zio-maelstrom

A framework for building and running nodes for maelstrom simulations in Scala.

Uses [ZIO](https://zio.dev)

## Echo example

```scala
//Define the message types
case class Echo(echo: String, msg_id: MessageId) extends NeedsReply derives JsonDecoder
case class EchoOk(echo: String, in_reply_to: MessageId, `type`: String = "echo_ok") extends Sendable, Reply derives JsonEncoder

object EchoProgram extends ZIOAppDefault:
  //Define the node behaviour
  val echoHandler = receive[Echo](msg => msg reply EchoOk(echo = msg.echo, in_reply_to = msg.msg_id))
  //Run the node
  val run         = echoHandler.provideSome[Scope](MaelstromRuntime.live)
```