package com.example

import zio.json.JsonCodec
import zio.*
import com.bilalfazlani.zioMaelstrom.*

object IODocs:

  //format: off        
  object Receive {
    case class Gossip(msg_id: MessageId, numbers: Seq[Int], `type`: String = "gossip")
       derives JsonCodec

    val messageHandler: ZIO[MaelstromRuntime, Nothing, Unit] = 
      receive[Gossip] { 
        case msg: Gossip =>
          ZIO.logDebug(s"received $msg from $src") //(1)!
          *> ZIO.logDebug(s"my node id is $me") //(2)!
          *> ZIO.logDebug(s"other node ids are $others") //(3)!
      }
  }

  object Send {
    case class Gossip(numbers: Seq[Int], `type`: String = "gossip")
      extends Sendable derives JsonCodec

    val messageHandler = 
      receive[Gossip] { 
        case msg: Gossip => 
          ZIO.foreach(others)(_.send(Gossip(Seq(1,2)))).unit //(1)!
      }

    val result = NodeId("n5") send Gossip(Seq(1,2))
  }

  object Reply {
    case class Gossip(msg_id: MessageId, numbers: Seq[Int], `type`: String = "gossip")
      extends NeedsReply derives JsonCodec

    case class GossipOk(in_reply_to: MessageId, myNumbers: Seq[Int], `type`: String = "gossip_ok")
      extends Reply, Sendable derives JsonCodec  

    val messageHandler =  
      receive[Gossip] { 
        case msg: Gossip => reply(GossipOk(msg.msg_id, Seq(1,2)))
      }
  }

  object Ask {
    case class Gossip(msg_id: MessageId, numbers: Seq[Int], `type`: String = "gossip")
      extends NeedsReply, Sendable derives JsonCodec

    case class GossipOk(in_reply_to: MessageId, myNumbers: Seq[Int], `type`: String = "gossip_ok")
      extends Reply derives JsonCodec  

    val gossipResult: ZIO[MaelstromRuntime, AskError, GossipOk] =
      MessageId.next.flatMap( msgId => //(1)!
        NodeId("n2").ask[GossipOk](Gossip(msgId, Seq(1,2)), 5.seconds)
      )
  }

  //format: on
