package com.bilalfazlani.zioMaelstrom

import zio.*
import zio.Console.*

private[zioMaelstrom] trait Logger:
  def debug(line: => String): UIO[Unit]
  def info(line: => String): UIO[Unit]
  def error(line: => String): UIO[Unit]

private[zioMaelstrom] object Logger:
  val live = ZLayer.fromFunction { (settings: Settings) =>
    if settings.logLevel == NodeLogLevel.Disabled then DisabledLogger else LoggerLive(settings)
  }

  def debug(line: String): URIO[Logger, Unit] = ZIO.serviceWithZIO[Logger](_.debug(line))
  def info(line: String): URIO[Logger, Unit]  = ZIO.serviceWithZIO[Logger](_.info(line))
  def error(line: String): URIO[Logger, Unit] = ZIO.serviceWithZIO[Logger](_.error(line))

private case class LoggerLive(settings: Settings) extends Logger:
  import zio.internal.ansi.AnsiStringOps

  def debug(line: => String): UIO[Unit] = ZIO
    .when(settings.logLevel <= NodeLogLevel.Debug)(printLineError(line.faint).orDie).unit

  def info(line: => String): UIO[Unit] = ZIO
    .when(settings.logLevel <= NodeLogLevel.Info)(printLineError(line.yellow).orDie).unit

  def error(line: => String): UIO[Unit] = ZIO
    .when(settings.logLevel <= NodeLogLevel.Error)(printLineError(line.red).orDie).unit

private object PlainLogger extends Logger:
  def debug(line: => String): UIO[Unit] = printLineError(line).orDie.unit
  def info(line: => String): UIO[Unit]  = printLineError(line).orDie.unit
  def error(line: => String): UIO[Unit] = printLineError(line).orDie.unit

private object DisabledLogger extends Logger:
  def debug(line: => String): UIO[Unit] = ZIO.unit
  def info(line: => String): UIO[Unit]  = ZIO.unit
  def error(line: => String): UIO[Unit] = ZIO.unit
