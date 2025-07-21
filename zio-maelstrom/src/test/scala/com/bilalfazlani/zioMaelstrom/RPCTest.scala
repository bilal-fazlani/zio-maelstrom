package com.bilalfazlani.zioMaelstrom

import com.bilalfazlani.zioMaelstrom.models.{Body, MsgName}
import zio.*
import zio.test.*
import zio.json.*
import testkit.*

object RPCTest extends MaelstromSpec {
  val settings                  = Settings()
  val context                   = Context(NodeId("n1"), Set(NodeId("n2")))
  val tRuntime                  = testRuntime(settings, context)
  def sleep(duration: Duration) = live(ZIO.sleep(duration))

  case class Ping() derives JsonEncoder
  case class Pong() derives JsonCodec

  val spec = suite("RPC Tests")(
    test("successfully send and receive message") {
      (for {
        fiber      <- NodeId("n2").ask[Pong](Ping()).fork
        outMessage <- getNextMessage[Pong]
        _          <- sleep(80.millis)
        state1     <- getCallbackState
        _          <- inputReply(Pong(), NodeId("n2"), MessageId(1))
        pong       <- fiber.join
        state2     <- getCallbackState
      } yield assertTrue(pong == Pong()) &&
        assertTrue(state1.contains(CallbackId(MessageId(1), NodeId("n2")))) &&
        assertTrue(
          outMessage == Message(
            NodeId("n1"),
            NodeId("n2"),
            Body(MsgName[Ping], Ping(), Some(MessageId(1)), None)
          )
        ) &&
        assertTrue(state2.isEmpty))
        .provide(tRuntime)
    },
    test("timeout if no response is received") {
      (for {
        fiber          <- NodeId("n2").ask[Pong](Ping()).fork
        outMessage     <- getNextMessage[Pong]
        _              <- sleep(80.millis)
        callbackState1 <- getCallbackState
        _              <- TestClock.adjust(200.millis)
        callbackState2 <- getCallbackState
        _              <- inputSend(Pong(), NodeId("n2"))
        error          <- fiber.join.flip
      } yield assertTrue(error == Timeout(MessageId(1), NodeId("n2"), 100.millis)) &&
        assertTrue(
          outMessage == Message(NodeId("n1"), NodeId("n2"), Body(MsgName[Ping], Ping(), Some(MessageId(1)), None))
        ) &&
        assertTrue(callbackState1.contains(CallbackId(MessageId(1), NodeId("n2")))) &&
        assertTrue(callbackState2.isEmpty))
        .provide(tRuntime)
    },
    test("interruption should remove the callback") {
      (for {
        fiber          <- NodeId("n2").ask[Pong](Ping()).fork
        _              <- sleep(80.millis)
        callbackState1 <- getCallbackState
        _              <- TestClock.adjust(200.millis)
        _              <- fiber.interrupt
        callbackState2 <- getCallbackState
        _              <- inputSend(Pong(), NodeId("n2"))
      } yield assertTrue(callbackState1.contains(CallbackId(MessageId(1), NodeId("n2")))) &&
        assertTrue(callbackState2.isEmpty))
        .provide(tRuntime)
    },
    test("interruption due to another zio failure should remove callback") {
      (for {
        _ <- (NodeId("n2")
          .ask[Pong](Ping()) zipPar ZIO.fail("fail").delay(80.millis)).fork
        _              <- sleep(80.millis)
        callbackState1 <- getCallbackState
        _              <- TestClock.adjust(200.millis)
        callbackState2 <- getCallbackState
        _              <- inputSend(Pong(), NodeId("n2"))
      } yield assertTrue(callbackState1.contains(CallbackId(MessageId(1), NodeId("n2")))) &&
        assertTrue(callbackState2.isEmpty))
        .provide(tRuntime)
    },
    test("two parallel responses from same node should be awaited concurrently") {
      (for {
        fiber1 <- NodeId("n2").ask[Pong](Ping()).fork
        fiber2 <- NodeId("n2").ask[Pong](Ping()).fork
        _      <- sleep(80.millis)
        state0 <- getCallbackState
        _      <- inputReply(Pong(), NodeId("n2"), MessageId(1))
        _      <- sleep(80.millis)
        state1 <- getCallbackState
        _      <- inputReply(Pong(), NodeId("n2"), MessageId(2))
        _      <- sleep(80.millis)
        state2 <- getCallbackState
        pong1  <- fiber1.join
        pong2  <- fiber2.join
      } yield assertTrue(pong1 == Pong()) &&
        assertTrue(pong2 == Pong()) &&
        assertTrue(state2.isEmpty) &&
        assertTrue(state1.contains(CallbackId(MessageId(2), NodeId("n2")))) &&
        assertTrue(
          state0.contains(CallbackId(MessageId(1), NodeId("n2"))) &&
            state0.contains(CallbackId(MessageId(2), NodeId("n2")))
        ))
        .provide(tRuntime)
    }
  ) @@ TestAspect.timeout(10.seconds) @@ TestAspect.sequential
}
