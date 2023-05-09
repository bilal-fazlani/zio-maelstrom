package com.bilalfazlani.gossipGloomersScala
package zioMaelstrom

import zio.Console.*
import zio.*
import zio.json.*
import zio.stream.{ZStream, ZPipeline}
import scala.io.StdIn

trait MessageHandler[-I: JsonDecoder]:
  def handle(message: I): Task[Unit]

trait MessageOut[-O: JsonEncoder]:
  def send(message: O): Task[Unit] = 
    printLine(message.toJson)

trait Debugger:
  def debugMessage(line: String): Task[Unit] = 
    printLineError(line)

trait MaelstromNode[-I: JsonDecoder, -O: JsonEncoder] extends MessageOut[O], MessageHandler[I], Debugger, ZIOAppDefault:
  def run = 
    ZStream
      .fromInputStream(java.lang.System.in)
      .via(ZPipeline.utfDecode)
      .mapZIO(s => 
        ZIO.fromEither(JsonDecoder[I].decodeJson(s))
        .mapError(err => Exception(s"invalid json error: $err"))
      )
      .mapZIO(handle)
      .runCollect
      .debug