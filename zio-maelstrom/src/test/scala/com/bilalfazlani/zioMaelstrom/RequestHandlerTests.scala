package com.bilalfazlani.zioMaelstrom

import com.bilalfazlani.zioMaelstrom.models.Body
import zio.*
import zio.test.*
import zio.json.*
import testkit.*

object RequestHandlerTests extends MaelstromSpec {

  val settings                  = Settings()
  val context                   = Context(NodeId("n1"), Set(NodeId("n2"), NodeId("n5")))
  val tRuntime                  = testRuntime(settings, context)
  def sleep(duration: Duration) = live(ZIO.sleep(duration))

  case class Ping() derives JsonCodec
  case class PingOk() derives JsonCodec

  // --
  case class GetNumber() derives JsonCodec
  case class Number(value: Int) derives JsonCodec

  val spec = suite("RequestHandler Tests")(
    test("successfully send and receive message") {
      (for {
        fiber  <- receive[Ping](ping => reply(PingOk())).fork
        _      <- inputSend(Body("ping", Ping(), Some(MessageId(100)), None), NodeId("n2"))
        pingOk <- getNextMessage[PingOk]
        _      <- fiber.interrupt
        expectedMessage = Message(NodeId("n1"), NodeId("n2"), Body("ping_ok", PingOk(), None, Some(MessageId(100))))
      } yield assertTrue(pingOk == expectedMessage))
        .provide(tRuntime)
    },
    test("receive and ask should work concurrently") {
      (for {
        fiber <- receive[GetNumber] { case GetNumber() =>
          NodeId("n5")
            .ask[Number](GetNumber(), 2.seconds)
            .flatMap(n => reply(n))
            .catchAll(e => ZIO.dieMessage(e.toString))
        }.fork
        _          <- inputSend(Body("number", GetNumber(), Some(MessageId(60)), None), from = NodeId("n2"))
        _          <- sleep(100.millis)
        _          <- inputReply(Number(45), from = NodeId("n5"), MessageId(1))
        _ <- getNextMessage[Number]
        responseFromNode <- getNextMessage[Number]
      } yield assertTrue(
        responseFromNode == Message(NodeId("n1"), NodeId("n2"), Body("number", Number(45), None, Some(MessageId(60)))),
      ))
        .provide(tRuntime)
    }
  ) @@ TestAspect.timeout(10.seconds) @@ TestAspect.sequential
}
