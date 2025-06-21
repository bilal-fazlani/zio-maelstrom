package com.bilalfazlani.zioMaelstrom
package testkit

import zio.*
import zio.json.{JsonEncoder, EncoderOps}
import zio.test.ZIOSpecDefault
import models.Body

trait MaelstromSpec extends ZIOSpecDefault {
  private def isCI = sys.env.get("CI").contains("true")

  override val bootstrap =
    val logLevel = if isCI then LogLevel.Info else LogLevel.Debug
    zio.test.testEnvironment ++ Runtime.removeDefaultLoggers ++ ZIOMaelstromLogger.install(
      LogFormat.Colored,
      logLevel
    )

  def testRuntime(
      settings: Settings,
      context: Context
  ): ZLayer[Any, Nothing, MaelstromTestRuntime] =
    ZLayer.make[MaelstromTestRuntime](
      // pure layers
      ZLayer.succeed(settings),
      Scope.default,
      MessageSender.live,
      RequestHandler.live,
      ZLayer(Queue.unbounded[Message[Any]]),
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

  def getNextMessage: ZIO[MaelstromTestRuntime, Nothing, Message[Any]] =
    ZIO.serviceWithZIO[Queue[Message[Any]]](_.take)

  def getNextMessageV2[A: JsonEncoder]: ZIO[MaelstromTestRuntime, Nothing, Message[Body[A]]] =
    ZIO.serviceWithZIO[Queue[Message[Any]]](_.take).map(_.asInstanceOf[Message[Body[A]]])

  def getCallbackState
      : ZIO[MaelstromTestRuntime, Nothing, Map[CallbackId, Promise[AskError, GenericMessage]]] =
    ZIO.serviceWithZIO[CallbackRegistry](_.getState.map(_.view.mapValues(_.promise).toMap))
}

type MaelstromTestRuntime = MaelstromRuntime & Queue[Message[Any]] & Queue[String] &
  CallbackRegistry
