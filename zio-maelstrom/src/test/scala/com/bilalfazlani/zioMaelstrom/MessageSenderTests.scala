package com.bilalfazlani.zioMaelstrom

import zio.*
import zio.test.*
import zio.json.*
import testkit.*
import zio.json.ast.Json
import models.Body

object MessageSenderTests extends MaelstromSpec {
  case class MyMessage(text: String, number: Int) derives JsonEncoder

  val settings                  = Settings()
  val context                   = Context(NodeId("n1"), Set(NodeId("n2")))
  val tRuntime                  = testRuntime(settings, context)
  def sleep(duration: Duration) = live(ZIO.sleep(duration))

  val spec = suite("Message Sender Tests")(
    test("successfully send message") {
      for {
        _ <- ZIO.serviceWithZIO[MessageSender](_.sendV2(MyMessage("Hello World", 2), NodeId("n2")))
        outMessage: Message[Body[MyMessage]] <- getNextMessageV2[MyMessage]
        outJson                              <- ZIO.from(outMessage.toJsonAST)
        expectedJson = Json(
          "src"  -> Json.Str("n1"),
          "dest" -> Json.Str("n2"),
          "body" -> Json(
            "type"   -> Json.Str("my_message"),
            "text"   -> Json.Str("Hello World"),
            "number" -> Json.Num(2)
          )
        )
      } yield assertTrue(outJson == expectedJson)
    }.provide(tRuntime)
  ) @@ TestAspect.timeout(10.seconds) @@ TestAspect.sequential
}
