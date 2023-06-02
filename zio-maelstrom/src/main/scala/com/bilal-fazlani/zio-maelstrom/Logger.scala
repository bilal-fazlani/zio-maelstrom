package com.bilalfazlani.zioMaelstrom

import zio.*
import zio.Console.*

trait Logger:

  def debug(line: String): UIO[Unit]
  def info(line: String): UIO[Unit]
  def error(line: String): UIO[Unit]

  private[zioMaelstrom] def logInMessage(message: String): UIO[Unit]

private[zioMaelstrom] object Logger:
  val active: ZLayer[Settings, Nothing, Logger]   = ZLayer.fromFunction(LoggerLive.apply)
  val disabled: ZLayer[Settings, Nothing, Logger] = ZLayer.succeed(DisabledLogger)

  def debug(line: String): URIO[Logger, Unit] = ZIO.serviceWithZIO[Logger](_.info(line))
  def info(line: String): URIO[Logger, Unit]  = ZIO.serviceWithZIO[Logger](_.info(line))
  def error(line: String): URIO[Logger, Unit] = ZIO.serviceWithZIO[Logger](_.error(line))

private case class LoggerLive(settings: Settings) extends Logger:
  import com.bilalfazlani.rainbowcli.*
  given colorContext: ColorContext = ColorContext(settings.logFormat == LogFormat.Colored)

  def debug(line: String): UIO[Unit] = ZIO.when(settings.logLevel >= NodeLogLevel.Debug)(printLineError(line).orDie).unit

  def info(line: String): UIO[Unit] = ZIO.when(settings.logLevel >= NodeLogLevel.Info)(printLineError(line.yellow).orDie).unit

  def error(line: String): UIO[Unit] = ZIO.when(settings.logLevel >= NodeLogLevel.Error)(printLineError(line.red).orDie).unit

  def logInMessage(message: String): UIO[Unit] = ZIO.when(settings.logLevel >= NodeLogLevel.Debug)(printLineError(message.blue.onGreen.bold).orDie).unit

private object DisabledLogger extends Logger:
  def debug(line: String): UIO[Unit]            = ZIO.unit
  def info(line: String): UIO[Unit]             = ZIO.unit
  def error(line: String): UIO[Unit]            = ZIO.unit
  def logInMessage(message: String): UIO[Unit]  = ZIO.unit
