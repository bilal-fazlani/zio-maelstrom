package com.bilalfazlani.zioMaelstrom.services

import zio.*
import zio.json.*

private[zioMaelstrom] case class Read[Key](key: Key) derives JsonEncoder

private[zioMaelstrom] case class ReadOk[Value](
    value: Value
) derives JsonDecoder

private[zioMaelstrom] case class Write[Key, Value](
    key: Key,
    value: Value
) derives JsonEncoder

private[zioMaelstrom] case class WriteOk() derives JsonDecoder

private[zioMaelstrom] case class Cas[Key, Value](
    key: Key,
    from: Option[Value],
    to: Value,
    create_if_not_exists: Boolean
) derives JsonEncoder

private[zioMaelstrom] case class CasOk() derives JsonDecoder
