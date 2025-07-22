package com.bilalfazlani.zioMaelstrom

import com.bilalfazlani.zioMaelstrom.services.LinTso
import com.bilalfazlani.zioMaelstrom.testkit.*
import zio.*
import zio.test.*

object LinTsoTest extends MaelstromSpec {

  val settings                  = Settings()
  val context                   = Context(NodeId("n1"), Set(NodeId("n2")))
  
  // Use real LinTso implementation instead of fake to test timeout behavior
  val tRuntimeWithRealLinTso: ZLayer[Any, Nothing, MaelstromTestRuntime] =
    ZLayer.make[MaelstromTestRuntime](
      // pure layers
      ZLayer.succeed(settings),
      Scope.default,
      MessageSender.live,
      RequestHandler.live,
      ZLayer(Queue.unbounded[Message[Any]]),
      ZLayer(Queue.unbounded[String]),
      OutputChannel.queue, // FAKE
      InputStream.queue,   // FAKE
      InputChannel.live,
      CallbackRegistry.live,
      MessageIdStore.stub, // FAKE

      // Real Service (this will trigger network timeouts)
      LinTso.live,

      // Fake KV Services (not needed for LinTso tests)
      KvFake.linKv,
      KvFake.seqKv,
      KvFake.lwwKv,

      // effectful layers
      Initialisation.fake(context), // FAKE
      ResponseHandler.start
    )
  
  def sleep(duration: Duration) = live(ZIO.sleep(duration))

  val spec = suite("LinTso Timeout Tests")(
    test("ts with custom timeout vs default timeout") {
      (for {
        // Test ts with custom 25ms timeout
        fiber1 <- LinTso.ts(25.millis).fork
        _      <- TestClock.adjust(50.millis) // Should trigger 25ms timeout
        error1 <- fiber1.join.flip

        // Test ts without timeout (uses default 100ms)
        fiber2 <- LinTso.ts.fork
        _      <- TestClock.adjust(150.millis) // Should trigger 100ms default timeout
        error2 <- fiber2.join.flip
      } yield assertTrue(
        error1 == Timeout(MessageId(1), NodeId("lin-tso"), 25.millis),
        error2 == Timeout(MessageId(2), NodeId("lin-tso"), 100.millis)
      ))
        .provide(tRuntimeWithRealLinTso)
    },
    test("ts with longer custom timeout") {
      (for {
        // Test ts with custom 150ms timeout (longer than default 100ms)
        fiber1 <- LinTso.ts(150.millis).fork
        _      <- TestClock.adjust(200.millis) // Should trigger 150ms timeout
        error1 <- fiber1.join.flip

        // Test ts with shorter custom timeout
        fiber2 <- LinTso.ts(75.millis).fork
        _      <- TestClock.adjust(120.millis) // Should trigger 75ms timeout
        error2 <- fiber2.join.flip
      } yield assertTrue(
        error1 == Timeout(MessageId(1), NodeId("lin-tso"), 150.millis),
        error2 == Timeout(MessageId(2), NodeId("lin-tso"), 75.millis)
      ))
        .provide(tRuntimeWithRealLinTso)
    },
    test("ts timeout propagation verification") {
      (for {
        // Verify that very short timeout triggers faster than default
        fiber1 <- LinTso.ts(10.millis).fork
        _      <- TestClock.adjust(20.millis) // Should trigger 10ms timeout quickly
        error1 <- fiber1.join.flip

        // Verify that normal operations without timeout still work with default
        fiber2 <- LinTso.ts.fork
        _      <- TestClock.adjust(200.millis) // Should trigger 100ms default timeout
        error2 <- fiber2.join.flip
      } yield assertTrue(
        error1 == Timeout(MessageId(1), NodeId("lin-tso"), 10.millis),
        error2 == Timeout(MessageId(2), NodeId("lin-tso"), 100.millis)
      ))
        .provide(tRuntimeWithRealLinTso)
    }
  ) @@ TestAspect.timeout(10.seconds) @@ TestAspect.sequential
}
