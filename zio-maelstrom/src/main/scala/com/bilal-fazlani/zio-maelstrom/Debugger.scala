package com.bilalfazlani.zioMaelstrom

import zio.*
import zio.Console.*

trait Debugger:
  def debugMessage(line: String): UIO[Unit]
  def errorMessage(line: String): UIO[Unit]

object Debugger:
  val live: ZLayer[Settings, Nothing, Debugger] = ZLayer.fromFunction(DebuggerLive.apply)

case class DebuggerLive(settings: Settings) extends Debugger:
  import com.bilalfazlani.rainbowcli.*
  given colorContext: ColorContext = ColorContext(settings.enableColoredOutput)
  def debugMessage(line: String): UIO[Unit] = printLineError(line.yellow).orDie

  def errorMessage(line: String): UIO[Unit] = printLineError(line.red).orDie
