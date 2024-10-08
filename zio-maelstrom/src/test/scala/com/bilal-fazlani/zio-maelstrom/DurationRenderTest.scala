package com.bilalfazlani.zioMaelstrom

import zio.test.*
import zio.*

object TimeValueRenderingTest extends ZIOSpecDefault:
  val spec = suite("render time value")(
    test("render zero") {
      val value = TimeValue(0)
      assertTrue(value.toString == "0")
    },
    test("render one decimal place") {
      val value = TimeValue(1.5)
      assertTrue(value.toString == "1.5")
    },
    test("render int without decimal places") {
      val value = TimeValue(1)
      assertTrue(value.toString == "1")
    },
    test("render non-zero with trailing zero 2") {
      val value = TimeValue(1.004)
      assertTrue(value.toString == "1")
    },
    test("round down to two decimal places") {
      val value = TimeValue(1.234)
      assertTrue(value.toString == "1.23")
    },
    test("round up to two decimal places") {
      val value = TimeValue(1.236)
      assertTrue(value.toString == "1.24")
    }
  )

object TimeSegmentRenderingTest extends ZIOSpecDefault:
  val spec = suite("render time segment")(
    test("render singular unit") {
      val segment = TimeSegment(TimeValue(1), TimeUnit.Day)
      assertTrue(segment.toString == "1 day")
    },
    test("render plural units") {
      val segment = TimeSegment(TimeValue(3), TimeUnit.Hour)
      assertTrue(segment.toString == "3 hours")
    },
    test("render zero units") {
      val segment = TimeSegment(TimeValue(0), TimeUnit.Millisecond)
      assertTrue(segment.toString == "0 milliseconds")
    }
  )

object DurationRenderTests extends ZIOSpecDefault:
  def spec =
    suite("decimal duration render tests")(
      test("render zero") {
        val duration = Duration.Zero
        assertTrue(duration.renderDecimal == "0 milliseconds")
      },
      test("render millis") {
        val duration = 100.millis
        assertTrue(duration.renderDecimal == "100 milliseconds")
      },
      test("render seconds") {
        val duration = 1.second + 100.millis
        assertTrue(duration.renderDecimal == "1.1 seconds")
      },
      test("render 1 minute") {
        val duration = 1.minute
        assertTrue(duration.renderDecimal == "1 minute")
      },
      test("render minutes") {
        val duration = 1.minute + 10.seconds
        assertTrue(duration.renderDecimal == "1.17 minutes")
      },
      test("render more minutes") {
        val duration = 1.minute + 50.seconds
        assertTrue(duration.renderDecimal == "1.83 minutes")
      },
      test("render hours") {
        val duration = 2.hours + 30.minutes
        assertTrue(duration.renderDecimal == "2.5 hours")
      },
      test("round to next hour") {
        val duration = 22.hours + 59.minutes + 59.seconds
        assertTrue(duration.renderDecimal == "23 hours")
      },
      test("render days") {
        val duration = 2.days + 1.hour + 1.minute
        assertTrue(duration.renderDecimal == "2.04 days")
      },
      test("render more days") {
        val duration = 500.days + 23.hours + 50.minutes
        assertTrue(duration.renderDecimal == "500.99 days")
      },
      test("round to next day") {
        val duration = 500.days + 23.hours + 59.minutes + 59.seconds + 999.millis
        assertTrue(duration.renderDecimal == "501 days")
      },
      test("minute should upgrade to next hour when rounding up") {
        val duration = 59.minutes + 59.seconds + 999.millis
        assertTrue(duration.renderDecimal == "1 hour")
      },
      test("minutes should upgrade to plural hours when rounding up") {
        val duration = 119.minutes + 59.seconds + 999.millis
        assertTrue(duration.renderDecimal == "2 hours")
      },
      // round down tests
      test("round down to 1 hour") {
        val duration = 1.hour + 100.millis
        assertTrue(duration.renderDecimal == "1 hour")
      },
      test("round down to 2 hours") {
        val duration = 2.hour + 100.millis
        assertTrue(duration.renderDecimal == "2 hours")
      },
      test("round down to 1 day") {
        val duration = 1.day + 100.millis
        assertTrue(duration.renderDecimal == "1 day")
      },
      test("round down to plural seconds") {
        val duration = 2.second + 1.millis
        assertTrue(duration.renderDecimal == "2 seconds")
      },
      test("round down to 1 minute") {
        val duration = 1.minute + 1.millis
        assertTrue(duration.renderDecimal == "1 minute")
      }
    )
