package com.bilalfazlani.zioMaelstrom

import zio.*
import zio.concurrent.ConcurrentMap

private case class TimedPromise(promise: Promise[AskError, GenericMessage], startTime: Long) {
  def gap: ZIO[Any, Nothing, Duration] = for endTime <- Clock.instant.map(_.toEpochMilli())
  yield Duration.fromMillis(endTime - startTime)
}
private object TimedPromise:
  def make: ZIO[Any, Nothing, TimedPromise] = for
    promise   <- Promise.make[AskError, GenericMessage]
    startTime <- Clock.instant.map(_.toEpochMilli())
  yield TimedPromise(promise, startTime)

private[zioMaelstrom] trait CallbackRegistry:
  def awaitCallback(
      messageId: MessageId,
      remote: NodeId,
      timeout: Duration
  ): ZIO[Scope, AskError, GenericMessage]

  def completeCallback(message: GenericMessage): ZIO[Any, Nothing, Unit]

  def getState: ZIO[Any, Nothing, Map[CallbackId, TimedPromise]]

private[zioMaelstrom] object CallbackRegistry:
  val live: ZLayer[Logger, Nothing, CallbackRegistryLive] = {
    val callbackRegistry =
      ZLayer.fromZIO(ConcurrentMap.empty[CallbackId, TimedPromise])
    callbackRegistry >>> ZLayer.fromFunction(CallbackRegistryLive.apply)
  }

private case class CallbackId(messageId: MessageId, remote: NodeId) {
  override def toString: String = s"CallbackId(messageId=$messageId, remote=$remote)"
}

private case class CallbackRegistryLive(
    callbackRegistry: ConcurrentMap[CallbackId, TimedPromise],
    logger: Logger
) extends CallbackRegistry:

  def awaitCallback(
      messageId: MessageId,
      remote: NodeId,
      timeout: Duration
  ): ZIO[Scope, AskError, GenericMessage] =
    for {
      promise        <- TimedPromise.make
      _              <- suspend(messageId, remote, promise, timeout)
      genericMessage <- promise.promise.await
    } yield genericMessage

  def getState = callbackRegistry.toList.map(_.toMap)

  def completeCallback(message: GenericMessage): ZIO[Any, Nothing, Unit] =
    // .get is used here because we know that the message is a reply
    val callbackId = CallbackId(message.inReplyTo.get, message.src)
    for {
      promise <- callbackRegistry.remove(callbackId)
      _ <-
        promise match {
          case Some(p) =>
            for
              gap <- p.gap
              _ <- logger.debug(
                s"callback received from ${callbackId.remote} for message ${callbackId.messageId} in ${gap.renderDetailed}"
              )
              _ <- p.promise.succeed(message)
            yield ()
          case None =>
            logger.debug(
              s"$callbackId not found in callback registry. This could be be because request was timed out or interrupted"
            )
        }
    } yield ()

  private def suspend(
      messageId: MessageId,
      remote: NodeId,
      promise: TimedPromise,
      messageTimeout: Duration
  ): URIO[Scope, Unit] =
    val callbackId = CallbackId(messageId, remote.nodeId)
    for
      _ <- ZIO.addFinalizerExit(exit => discardCallback(exit, callbackId)).unit
      _ <- callbackRegistry.put(callbackId, promise).unit
      _ <- timeout(callbackId, messageTimeout).delay(messageTimeout).forkScoped.unit
    yield ()

  private def discardCallback(
      exit: Exit[Any, Any],
      callbackId: CallbackId
  ): ZIO[Any, Nothing, Unit] =
    ZIO.when(exit.isInterrupted)(callbackRegistry.remove(callbackId)).unit

  private def timeout(callbackId: CallbackId, timeout: Duration) =
    for {
      promise <- callbackRegistry.remove(callbackId)
      _ <-
        promise match {
          case Some(p) =>
            for
              gap <- p.gap
              _ <- logger.warn(
                s"callback timed out for message ${callbackId.messageId} from ${callbackId.remote} after ${gap.renderDetailed}"
              )
              _ <- p.promise.fail(Timeout(callbackId.messageId, callbackId.remote, timeout))
            yield ()
          case None =>
            logger.warn(
              s"$callbackId not found in callback registry. This could be due to a bug in the library"
            )
        }
    } yield ()
