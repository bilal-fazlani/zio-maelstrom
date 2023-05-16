package com.bilalfazlani.gossipGloomersScala
package zioMaelstrom

import protocol.*

case class Context(me: NodeId, others: Seq[NodeId])
object Context:
  def apply(init: Message[MaelstromInit]): Context =
    Context(init.body.node_id, init.body.node_ids)