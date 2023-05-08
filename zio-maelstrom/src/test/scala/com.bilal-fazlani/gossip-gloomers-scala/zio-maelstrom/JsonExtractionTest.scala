package com.bilalfazlani.gossipGloomersScala.zioMaelstrom

import zio.test.*

object JsonExtractionTest extends ZIOSpecDefault {
  val spec = suite("JsonExtractionTest")(
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
    test(
      "Use a previosly provided state to combine two complete json objects"
    ) {
      val input1 = """{"name": "john"}"""
      val output1 = JsonExtraction.extract(input1)
      val input2 = """{"name": "doe"}"""
      val output2 = JsonExtraction.extract(output1, input2)
      assertTrue(
        output2 ==
          NonEscapingState(
            inProgres = "",
            jsonObjects =
              Seq(Json("""{"name": "john"}"""), Json("""{"name": "doe"}""")),
            openCount = 0,
            closeCount = 0
          )
      )
    },
    test(
      "Use a previosly provided state to combine two incomplete json objects with remainder and escape"
    ) {
      val input1 = """{"name": "joh"""
      val output1 = JsonExtraction.extract(input1)
      val input2 = """n"}{"name": "doe"}{"name": \"""
      val output2 = JsonExtraction.extract(output1, input2)
      assertTrue(
        output2 ==
          EscapingState(
            inProgres = """{"name": \""",
            jsonObjects =
              Seq(Json("""{"name": "john"}"""), Json("""{"name": "doe"}""")),
            openCount = 1,
            closeCount = 0
          )
      )
    },
    test(
      "there are spaces between json objects"
    ) {
      val input = """{"name": "john"} {"name": "doe"}"""
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
    test(
      "there are new lines between json objects"
    ) {
      val input = """{"name": "john"}
                    |{"name": "doe"}""".stripMargin
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
    }
  )
}
