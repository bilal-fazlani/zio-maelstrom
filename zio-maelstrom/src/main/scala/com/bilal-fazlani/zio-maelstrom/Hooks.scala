package com.bilalfazlani.zioMaelstrom

import zio.*
import protocol.*
import zio.concurrent.ConcurrentMap

private[zioMaelstrom] trait Hooks:
  def awaitRemote(
      messageId: MessageId,
      remote: NodeId,
      timeout: Duration
  ): ZIO[Scope, ResponseError, GenericMessage]

  def complete(message: GenericMessage): ZIO[Logger, Nothing, Unit]

private[zioMaelstrom] object Hooks:
  val live: ZLayer[Any, Nothing, HooksLive] = {
    val hooks = ZLayer.fromZIO(ConcurrentMap.empty[MessageCorrelation, Promise[ResponseError, GenericMessage]])
    hooks >>> ZLayer.fromFunction(HooksLive.apply)
  }

private case class MessageCorrelation(messageId: MessageId, remote: NodeId)

private case class HooksLive(hooks: ConcurrentMap[MessageCorrelation, Promise[ResponseError, GenericMessage]]) extends Hooks:

  def awaitRemote(messageId: MessageId, remote: NodeId, timeout: Duration): ZIO[Scope, ResponseError, GenericMessage] =
    for {
      promise        <- Promise.make[ResponseError, GenericMessage]
      _              <- suspend(messageId, remote, promise, timeout)
      genericMessage <- promise.await
    } yield genericMessage

  def complete(message: GenericMessage): ZIO[Logger, Nothing, Unit] =
    // .get is used here because we know that the message is a reply
    val correlation = MessageCorrelation(message.inReplyTo.get, message.src)
    for {
      promise <- hooks.remove(correlation)
      _ <- promise match {
        case Some(p) => p.succeed(message).unit
        case None    => logError(s"Message $message not found in hooks")
      }
    } yield ()

  private def suspend(
      messageId: MessageId,
      remote: NodeId,
      promise: Promise[ResponseError, GenericMessage],
      messageTimeout: Duration
  ): URIO[Scope, Unit] =
    val correlation = MessageCorrelation(messageId, remote.nodeId)
    hooks.put(correlation, promise).unit *>
      (timeout(correlation, messageTimeout).delay(messageTimeout).forkScoped.unit)

  private def timeout(correlation: MessageCorrelation, timeout: Duration) =
    for {
      promise <- hooks.remove(correlation)
      _ <- promise match {
        case Some(p) => p.fail(Timeout(correlation.messageId, correlation.remote, timeout))
        case None    => ZIO.unit
      }
    } yield ()
