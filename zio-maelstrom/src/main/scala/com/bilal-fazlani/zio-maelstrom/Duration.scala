package com.bilalfazlani.zioMaelstrom

import zio.Duration
import zio.NonEmptyChunk
import java.text.DecimalFormat

private enum TimeUnit(val multiplier: Int):
  case Millisecond extends TimeUnit(1)
  case Second      extends TimeUnit(1000)
  case Minute      extends TimeUnit(60 * 1000)
  case Hour        extends TimeUnit(60 * 60 * 1000)
  case Day         extends TimeUnit(24 * 60 * 60 * 1000)

private case class TimeValue(value: Double):
  override def toString: String =
    val formatter = DecimalFormat("0.##")
    formatter.format(value)

private case class TimeSegment private (value: TimeValue, unit: TimeUnit):
  private[zioMaelstrom] val isEmpty: Boolean = value.value == 0

  override def toString: String =
    val unitRendered = unit.toString.toLowerCase
    this match {
      case TimeSegment(TimeValue(1), unit) =>
        s"1 $unitRendered"
      case TimeSegment(value, unit) =>
        s"$value ${unitRendered}s"
    }

private object TimeSegment:
  private[zioMaelstrom] val zero = TimeSegment(TimeValue(0), TimeUnit.Millisecond)

  private[zioMaelstrom] def apply(value: TimeValue, unit: TimeUnit): TimeSegment =
    (value, unit) match {
      case (TimeValue(millis), TimeUnit.Millisecond) if 1000 - millis < 0.01 =>
        new TimeSegment(TimeValue(1), TimeUnit.Second)
      case (TimeValue(seconds), TimeUnit.Second) if 60 - seconds < 0.01 =>
        new TimeSegment(TimeValue(1), TimeUnit.Minute)
      case (TimeValue(minutes), TimeUnit.Minute) if 60 - minutes < 0.01 =>
        new TimeSegment(TimeValue(1), TimeUnit.Hour)
      case (TimeValue(hours), TimeUnit.Hour) if 24 - hours < 0.01 =>
        new TimeSegment(TimeValue(1), TimeUnit.Day)
      case (TimeValue(hours), unit) =>
        val v = BigDecimal(value.value).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
        new TimeSegment(TimeValue(v), unit)
    }

extension (duration: Duration)
  private def nonZeroParts: NonEmptyChunk[TimeSegment] =
    val millis  = duration.toMillis
    val seconds = millis / 1000
    val minutes = seconds / 60
    val hours   = minutes / 60
    val days    = hours / 24

    val remainingMillis  = TimeValue(millis.toDouble % 1000)
    val remainingSeconds = TimeValue(seconds.toDouble % 60)
    val remainingMinutes = TimeValue(minutes.toDouble % 60)
    val remainingHours   = TimeValue(hours.toDouble % 24)
    val remainingDays    = TimeValue(days.toDouble)
    val parts = List(
      TimeSegment(remainingDays, TimeUnit.Day),
      TimeSegment(remainingHours, TimeUnit.Hour),
      TimeSegment(remainingMinutes, TimeUnit.Minute),
      TimeSegment(remainingSeconds, TimeUnit.Second),
      TimeSegment(remainingMillis, TimeUnit.Millisecond)
    )
    val nonEmpty = parts.filter(!_.isEmpty)

    nonEmpty match
      case head :: next => NonEmptyChunk(head, next*)
      case Nil          => NonEmptyChunk(TimeSegment.zero)

  private[zioMaelstrom] def renderDecimal: String = {
    val millis          = nonZeroParts.map(x => x.value.value * x.unit.multiplier).sum
    val firstUnit       = nonZeroParts.head.unit
    val firstMultiplier = firstUnit.multiplier
    TimeSegment(TimeValue(millis / firstMultiplier), firstUnit).toString
  }
