package com.example.broadcast

// imports {
import zio.json.*
import zio.*
import com.bilalfazlani.zioMaelstrom.*
// }

// input_messages {
@jsonDiscriminator("type")                 // (1)!
sealed trait InMessage derives JsonDecoder // (2)!

@jsonHint("topology")
case class Topology(topology: Map[NodeId, List[NodeId]], msg_id: MessageId)
    extends InMessage,
      NeedsReply // (3)!

@jsonHint("broadcast")
case class Broadcast(message: Int, msg_id: MessageId) extends InMessage, NeedsReply

@jsonHint("read")
case class Read(msg_id: MessageId) extends InMessage, NeedsReply

@jsonHint("gossip")
case class Gossip(iHaveSeen: Set[Int], `type`: String = "gossip")
    extends InMessage,
      Sendable          // (4)!
    derives JsonEncoder // (5)!
//}

// reply_messages {
case class BroadcastOk(in_reply_to: MessageId, `type`: String = "broadcast_ok")
    extends Sendable,
      Reply derives JsonEncoder // (1)!

case class ReadOk(messages: Set[Int], in_reply_to: MessageId, `type`: String = "read_ok") // (2)!
    extends Sendable,
      Reply derives JsonEncoder

case class TopologyOk(in_reply_to: MessageId, `type`: String = "topology_ok")
    extends Sendable,
      Reply derives JsonEncoder
// }

// state {
case class State(messages: Set[Int] = Set.empty, neighbours: Set[NodeId] = Set.empty) {
  def addBroadcast(message: Int): State             = copy(messages = messages + message)
  def addGossip(gossipMessages: Set[Int]): State    = copy(messages = messages ++ gossipMessages)
  def addNeighbours(neighbours: Set[NodeId]): State = copy(neighbours = neighbours)
}
//}

object Main extends MaelstromNode {

  // some helper functions
  val getState                       = ZIO.serviceWithZIO[Ref[State]](_.get)
  def updateState(f: State => State) = ZIO.serviceWithZIO[Ref[State]](_.update(f))

  def gossip(state: State) = ZIO.foreachPar(state.neighbours)(_ send Gossip(state.messages)) // (1)!

  val startGossip = getState.flatMap(gossip).delay(500.millis).forever // (2)!

  val handleMessages: ZIO[MaelstromRuntime & Ref[State] & Scope, Nothing, Unit] =
    receive[InMessage] {
      case msg @ Broadcast(broadcast, messageId) =>
        updateState(_.addBroadcast(broadcast)) *>
          reply(BroadcastOk(messageId)) // (3)!

      case msg @ Read(messageId) =>
        getState
          .map(_.messages)
          .flatMap(messages => reply(ReadOk(messages, messageId)))

      case msg @ Topology(topology, messageId) =>
        val neighbours = topology(me).toSet // (4)!
        updateState(_.addNeighbours(neighbours)) // (5)!
          *> reply(TopologyOk(messageId))        // (6)!
          *> startGossip.forkScoped.unit         // (7)!
      // .forkScoped adds a `Scope` requirement in the environment

      case msg @ Gossip(gossipMessages, _) => updateState(_.addGossip(gossipMessages)) // (8)!
    }

  val program =
    handleMessages.provideSome[MaelstromRuntime & Scope](ZLayer(Ref.make(State()))) // (9)!

}
