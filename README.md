## zio-maelstrom

A library for building nodes for [maelstrom simulations](https://github.com/jepsen-io/maelstrom) in Scala using [ZIO](https://zio.dev)

## Echo example

```scala
// Define a message that needs a reply
case class Echo(echo: String, msg_id: MessageId) extends NeedsReply derives JsonDecoder

// Define reply message
case class EchoOk(echo: String, in_reply_to: MessageId, `type`: String = "echo_ok") 
  extends Sendable, Reply derives JsonEncoder

object EchoProgram extends ZIOAppDefault:
  //Define a handler for the node
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

  // Define a handler for the node
  val handler = receiveR[Ref[Int], Generate] { case request =>
    for {
      newId <- ZIO.serviceWithZIO[Ref[Int]](_.updateAndGet(_ + 1))
      combinedId = s"${myNodeId}_$newId"
      _     <- request reply GenerateOk(id = combinedId, in_reply_to = request.msg_id)
    } yield ()
  }

  // Run the node
  val run = handler.provideSome[Scope](
    MaelstromRuntime.live, 
    ZLayer.fromZIO(Ref.make(0))
  )
```

## Naive Gossip example

```scala
// Define messages that you need to handle
@jsonDiscriminator("type") sealed trait InMessage derives JsonDecoder
@jsonHint("topology") case class Topology(topology: Map[NodeId, List[NodeId]], msg_id: MessageId) extends InMessage, NeedsReply
@jsonHint("broadcast") case class Broadcast(message: Int, msg_id: MessageId)                      extends InMessage, NeedsReply
@jsonHint("read") case class Read(msg_id: MessageId)                                              extends InMessage, NeedsReply

// Define messages that you can send
case class BroadcastOk(in_reply_to: MessageId, `type`: String = "broadcast_ok")           extends Sendable, Reply derives JsonEncoder
case class ReadOk(messages: Set[Int], in_reply_to: MessageId, `type`: String = "read_ok") extends Sendable, Reply derives JsonEncoder
case class TopologyOk(in_reply_to: MessageId, `type`: String = "topology_ok")             extends Sendable, Reply derives JsonEncoder

//gossip is a messages which you can receive and send. It does not need a reply
@jsonHint("gossip") case class Gossip(iHaveSeen: Set[Int], `type`: String = "gossip")     extends InMessage, Sendable derives JsonEncoder

// Define the state of the node
case class State(messages: Set[Int] = Set.empty, neighbours: Set[NodeId] = Set.empty) {
  def addBroadcast(message: Int): State             = copy(messages = messages + message)
  def addGossip(gossipMessages: Set[Int]): State    = copy(messages = messages ++ gossipMessages)
  def addNeighbours(neighbours: Set[NodeId]): State = copy(neighbours = neighbours)
}

object Main extends ZIOAppDefault:

  //Some helper functions
  val getState                       = ZIO.serviceWithZIO[Ref[State]](_.get)
  def updateState(f: State => State) = ZIO.serviceWithZIO[Ref[State]](_.update(f))

  // Define a message handler
  val handleMessages = receiveR[Ref[State] & Scope, InMessage] {
    case msg @ Broadcast(broadcast, messageId) =>
      updateState(_.addBroadcast(broadcast)) *> (msg reply BroadcastOk(messageId))

    case msg @ Read(messageId) =>
      getState.map(_.messages) flatMap (messages => msg reply ReadOk(messages, messageId))

    case msg @ Topology(topology, messageId) =>
      val neighbours = topology(myNodeId).toSet
      updateState(_.addNeighbours(neighbours)) 
        *> (msg reply TopologyOk(messageId)) 
        *> startGossip.forkScoped.unit 
        //gossip starts after topology is received
        //and runs in a background fiber. 
        //`.forkScoped` requires us to add `Scope` to the handler type

    case msg @ Gossip(gossipMessages, _) =>
      updateState(_.addGossip(gossipMessages))
  }

  def startGossip = getState.flatMap(gossip).delay(500.millis).forever

  //here our gossip size is too large because we are sending all messages
  //we should use a more optimised gossip protocol
  def gossip(state: State) =
    logInfo(s"sending gossip of size ${state.messages.size} to [${state.neighbours.mkString(",")}]") *>
      ZIO
        .foreachPar(state.neighbours)(nodeId => nodeId.send(Gossip(state.messages)))
        .withParallelism(5)
        .unit

  val run = handleMessages
    .provideSome[Scope](MaelstromRuntime.live, ZLayer.fromZIO(Ref.make(State())))
```

