package com.bilalfazlani.zioMaelstrom
package testkit

import zio.*
import zio.json.{JsonEncoder, EncoderOps}

type TestRuntime = MaelstromRuntime & Queue[Message[Sendable]] & Queue[String] & CallbackRegistry

object TestRuntime:
  def layer(
      settings: Settings,
      context: Context
  ): ZLayer[Any, Nothing, TestRuntime] =
    ZLayer.make[TestRuntime](
      // pure layers
      ZLayer.succeed(settings),
      Scope.default,
      MessageSender.live,
      Logger.live,
      RequestHandler.live,
      ZLayer(Queue.unbounded[Message[Sendable]]),
      ZLayer(Queue.unbounded[String]),
      OutputChannel.queue, // FAKE
      InputStream.queue,   // FAKE
      InputChannel.live,
      CallbackRegistry.live,
      MessageIdStore.live,

      // Fake Services
      KvFake.linKv,
      KvFake.seqKv,
      KvFake.lwwKv,
      LinTsoFake.make,

      // effectful layers
      Initialisation.fake(context), // FAKE
      ResponseHandler.start
    )

  def inputMessage[A: JsonEncoder](in: A, from: NodeId) =
    for {
      queue <- ZIO.service[Queue[String]]
      init  <- ZIO.service[Initialisation]
      _     <- queue.offer(Message(from, init.context.me, in).toJson)
    } yield ()

  def getNextMessage: ZIO[TestRuntime, Nothing, Message[Sendable]] =
    ZIO.serviceWithZIO[Queue[Message[Sendable]]](_.take)

  def getCallbackState
      : ZIO[TestRuntime, Nothing, Map[CallbackId, Promise[AskError, GenericMessage]]] =
    ZIO.serviceWithZIO[CallbackRegistry](_.getState.map(_.mapValues(_.promise).toMap))
