package com.bilalfazlani.zioMaelstrom

import java.nio.file.Path
import zio.stream.{ZStream, ZPipeline}
import zio.{ZLayer, Queue}

case class InputStream(stream: ZStream[Any, Nothing, String])

object InputStream:
  val stdIn: ZLayer[Any, Nothing, InputStream] =
    def stream = ZStream
      .fromInputStream(java.lang.System.in)
      .via(ZPipeline.utfDecode)
      .via(ZPipeline.splitLines)
      .orDie
    ZLayer.succeed(InputStream(stream))

  def file(path: Path): ZLayer[Any, Nothing, InputStream] =
    def stream = ZStream
      .fromFile(path.toFile, 128)
      .via(ZPipeline.utfDecode)
      .via(ZPipeline.splitLines)
      .orDie
    ZLayer.succeed(InputStream(stream))

  def inline(input: String): ZLayer[Any, Nothing, InputStream] =
    ZLayer.succeed(InputStream(ZStream.fromIterable(input.split("\n"))))

  val queue: ZLayer[Queue[String], Nothing, InputStream] =
    val func = (queue: Queue[String]) => InputStream(ZStream.fromQueue(queue))
    ZLayer.fromFunction(func)
