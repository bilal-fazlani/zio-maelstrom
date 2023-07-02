package com.bilalfazlani.zioMaelstrom

import protocol.*

case class Context(me: NodeId, others: Set[NodeId])

object Context:
  private[zioMaelstrom] def apply(init: Message[MaelstromInit]): Context =
    Context(init.body.node_id, init.body.node_ids - init.body.node_id)
