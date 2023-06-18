package com.bilalfazlani.zioMaelstrom

import zio.*
import scala.concurrent.duration.{Duration => ScalaDuration}

private[zioMaelstrom] case class Sleep(duration: Duration)

private case class InvalidSleepDuration(str: String)

private[zioMaelstrom] object Sleep:

  private def parseDuration(durationStr: String): Either[InvalidSleepDuration, Sleep] = for {
    duration <- durationStr match {
      case ScalaDuration((drn, unit)) => Right(Duration(drn, unit))
      case _                          => Left(InvalidSleepDuration(durationStr))
    }
  } yield Sleep(duration)

  /** returns the duration string if it is a sleep command
    *
    * @param str
    *   input string
    * @return
    *   duration string if it is a sleep command
    */
  def unapply(str: String): Option[String] = for {
    strMatch    <- "sleep\\s*(\\d+\\w)$".r.findFirstMatchIn(str)
    durationStr <- strMatch.subgroups.headOption
  } yield durationStr

  def conditionally(logger: Logger, duration: String): ZIO[Any, Nothing, Unit] =
    parseDuration(duration).fold(
      err => ZIO.die(Throwable(err.toString)),
      sleep =>
        logger.info(s"input paused for ${sleep.duration.render}") *> ZIO.sleep(sleep.duration) *>
          logger.info(s"input resumed")
    )
