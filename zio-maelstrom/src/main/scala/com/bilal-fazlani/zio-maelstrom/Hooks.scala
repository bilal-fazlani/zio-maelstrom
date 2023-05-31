package com.bilalfazlani.zioMaelstrom

import zio.*
import protocol.*
import zio.concurrent.ConcurrentMap
import zio.json.JsonEncoder

opaque type Hooks = ConcurrentMap[MessageCorrelation, Promise[ResponseError, Message[?]]]

object Hooks:
  val apply: UIO[Hooks] = ConcurrentMap.empty[MessageId, Promise[ResponseError, Message[?]]].asInstanceOf
  val live: ZLayer[Any, Nothing, Hooks] = ZLayer.fromZIO(apply)

  def awaitMessage[A <: MessageWithReply: JsonEncoder](
      messageId: MessageId,
      from: NodeId,
      timeout: Duration
  ): ZIO[Hooks, ResponseError, Message[A]] =
    ZIO.serviceWithZIO[Hooks](_.awaitMessage(messageId, from, timeout))

  def add[I <: MessageWithReply](
      messageId: MessageId,
      remote: NodeId,
      promise: Promise[ResponseError, Message[I]],
      messageTimeout: Duration
  ): ZIO[Hooks, Nothing, Unit] =
    ZIO.serviceWithZIO[Hooks](_.add(messageId, remote, promise, messageTimeout))

  def success[I <: MessageWithReply](message: Message[I]): ZIO[Hooks, Nothing, Option[Message[I]]] =
    ZIO.serviceWithZIO[Hooks](_.success(message))

private case class MessageCorrelation(messageId: MessageId, remote: NodeId)

extension (hooks: Hooks)
  def awaitMessage[A <: MessageWithReply: JsonEncoder](
      messageId: MessageId,
      from: NodeId,
      timeout: Duration
  ): IO[ResponseError, Message[A]] =
    for {
      promise <- Promise.make[ResponseError, Message[A]]
      _ <- hooks.add(messageId, from, promise, timeout)
      responseMessage <- promise.await
    } yield responseMessage

  def add[I <: MessageWithReply](
      messageId: MessageId,
      remote: NodeId,
      promise: Promise[ResponseError, Message[I]],
      messageTimeout: Duration
  ): UIO[Unit] =
    val correlation = MessageCorrelation(messageId, remote)
    hooks.put(correlation, promise.asInstanceOf).unit *> (timeout(correlation, messageTimeout).delay(messageTimeout))

  def success[I <: MessageWithReply](message: Message[I]): UIO[Option[Message[I]]] =
    val correlation = MessageCorrelation(message.body.in_reply_to, message.source)
    for {
      promise <- hooks.remove(correlation)
      reponseMaybe <- promise match {
        case Some(p) => p.succeed(message) as Some(message)
        case None    => ZIO.succeed(None)
      }
    } yield reponseMaybe

  private def timeout(correlation: MessageCorrelation, timeout: Duration) =
    for {
      promise <- hooks.remove(correlation)
      _ <- promise match {
        case Some(p) => p.fail(ResponseError.MessageTimeoutError(correlation.messageId, correlation.remote, timeout))
        case None    => ZIO.unit
      }
    } yield ()
