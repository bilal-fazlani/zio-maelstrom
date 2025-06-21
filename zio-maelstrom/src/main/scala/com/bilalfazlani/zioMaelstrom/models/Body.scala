package com.bilalfazlani.zioMaelstrom.models

import com.bilalfazlani.zioMaelstrom.MessageId
import zio.json.JsonEncoder
import zio.json.ast.Json
import zio.json.internal.Write

// never exposed to end‑users
private[zioMaelstrom] case class Body[A](
    `type`: String, // filled from MsgName[A]
    payload: A,     // the user's case class
    msg_id: Option[MessageId],
    in_reply_to: Option[MessageId]
)

object Body:

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
    out.write(s",")
    JsonEncoder[A].toJsonAST(a.payload) match {
      case Left(value) => throw new RuntimeException(s"Failed to encode payload: $value")
      case Right(json) => json match {
        case Json.Obj(fields) => fields.zipWithIndex.foreach{case ((k,v), i) =>
          out.write(s"\"$k\":")
          JsonEncoder[Json].unsafeEncode(v,indent, out)
          if (i < fields.size - 1) out.write(",")
        }
        case x: Json => JsonEncoder[Json].unsafeEncode(x,indent, out)
      }
    }
    out.write("}")
  }
