package com.bilalfazlani.zioMaelstrom

import protocol.*
import zio.json.JsonDecoder
import zio.*

type MaelstromApp[I <: MessageBody] = MaelstromAppR[Any, I]

object MaelstromApp {
  def make[I <: MessageBody: JsonDecoder](f: Message[I] => ZIO[Context & MessageSender & Logger, Nothing, Unit]) =
    new MaelstromApp[I] {
      def handle(message: Message[I]) = f(message)
    }
}

trait MaelstromAppR[R: Tag, I <: MessageBody: JsonDecoder] {
  def handle(message: Message[I]): ZIO[Context & MessageSender & Logger & R, Nothing, Unit]
}

object MaelstromAppR {
  def make[R: Tag, I <: MessageBody: JsonDecoder](f: Message[I] => ZIO[Context & MessageSender & Logger & R, Nothing, Unit]) =
    new MaelstromAppR[R, I] {
      def handle(message: Message[I]) = f(message)
    }
}
