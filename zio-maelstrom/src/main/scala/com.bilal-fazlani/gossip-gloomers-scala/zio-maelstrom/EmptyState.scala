package com.bilalfazlani.gossipGloomersScala
package zioMaelstrom

trait EmptyState[+S]:
  val emptyValue: S

object EmptyState {

  def apply[S](using z: EmptyState[S]): S = z.emptyValue

  given EmptyState[Unit] = new EmptyState[Unit] {
    val emptyValue = ()
  }

  given EmptyState[Int] = new EmptyState[Int] {
    val emptyValue = 0
  }

  given EmptyState[Long] = new EmptyState[Long] {
    val emptyValue = 0L
  }

  given EmptyState[Float] = new EmptyState[Float] {
    val emptyValue = 0.0f
  }

  given EmptyState[Double] = new EmptyState[Double] {
    val emptyValue = 0.0
  }

  given EmptyState[Boolean] = new EmptyState[Boolean] {
    val emptyValue = false
  }

  given EmptyState[String] = new EmptyState[String] {
    val emptyValue = ""
  }

  given [A]: EmptyState[Option[A]] = new EmptyState[Option[A]] {
    val emptyValue = None
  }

  given [A]: EmptyState[Seq[A]] = new EmptyState[Seq[A]] {
    val emptyValue = Seq.empty
  }
}
