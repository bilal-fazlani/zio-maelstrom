package com.example

import com.bilalfazlani.zioMaelstrom.*
import com.bilalfazlani.zioMaelstrom.protocol.*
import zio.json.JsonCodec
import zio.*
import com.bilalfazlani.zioMaelstrom.MessageSender

object HandlerDocumentation:

  case class Gossip(`type`: String, msg_id: MessageId, numbers: Seq[Int])
      extends NeedsReply,
        Sendable derives JsonCodec

  

  //format: off        
  object Receive {
    case class Gossip(msg_id: MessageId, numbers: Seq[Int], `type`: String = "gossip")
      extends NeedsReply, Sendable derives JsonCodec

    val messageHandler: ZIO[MaelstromRuntime, Nothing, Unit] = 
      receive[Gossip] { 
        case msg: Gossip => 
          logDebug(s"received $msg from $src") //(1)!
          *> logDebug(s"my node id is $me") //(2)!
          *> logDebug(s"other node ids are $others") //(3)!
          *> ZIO.unit
      }
  }

  object Send {
    case class Gossip(msg_id: MessageId, numbers: Seq[Int], `type`: String = "gossip")
      extends NeedsReply, Sendable derives JsonCodec

    case class GossipOk(in_reply_to: MessageId, myNumbers: Seq[Int], `type`: String = "gossip_ok")
          extends Reply, Sendable derives JsonCodec  

    val messageHandler = 
      receive[Gossip] { 
        case msg: Gossip => 
          ZIO.foreach(others)(_.send(Gossip(MessageId(5), Seq(1,2)))).unit //(1)!
      }

    val result = NodeId("n5") send Gossip(MessageId(1), Seq(1,2)) //(2)!
  }

  object Reply {
    case class Gossip(msg_id: MessageId, numbers: Seq[Int], `type`: String = "gossip")
      extends NeedsReply, Sendable derives JsonCodec

    case class GossipOk(in_reply_to: MessageId, myNumbers: Seq[Int], `type`: String = "gossip_ok")
      extends Reply, Sendable derives JsonCodec  

    val messageHandler =  
      receive[Gossip] { 
        case msg: Gossip => msg reply GossipOk(msg.msg_id, Seq(1,2)) //(1)!
      }
  }

  object Ask {
    case class Gossip(msg_id: MessageId, numbers: Seq[Int], `type`: String = "gossip")
      extends NeedsReply, Sendable derives JsonCodec

    case class GossipOk(in_reply_to: MessageId, myNumbers: Seq[Int], `type`: String = "gossip_ok")
      extends Reply, Sendable derives JsonCodec  

    val gosspiResult: ZIO[MessageSender, ErrorMessage | DecodingFailure | Timeout, GossipOk] = //(1)!
      NodeId("n2").ask[GossipOk](Gossip(MessageId(1), Seq(1,2)), 5.seconds) //(2)!
  }

  //format: on
