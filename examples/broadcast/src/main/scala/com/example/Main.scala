package com.example.broadcast

import zio.json.*
import zio.*
import com.bilalfazlani.zioMaelstrom.protocol.*
import com.bilalfazlani.zioMaelstrom.*

@jsonDiscriminator("type") sealed trait BroadcastMessage                                          extends NeedsReply derives JsonDecoder
@jsonHint("broadcast") case class Broadcast(message: Int, msg_id: MessageId)                      extends BroadcastMessage
@jsonHint("read") case class Read(msg_id: MessageId)                                              extends BroadcastMessage
@jsonHint("topology") case class Topology(msg_id: MessageId, topology: Map[NodeId, List[NodeId]]) extends BroadcastMessage

case class BroadcastOk(in_reply_to: MessageId, `type`: String = "broadcast_ok")           extends Sendable, Reply derives JsonEncoder
case class ReadOk(messages: Seq[Int], in_reply_to: MessageId, `type`: String = "read_ok") extends Sendable, Reply derives JsonEncoder
case class TopologyOk(in_reply_to: MessageId, `type`: String = "topology_ok")             extends Sendable, Reply derives JsonEncoder

object Main extends ZIOAppDefault:
  val settings = Settings(logFormat = LogFormat.Colored)

  case class State(messages: List[Int] = List.empty, neighbours: List[NodeId] = List.empty) {
    def addMessage(message: Int): State                = copy(messages = message +: messages)
    def addNeighbours(neighbours: List[NodeId]): State = copy(neighbours = neighbours)
  }

  val run = receiveR[Ref[State], BroadcastMessage] {
    case msg @ Broadcast(message, messageId) =>
      for
        state <- ZIO.serviceWithZIO[Ref[State]](_.get)
        _     <- ZIO.serviceWithZIO[Ref[State]](_.getAndUpdate(_.addMessage(message)))
        _     <- msg reply BroadcastOk(messageId)
      yield ()
    case msg @ Read(messageId) =>
      for
        myMessages <- ZIO.serviceWithZIO[Ref[State]](_.get.map(_.messages))
        _          <- msg reply ReadOk(myMessages, messageId)
      yield ()
    case msg @ Topology(id, topology) =>
      for
        _ <- ZIO.serviceWithZIO[Ref[State]](_.getAndUpdate(_.addNeighbours(topology.get(myNodeId).getOrElse(List.empty))))
        _ <- msg reply TopologyOk(id)
      yield ()
  }.provideSome[Scope](
    MaelstromRuntime.live(settings),
    ZLayer.fromZIO(Ref.make(State()))
  )
