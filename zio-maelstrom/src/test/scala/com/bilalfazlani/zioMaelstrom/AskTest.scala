package com.bilalfazlani.zioMaelstrom

import com.bilalfazlani.zioMaelstrom.models.{Body, MsgName}
import com.bilalfazlani.zioMaelstrom.testkit.*
import zio.*
import zio.json.*
import zio.json.ast.Json
import zio.test.*

object AskTest extends MaelstromSpec {

  val settings                  = Settings()
  val context                   = Context(NodeId("n1"), Set(NodeId("n2")))
  val tRuntime                  = testRuntime(settings, context)
  def sleep(duration: Duration) = live(ZIO.sleep(duration))

  val spec = suite("Ask Tests")(
    test("successfully send and receive message with 1 field each") {
      case class Ping(text: String) derives JsonCodec
      case class Pong(text: String) derives JsonCodec
      for {
        pongFiber <- ZIO
          .serviceWithZIO[MessageSender](_.ask[Ping, Pong](Ping("Hello"), NodeId("n2"), None))
          .fork
        pingMessage: Message[Ping] <- getNextMessage[Ping]
        _                          <- inputReply(Pong("World"), NodeId("n2"), MessageId(1))
        pong                       <- pongFiber.join
        pingJson                   <- ZIO.from(pingMessage.toJsonAST)
        pingExpectedJson = Json(
          "src"  -> Json.Str("n1"),
          "dest" -> Json.Str("n2"),
          "body" -> Json(
            "msg_id" -> Json.Num(1),
            "type"   -> Json.Str("ping"),
            "text"   -> Json.Str("Hello")
          )
        )
      } yield assertTrue(
        pingJson == pingExpectedJson,
        pong == Pong("World")
      )
    }.provide(tRuntime),
    test("successfully send and receive message with 0 field each") {
      case class Ping() derives JsonCodec
      case class Pong() derives JsonCodec

      // Create isolated runtime for this test to eliminate resource contention
      val isolatedSettings = Settings()
      val isolatedContext  = Context(NodeId("n1"), Set(NodeId("n2")))
      val isolatedRuntime  = testRuntime(isolatedSettings, isolatedContext)

      (for {
        pongFiber <- ZIO
          .serviceWithZIO[MessageSender](_.ask[Ping, Pong](Ping(), NodeId("n2"), None))
          .fork
        pingMessage: Message[Ping] <- getNextMessage[Ping]
        _                          <- inputReply(Pong(), NodeId("n2"), MessageId(1))
        pong                       <- pongFiber.join
        pingJson                   <- ZIO.from(pingMessage.toJsonAST)
        pingExpectedJson = Json(
          "src"  -> Json.Str("n1"),
          "dest" -> Json.Str("n2"),
          "body" -> Json(
            "type"   -> Json.Str("ping"),
            "msg_id" -> Json.Num(1)
          )
        )
      } yield assertTrue(
        pingJson == pingExpectedJson,
        pong == Pong()
      )).provide(isolatedRuntime)
    },
    test("successfully get and respond to a message with 1 field each") {
      // Create isolated runtime for this test to eliminate resource contention
      val isolatedSettings = Settings()
      val isolatedContext  = Context(NodeId("n1"), Set(NodeId("n2")))
      val isolatedRuntime  = testRuntime(isolatedSettings, isolatedContext)

      case class Ping(text: String) derives JsonCodec
      case class Pong(text: String) derives JsonCodec

      (for {
        fiber <- receive[Ping] { ping =>
          reply(Pong("Hello " + ping.text))
        }.timeout(500.millis).fork
        _                          <- inputAsk(Ping("World"), NodeId("n2"), MessageId(1))
        _                          <- TestClock.adjust(1.second) zipPar fiber.join.ignore
        pongMessage: Message[Pong] <- getNextMessage[Pong]
      } yield assertTrue(
        pongMessage == Message(
          NodeId("n1"),
          NodeId("n2"),
          Body(MsgName[Pong], Pong("Hello World"), None, Some(MessageId(1)))
        )
      )).provide(isolatedRuntime)
    },
    test("successfully get and respond to a message with 0 field each") {
      case class Ping() derives JsonCodec
      case class Pong() derives JsonCodec
      for {
        fiber <- receive[Ping] { _ =>
          reply(Pong())
        }.timeout(500.millis).fork
        _                          <- inputAsk(Ping(), NodeId("n2"), MessageId(1))
        _                          <- TestClock.adjust(1.second) zipPar fiber.join.ignore
        pongMessage: Message[Pong] <- getNextMessage[Pong]
      } yield assertTrue(
        pongMessage == Message(
          NodeId("n1"),
          NodeId("n2"),
          Body(MsgName[Pong], Pong(), None, Some(MessageId(1)))
        )
      )
    }.provide(tRuntime),
    test("successfully get subtype message") {
      sealed trait Ping derives JsonCodec
      case class PingPong(textEncoded: String) extends Ping derives JsonEncoder

      case class PingOk() derives JsonEncoder

      for {
        fiber <- receive[Ping] { case _: PingPong =>
          reply(PingOk())
        }.timeout(500.millis).fork
        _                       <- inputAsk(PingPong("hi"), NodeId("n2"), MessageId(1))
        _                       <- TestClock.adjust(1.second) zipPar fiber.join.ignore
        pingOk: Message[PingOk] <- getNextMessage[PingOk]
      } yield assertTrue(
        pingOk == Message(
          NodeId("n1"),
          NodeId("n2"),
          Body(MsgName[PingOk], PingOk(), None, Some(MessageId(1)))
        )
      )
    }.provide(tRuntime),
    test("forward response happy path") {
      case class Question(q: String) derives JsonCodec
      case class Answer(a: String) derives JsonCodec

      for {
        fiber <- receive[Question] { (q: Question) =>
          NodeId("g1").ask[Answer](q).defaultAskHandler.flatMap(reply)
        }.timeout(200.millis).fork
        _   <- inputAsk(Question("q"), NodeId("c1"), MessageId(1))
        _   <- zio.test.live(ZIO.sleep(80.millis))
        _   <- inputReply(Answer("a"), NodeId("g1"), MessageId(1))
        _   <- TestClock.adjust(200.millis)
        _   <- fiber.join.ignore
        _   <- getNextMessage[Question]
        ans <- getNextMessage[Answer]
      } yield assertTrue(
        ans == Message(NodeId("n1"), NodeId("c1"), Body(MsgName[Answer], Answer("a"), None, Some(MessageId(1))))
      )
    }.provide(tRuntime),
    test("auto reply error when downstream ask fails") {
      case class Question(q: String) derives JsonCodec
      case class Answer(a: String) derives JsonCodec

      for {
        fiber <- receive[Question] { (q: Question) =>
          NodeId("g1").ask[Answer](q).defaultAskHandler.flatMap(reply)
        }.timeout(200.millis).fork
        _   <- inputAsk(Question("q"), NodeId("c1"), MessageId(1))
        _   <- zio.test.live(ZIO.sleep(80.millis))
        _   <- inputReply(Error(ErrorCode.KeyDoesNotExist, "boom"), NodeId("g1"), MessageId(1))
        _   <- TestClock.adjust(200.millis)
        _   <- fiber.join.ignore
        _   <- getNextMessage[Question]
        ans <- getNextMessage[Error]
      } yield assertTrue(
        ans == Message(
          NodeId("n1"),
          NodeId("c1"),
          Body(
            MsgName[Error],
            Error(ErrorCode.Crash, "ask operation failed at remote node for another node"),
            None,
            Some(MessageId(1))
          )
        )
      )
    }.provide(tRuntime),
    test("ask with custom timeout uses specified timeout instead of default") {
      case class Ping(text: String) derives JsonCodec
      case class Pong(text: String) derives JsonCodec

      (for {
        // Use 50ms custom timeout (shorter than default 100ms)
        fiber <- NodeId("n2").ask[Pong](Ping("test"), 50.millis).fork
        _     <- getNextMessage[Ping]
        _     <- TestClock.adjust(100.millis) // Should trigger 50ms timeout
        error <- fiber.join.flip
      } yield assertTrue(error == Timeout(MessageId(1), NodeId("n2"), 50.millis)))
        .provide(tRuntime)
    },
    test("ask without timeout uses default timeout") {
      case class Ping(text: String) derives JsonCodec
      case class Pong(text: String) derives JsonCodec

      (for {
        // Use default timeout (no timeout parameter)
        fiber <- NodeId("n2").ask[Pong](Ping("test")).fork
        _     <- getNextMessage[Ping]
        _     <- TestClock.adjust(200.millis) // Should trigger 100ms default timeout
        error <- fiber.join.flip
      } yield assertTrue(error == Timeout(MessageId(1), NodeId("n2"), 100.millis)))
        .provide(tRuntime)
    },
    test("ask with longer custom timeout waits longer than default") {
      case class Ping(text: String) derives JsonCodec
      case class Pong(text: String) derives JsonCodec

      (for {
        // Use 200ms custom timeout (longer than default 100ms)
        fiber <- NodeId("n2").ask[Pong](Ping("test"), 200.millis).fork
        _     <- getNextMessage[Ping]
        _     <- TestClock.adjust(300.millis) // Should trigger 200ms custom timeout
        error <- fiber.join.flip
      } yield assertTrue(error == Timeout(MessageId(1), NodeId("n2"), 200.millis)))
        .provide(tRuntime)
    }
  ) @@ TestAspect.timeout(10.seconds) @@ TestAspect.sequential
}
