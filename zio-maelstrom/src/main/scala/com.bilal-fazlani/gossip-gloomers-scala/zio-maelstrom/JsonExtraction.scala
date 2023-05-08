package com.bilalfazlani.gossipGloomersScala.zioMaelstrom

case class Json(in: String) {
  val value = in.trim

  override def equals(x: Any): Boolean =
    x match {
      case j: Json => this.value == j.value
      case _       => false
    }
}

trait State:
  def open: State
  def close: State
  def take(char: Char): State
  def escpaeNext: State

  val jsonObjects: Seq[Json]

object State:
  val empty = NonEscapingState("", Seq.empty, 0, 0)

case class NonEscapingState(
    inProgres: String,
    jsonObjects: Seq[Json],
    openCount: Int,
    closeCount: Int
) extends State {
  lazy val open = copy(
    inProgres = inProgres + "{",
    openCount = openCount + 1
  )

  lazy val close =
    if openCount == closeCount + 1
    then
      NonEscapingState(
        inProgres = "",
        jsonObjects = jsonObjects :+ Json(inProgres + "}"),
        openCount = 0,
        closeCount = 0
      )
    else
      copy(
        inProgres = inProgres + "}",
        closeCount = closeCount + 1
      )

  def take(char: Char) = copy(
    inProgres = inProgres + char
  )

  lazy val escpaeNext = EscapingState(
    copy(
      inProgres = inProgres + '\\'
    )
  )
}

object NonEscapingState:
  def apply(prev: EscapingState) = new NonEscapingState(
    inProgres = prev.inProgres,
    jsonObjects = prev.jsonObjects,
    openCount = prev.openCount,
    closeCount = prev.closeCount
  )

case class EscapingState(
    inProgres: String,
    jsonObjects: Seq[Json],
    openCount: Int,
    closeCount: Int
) extends State {

  lazy val open = NonEscapingState(copy(inProgres = inProgres + "{"))
  lazy val close = NonEscapingState(copy(inProgres = inProgres + "}"))
  def take(char: Char) = NonEscapingState(copy(inProgres = inProgres + char))

  lazy val escpaeNext = copy(inProgres = inProgres + '\\')
}

object EscapingState:
  def apply(previous: NonEscapingState) = new EscapingState(
    inProgres = previous.inProgres,
    jsonObjects = previous.jsonObjects,
    openCount = previous.openCount,
    closeCount = previous.closeCount
  )

object JsonExtraction:
  def extract(input: String): State = extract(State.empty, input)

  def extract(prev: State, input: String): State = {
    input.foldLeft[State](prev)((state, char) => {
      char match {
        case '\\' => state.escpaeNext
        case '{'  => state.open
        case '}'  => state.close
        case x    => state.take(x)
      }
    })
  }
