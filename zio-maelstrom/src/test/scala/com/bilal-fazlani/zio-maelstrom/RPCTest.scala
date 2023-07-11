package com.bilalfazlani.zioMaelstrom

import zio.test.*
import zio.*
import com.bilalfazlani.zioMaelstrom.protocol.*
import zio.json.*
import testkit.TestRuntime
import testkit.TestRuntime.*

object RPCTest extends ZIOSpecDefault {
  def isCI        = sys.env.get("CI").contains("true")
  val settings    = Settings(logLevel = if isCI then NodeLogLevel.Info else NodeLogLevel.Debug)
  val context     = Context(NodeId("n1"), Set(NodeId("n2")))
  val testRuntime = TestRuntime.layer(settings, context)
  def sleep(duration: Duration) = live(ZIO.sleep(duration))

  case class Ping(msg_id: MessageId, `type`: String = "ping") extends Sendable, NeedsReply
      derives JsonEncoder
  case class Pong(in_reply_to: MessageId) extends Reply derives JsonCodec

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
    test("interruption should remove the callback") {
      (for {
        fiber          <- NodeId("n2").ask[Pong](Ping(MessageId(1)), 4.seconds).fork
        _              <- sleep(100.millis)
        callbackState1 <- getCallbackState
        _              <- TestClock.adjust(1.second)
        _              <- fiber.interrupt
        callbackState2 <- getCallbackState
        _              <- inputMessage(Pong(MessageId(1)), NodeId("n2"))
      } yield assertTrue(callbackState1.contains(CallbackId(MessageId(1), NodeId("n2")))) &&
        assertTrue(callbackState2.isEmpty))
        .provide(testRuntime)
    },
    test("interruption due to another zio failure should remove callback") {
      (for {
        fiber <- (NodeId("n2")
          .ask[Pong](Ping(MessageId(1)), 4.seconds) zipPar ZIO.fail("fail").delay(1.second)).fork
        _              <- sleep(100.millis)
        callbackState1 <- getCallbackState
        _              <- TestClock.adjust(4.second)
        callbackState2 <- getCallbackState
        _              <- inputMessage(Pong(MessageId(1)), NodeId("n2"))
      } yield assertTrue(callbackState1.contains(CallbackId(MessageId(1), NodeId("n2")))) &&
        assertTrue(callbackState2.isEmpty))
        .provide(testRuntime)
    },
    test("two parallel responses from same node should be awaited concurrently") {
      (for {
        fiber1 <- NodeId("n2").ask[Pong](Ping(MessageId(1)), 4.seconds).fork
        fiber2 <- NodeId("n2").ask[Pong](Ping(MessageId(2)), 4.seconds).fork
        _      <- sleep(100.millis)
        state0 <- getCallbackState
        _      <- inputMessage(Pong(MessageId(2)), NodeId("n2"))
        _      <- sleep(100.millis)
        state1 <- getCallbackState
        _      <- inputMessage(Pong(MessageId(1)), NodeId("n2"))
        _      <- sleep(100.millis)
        state2 <- getCallbackState
        pong1  <- fiber1.join
        pong2  <- fiber2.join
      } yield assertTrue(pong1 == Pong(MessageId(1))) &&
        assertTrue(pong2 == Pong(MessageId(2))) &&
        assertTrue(state2.isEmpty) &&
        assertTrue(state1.contains(CallbackId(MessageId(1), NodeId("n2")))) &&
        assertTrue(
          state0.contains(CallbackId(MessageId(1), NodeId("n2"))) &&
            state0.contains(CallbackId(MessageId(2), NodeId("n2")))
        ))
        .provide(testRuntime)
    }
  ) @@ TestAspect.timeout(10.seconds) @@ TestAspect.sequential
}
