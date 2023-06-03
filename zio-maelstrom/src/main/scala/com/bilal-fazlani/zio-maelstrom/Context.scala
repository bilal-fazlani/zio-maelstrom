package com.bilalfazlani.zioMaelstrom

import protocol.*

private[zioMaelstrom] case class Context(me: NodeId, others: Seq[NodeId])

private[zioMaelstrom] object Context:
  def apply(init: Message[MaelstromInit]): Context =
    Context(init.body.node_id, init.body.node_ids)
