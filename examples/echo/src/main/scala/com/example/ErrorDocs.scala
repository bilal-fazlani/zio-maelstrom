package com.example

import zio.json.*
import zio.*
import com.bilalfazlani.zioMaelstrom.*

object ErrorDocs:
  object ReplyStandardError {
    case class InMessage() derives JsonCodec

    val handler = receive[InMessage](_ =>
      reply(Error(ErrorCode.PreconditionFailed, "some text message")) // (1)!
    )
  }

  object ReplyCustomError {
    case class InMessage() derives JsonCodec

    val handler = receive[InMessage](_ =>
      reply(Error(ErrorCode.Custom(1005), "some text message"))
    )
  }

  object GetErrorMessage {

    case class Query(id: Int) derives JsonCodec

    case class Answer(text: String) derives JsonCodec

    val askResponse: ZIO[MaelstromRuntime, AskError, Unit] = for
      answer <- NodeId("g4").ask[Answer](Query(1), 5.seconds)
      _      <- ZIO.logInfo(s"answer: $answer")
    yield ()

    askResponse
      .catchAll {
        case t: Timeout         => ZIO.logError(s"timeout: ${t.timeout}")
        case d: DecodingFailure => ZIO.logError(s"decoding failure: ${d.error}")
        case e: Error           =>
          val code: ErrorCode = e.code
          val text: String    = e.text
          ZIO.logError(s"error code: $code, error text: $text")
      }
  }
