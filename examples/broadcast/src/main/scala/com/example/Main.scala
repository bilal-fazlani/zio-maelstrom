package com.example.broadcast

import zio.json.*
import zio.*
import com.bilalfazlani.zioMaelstrom.protocol.*
import com.bilalfazlani.zioMaelstrom.*

@jsonDiscriminator("type")
sealed trait BroadcastMessage extends NeedsReply derives JsonDecoder

@jsonHint("broadcast")
case class Broadcast(message: Int, msg_id: MessageId) extends BroadcastMessage

@jsonHint("read")
case class Read(msg_id: MessageId) extends BroadcastMessage

@jsonHint("topology")
case class Topology(msg_id: MessageId) extends BroadcastMessage

case class BroadcastOk(in_reply_to: MessageId, `type`: String = "broadcast_ok") extends Sendable, Reply derives JsonEncoder

case class ReadOk(messages: Seq[Int], in_reply_to: MessageId, `type`: String = "read_ok") extends Sendable, Reply derives JsonEncoder

case class TopologyOk(in_reply_to: MessageId, `type`: String = "topology_ok") extends Sendable, Reply derives JsonEncoder

object Main extends ZIOAppDefault:
  val run = receiveR[Ref[List[Int]], BroadcastMessage] {
    case broadcast: Broadcast =>
      for {
        _ <- ZIO.serviceWithZIO[Ref[List[Int]]](_.getAndUpdate(broadcast.message +: _))
        _ <- broadcast reply BroadcastOk(broadcast.msg_id)
      } yield ()
    case read: Read =>
      for {
        messages <- ZIO.serviceWithZIO[Ref[List[Int]]](_.get)
        _        <- read reply ReadOk(messages, read.msg_id)
      } yield ()
    case topology: Topology => topology reply TopologyOk(topology.msg_id)
  }.provideSome[Scope](MaelstromRuntime.live, ZLayer.fromZIO(Ref.make(List.empty[Int])))
