## zio-maelstrom

A library for building nodes for [maelstrom simulations](https://github.com/jepsen-io/maelstrom) in Scala using [ZIO](https://zio.dev)

## Echo example

```scala
//Define the message types
case class Echo(echo: String, msg_id: MessageId) extends NeedsReply derives JsonDecoder

case class EchoOk(echo: String, in_reply_to: MessageId, `type`: String = "echo_ok") 
  extends Sendable, Reply derives JsonEncoder

object EchoProgram extends ZIOAppDefault:
  //Define the node behaviour
  val echoHandler = receive[Echo](
    msg => msg reply EchoOk(echo = msg.echo, in_reply_to = msg.msg_id)
  )
  
  //Run the node
  val run         = echoHandler.provideSome[Scope](MaelstromRuntime.live)
```


## Unique Ids example

```scala
// Define a message that needs a reply
case class Generate(msg_id: MessageId) extends NeedsReply derives JsonDecoder

// Define reply message
case class GenerateOk(id: String, in_reply_to: MessageId, `type`: String = "generate_ok") 
  extends Sendable, Reply derives JsonEncoder

object Main extends ZIOAppDefault:

  // Define a handler for the message
  val handler = receiveR[Ref[Int], Generate] { case request =>
    for {
      newId <- ZIO.serviceWithZIO[Ref[Int]](_.updateAndGet(_ + 1))
      combinedId = s"${myNodeId}_$newId"
      _     <- request reply GenerateOk(id = combinedId, in_reply_to = request.msg_id)
    } yield ()
  }

  // Run the handler
  val run = handler.provideSome[Scope](
    MaelstromRuntime.live, 
    ZLayer.fromZIO(Ref.make(0))
  )
```

