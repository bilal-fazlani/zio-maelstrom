package com.bilalfazlani.gossipGloomersScala
package zioMaelstrom

import zio.Task
import zio.Console.*
import zio.ZLayer

trait Debugger:
  def debugMessage(line: String): Task[Unit]

object Debugger:
  val live: ZLayer[Any, Nothing, Debugger] = ZLayer.succeed(DebuggerLive)

case object DebuggerLive extends Debugger:
  def debugMessage(line: String): Task[Unit] = printLineError(line)