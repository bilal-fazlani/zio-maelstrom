package com.bilalfazlani.zioMaelstrom

import zio.*
import zio.json.*
import protocol.*

private[zioMaelstrom] case class KvRead[Key](key: Key, msg_id: MessageId, `type`: String = "read")
    extends NeedsReply,
      Sendable derives JsonEncoder

private[zioMaelstrom] case class KvReadOk[Value](
    in_reply_to: MessageId,
    value: Value
) extends Reply
    derives JsonDecoder

private[zioMaelstrom] case class KvWrite[Key, Value](
    key: Key,
    value: Value,
    msg_id: MessageId,
    `type`: String = "write"
) extends NeedsReply,
      Sendable
    derives JsonEncoder

private[zioMaelstrom] case class KvWriteOk(in_reply_to: MessageId) extends Reply derives JsonDecoder

private[zioMaelstrom] case class CompareAndSwap[Key, Value](
    key: Key,
    from: Value,
    to: Value,
    create_if_not_exists: Boolean,
    msg_id: MessageId,
    `type`: String = "cas"
) extends NeedsReply,
      Sendable
    derives JsonEncoder

private[zioMaelstrom] case class CompareAndSwapOk(in_reply_to: MessageId) extends Reply
    derives JsonDecoder
