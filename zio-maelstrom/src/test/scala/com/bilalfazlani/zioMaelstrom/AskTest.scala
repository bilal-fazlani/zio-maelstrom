package com.bilalfazlani.zioMaelstrom

import zio.*
import zio.test.*
import zio.json.*
import testkit.*
import zio.json.ast.Json
import models.Body
import com.bilalfazlani.zioMaelstrom.models.MsgName

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
          .serviceWithZIO[MessageSender](_.ask[Ping, Pong](Ping("Hello"), NodeId("n2"), 1.second))
          .fork
        pingMessage: Message[Ping] <- getNextMessage[Ping]
        _                          <- inputReply(Pong("World"), NodeId("n2"), MessageId(1))
        pong                       <- pongFiber.join.debug("pong")
        pingJson                   <- ZIO.from(pingMessage.toJsonAST).debug("pingJson")
        pingExpectedJson = Json(
          "src"  -> Json.Str("n1"),
          "dest" -> Json.Str("n2"),
          "body" -> Json(
            "type" -> Json.Str("ping"),
            "text" -> Json.Str("Hello")
          )
        )
        pongExpectedJson = Json(
          "type" -> Json.Str("pong"),
          "text" -> Json.Str("World")
        )
      } yield assertTrue(true)
    }.provide(tRuntime),
    test("successfully send and receive message with 0 field each") {
      case class Ping() derives JsonCodec
      case class Pong() derives JsonCodec
      for {
        pongFiber <- ZIO
          .serviceWithZIO[MessageSender](_.ask[Ping, Pong](Ping(), NodeId("n2"), 1.second))
          .fork
        pingMessage: Message[Ping] <- getNextMessage[Ping]
        _                          <- inputReply(Pong(), NodeId("n2"), MessageId(1))
        pong                       <- pongFiber.join.debug("pong")
        pingJson                   <- ZIO.from(pingMessage.toJsonAST).debug("pingJson")
        pingExpectedJson = Json(
          "src"  -> Json.Str("n1"),
          "dest" -> Json.Str("n2"),
          "body" -> Json(
            "type" -> Json.Str("ping")
          )
        )
        pongExpectedJson = Json(
          "type" -> Json.Str("pong")
        )
      } yield assertTrue(true)
    }.provide(tRuntime),
    test("successfully get and respond to a message with 1 field each") {
      case class Ping(text: String) derives JsonCodec
      case class Pong(text: String) derives JsonCodec
      for {
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
      )
    }.provide(tRuntime),
    test("successfully get and respond to a message with 0 field each") {
      case class Ping() derives JsonCodec
      case class Pong() derives JsonCodec
      for {
        fiber <- receive[Ping] { ping =>
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
    }.provide(tRuntime)
  ) @@ TestAspect.timeout(10.seconds) @@ TestAspect.sequential
}
