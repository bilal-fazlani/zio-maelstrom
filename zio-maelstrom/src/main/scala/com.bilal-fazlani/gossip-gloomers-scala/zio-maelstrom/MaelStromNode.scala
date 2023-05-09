package com.bilalfazlani.gossipGloomersScala
package zioMaelstrom

import protocol.*
import zio.Console.*
import zio.*
import zio.json.*
import zio.stream.{ZStream, ZPipeline}
import scala.io.StdIn

trait MessageHandler[I <:MessageBody : JsonDecoder]:
  def handle(message: Message[I]): Task[Unit]

trait MessageOut[O <:MessageBody : JsonEncoder]:
  def send(message: Message[O]): Task[Unit] = 
    printLine(message.toJson)

trait Debugger:
  def debugMessage(line: String): Task[Unit] = 
    printLineError(line)

trait MaelstromNode[I <:MessageBody : JsonDecoder, O <:MessageBody : JsonEncoder] extends MessageOut[O], MessageHandler[I], Debugger, ZIOAppDefault:
  def run = 
    ZStream
      .fromInputStream(java.lang.System.in)
      .via(ZPipeline.utfDecode)
      .takeWhile(line => line.trim != "" && line.trim != "q" && line.trim != "quit")
      .mapZIO(s => 
        ZIO.fromEither(JsonDecoder[Message[I]].decodeJson(s))
        .mapError(err => Exception(s"invalid json error: $err"))
      )
      .mapZIO(handle)
      .runCollect
      .debug