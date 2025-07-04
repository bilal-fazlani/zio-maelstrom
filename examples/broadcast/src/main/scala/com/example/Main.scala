package com.example.broadcast

// imports {
import com.bilalfazlani.zioMaelstrom.*
import zio.*
import zio.json.*
// }

// input_messages {
@jsonDiscriminator("type") // (1)!
sealed trait InMessage derives JsonDecoder // (2)!

@jsonHint("topology")
case class Topology(topology: Map[NodeId, List[NodeId]]) extends InMessage

@jsonHint("broadcast")
case class Broadcast(message: Int) extends InMessage

@jsonHint("read")
case class Read() extends InMessage

@jsonHint("gossip")
case class Gossip(iHaveSeen: Set[Int]) extends InMessage derives JsonEncoder // (3)!
//}

// reply_messages {
case class BroadcastOk() derives JsonEncoder
case class ReadOk(messages: Set[Int]) derives JsonEncoder
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

  val program = receive[InMessage] {
    case Broadcast(broadcast) =>
      updateState(_.addBroadcast(broadcast)) *>
        reply(BroadcastOk()) // (3)!

    case Read() => getState.map(_.messages).flatMap(x => reply(ReadOk(x)))

    case Topology(topology) =>
      for {
        me        <- MaelstromRuntime.me
        neighbours = topology(me).toSet                       // (4)!
        _         <- updateState(_.addNeighbours(neighbours)) // (5)!
        _         <- reply(TopologyOk())                      // (6)!
        _         <- startGossip.forkScoped.unit              // (7)!
      } yield ()

    case Gossip(gossipMessages) => updateState(_.addGossip(gossipMessages)) // (8)!
  }.provideSome[MaelstromRuntime](ZLayer(Ref.make(State()))) // (9)!

}
