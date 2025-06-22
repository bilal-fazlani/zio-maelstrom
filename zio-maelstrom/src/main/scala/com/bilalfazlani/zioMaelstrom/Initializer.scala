package com.bilalfazlani.zioMaelstrom

import zio.*
import zio.stream.{ZStream, ZSink}
import zio.json.JsonDecoder
import java.time.OffsetDateTime
import com.bilalfazlani.zioMaelstrom.models.Body

private[zioMaelstrom] type MessageStream = ZStream[Any, Nothing, GenericMessage]

private[zioMaelstrom] case class Inputs(responseStream: MessageStream, messageStream: MessageStream)

private[zioMaelstrom] case class Initialisation(context: Context, inputs: Inputs)

private[zioMaelstrom] object Initialisation:
  val run: ZLayer[Scope & InputChannel & OutputChannel, Nothing, Initialisation] = ZLayer
    .derive[InitializerLive]
    .flatMap(initializer => ZLayer(initializer.get.initialize))

  def fake(context: Context): ZLayer[InputChannel & Scope, Nothing, Initialisation] =
    ZLayer
      .derive[TestInitializer]
      .flatMap(x => ZLayer(x.get.initialize(context)))

private class InitializerLive(
    outputChannel: OutputChannel,
    inputChannel: InputChannel
):
  private val preInitMessages = for {
    initialTime <- Clock.currentDateTime
    _           <- ZIO.logDebug("node started. awaiting init message...")
    schedule = Schedule.exponential(5.seconds, 2)
    printFiber <- (ZIO.sleep(5.seconds) *> logInitDelayWarning(initialTime).repeat(schedule)).fork
  } yield printFiber

  private def logInitDelayWarning(initialTime: OffsetDateTime): ZIO[Any, Nothing, Unit] =
    for {
      currentTime <- Clock.currentDateTime
      gap = Duration.fromMillis(
        currentTime.toInstant.toEpochMilli - initialTime.toInstant.toEpochMilli
      )
      loggerf = gap match {
        case gap if gap.toSeconds <= 10 => ZIO.logInfo(_)
        case gap if gap.toSeconds < 60  => ZIO.logWarning(_)
        case _                          => ZIO.logError(_)
      }
      _ <- loggerf(s"init message not received in ${gap.renderDecimal}")
    } yield ()

  val initialize: ZIO[Scope, Nothing, Initialisation] =
    for
      warningFiber <- preInitMessages
      _            <- ZIO.addFinalizer(warningFiber.interrupt)
      inputs       <- inputChannel.partitionInputs
      init <- inputs.messageStream.peel(ZSink.head).flatMap {
        // happy case: found init message
        case (Some(genericMessage), remainder) =>
          val initMessage = JsonDecoder[Message[MaelstromInit]].fromJsonAST(genericMessage.raw)
          initMessage match
            // when decoded, send it to init handler
            case Right(initMessage) =>
              handleInit(initMessage) *> warningFiber.interrupt as
                Initialisation(Context(initMessage), Inputs(inputs.responseStream, remainder))

            // if decoding failed, send an error message to sender and log error
            // because this was promised to be a init message, but was not,
            // we will have to shut down the node
            case Left(error) =>
              handleInitDecodingError(genericMessage) *>
                ZIO.die(new Exception("init message decoding failed"))
        case (None, _) =>
          // if we don't have a some yet, it means we didn't get any init message
          // since we can't proceed without init message, its safe to fail the program
          ZIO.dieMessage("no init message received")
      }
    yield init

  private def handleInit(message: Message[MaelstromInit]) =
    val replyMessage: Message[MaelstromInitOk] = Message[MaelstromInitOk](
      message.destination,
      message.source,
      Body(
        "init_ok",
        MaelstromInitOk(),
        None,
        message.body.msg_id
      )
    )
    for {
      _ <- outputChannel.transport(replyMessage)
      _ <- ZIO.logInfo("initialised")
    } yield ()

  private def handleInitDecodingError(genericMessage: GenericMessage) =
    ZIO.logError(s"could not decode init message from ${genericMessage.raw}")
      *> ZIO.logWarning("please check maelstrom documentation about initialisation message format")
      *> ZIO.logInfo(
        "https://github.com/jepsen-io/maelstrom/blob/main/doc/protocol.md#initialization"
      )
      *> genericMessage
        .makeError(ErrorCode.MalformedRequest, "init message is malformed")
        .fold(ZIO.unit)(outputChannel.transport(_))

private case class TestInitializer(inputChannel: InputChannel):
  def initialize(context: Context): ZIO[Scope, Nothing, Initialisation] =
    for
      _      <- ZIO.logWarning(s"initialised with fake context: $context")
      inputs <- inputChannel.partitionInputs
    yield Initialisation(context, inputs)
