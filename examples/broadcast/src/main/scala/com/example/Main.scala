package com.example.broadcast

import zio.json.*
import zio.*
import com.bilalfazlani.zioMaelstrom.protocol.*
import com.bilalfazlani.zioMaelstrom.*

@jsonDiscriminator("type") sealed trait InMessage derives JsonDecoder
@jsonHint("topology") case class Topology(msg_id: MessageId, topology: Map[NodeId, List[NodeId]]) extends InMessage, NeedsReply
@jsonHint("broadcast") case class Broadcast(message: Int, msg_id: MessageId)                      extends InMessage, NeedsReply
@jsonHint("read") case class Read(msg_id: MessageId)                                              extends InMessage, NeedsReply
@jsonHint("gossip") case class Gossip(iHaveSeen: Set[Int], `type`: String = "gossip")             extends InMessage, Sendable derives JsonEncoder

case class BroadcastOk(in_reply_to: MessageId, `type`: String = "broadcast_ok")           extends Sendable, Reply derives JsonEncoder
case class ReadOk(messages: Set[Int], in_reply_to: MessageId, `type`: String = "read_ok") extends Sendable, Reply derives JsonEncoder
case class TopologyOk(in_reply_to: MessageId, `type`: String = "topology_ok")             extends Sendable, Reply derives JsonEncoder

object Main extends ZIOAppDefault:
  case class State(messages: Set[Int] = Set.empty, neighbours: Set[NodeId] = Set.empty) {
    def addBroadcast(message: Int): State             = copy(messages = messages + message)
    def addGossip(gossipMessages: Set[Int]): State    = copy(messages = messages ++ gossipMessages)
    def addNeighbours(neighbours: Set[NodeId]): State = copy(neighbours = neighbours)
  }

  val startGossip = getState
    .flatMap(state => ZIO.when(state.messages.nonEmpty && state.neighbours.nonEmpty)(gossip(state)))
    .delay(500.millis)
    .forever

  def gossip(state: State) =
    logInfo(s"sending gossip of size ${state.messages.size} to [${state.neighbours.mkString(",")}]") *>
      ZIO
        .foreachPar(state.neighbours)(nodeId => nodeId.send(Gossip(state.messages)))
        .withParallelism(5)
        .unit

  val getState                       = ZIO.serviceWithZIO[Ref[State]](_.get)
  def updateState(f: State => State) = ZIO.serviceWithZIO[Ref[State]](_.update(f))

  val handleMessages = receiveR[Ref[State], InMessage] {
    case msg @ Broadcast(message, messageId) =>
      updateState(_.addBroadcast(message)) *> (msg reply BroadcastOk(messageId))

    case msg @ Read(messageId) =>
      getState.map(_.messages) flatMap (myMessages => msg reply ReadOk(myMessages, messageId))

    case msg @ Topology(messageId, topology) =>
      val neighbours = topology(myNodeId).toSet
      updateState(_.addNeighbours(neighbours)) *> (msg reply TopologyOk(messageId))

    case msg @ Gossip(gossipMessages, _) =>
      updateState(_.addGossip(gossipMessages))
  }

  val run = (handleMessages race startGossip)
    .provideSome[Scope](
      MaelstromRuntime.live,
      ZLayer.fromZIO(Ref.make(State()))
    )
