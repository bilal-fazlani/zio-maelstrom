package com.bilalfazlani.zioMaelstrom

import zio.{Scope, ZIO, ZLayer}
import zio.stream.{ZStream}
import zio.json.JsonDecoder
import protocol.*

private[zioMaelstrom] trait InputChannel:
  def readInputs: ZIO[Scope, Nothing, Inputs]

private object InputChannel:
  val live = ZLayer.fromFunction(InputChannelLive.apply)

private case class InvalidInput(input: String, error: String)

private case class InputChannelLive(logger: Logger, inputStream: InputStream) extends InputChannel:
  val readInputs = spliStream(inputStream.stream, logger)

  private def spliStream(strings: ZStream[Any, Nothing, String], logger: Logger) = strings
    .collectZIO {
      case str @ Sleep(duration) => Sleep.conditionally(logger, duration) *> ZIO.succeed(None)
      case str                   => ZIO.succeed(Some(str))
    }
    .collectSome
    .map(str => JsonDecoder[GenericMessage].decodeJson(str).left.map(e => InvalidInput(str, e)))
    .tap {
      case Left(errorMessage) =>
        logger
          .error(s"could not read `${errorMessage.input}`, error: ${errorMessage.error}")
      case Right(genericMessage) => ZIO.unit
    }
    .collectRight
    .partition(_.isResponse, 1024)
    .map(x => Inputs(x._1, x._2))
