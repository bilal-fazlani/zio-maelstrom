package com.bilalfazlani.zioMaelstrom.models

import com.bilalfazlani.zioMaelstrom.MessageId
import zio.json.{JsonDecoder, JsonEncoder}
import zio.json.ast.Json
import zio.json.internal.Write

// never exposed to end‑users
private[zioMaelstrom] case class Body[+A](
    `type`: String, // filled from MsgName[A]
    payload: A,     // the user's case class
    msg_id: Option[MessageId],
    in_reply_to: Option[MessageId]
)

object Body:

  given [A: JsonDecoder]: JsonDecoder[Body[A]] =
    JsonDecoder[Json.Obj].mapOrFail { jsonObj =>
      val fields = jsonObj.fields.toMap

      // Extract Body-specific fields
      val typeField = fields
        .get("type")
        .flatMap(_.as[String].toOption)
        .toRight("Missing or invalid 'type' field")

      val msgId     = fields.get("msg_id").flatMap(_.as[MessageId].toOption)
      val inReplyTo = fields.get("in_reply_to").flatMap(_.as[MessageId].toOption)

      val payloadJson   = Json.Obj(zio.Chunk.fromIterable(fields))

      for {
        tpe     <- typeField
        payload <- payloadJson.as[A].left.map(s => s"Failed to decode payload: $s")
      } yield Body(tpe, payload, msgId, inReplyTo)
    }

  given [A: JsonEncoder]: JsonEncoder[Body[A]] =
    (a: Body[A], indent: Option[Int], out: Write) => {
      out.write(s"{\"type\":\"${a.`type`}\"")
      if (a.msg_id.isDefined) {
        out.write(",\"msg_id\":")
        JsonEncoder[MessageId].unsafeEncode(a.msg_id.get, indent, out)
      }
      if (a.in_reply_to.isDefined) {
        out.write(",\"in_reply_to\":")
        JsonEncoder[MessageId].unsafeEncode(a.in_reply_to.get, indent, out)
      }
      JsonEncoder[A].toJsonAST(a.payload) match {
        case Left(value) => throw new RuntimeException(s"Failed to encode payload: $value")
        case Right(json) =>
          json match {
            case Json.Obj(fields) =>
              fields.zipWithIndex.foreach { case ((k, v), i) =>
                if (i <= fields.size - 1) out.write(",")
                out.write(s"\"$k\":")
                JsonEncoder[Json].unsafeEncode(v, indent, out)
              }
            case x: Json => JsonEncoder[Json].unsafeEncode(x, indent, out)
          }
      }
      out.write("}")
    }
