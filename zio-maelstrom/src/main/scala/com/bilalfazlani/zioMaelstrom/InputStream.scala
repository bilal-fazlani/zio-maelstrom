package com.bilalfazlani.zioMaelstrom

import zio.Queue
import zio.Tag
import zio.ZLayer
import zio.json.EncoderOps
import zio.json.JsonEncoder
import zio.stream.ZPipeline
import zio.stream.ZStream

import java.nio.file.Path

case class InputStream(stream: ZStream[Any, Nothing, String])

object InputStream:

  case class InlineMessage[Body](src: NodeId, message: Body)

  val stdIn: ZLayer[Any, Nothing, InputStream] =
    def stream = ZStream
      .fromInputStream(java.lang.System.in)
      .via(ZPipeline.utfDecode)
      .via(ZPipeline.splitLines)
      .orDie
    ZLayer.succeed(InputStream(stream))

  val queue: ZLayer[Queue[String], Nothing, InputStream] =
    val func = (queue: Queue[String]) => InputStream(ZStream.fromQueue(queue))
    ZLayer.fromFunction(func)

  def file(path: Path): ZLayer[Any, Nothing, InputStream] =
    def stream = ZStream
      .fromFile(path.toFile, 128)
      .via(ZPipeline.utfDecode)
      .via(ZPipeline.splitLines)
      .orDie
    ZLayer.succeed(InputStream(stream))

  def inline[A: JsonEncoder: Tag](
      messages: Seq[InlineMessage[A]],
      context: Context
  ): ZLayer[Any, Nothing, InputStream] =
    def encode(m: InlineMessage[A]) =
      val message = Message[A](m.src, context.me, m.message)
      message.toJson
    val stream = InputStream(
      ZStream
        .fromIterable(messages)
        .map(encode(_))
    )
    ZLayer.succeed(stream)
