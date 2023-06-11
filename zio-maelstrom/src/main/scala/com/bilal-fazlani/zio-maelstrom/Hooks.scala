package com.bilalfazlani.zioMaelstrom

import zio.*
import protocol.*
import zio.concurrent.ConcurrentMap

case class DuplicateCallbackAttempt(callbackId: CallbackId)

private[zioMaelstrom] trait Hooks:
  def awaitRemote(
      messageId: MessageId,
      remote: NodeId,
      timeout: Duration
  ): ZIO[Scope, AskError, GenericMessage]

  def complete(message: GenericMessage): ZIO[Any, Nothing, Unit]

private[zioMaelstrom] object Hooks:
  val live: ZLayer[Logger, Nothing, HooksLive] = {
    val hooks = ZLayer.fromZIO(ConcurrentMap.empty[CallbackId, Promise[AskError, GenericMessage]])
    hooks >>> ZLayer.fromFunction(HooksLive.apply)
  }

private case class CallbackId(messageId: MessageId, remote: NodeId) {
  override def toString(): String = s"CallbackId(messageId=$messageId, remote=$remote)"
}

private case class HooksLive(hooks: ConcurrentMap[CallbackId, Promise[AskError, GenericMessage]], logger: Logger) extends Hooks:

  def awaitRemote(messageId: MessageId, remote: NodeId, timeout: Duration): ZIO[Scope, AskError, GenericMessage] =
    for {
      promise        <- Promise.make[AskError, GenericMessage]
      _              <- suspend(messageId, remote, promise, timeout)
      genericMessage <- promise.await
    } yield genericMessage

  def complete(message: GenericMessage): ZIO[Any, Nothing, Unit] =
    // .get is used here because we know that the message is a reply
    val callbackId = CallbackId(message.inReplyTo.get, message.src)
    for {
      promise <- hooks.remove(callbackId)
      _ <- promise match {
        case Some(p) => p.succeed(message).unit
        case None    => logger.error(s"$callbackId not found in callback registry. This could be because of duplicate message ids")
      }
    } yield ()

  private def suspend(
      messageId: MessageId,
      remote: NodeId,
      promise: Promise[AskError, GenericMessage],
      messageTimeout: Duration
  ): URIO[Scope, Unit] =
    val callbackId = CallbackId(messageId, remote.nodeId)
    hooks.put(callbackId, promise).unit *>
      (timeout(callbackId, messageTimeout).delay(messageTimeout).forkScoped.unit)

  private def timeout(callbackId: CallbackId, timeout: Duration) =
    for {
      promise <- hooks.remove(callbackId)
      _ <- promise match {
        case Some(p) =>
          p.fail(Timeout(callbackId.messageId, callbackId.remote, timeout))
        case None =>
          logger.error(s"$callbackId not found in callback registry. This would have happened because call was completed before the timeout")
      }
    } yield ()
