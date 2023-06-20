package com.bilalfazlani.zioMaelstrom

import java.nio.file.Path
import zio.stream.{ZStream, ZPipeline}
import zio.{ZLayer, Queue}

sealed trait InputStream:
  val stream: ZStream[Any, Nothing, String]

object InputStream:
  val stdIn: ZLayer[Any, Nothing, InputStream] = ZLayer.succeed(StdIn)

  def file(path: Path): ZLayer[Logger, Nothing, InputStream] =
    ZLayer.succeed(path) >>> ZLayer.fromFunction(File.apply)

  val stream: ZLayer[Queue[String], Nothing, InputStream] =
    val stream = (queue: Queue[String]) => Stream(ZStream.fromQueue[String](queue))
    ZLayer.fromFunction(stream)

  case object StdIn extends InputStream:
    val stream = ZStream
      .fromInputStream(java.lang.System.in)
      .via(ZPipeline.utfDecode)
      .orDie

  case class File(path: Path, logger: Logger) extends InputStream:
    val stream = ZStream
      .fromFile(path.toFile, 128)
      .via(ZPipeline.utfDecode)
      .via(ZPipeline.splitLines)
      .tap(line => logger.debug(s"read: $line"))
      .orDie

  case class Stream(stream: ZStream[Any, Nothing, String]) extends InputStream
