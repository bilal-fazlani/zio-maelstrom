package com.bilalfazlani.gossipGloomersScala
package zioMaelstrom

import protocol.*
import zio.json.JsonEncoder
import zio.Task
import zio.Console.printLine
import zio.json.EncoderOps
import zio.ZLayer

trait MessageTransport:
  def transport[A <: MessageBody : JsonEncoder](message: Message[A]): Task[Unit]

object MessageTransport:
  val live = ZLayer.fromFunction(MessageTransportLive.apply)

case class MessageTransportLive(debugger: Debugger) extends MessageTransport:
  def transport[A <: MessageBody: JsonEncoder](message: Message[A]): Task[Unit] = 
    printLine(message.toJson) *> debugger.debugMessage(s"sent message: ${message.body} to ${message.destination}")
