package com.example

import com.bilalfazlani.zioMaelstrom.*
import com.bilalfazlani.zioMaelstrom.protocol.*
import zio.json.*
import zio.*

object ErrorDocs:
  object ReplyStandardError {
    case class InMessage(msg_id: MessageId) extends NeedsReply derives JsonCodec

    val handler = receive[InMessage] { case msg: InMessage =>
      reply(ErrorMessage(msg.msg_id, ErrorCode.PreconditionFailed, "some text message")) // (1)!
    }
  }

  object ReplyCustomError {
    case class InMessage(msg_id: MessageId) extends NeedsReply derives JsonCodec

    val handler = receive[InMessage] { case msg: InMessage =>
      reply(ErrorMessage(msg.msg_id, ErrorCode.Custom(1005), "some text message"))
    }
  }

  object GetErrorMessage {

    case class Query(id: Int, msg_id: MessageId, `type`: String = "query")
        extends NeedsReply,
          Sendable derives JsonCodec

    case class Answer(in_reply_to: MessageId, text: String) extends Reply derives JsonCodec

    val askResponse: ZIO[MaelstromRuntime, Nothing, Unit] =
      NodeId("g4")
        .ask[Answer](Query(1, MessageId(1)), 5.seconds)
        .flatMap(answer => logInfo(s"answer: $answer"))
        .catchAll {
          case t: Timeout         => logError(s"timeout: ${t.timeout}")
          case d: DecodingFailure => logError(s"decoding failure: ${d.error}")
          case e: ErrorMessage =>
            val code: ErrorCode = e.code
            val text: String    = e.text
            logError(s"error code: $code, error text: $text")
        }
  }
