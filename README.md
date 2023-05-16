## zio-maelstrom

A framework for building and running nodes for maelstrom simuations.

Uses [zio](https://zio.dev)

## Echo example

```scala
//Define the message types
case class Echo(echo: String, msg_id: MessageId, `type`: String) 
    extends MessageWithId derives JsonDecoder
case class EchoOk(echo: String, in_reply_to: MessageId, `type`: String = "echo_ok") 
    extends MessageWithReply derives JsonEncoder


object Main extends ZIOAppDefault:
  //Define the node behaviour
  val app = MaelstromApp.make[Echo](
    in => in reply EchoOk(echo = in.body.echo, in_reply_to = in.body.msg_id)
  )

  //Run the node
  val run = MaelstromRuntime run app
```

