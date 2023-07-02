package com.bilalfazlani.zioMaelstrom

import zio.Duration

extension (duration: Duration)
  def renderDetailed =
    val millis  = duration.toMillis
    val seconds = millis / 1000
    val minutes = seconds / 60
    val hours   = minutes / 60
    val days    = hours / 24
    val years   = days / 365
    val months  = years / 12

    val remainingMillis  = millis  % 1000
    val remainingSeconds = seconds % 60
    val remainingMinutes = minutes % 60
    val remainingHours   = hours   % 24
    val remainingDays    = days    % 365
    val remainingMonths  = months  % 12

    val parts = Seq(
      (remainingMonths, "month"),
      (remainingDays, "day"),
      (remainingHours, "hour"),
      (remainingMinutes, "minute"),
      (remainingSeconds, "second"),
      (remainingMillis, "millisecond")
    )

    val nonZeroParts = parts.filter(_._1 > 0)

    if (nonZeroParts.isEmpty) {
      "0 milliseconds"
    } else {
      nonZeroParts
        .map { case (value, unit) =>
          s"$value ${unit}${if (value > 1) "s" else ""}"
        }
        .mkString(" ")
    }
