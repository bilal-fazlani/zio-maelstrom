package com.bilalfazlani.gossipGloomersScala.zioMaelstrom

import zio.test.*

object JsonFramingTest extends ZIOSpecDefault {
  val spec = suite("JsonFramingTest")(
    test("Extract sigle json from valid input") {
      val input = """{"name": "john"}"""
      val output = JsonExtraction.extract(input)
      assertTrue(
        output ==
          NonEscapingState(
            inProgres = "",
            jsonObjects = Seq(Json(input)),
            openCount = 0,
            closeCount = 0
          )
      )
    },
    test("Extract multiple json from valid input") {
      val input = """{"name": "john"}{"name": "doe"}"""
      val output = JsonExtraction.extract(input)
      assertTrue(
        output ==
          NonEscapingState(
            inProgres = "",
            jsonObjects =
              Seq(Json("""{"name": "john"}"""), Json("""{"name": "doe"}""")),
            openCount = 0,
            closeCount = 0
          )
      )
    },
    test("Extract json from valid input with remainder") {
      val input = """{"name": "john"}{"name": "doe"}{"name": """
      val output = JsonExtraction.extract(input)
      assertTrue(
        output ==
          NonEscapingState(
            inProgres = """{"name": """,
            jsonObjects = Seq(
              Json("""{"name": "john"}"""),
              Json("""{"name": "doe"}""")
            ),
            openCount = 1,
            closeCount = 0
          )
      )
    },
    test("Extract json from valid input with escape of open brace") {
      val input = """{"name": "a\{b"}"""
      val output = JsonExtraction.extract(input)
      assertTrue(
        output ==
          NonEscapingState(
            inProgres = "",
            jsonObjects = Seq(Json(input)),
            openCount = 0,
            closeCount = 0
          )
      )
    },
    test("Extract json from valid input with escape of close brace") {
      val input = """{"name": "a\}b"}"""
      val output = JsonExtraction.extract(input)
      assertTrue(
        output ==
          NonEscapingState(
            inProgres = "",
            jsonObjects = Seq(Json(input)),
            openCount = 0,
            closeCount = 0
          )
      )
    },
    test("Extract json from valid input with escape of escape") {
      val input = """{"name": "a\\"}"""
      val output = JsonExtraction.extract(input)
      assertTrue(
        output ==
          NonEscapingState(
            inProgres = "",
            jsonObjects = Seq(Json(input)),
            openCount = 0,
            closeCount = 0
          )
      )
    },
  )
}
