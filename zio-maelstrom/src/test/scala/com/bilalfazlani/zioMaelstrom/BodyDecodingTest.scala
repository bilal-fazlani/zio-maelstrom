package com.bilalfazlani.zioMaelstrom

import zio.*
import zio.test.*
import zio.json.*
import zio.json.ast.Json
import com.bilalfazlani.zioMaelstrom.models.Body

object BodyDecodingTest extends ZIOSpecDefault {

  // Test case classes
  case class SimpleMessage(text: String) derives JsonCodec
  case class ComplexMessage(text: String, number: Int, flag: Boolean) derives JsonCodec
  case class EmptyMessage() derives JsonCodec

  val spec = suite("Body Decoding Tests")(
    suite("Successful Decoding")(
      test("decode body with simple payload and all fields") {
        val json = Json.Obj(
          "type"        -> Json.Str("simple_message"),
          "text"        -> Json.Str("Hello World"),
          "msg_id"      -> Json.Num(123),
          "in_reply_to" -> Json.Num(456)
        )

        for {
          result <- ZIO.from(json.as[Body[SimpleMessage]])
        } yield assertTrue(
          result.`type` == "simple_message",
          result.payload == SimpleMessage("Hello World"),
          result.msg_id == Some(MessageId(123)),
          result.in_reply_to == Some(MessageId(456))
        )
      },
      test("decode body with complex payload") {
        val json = Json.Obj(
          "type"   -> Json.Str("complex_message"),
          "text"   -> Json.Str("Hello"),
          "number" -> Json.Num(42),
          "flag"   -> Json.Bool(true),
          "msg_id" -> Json.Num(789)
        )

        for {
          result <- ZIO.from(json.as[Body[ComplexMessage]])
        } yield assertTrue(
          result.`type` == "complex_message",
          result.payload == ComplexMessage("Hello", 42, true),
          result.msg_id == Some(MessageId(789)),
          result.in_reply_to == None
        )
      },
      test("decode body with empty payload") {
        val json = Json.Obj(
          "type"   -> Json.Str("empty_message"),
          "msg_id" -> Json.Num(100)
        )

        for {
          result <- ZIO.from(json.as[Body[EmptyMessage]])
        } yield assertTrue(
          result.`type` == "empty_message",
          result.payload == EmptyMessage(),
          result.msg_id == Some(MessageId(100)),
          result.in_reply_to == None
        )
      },
      test("decode body with only type field") {
        val json = Json.Obj(
          "type" -> Json.Str("empty_message")
        )

        for {
          result <- ZIO.from(json.as[Body[EmptyMessage]])
        } yield assertTrue(
          result.`type` == "empty_message",
          result.payload == EmptyMessage(),
          result.msg_id == None,
          result.in_reply_to == None
        )
      },
      test("decode body with only in_reply_to") {
        val json = Json.Obj(
          "type"        -> Json.Str("simple_message"),
          "text"        -> Json.Str("Reply"),
          "in_reply_to" -> Json.Num(999)
        )

        for {
          result <- ZIO.from(json.as[Body[SimpleMessage]])
        } yield assertTrue(
          result.`type` == "simple_message",
          result.payload == SimpleMessage("Reply"),
          result.msg_id == None,
          result.in_reply_to == Some(MessageId(999))
        )
      }
    ),
    suite("Error Cases")(
      test("fail when type field is missing") {
        val json = Json.Obj(
          "text"   -> Json.Str("Hello"),
          "msg_id" -> Json.Num(123)
        )

        for {
          result <- ZIO.from(json.as[Body[SimpleMessage]]).flip
        } yield assertTrue(result.contains("Missing or invalid 'type' field"))
      },
      test("fail when type field is not a string") {
        val json = Json.Obj(
          "type" -> Json.Num(123),
          "text" -> Json.Str("Hello")
        )

        for {
          result <- ZIO.from(json.as[Body[SimpleMessage]]).flip
        } yield assertTrue(result.contains("Missing or invalid 'type' field"))
      },
      test("fail when payload fields are invalid") {
        val json = Json.Obj(
          "type" -> Json.Str("simple_message"),
          "text" -> Json.Num(123) // text should be string
        )

        for {
          result <- ZIO.from(json.as[Body[SimpleMessage]]).flip
        } yield assertTrue(result.contains("Failed to decode payload"))
      },
      test("fail when required payload field is missing") {
        val json = Json.Obj(
          "type" -> Json.Str("simple_message")
          // missing "text" field required by SimpleMessage
        )

        for {
          result <- ZIO.from(json.as[Body[SimpleMessage]]).flip
        } yield assertTrue(result.contains("Failed to decode payload"))
      },
      test("fail when msg_id is not a number") {
        val json = Json.Obj(
          "type"   -> Json.Str("simple_message"),
          "text"   -> Json.Str("Hello"),
          "msg_id" -> Json.Str("not-a-number")
        )

        for {
          result <- ZIO.from(json.as[Body[SimpleMessage]])
        } yield assertTrue(
          result.`type` == "simple_message",
          result.payload == SimpleMessage("Hello"),
          result.msg_id == None // invalid msg_id should be treated as None
        )
      }
    ),
    suite("Round-trip Encoding/Decoding")(
      test("encode then decode should preserve data") {
        val original =
          Body("test_message", SimpleMessage("Test"), Some(MessageId(42)), Some(MessageId(24)))

        for {
          encoded <- ZIO.from(original.toJsonAST)
          decoded <- ZIO.from(encoded.as[Body[SimpleMessage]])
        } yield assertTrue(decoded == original)
      },
      test("encode then decode complex message") {
        val original = Body("complex", ComplexMessage("Test", 100, false), Some(MessageId(1)), None)

        for {
          encoded <- ZIO.from(original.toJsonAST)
          decoded <- ZIO.from(encoded.as[Body[ComplexMessage]])
        } yield assertTrue(decoded == original)
      },
      test("encode then decode empty message") {
        val original = Body("empty", EmptyMessage(), None, Some(MessageId(55)))

        for {
          encoded <- ZIO.from(original.toJsonAST)
          decoded <- ZIO.from(encoded.as[Body[EmptyMessage]])
        } yield assertTrue(decoded == original)
      }
    ),
    suite("Edge Cases")(
      test("handle JSON with extra unknown fields") {
        val json = Json.Obj(
          "type"            -> Json.Str("simple_message"),
          "text"            -> Json.Str("Hello"),
          "unknown_field"   -> Json.Str("ignored"),
          "another_unknown" -> Json.Num(999),
          "msg_id"          -> Json.Num(123)
        )

        for {
          result <- ZIO.from(json.as[Body[SimpleMessage]])
        } yield assertTrue(
          result.`type` == "simple_message",
          result.payload == SimpleMessage("Hello"),
          result.msg_id == Some(MessageId(123))
        )
      },
      test("handle nested JSON objects in payload") {
        case class NestedMessage(data: Map[String, String]) derives JsonCodec

        val json = Json.Obj(
          "type" -> Json.Str("nested_message"),
          "data" -> Json.Obj(
            "key1" -> Json.Str("value1"),
            "key2" -> Json.Str("value2")
          )
        )

        for {
          result <- ZIO.from(json.as[Body[NestedMessage]])
        } yield assertTrue(
          result.`type` == "nested_message",
          result.payload == NestedMessage(Map("key1" -> "value1", "key2" -> "value2"))
        )
      }
    )
  )
}
