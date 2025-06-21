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
case class Topology(topology: Map[NodeId, List[NodeId]]) extends InMessage // (3)!

@jsonHint("broadcast")
case class Broadcast(message: Int) extends InMessage

@jsonHint("read")
case class Read() extends InMessage

@jsonHint("gossip")
case class Gossip(iHaveSeen: Set[Int]) extends InMessage derives JsonEncoder // (4)!
//}

// reply_messages {
case class BroadcastOk() derives JsonEncoder // (1)!

case class ReadOk(messages: Set[Int]) derives JsonEncoder // (2)!

case class TopologyOk() derives JsonEncoder
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
      case Broadcast(broadcast) =>
        updateState(_.addBroadcast(broadcast)) *>
          reply(BroadcastOk()) // (3)!

      case Read() => getState.map(_.messages).flatMap(x => reply(ReadOk(x)))

      case Topology(topology) =>
        val neighbours = topology(me).toSet // (4)!
        updateState(_.addNeighbours(neighbours)) // (5)!
          *> reply(TopologyOk()) // (6)!
          *> startGossip.forkScoped.unit // (7)!
      // .forkScoped adds a `Scope` requirement in the environment

      case Gossip(gossipMessages) => updateState(_.addGossip(gossipMessages)) // (8)!
    }

  val program =
    handleMessages.provideSome[MaelstromRuntime & Scope](ZLayer(Ref.make(State()))) // (9)!

}
