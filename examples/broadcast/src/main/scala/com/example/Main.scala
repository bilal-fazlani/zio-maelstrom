package com.example.broadcast

import zio.json.*
import zio.*
import com.bilalfazlani.zioMaelstrom.*
import com.bilalfazlani.zioMaelstrom.protocol.*

@jsonDiscriminator("type") sealed trait InMessage derives JsonDecoder
@jsonHint("topology") case class Topology(topology: Map[NodeId, List[NodeId]], msg_id: MessageId) extends InMessage, NeedsReply
@jsonHint("broadcast") case class Broadcast(message: Int, msg_id: MessageId)                      extends InMessage, NeedsReply
@jsonHint("read") case class Read(msg_id: MessageId)                                              extends InMessage, NeedsReply
@jsonHint("gossip") case class Gossip(iHaveSeen: Set[Int], `type`: String = "gossip")             extends InMessage, Sendable derives JsonEncoder

case class BroadcastOk(in_reply_to: MessageId, `type`: String = "broadcast_ok")           extends Sendable, Reply derives JsonEncoder
case class ReadOk(messages: Set[Int], in_reply_to: MessageId, `type`: String = "read_ok") extends Sendable, Reply derives JsonEncoder
case class TopologyOk(in_reply_to: MessageId, `type`: String = "topology_ok")             extends Sendable, Reply derives JsonEncoder

case class State(messages: Set[Int] = Set.empty, neighbours: Set[NodeId] = Set.empty) {
  def addBroadcast(message: Int): State             = copy(messages = messages + message)
  def addGossip(gossipMessages: Set[Int]): State    = copy(messages = messages ++ gossipMessages)
  def addNeighbours(neighbours: Set[NodeId]): State = copy(neighbours = neighbours)
}

object Main extends ZIOAppDefault:

  val getState                       = ZIO.serviceWithZIO[Ref[State]](_.get)
  def updateState(f: State => State) = ZIO.serviceWithZIO[Ref[State]](_.update(f))

  val startGossip = getState.flatMap(gossip).delay(500.millis).forever

  def gossip(state: State) =
    logInfo(s"sending gossip of size ${state.messages.size} to [${state.neighbours.mkString(",")}]") *>
      ZIO
        .foreachPar(state.neighbours)(nodeId => nodeId.send(Gossip(state.messages)))
        .withParallelism(5)
        .unit

  val handleMessages = receiveR[Ref[State] & Scope, InMessage] {
    case msg @ Broadcast(broadcast, messageId) =>
      updateState(_.addBroadcast(broadcast)) *> (msg reply BroadcastOk(messageId))

    case msg @ Read(messageId) =>
      getState.map(_.messages) flatMap (messages => msg reply ReadOk(messages, messageId))

    case msg @ Topology(topology, messageId) =>
      val neighbours = topology(myNodeId).toSet
      updateState(_.addNeighbours(neighbours)) *> (msg reply TopologyOk(messageId)) *> startGossip.forkScoped.unit

    case msg @ Gossip(gossipMessages, _) =>
      updateState(_.addGossip(gossipMessages))
  }

  val run = handleMessages
    .provideSome[Scope](
      MaelstromRuntime.live,
      ZLayer.fromZIO(Ref.make(State()))
    )
