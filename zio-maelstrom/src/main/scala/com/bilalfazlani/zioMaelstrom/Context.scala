package com.bilalfazlani.zioMaelstrom

case class Context(me: NodeId, others: Set[NodeId])

object Context:
  private[zioMaelstrom] def apply(init: Message[MaelstromInit]): Context =
    Context(init.body.payload.node_id, init.body.payload.node_ids - init.body.payload.node_id)
