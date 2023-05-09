package com.bilalfazlani.gossipGloomersScala
package zioMaelstrom

import protocol.*
import zio.Console.*
import zio.*
import zio.json.*
import zio.stream.{ZStream, ZPipeline}
import scala.io.StdIn

enum NodeInput:
  case StdIn
  case File(path: String)

trait MessageHandler[I <: MessageBody: JsonDecoder]:
  def handle(message: Message[I]): Task[Unit]

trait MessageOut[O <: MessageBody: JsonEncoder]:
  def send(message: Message[O]): Task[Unit] =
    printLine(message.toJson)

trait Debugger:
  def debugMessage(line: String): Task[Unit] =
    printLineError(line)

trait MaelstromNode[I <: MessageBody: JsonDecoder, O <: MessageBody: JsonEncoder]
    extends MessageOut[O],
      MessageHandler[I],
      Debugger,
      ZIOAppDefault:

  def onInvalidJson(input: String, error: String): Task[Unit] =
    val msg = s"error: $error, input: $input"
    debugMessage(msg) *> this.exit(ExitCode.failure)

  def nodeInput = NodeInput.StdIn

  private def inputStream = nodeInput match
    case NodeInput.StdIn      => ZStream.fromInputStream(java.lang.System.in).via(ZPipeline.utfDecode)
    case NodeInput.File(path) => ZStream.fromFileName(path, 4096).via(ZPipeline.utfDecode).via(ZPipeline.splitLines)

  def run =
    inputStream
      // .takeWhile(line => line.trim != "q" && line.trim != "quit")
      .mapZIO(s =>
        ZIO
          .fromEither(JsonDecoder[Message[I]].decodeJson(s))
          .mapError(onInvalidJson(s, _))
      )
      .mapZIO(handle)
      .runCollect
      .debug
