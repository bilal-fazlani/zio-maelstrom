package com.bilalfazlani.zioMaelstrom

import zio.*
import zio.Console.*

trait Logger:

  def info(line: String): UIO[Unit]
  def error(line: String): UIO[Unit]

private[zioMaelstrom] object Logger:
  val active: ZLayer[Settings, Nothing, Logger]   = ZLayer.fromFunction(LoggerLive.apply)
  val disabled: ZLayer[Settings, Nothing, Logger] = ZLayer.succeed(DisabledLogger)

  def info(line: String): URIO[Logger, Unit]  = ZIO.serviceWithZIO[Logger](_.info(line))
  def error(line: String): URIO[Logger, Unit] = ZIO.serviceWithZIO[Logger](_.error(line))

private case class LoggerLive(settings: Settings) extends Logger:
  import com.bilalfazlani.rainbowcli.*
  given colorContext: ColorContext = ColorContext(settings.logFormat == LogFormat.Colored)

  def info(line: String): UIO[Unit] = ZIO.when(settings.logLevel <= NodeLogLevel.Info)(printLineError(line.yellow).orDie).unit

  def error(line: String): UIO[Unit] = ZIO.when(settings.logLevel <= NodeLogLevel.Error)(printLineError(line.red).orDie).unit

private object DisabledLogger extends Logger:
  def info(line: String): UIO[Unit]            = ZIO.unit
  def error(line: String): UIO[Unit]           = ZIO.unit
