## zio-maelstrom

A library for building nodes for [maelstrom simulations](https://github.com/jepsen-io/maelstrom)([docs](https://github.com/jepsen-io/maelstrom/tree/main/doc)) in Scala using [ZIO](https://zio.dev)

There are some good challenges to solve [here](https://fly.io/dist-sys/)

## Echo example

```scala
//define input message
case class Echo(echo: String, msg_id: MessageId)                                    extends NeedsReply derives JsonDecoder

//define reply message
case class EchoOk(echo: String, in_reply_to: MessageId, `type`: String = "echo_ok") extends Sendable, Reply derives JsonEncoder

object Main extends ZIOAppDefault:
  //define a handler for the message
  val echoHandler = receive[Echo](msg => msg reply EchoOk(echo = msg.echo, in_reply_to = msg.msg_id))

  //run the handler
  val run         = echoHandler.provideSome[Scope](MaelstromRuntime.live)
```


## Unique Ids example

```scala
// Define input message
case class Generate(msg_id: MessageId) extends NeedsReply derives JsonDecoder

// Define reply message
case class GenerateOk(id: String, in_reply_to: MessageId, `type`: String = "generate_ok") extends Sendable, Reply derives JsonEncoder

object Main extends ZIOAppDefault:

  // Define a handler for the message
  val handler = receiveR[Ref[Int], Generate] { case request =>
    for {
      generated <- ZIO.serviceWithZIO[Ref[Int]](_.updateAndGet(_ + 1))
      combinedId = s"${me}_${generated}"
      _ <- request reply GenerateOk(id = combinedId, in_reply_to = request.msg_id)
    } yield ()
  }

  // Run the handler
  val run = handler.provideSome[Scope](MaelstromRuntime.live, ZLayer.fromZIO(Ref.make(0)))
```

## Naive Gossip example

```scala
// Define your input protocol.
// Use @jsonDiscriminator to specify the field that will be used to determine the type of message.
@jsonDiscriminator("type") sealed trait InMessage derives JsonDecoder
// Use @jsonHint to specify the type of message.
@jsonHint("topology") case class Topology(topology: Map[NodeId, List[NodeId]], msg_id: MessageId) extends InMessage, NeedsReply
@jsonHint("broadcast") case class Broadcast(message: Int, msg_id: MessageId)                      extends InMessage, NeedsReply
@jsonHint("read") case class Read(msg_id: MessageId)                                              extends InMessage, NeedsReply
@jsonHint("gossip") case class Gossip(iHaveSeen: Set[Int], `type`: String = "gossip")             extends InMessage, Sendable derives JsonEncoder

// Reply messages
case class BroadcastOk(in_reply_to: MessageId, `type`: String = "broadcast_ok")           extends Sendable, Reply derives JsonEncoder
case class ReadOk(messages: Set[Int], in_reply_to: MessageId, `type`: String = "read_ok") extends Sendable, Reply derives JsonEncoder
case class TopologyOk(in_reply_to: MessageId, `type`: String = "topology_ok")             extends Sendable, Reply derives JsonEncoder

// Define the state of node
case class State(messages: Set[Int] = Set.empty, neighbours: Set[NodeId] = Set.empty) {
  def addBroadcast(message: Int): State             = copy(messages = messages + message)
  def addGossip(gossipMessages: Set[Int]): State    = copy(messages = messages ++ gossipMessages)
  def addNeighbours(neighbours: Set[NodeId]): State = copy(neighbours = neighbours)
}

object Main extends ZIOAppDefault:

  // some helper functions
  val getState                       = ZIO.serviceWithZIO[Ref[State]](_.get)
  def updateState(f: State => State) = ZIO.serviceWithZIO[Ref[State]](_.update(f))

  // gossip every 500 millis.. forver
  val startGossip          = getState.flatMap(gossip).delay(500.millis).forever
  // gossip all state to all neighbours (inefficient)
  def gossip(state: State) = ZIO.foreachPar(state.neighbours)(nodeId => nodeId.send(Gossip(state.messages)))

  // handle IN messages
  // notice `receiveR` which allows you to use R, in this case Ref[State]
  val handleMessages = receiveR[Ref[State] & Scope, InMessage] {
    case msg @ Broadcast(broadcast, messageId) =>
      updateState(_.addBroadcast(broadcast)) *> (msg reply BroadcastOk(messageId))

    case msg @ Read(messageId) =>
      getState.map(_.messages) flatMap (messages => msg reply ReadOk(messages, messageId))

    case msg @ Topology(topology, messageId) =>
      val neighbours = topology(me).toSet
      updateState(_.addNeighbours(neighbours)) *> (msg reply TopologyOk(messageId))
      // start gossiping after we have received the topology
        *> startGossip.forkScoped.unit // .forkScoped requires us to add a `Scope` in the environment

    case msg @ Gossip(gossipMessages, _) =>
      updateState(_.addGossip(gossipMessages))
  }

  val run = handleMessages.provideSome[Scope](MaelstromRuntime.live, ZLayer.fromZIO(Ref.make(State())))
```

