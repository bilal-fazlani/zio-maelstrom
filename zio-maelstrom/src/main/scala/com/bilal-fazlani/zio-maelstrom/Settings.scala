package com.bilalfazlani.zioMaelstrom

/** Settings for the node
  * @param concurrency
  *   Concurrency level for processing messages. Default is 1024. This means 1024 request messages
  *   (receive api) + 1024 response messages (ask api) = 2048 messages can be processed in parallel.
  */
private[zioMaelstrom] case class Settings(
    concurrency: Int = 1024
)
