package com.example

import zio.json.*
import zio.*
import com.bilalfazlani.zioMaelstrom.*

object ErrorDocs:
  object ReplyStandardError extends MaelstromNode {
    case class InMessage() derives JsonCodec

    val program = receive[InMessage](_ =>
      replyError(ErrorCode.PreconditionFailed, "some text message") // (1)!
    )
  }

  object ReplyCustomError extends MaelstromNode {
    case class InMessage() derives JsonCodec

    val program = receive[InMessage](_ => replyError(ErrorCode.Custom(1005), "some text message"))
  }

  object GetErrorMessage extends MaelstromNode {

    case class Query(id: Int) derives JsonCodec

    case class Answer(text: String) derives JsonCodec

    val askResponse: ZIO[MaelstromRuntime, AskError, Unit] = for
      answer <- NodeId("g4").ask[Answer](Query(1), 5.seconds)
      _      <- ZIO.logInfo(s"answer: $answer")
    yield ()

    val program = askResponse
      .catchAll {
        case t: Timeout         => ZIO.logError(s"timeout: ${t.timeout}")
        case d: DecodingFailure => ZIO.logError(s"decoding failure: ${d.error}")
        case e: Error           =>
          val code: ErrorCode = e.code
          val text: String    = e.text
          ZIO.logError(s"error code: $code, error text: $text")
      }
  }

  object ReplyErrorAPI extends MaelstromNode {
    case class Query(id: Int) derives JsonCodec

    val program = receive[Query](_ => replyError(ErrorCode.PreconditionFailed, "some text message"))
  }

  object FailedZIOEffect extends MaelstromNode {
    case class Query(id: Int) derives JsonCodec

    val program = receive[Query](_ => ZIO.fail(Error(ErrorCode.PreconditionFailed, "some text message")))
  }

  object DefaultAskErrorHandler extends MaelstromNode {
    case class Query(id: Int) derives JsonCodec
    case class Answer(text: String) derives JsonCodec

    private def askResponse(q: Query) = NodeId("g4").ask[Answer](q, 5.seconds)

    val program = receive[Query] { q =>
      for
        answer <- askResponse(q).defaultAskHandler
        _      <- reply(answer)
      yield ()
    }
  }
