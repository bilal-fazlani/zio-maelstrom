package com.bilalfazlani.zioMaelstrom

import zio.*
import zio.test.*
import zio.json.*
import testkit.*
import zio.json.ast.Json
import models.Body
import com.bilalfazlani.zioMaelstrom.models.MsgName

object MessageSenderTests extends MaelstromSpec {

  val settings                  = Settings()
  val context                   = Context(NodeId("n1"), Set(NodeId("n2")))
  val tRuntime                  = testRuntime(settings, context)
  def sleep(duration: Duration) = live(ZIO.sleep(duration))

  val spec = suite("Message Sender Tests")(
    test("successfully send message with 2 fields") {
      case class MyMessage(text: String, number: Int) derives JsonEncoder
      for {
        _ <- ZIO.serviceWithZIO[MessageSender](_.send(MyMessage("Hello World", 2), NodeId("n2")))
        outMessage: Message[Body[MyMessage]] <- getNextMessage[MyMessage]
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
    }.provide(tRuntime),
    test("successfully send message with 1 field") {
      case class MyMessage(text: String) derives JsonEncoder
      for {
        _ <- ZIO.serviceWithZIO[MessageSender](_.send(MyMessage("Hello World"), NodeId("n2")))
        outMessage: Message[Body[MyMessage]] <- getNextMessage[MyMessage]
        outJson                              <- ZIO.from(outMessage.toJsonAST)
        expectedJson = Json(
          "src"  -> Json.Str("n1"),
          "dest" -> Json.Str("n2"),
          "body" -> Json(
            "type" -> Json.Str("my_message"),
            "text" -> Json.Str("Hello World")
          )
        )
      } yield assertTrue(outJson == expectedJson)
    }.provide(tRuntime),
    test("successfully send message with 0 fields") {
      case class MyMessage() derives JsonEncoder
      for {
        _ <- ZIO.serviceWithZIO[MessageSender](_.send(MyMessage(), NodeId("n2")))
        outMessage: Message[Body[MyMessage]] <- getNextMessage[MyMessage]
        outJson                              <- ZIO.from(outMessage.toJsonAST)
        expectedJson = Json(
          "src"  -> Json.Str("n1"),
          "dest" -> Json.Str("n2"),
          "body" -> Json(
            "type" -> Json.Str("my_message")
          )
        )
      } yield assertTrue(outJson == expectedJson)
    }.provide(tRuntime),
    test("successfully send message with 0 fields and in_reply_to") {
      case class MyMessage() derives JsonEncoder
      for {
        _ <- ZIO.serviceWithZIO[MessageSender](
          _.sendRaw(Body(MsgName[MyMessage], MyMessage(), None, Some(MessageId(123))), NodeId("n2"))
        )
        outMessage: Message[Body[MyMessage]] <- getNextMessage[MyMessage]
        outJson                              <- ZIO.from(outMessage.toJsonAST)
        expectedJson = Json(
          "src"  -> Json.Str("n1"),
          "dest" -> Json.Str("n2"),
          "body" -> Json(
            "type"        -> Json.Str("my_message"),
            "in_reply_to" -> Json.Num(123)
          )
        )
      } yield assertTrue(outJson == expectedJson)
    }.provide(tRuntime)
  ) @@ TestAspect.timeout(10.seconds) @@ TestAspect.sequential
}
