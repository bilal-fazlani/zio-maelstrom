package com.bilalfazlani.zioMaelstrom

import zio.{Scope, ZIO, ZLayer}
import zio.json.JsonDecoder

private[zioMaelstrom] trait InputChannel:
  def partitionInputs: ZIO[Scope, Nothing, Inputs]

private object InputChannel:
  val live = ZLayer.fromFunction(InputChannelLive.apply)

private case class InvalidInput(input: String, error: String)

private case class InputChannelLive(logger: Logger, inputStream: InputStream) extends InputChannel:
  val partitionInputs = inputStream.stream
    .tap(str => logger.debug(s"read: $str"))
    .collectZIO {
      case str @ Sleep(duration) => Sleep.conditionally(logger, duration) *> ZIO.succeed(None)
      case str                   => ZIO.succeed(Some(str))
    }
    .collectSome
    .map(str => JsonDecoder[GenericMessage].decodeJson(str).left.map(e => InvalidInput(str, e)))
    .tap {
      case Left(InvalidInput(input, error)) =>
        logger.error(s"could not decode input json message: `${input.trim}`")
          *> logger.warn(s"${error}")
      case Right(genericMessage) => ZIO.unit
    }
    .collectRight
    .partition(_.isResponse, 1024)
    .map(x => Inputs(x._1, x._2))
