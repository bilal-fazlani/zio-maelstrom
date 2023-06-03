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
  val settings = Settings(logFormat = LogFormat.Colored)

  case class State(messages: Set[Int] = Set.empty, neighbours: Set[NodeId] = Set.empty) {
    def addBroadcast(message: Int): State             = copy(messages = messages + message)
    def addGossip(newMessages: Set[Int]): State       = copy(messages = messages ++ newMessages)
    def addNeighbours(neighbours: Set[NodeId]): State = copy(neighbours = neighbours)
  }

  val gossip = (for
    state <- ZIO.serviceWithZIO[Ref[State]](_.get)
    _ <- ZIO
      .foreach(state.neighbours) { nodeId =>
        logInfo(s"sending gossip of size ${state.messages.size} to $nodeId")
          *> nodeId.send(Gossip(state.messages))
      }
      .unit
  yield ()).delay(500.millis).forever

  val handleMessages = receiveR[Ref[State], InMessage] {
    case msg @ Broadcast(message, messageId) =>
      for
        state <- ZIO.serviceWithZIO[Ref[State]](_.updateAndGet(_.addBroadcast(message)))
        _     <- msg reply BroadcastOk(messageId)
      yield ()
    case msg @ Read(messageId) =>
      for
        myMessages <- ZIO.serviceWithZIO[Ref[State]](_.get.map(_.messages))
        _          <- msg reply ReadOk(myMessages, messageId)
      yield ()
    case msg @ Topology(id, topology) =>
      for
        state <- ZIO.service[Ref[State]]
        neighbours = topology(myNodeId).toSet
        _ <- state.update(_.addNeighbours(neighbours))
        _ <- msg reply TopologyOk(id)
      yield ()
    case msg @ Gossip(iHaveSeen, _) =>
      ZIO.serviceWithZIO[Ref[State]](_.update(_.addGossip(iHaveSeen, src)))
  }

  val run = (handleMessages race gossip)
    .provideSome[Scope](
      MaelstromRuntime.live(settings),
      ZLayer.fromZIO(Ref.make(State()))
    )
