package com.bilalfazlani.zioMaelstrom

import zio.{Duration, durationInt}

/** Settings for the node
  *
  * @param concurrency
  *   Concurrency level for processing messages. Default is 1024. This means 1024 request messages
  *   (receive api) + 1024 response messages (ask api) = 2048 messages can be processed in parallel.
  * @param defaultAskTimeout
  *   Default duration an ask operation waits before failing with a Timeout error
  */
private[zioMaelstrom] case class Settings(
    concurrency: Int = 1024,
    defaultAskTimeout: Duration = 100.millis
)
