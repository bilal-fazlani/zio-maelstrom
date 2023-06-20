package com.bilalfazlani.zioMaelstrom

import zio.*
import protocol.*
import zio.json.{JsonEncoder, EncoderOps}

type TestRuntime = MaelstromRuntime & Queue[Message[Sendable]] & Queue[String] & Hooks

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
      ZLayer.fromZIO(Queue.unbounded[Message[Sendable]]),
      ZLayer.fromZIO(Queue.unbounded[String]),
      OutputChannel.queue,
      InputStream.stream,
      InputChannel.live,
      Hooks.live,

      // effectful layers
      Initialisation.fake(context),
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
    ZIO.serviceWithZIO[Hooks](_.getState)