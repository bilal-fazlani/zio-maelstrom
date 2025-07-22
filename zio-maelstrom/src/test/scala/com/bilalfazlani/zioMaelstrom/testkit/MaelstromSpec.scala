package com.bilalfazlani.zioMaelstrom
package testkit

import zio.*
import zio.json.{EncoderOps, JsonEncoder}
import zio.test.ZIOSpecDefault
import models.Body
import com.bilalfazlani.zioMaelstrom.models.MsgName

trait MaelstromSpec extends ZIOSpecDefault {
  private def isCI = sys.env.get("CI").contains("true")
  
  val logLevel = LogLevel.Error //if isCI then LogLevel.Info else LogLevel.Debug

  override val bootstrap =
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
      MessageIdStore.stub, // FAKE

      // Fake Services
      KvFake.linKv,
      KvFake.seqKv,
      KvFake.lwwKv,
      LinTsoFake.make,

      // effectful layers
      Initialisation.fake(context), // FAKE
      ResponseHandler.start
    )

  def testRuntime(
        settings: Settings,
        inputStream: ZLayer[Any, Nothing, InputStream],
        inputQueue: ZLayer[Any, Nothing, Queue[String]]
    ): ZLayer[Any, Nothing, MaelstromTestRuntime] =
      ZLayer.make[MaelstromTestRuntime](
        // pure layers
        ZLayer.succeed(settings),
        Scope.default,
        MessageSender.live,
        RequestHandler.live,
        ZLayer(Queue.unbounded[Message[Any]]),
        inputQueue,
        OutputChannel.queue, // FAKE
        inputStream,   // FAKE
        InputChannel.live,
        CallbackRegistry.live,
        MessageIdStore.stub, // FAKE

        // Fake Services
        KvFake.linKv,
        KvFake.seqKv,
        KvFake.lwwKv,
        LinTsoFake.make,

        // effectful layers
        Initialisation.run,
        ResponseHandler.start
      )

  def inputRawJson(json: String) =
    for {
      queue <- ZIO.service[Queue[String]]
      _ <- queue.offer(json)
    } yield ()

  def inputSend[A: {JsonEncoder, MsgName}](in: A, from: NodeId) =
    for {
      queue <- ZIO.service[Queue[String]]
      init <- ZIO.service[Initialisation]
      body = Body(MsgName[A], in, None, None)
      _ <- queue.offer(Message(from, init.context.me, body).toJson)
    } yield ()

  def inputSend[A: JsonEncoder](body: Body[A], from: NodeId) =
    for {
      queue <- ZIO.service[Queue[String]]
      init  <- ZIO.service[Initialisation]
      _ <- queue.offer(Message(from, init.context.me, body).toJson)
    } yield ()

  def inputAsk[A: {JsonEncoder, MsgName}](in: A, from: NodeId, messageId: MessageId) =
    for {
      queue <- ZIO.service[Queue[String]]
      init  <- ZIO.service[Initialisation]
      body = Body(MsgName[A], in, Some(messageId), None)
      _ <- queue.offer(Message(from, init.context.me, body).toJson)
    } yield ()

  def inputReply[A: {JsonEncoder, MsgName}](in: A, from: NodeId, inReplyTo: MessageId) =
    for {
      queue <- ZIO.service[Queue[String]]
      init  <- ZIO.service[Initialisation]
      body = Body(MsgName[A], in, None, Some(inReplyTo))
      _ <- queue.offer(Message(from, init.context.me, body).toJson)
    } yield ()

  def setNextMessageId(next: MessageId) =
    ZIO.serviceWithZIO[MessageIdStore](_.asInstanceOf[MessageIdStoreStub].setNext(next))

  def getNextMessage[A]: ZIO[MaelstromTestRuntime, Nothing, Message[A]] =
    ZIO.serviceWithZIO[Queue[Message[Any]]](_.take).map(_.asInstanceOf[Message[A]])

  def getCallbackState
      : ZIO[MaelstromTestRuntime, Nothing, Map[CallbackId, Promise[AskError, GenericMessage]]] =
    ZIO.serviceWithZIO[CallbackRegistry](_.getState.map(_.view.mapValues(_.promise).toMap))
}

type MaelstromTestRuntime = MaelstromRuntime & Queue[Message[Any]] & Queue[String] &
  CallbackRegistry
