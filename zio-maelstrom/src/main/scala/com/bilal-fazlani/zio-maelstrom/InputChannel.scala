package com.bilalfazlani.zioMaelstrom

import zio.{Scope, ZIO, ZLayer}
import zio.stream.{ZStream, ZPipeline}
import zio.json.JsonDecoder
import protocol.*

private[zioMaelstrom] trait InputChannel:
  def readInputs: ZIO[Scope, Nothing, Inputs]

private object InputChannel:
  val live: ZLayer[Logger & Settings, Nothing, InputChannel] = ZLayer
    .fromFunction(InputChannelLive.apply)

private case class InvalidInput(input: String, error: String)

private case class InputChannelLive(logger: Logger, settings: Settings) extends InputChannel:
  private val strings =
    val nodeInput = settings.nodeInput
    ZStream.unwrap(for {
      _ <- logger.info(s"using $nodeInput")
      strm = (nodeInput match {
        case NodeInput.StdIn => ZStream.fromInputStream(java.lang.System.in)
            .via(ZPipeline.utfDecode)
        case NodeInput.FilePath(path) => ZStream.fromFile(path.toFile, 128).via(ZPipeline.utfDecode)
            .via(ZPipeline.splitLines).tap(line => logger.debug(s"read: $line"))
      }).filter(line => line.trim != "").takeWhile(line => line.trim != "q")
    } yield strm).orDie

  val readInputs = strings.collectZIO {
    case str @ Sleep(duration) => Sleep.conditionally(logger, duration) *> ZIO.succeed(None)
    case str                   => ZIO.succeed(Some(str))
  }.collectSome
    .map(str => JsonDecoder[GenericMessage].decodeJson(str).left.map(e => InvalidInput(str, e)))
    .tap {
      case Left(errorMessage) => logger
          .error(s"could not read `${errorMessage.input}`, error: ${errorMessage.error}")
      case Right(genericMessage) => ZIO.unit
    }.collectRight.partition(_.isResponse, 1024).map(x => Inputs(x._1, x._2))
