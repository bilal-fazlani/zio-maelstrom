package com.bilalfazlani.gossipGloomersScala
package zioMaelstrom

import protocol.*
import zio.json.JsonDecoder
import zio.*

type MaelstromApp[I <: MessageBody] = MaelstromAppR[Any, I]

object MaelstromApp {
  def collectMessages[I <: MessageBody: JsonDecoder](f: Message[I] => ZIO[Context & MessageSender & Debugger, Throwable, Unit]) =
    new MaelstromApp[I] {
      def handleMessage(message: Message[I]) = f(message)
    }
}

trait MaelstromAppR[R: Tag, I <: MessageBody: JsonDecoder] {
  def handleMessage(message: Message[I]): ZIO[Context & MessageSender & Debugger & R, Throwable, Unit]
}

object MaelstromAppR {
  def collectMessages[R: Tag, I <: MessageBody: JsonDecoder](f: Message[I] => ZIO[Context & MessageSender & Debugger & R, Throwable, Unit]) =
    new MaelstromAppR[R, I] {
      def handleMessage(message: Message[I]) = f(message)
    }
}



