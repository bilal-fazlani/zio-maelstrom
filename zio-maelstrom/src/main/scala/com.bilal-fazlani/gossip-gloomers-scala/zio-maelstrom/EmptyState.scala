package com.bilalfazlani.gossipGloomersScala
package zioMaelstrom

trait EmptyState[+S]:
  def emptyValue: S

object EmptyState {

  def apply[S](using z: EmptyState[S]): S = z.emptyValue

  given EmptyState[Unit] = new EmptyState[Unit] {
    def emptyValue = ()
  }

  given EmptyState[Int] = new EmptyState[Int] {
    def emptyValue = 0
  }

  given EmptyState[Long] = new EmptyState[Long] {
    def emptyValue = 0L
  }

  given EmptyState[Float] = new EmptyState[Float] {
    def emptyValue = 0.0f
  }

  given EmptyState[Double] = new EmptyState[Double] {
    def emptyValue = 0.0
  }

  given EmptyState[Boolean] = new EmptyState[Boolean] {
    def emptyValue = false
  }

  given EmptyState[String] = new EmptyState[String] {
    def emptyValue = ""
  }

  given [A]: EmptyState[Option[A]] = new EmptyState[Option[A]] {
    def emptyValue = None
  }

  given [A]: EmptyState[Seq[A]] = new EmptyState[Seq[A]] {
    def emptyValue = Seq.empty
  }
}
