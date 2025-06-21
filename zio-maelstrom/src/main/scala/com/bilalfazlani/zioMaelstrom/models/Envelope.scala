package com.bilalfazlani.zioMaelstrom.models

import com.bilalfazlani.zioMaelstrom.NodeId
import com.bilalfazlani.zioMaelstrom.MessageId

private[zioMaelstrom] case class Envelope[A](
    src: NodeId,  // who sent the message
    dest: NodeId, // you (self)
    msgId: Option[MessageId],
    inReplyTo: Option[MessageId],
    body: A
)
