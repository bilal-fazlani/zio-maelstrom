package com.example

import zio.json.JsonCodec
import zio.*
import com.bilalfazlani.zioMaelstrom.*

object IODocs:

  //format: off        
  object Receive {
    case class Gossip(numbers: Seq[Int]) derives JsonCodec

    val messageHandler =
      receive[Gossip] (msg =>
          for {
            src <- MaelstromRuntime.src
            me <- MaelstromRuntime.me
            others <- MaelstromRuntime.others
            _ <- ZIO.logDebug(s"received $msg from $src") //(1)!
            _ <- ZIO.logDebug(s"my node id is $me") //(2)!
            _ <- ZIO.logDebug(s"other node ids are $others") //(3)!
          } yield ()
      )
  }

  object Send {
    case class Gossip(numbers: Seq[Int]) derives JsonCodec

    val messageHandler =
      receive[Gossip] (_ =>
        for
          others <- MaelstromRuntime.others
          _ <- ZIO.foreach(others)(_.send(Gossip(Seq(1,2)))).unit //(1)!
        yield ()
      )

    val result = NodeId("n5") send Gossip(Seq(1,2))
  }

  object Reply1 extends MaelstromNode {
    case class Gossip(numbers: Seq[Int]) derives JsonCodec

    case class GossipOk(myNumbers: Seq[Int]) derives JsonCodec

    val program = receive[Gossip] (_ => reply(GossipOk(Seq(1,2))))
  }


  object ErrorReply2 extends MaelstromNode {
    case class Gossip(numbers: Seq[Int]) derives JsonCodec

    val program = receive[Gossip] (_ => replyError(ErrorCode.PreconditionFailed, "some text message"))
  }

  object AskDefaultTimeout {
    case class Ping(text: String) derives JsonCodec
    case class Pong(text: String) derives JsonCodec

    // Uses default timeout configured in Settings (100ms by default)
    val pingResult: ZIO[MaelstromRuntime, AskError, Pong] =
      NodeId("n2").ask[Pong](Ping("Hello"))
  }

  object AskCustomTimeout {
    case class Ping(text: String) derives JsonCodec
    case class Pong(text: String) derives JsonCodec

    // Custom timeout overrides the default timeout
    val pingResult: ZIO[MaelstromRuntime, AskError, Pong] =
      NodeId("n2").ask[Pong](Ping("Hello"), 5.seconds)
  }

  //format: on
