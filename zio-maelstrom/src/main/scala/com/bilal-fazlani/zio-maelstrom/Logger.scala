package com.bilalfazlani.zioMaelstrom

import zio.*
import zio.Console.*

trait Logger:
  def info(line: String): UIO[Unit]
  def error(line: String): UIO[Unit]

object Logger:
  val live: ZLayer[Settings, Nothing, Logger] = ZLayer.fromFunction(LoggerLive.apply)

  def info(line: String): URIO[Logger, Unit] = ZIO.serviceWithZIO[Logger](_.info(line))
  def error(line: String): URIO[Logger, Unit] = ZIO.serviceWithZIO[Logger](_.error(line))

case class LoggerLive(settings: Settings) extends Logger:
  import com.bilalfazlani.rainbowcli.*
  given colorContext: ColorContext = ColorContext(settings.enableColoredOutput)

  def info(line: String): UIO[Unit] = printLineError(line).orDie

  def error(line: String): UIO[Unit] = printLineError(line.red).orDie
