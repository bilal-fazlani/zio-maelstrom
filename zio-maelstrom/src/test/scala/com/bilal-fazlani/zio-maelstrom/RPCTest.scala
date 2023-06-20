package com.bilalfazlani.zioMaelstrom

import zio.test.*
import zio.*
import com.bilalfazlani.zioMaelstrom.protocol.*
import zio.json.*
import TestRuntime.*

case class Ping(msg_id: MessageId, `type`: String = "ping") extends Sendable, NeedsReply
    derives JsonEncoder
case class Pong(in_reply_to: MessageId) extends Reply derives JsonCodec

object RPCTest extends ZIOSpecDefault {
  val settings                  = Settings(logLevel = NodeLogLevel.Debug)
  val context                   = Context(NodeId("n1"), List(NodeId("n2")))
  val testRuntime               = TestRuntime.layer(settings, context)
  def sleep(duration: Duration) = live(ZIO.sleep(duration))

  val spec = suite("RPC Tests")(
    test("successfully send and receive message") {
      (for {
        fiber      <- NodeId("n2").ask[Pong](Ping(MessageId(1)), 2.seconds).fork
        outMessage <- getNextMessage
        _          <- sleep(100.millis)
        state1     <- getCallbackState
        _          <- inputMessage(Pong(MessageId(1)), NodeId("n2"))
        pong       <- fiber.join
        state2     <- getCallbackState
      } yield assertTrue(pong == Pong(MessageId(1))) &&
        assertTrue(state1.contains(CallbackId(MessageId(1), NodeId("n2")))) &&
        assertTrue(outMessage == Message(NodeId("n1"), NodeId("n2"), Ping(MessageId(1)))) &&
        assertTrue(state2.isEmpty))
        .provide(testRuntime)
    },
    test("timeout if no response is received") {
      (for {
        fiber          <- NodeId("n2").ask[Pong](Ping(MessageId(1)), 4.seconds).fork
        outMessage     <- getNextMessage
        _              <- sleep(100.millis)
        callbackState1 <- getCallbackState
        _              <- TestClock.adjust(4.seconds)
        callbackState2 <- getCallbackState
        _              <- inputMessage(Pong(MessageId(1)), NodeId("n2"))
        error          <- fiber.join.flip
      } yield assertTrue(error == Timeout(MessageId(1), NodeId("n2"), 4.seconds)) &&
        assertTrue(outMessage == Message(NodeId("n1"), NodeId("n2"), Ping(MessageId(1)))) &&
        assertTrue(callbackState1.contains(CallbackId(MessageId(1), NodeId("n2")))) &&
        assertTrue(callbackState2.isEmpty))
        .provide(testRuntime)
    },
    test("interruption") {
      assertCompletes
    }
  ) @@ TestAspect.timeout(10.seconds) @@ TestAspect.sequential
}
