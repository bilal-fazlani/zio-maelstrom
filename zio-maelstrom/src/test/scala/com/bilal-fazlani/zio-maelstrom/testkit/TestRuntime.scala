package com.bilalfazlani.zioMaelstrom
package testkit

import zio.*
import zio.json.{JsonEncoder, EncoderOps}

type TestMaelstromRuntime = MaelstromRuntime & Queue[Message[Sendable]] & Queue[String] & CallbackRegistry

object TestMaelstromRuntime:
  def layer(
      settings: Settings,
      context: Context
  ): ZLayer[Any, Nothing, TestMaelstromRuntime] =
    ZLayer.make[TestMaelstromRuntime](
      // pure layers
      ZLayer.succeed(settings),
      Scope.default,
      MessageSender.live,
      RequestHandler.live,
      ZLayer(Queue.unbounded[Message[Sendable]]),
      ZLayer(Queue.unbounded[String]),
      OutputChannel.queue, // FAKE
      InputStream.queue,   // FAKE
      InputChannel.live,
      CallbackRegistry.live,
      MessageIdStore.live,

      // Services
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

  def getNextMessage: ZIO[TestMaelstromRuntime, Nothing, Message[Sendable]] =
    ZIO.serviceWithZIO[Queue[Message[Sendable]]](_.take)

  def getCallbackState
      : ZIO[TestMaelstromRuntime, Nothing, Map[CallbackId, Promise[AskError, GenericMessage]]] =
    ZIO.serviceWithZIO[CallbackRegistry](_.getState.map(_.mapValues(_.promise).toMap))
