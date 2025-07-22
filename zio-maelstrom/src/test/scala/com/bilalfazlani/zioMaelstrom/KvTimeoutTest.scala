package com.bilalfazlani.zioMaelstrom

import com.bilalfazlani.zioMaelstrom.services.{SeqKv, LinKv, LwwKv, LinTso}
import com.bilalfazlani.zioMaelstrom.testkit.*
import zio.*
import zio.json.*
import zio.test.*

object KvTimeoutTest extends MaelstromSpec {

  val settings = Settings()
  val context  = Context(NodeId("n1"), Set(NodeId("n2")))

  // Use real KV implementations instead of fakes to test timeout behavior
  val tRuntimeWithRealKv: ZLayer[Any, Nothing, MaelstromTestRuntime] =
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

      // Real Services (these will trigger network timeouts)
      LinKv.live,
      SeqKv.live,
      LwwKv.live,
      LinTso.live,

      // effectful layers
      Initialisation.fake(context), // FAKE
      ResponseHandler.start
    )

  def sleep(duration: Duration) = live(ZIO.sleep(duration))

  val spec = suite("KV Timeout Tests")(
    suite("SeqKv Timeout Tests")(
      test("read with timeout vs without timeout") {
        (for {
          // Test read with custom 50ms timeout
          fiber1 <- SeqKv.read[String]("test-key", 50.millis).fork
          _      <- TestClock.adjust(100.millis) // Should trigger 50ms timeout
          error1 <- fiber1.join.flip

          // Test read without timeout (uses default 100ms)
          fiber2 <- SeqKv.read[String]("test-key").fork
          _      <- TestClock.adjust(200.millis) // Should trigger 100ms default timeout
          error2 <- fiber2.join.flip
        } yield assertTrue(
          error1 == Timeout(MessageId(1), NodeId("seq-kv"), 50.millis),
          error2 == Timeout(MessageId(2), NodeId("seq-kv"), 100.millis)
        ))
          .provide(tRuntimeWithRealKv)
      },
      test("write with timeout vs without timeout") {
        (for {
          // Test write with custom 30ms timeout
          fiber1 <- SeqKv.write("key1", "value1", 30.millis).fork
          _      <- TestClock.adjust(60.millis) // Should trigger 30ms timeout
          error1 <- fiber1.join.flip

          // Test write without timeout (uses default 100ms)
          fiber2 <- SeqKv.write("key2", "value2").fork
          _      <- TestClock.adjust(150.millis) // Should trigger 100ms default timeout
          error2 <- fiber2.join.flip
        } yield assertTrue(
          error1 == Timeout(MessageId(1), NodeId("seq-kv"), 30.millis),
          error2 == Timeout(MessageId(2), NodeId("seq-kv"), 100.millis)
        ))
          .provide(tRuntimeWithRealKv)
      },
      test("cas with timeout vs without timeout") {
        (for {
          // Test cas with custom 75ms timeout
          fiber1 <- SeqKv.cas("key", "old", "new", true, 75.millis).fork
          _      <- TestClock.adjust(120.millis) // Should trigger 75ms timeout
          error1 <- fiber1.join.flip

          // Test cas without timeout (uses default 100ms)
          fiber2 <- SeqKv.cas("key", "old", "new", true).fork
          _      <- TestClock.adjust(150.millis) // Should trigger 100ms default timeout
          error2 <- fiber2.join.flip
        } yield assertTrue(
          error1 == Timeout(MessageId(1), NodeId("seq-kv"), 75.millis),
          error2 == Timeout(MessageId(2), NodeId("seq-kv"), 100.millis)
        ))
          .provide(tRuntimeWithRealKv)
      },
      test("writeIfNotExists with timeout vs without timeout") {
        (for {
          // Test writeIfNotExists with custom 40ms timeout
          fiber1 <- SeqKv.writeIfNotExists("key1", "value1", 40.millis).fork
          _      <- TestClock.adjust(80.millis) // Should trigger 40ms timeout
          error1 <- fiber1.join.flip

          // Test writeIfNotExists without timeout (uses default 100ms)
          fiber2 <- SeqKv.writeIfNotExists("key2", "value2").fork
          _      <- TestClock.adjust(150.millis) // Should trigger 100ms default timeout
          error2 <- fiber2.join.flip
        } yield assertTrue(
          error1 == Timeout(MessageId(1), NodeId("seq-kv"), 40.millis),
          error2 == Timeout(MessageId(2), NodeId("seq-kv"), 100.millis)
        ))
          .provide(tRuntimeWithRealKv)
      }
    ),
    suite("LinKv Timeout Tests")(
      test("LinKv read with timeout vs without timeout") {
        (for {
          // Test LinKv read with custom 60ms timeout
          fiber1 <- LinKv.read[String]("test-key", 60.millis).fork
          _      <- TestClock.adjust(100.millis) // Should trigger 60ms timeout
          error1 <- fiber1.join.flip

          // Test LinKv read without timeout (uses default 100ms)
          fiber2 <- LinKv.read[String]("test-key").fork
          _      <- TestClock.adjust(150.millis) // Should trigger 100ms default timeout
          error2 <- fiber2.join.flip
        } yield assertTrue(
          error1 == Timeout(MessageId(1), NodeId("lin-kv"), 60.millis),
          error2 == Timeout(MessageId(2), NodeId("lin-kv"), 100.millis)
        ))
          .provide(tRuntimeWithRealKv)
      },
      test("LinKv write with timeout vs without timeout") {
        (for {
          // Test LinKv write with custom 35ms timeout
          fiber1 <- LinKv.write("key1", "value1", 35.millis).fork
          _      <- TestClock.adjust(70.millis) // Should trigger 35ms timeout
          error1 <- fiber1.join.flip

          // Test LinKv write without timeout (uses default 100ms)
          fiber2 <- LinKv.write("key2", "value2").fork
          _      <- TestClock.adjust(150.millis) // Should trigger 100ms default timeout
          error2 <- fiber2.join.flip
        } yield assertTrue(
          error1 == Timeout(MessageId(1), NodeId("lin-kv"), 35.millis),
          error2 == Timeout(MessageId(2), NodeId("lin-kv"), 100.millis)
        ))
          .provide(tRuntimeWithRealKv)
      }
    ),
    suite("LwwKv Timeout Tests")(
      test("LwwKv operations with timeout vs without timeout") {
        (for {
          // Test LwwKv read with custom 45ms timeout
          fiber1 <- LwwKv.read[String]("test-key", 45.millis).fork
          _      <- TestClock.adjust(90.millis) // Should trigger 45ms timeout
          error1 <- fiber1.join.flip

          // Test LwwKv write without timeout (uses default 100ms)
          fiber2 <- LwwKv.write("key", "value").fork
          _      <- TestClock.adjust(150.millis) // Should trigger 100ms default timeout
          error2 <- fiber2.join.flip
        } yield assertTrue(
          error1 == Timeout(MessageId(1), NodeId("lww-kv"), 45.millis),
          error2 == Timeout(MessageId(2), NodeId("lww-kv"), 100.millis)
        ))
          .provide(tRuntimeWithRealKv)
      }
    )
  ) @@ TestAspect.timeout(10.seconds) @@ TestAspect.sequential
}
