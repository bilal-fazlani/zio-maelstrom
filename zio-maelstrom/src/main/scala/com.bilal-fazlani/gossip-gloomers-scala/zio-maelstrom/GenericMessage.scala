package com.bilalfazlani.zioMaelstrom
package protocol

import zio.json.ast.Json
import zio.json.JsonDecoder

case class GenericDetails(
    `type`: Option[String],
    msg_id: Option[MessageId],
    in_reply_to: Option[MessageId]
)

extension (parent: Json)
  private def getChildOptional[A: JsonDecoder](field: String): Either[String, Option[A]] =
    parent.asObject.flatMap(_.get(field)).fold(Right(None))(json => JsonDecoder[A].fromJsonAST(json).map(Some(_)))
  private def getChild[A: JsonDecoder](field: String): Either[String, A] =
    parent.asObject.flatMap(_.get(field)).fold(Left(s"missing field '$field'"))(json => JsonDecoder[A].fromJsonAST(json))

object GenericDetails {
  val empty = GenericDetails(None, None, None)

  given JsonDecoder[GenericDetails] = JsonDecoder[Json].mapOrFail[GenericDetails](ast =>
    for {
      obj <- ast.asObject.toRight("message body is not a json object")
      tpe <- obj.getChildOptional[String]("type")
      msgId <- obj.getChildOptional[MessageId]("msg_id")
      inReplyTo <- obj.getChildOptional[MessageId]("in_reply_to")
    } yield GenericDetails(tpe, msgId, inReplyTo)
  )
}

case class GenericMessage(
    src: NodeId,
    dest: NodeId,
    details: GenericDetails,
    body: Option[Json],
    raw: Json
):
  def isOfType(tpe: String) = details.`type`.contains(tpe)

  def makeError(code: ErrorCode, text: String): Option[Message[MaelstromError]] =
    details.msg_id.map { msgid =>
      Message[MaelstromError](
        source = dest,
        destination = src,
        body = MaelstromError(
          in_reply_to = msgid,
          code = code.code,
          text = text
        )
      )
    }

object GenericMessage {
  given JsonDecoder[GenericMessage] = JsonDecoder[Json].mapOrFail[GenericMessage](ast =>
    for {
      obj <- ast.asObject.toRight("message is not a json object")
      src <- obj.getChild[NodeId]("src")
      dest <- obj.getChild[NodeId]("dest")
      body <- obj.getChildOptional[Json]("body")
      details <- body.fold(Right(GenericDetails.empty))(body => JsonDecoder[GenericDetails].fromJsonAST(body))
    } yield GenericMessage(src, dest, details, body, ast)
  )
}

trait GenericDecoder[A <: MessageBody: JsonDecoder]:
  def decode(msg: GenericMessage): Either[String, Message[A]]

object GenericDecoder:
  def apply[A <: MessageBody: JsonDecoder](using decoder: GenericDecoder[A]): GenericDecoder[A] = decoder
  given [A <: MessageBody: JsonDecoder]: GenericDecoder[A] = new GenericDecoder[A]:
    def decode(msg: GenericMessage): Either[String, Message[A]] =
      for {
        body <- msg.body.toRight("message body is missing")
        message <- JsonDecoder[A].fromJsonAST(body).map { a =>
          Message(
            source = msg.src,
            destination = msg.dest,
            body = a
          )
        }
      } yield message
