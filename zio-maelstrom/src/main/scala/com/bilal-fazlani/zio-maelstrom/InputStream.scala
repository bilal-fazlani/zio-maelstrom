package com.bilalfazlani.zioMaelstrom

import java.nio.file.Path
import zio.stream.{ZStream, ZPipeline}
import zio.{ZLayer, Queue}

sealed trait InputStream:
  def stream: ZStream[Any, Nothing, String]

object InputStream:
  val stdIn: ZLayer[Any, Nothing, InputStream] = ZLayer.succeed(StdIn)

  def file(path: Path): ZLayer[Logger, Nothing, InputStream] =
    ZLayer.succeed(path) >>> ZLayer.fromFunction(File.apply)

  val queue: ZLayer[Queue[String] & Logger, Nothing, InputStream] =
    val func = (queue: Queue[String], logger: Logger) => Stream(ZStream.fromQueue(queue), logger)
    ZLayer.fromFunction(func)

  case object StdIn extends InputStream:
    def stream = ZStream
      .fromInputStream(java.lang.System.in)
      .via(ZPipeline.utfDecode)
      .via(ZPipeline.splitLines)
      .orDie

  case class File(path: Path, logger: Logger) extends InputStream:
    def stream = ZStream
      .fromFile(path.toFile, 128)
      .via(ZPipeline.utfDecode)
      .via(ZPipeline.splitLines)
      .orDie

  case class Stream(stream: ZStream[Any, Nothing, String], logger: Logger) extends InputStream
