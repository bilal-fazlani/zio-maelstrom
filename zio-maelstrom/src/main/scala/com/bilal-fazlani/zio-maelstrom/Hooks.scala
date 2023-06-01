package com.bilalfazlani.zioMaelstrom

import zio.*
import protocol.*
import zio.concurrent.ConcurrentMap

trait Hooks:
  def awaitMessage(
      messageId: MessageId,
      remote: NodeId,
      timeout: Duration
  ): IO[ResponseError, GenericMessage]

  def complete(message: GenericMessage): ZIO[Any, Nothing, Unit]

object Hooks:
  val live: ZLayer[Any, Nothing, HooksLive] = {
    val hooks = ZLayer.fromZIO(ConcurrentMap.empty[MessageCorrelation, Promise[ResponseError, GenericMessage]])
    hooks >>> ZLayer.fromFunction(HooksLive.apply)
  }

private case class MessageCorrelation(messageId: MessageId, remote: NodeId)

case class HooksLive(hooks: ConcurrentMap[MessageCorrelation, Promise[ResponseError, GenericMessage]]) extends Hooks:

  def awaitMessage(messageId: MessageId, remote: NodeId, timeout: Duration): IO[ResponseError, GenericMessage] =
    for {
      promise <- Promise.make[ResponseError, GenericMessage]
      _ <- suspend(messageId, remote, promise, timeout)
      genericMessage <- promise.await
    } yield genericMessage

  def complete(message: GenericMessage): ZIO[Any, Nothing, Unit] =
    // .get is used here because we know that the message is a reply
    val correlation = MessageCorrelation(message.inReplyTo.get, message.src)
    for {
      promise <- hooks.remove(correlation)
      _ <- promise match {
        case Some(p) => p.succeed(message).unit
        case None    => ZIO.unit
      }
    } yield ()

  private def suspend[I <: MessageWithReply](
      messageId: MessageId,
      remote: NodeId,
      promise: Promise[ResponseError, GenericMessage],
      messageTimeout: Duration
  ): UIO[Unit] =
    val correlation = MessageCorrelation(messageId, remote.nodeId)
    hooks.put(correlation, promise.asInstanceOf).unit *> (timeout(correlation, messageTimeout).delay(messageTimeout))

  private def timeout(correlation: MessageCorrelation, timeout: Duration) =
    for {
      promise <- hooks.remove(correlation)
      _ <- promise match {
        case Some(p) => p.fail(ResponseError.MessageTimeoutError(correlation.messageId, correlation.remote, timeout))
        case None    => ZIO.unit
      }
    } yield ()
