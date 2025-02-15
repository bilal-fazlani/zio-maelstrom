package com.bilalfazlani.zioMaelstrom

import zio.*
import zio.test.*
import zio.json.*
import testkit.*

object RequestHandlerTests extends MaelstromSpec {

  val settings                  = Settings()
  val context                   = Context(NodeId("n1"), Set(NodeId("n2")))
  val tRuntime                  = testRuntime(settings, context)
  def sleep(duration: Duration) = live(ZIO.sleep(duration))

  case class Ping(msg_id: MessageId) extends NeedsReply derives JsonCodec
  case class PingOk(in_reply_to: MessageId, `type`: String = "ping_ok") extends Reply, Sendable
      derives JsonEncoder

  // --
  case class GetNumber(msg_id: MessageId, from: NodeId, `type`: String = "get_number")
      extends NeedsReply,
        Sendable derives JsonCodec
  case class Number(in_reply_to: MessageId, value: Int, `type`: String = "number")
      extends Reply,
        Sendable derives JsonCodec

  val spec = suite("RequestHandler Tests")(
    test("successfully send and receive message") {
      (for {
        fiber  <- receive[Ping](ping => reply(PingOk(ping.msg_id))).fork
        _      <- inputMessage(Ping(MessageId(1)), NodeId("n2"))
        pingOk <- getNextMessage
        _      <- fiber.interrupt
      } yield assertTrue(pingOk == Message(NodeId("n1"), NodeId("n2"), PingOk(MessageId(1)))))
        .provide(tRuntime)
    },
    test("receive and ask should work concurrently") {
      (for {
        fiber <- receive[GetNumber] { case msg @ GetNumber(msg_id, from, _) =>
          from
            .ask[Number](GetNumber(MessageId(2), from), 2.seconds)
            .flatMap(nmber => reply(Number(msg_id, nmber.value)))
            .catchAll(e => ZIO.dieMessage(e.toString))
        }.fork
        _ <- inputMessage(
          GetNumber(msg_id = MessageId(1), from = NodeId("n5")),
          from = NodeId("n2")
        )
        _          <- sleep(100.millis)
        n5Response <- getNextMessage
        _ <- inputMessage(Number(in_reply_to = MessageId(2), value = 5), from = NodeId("n5"))
        finalOutMessage <- getNextMessage
      } yield assertTrue(
        finalOutMessage == Message(NodeId("n1"), NodeId("n2"), Number(MessageId(1), 5))
      ))
        .provide(tRuntime)
    }
  ) @@ TestAspect.timeout(10.seconds) @@ TestAspect.sequential
}
