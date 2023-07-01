package com.bilalfazlani.zioMaelstrom

import protocol.*
import zio.*
import zio.stream.{ZStream, ZSink}
import zio.json.JsonDecoder
import java.lang.{System => JSystem}
import java.time.OffsetDateTime

private[zioMaelstrom] type MessageStream = ZStream[Any, Nothing, GenericMessage]

private[zioMaelstrom] case class Inputs(responseStream: MessageStream, messageStream: MessageStream)

private[zioMaelstrom] case class Initialisation(context: Context, inputs: Inputs)

private[zioMaelstrom] object Initialisation:
  val run: ZLayer[Scope & Logger & InputChannel & OutputChannel, Nothing, Initialisation] = ZLayer
    .fromFunction(InitializerLive.apply)
    .flatMap(initializer => ZLayer.fromZIO(initializer.get.initialize))

  def fake(context: Context): ZLayer[InputChannel & Scope, Nothing, Initialisation] = ZLayer
    .fromFunction(TestInitializer.apply)
    .flatMap(x => ZLayer.fromZIO(x.get.initialize(context)))

private case class InitializerLive(
    logger: Logger,
    outputChannel: OutputChannel,
    inputChannel: InputChannel
):
  private val preInitMessages = for {
    initialTime <- Clock.currentDateTime
    _           <- logger.debug("node started. awaiting init message...")
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
        case gap if gap.toSeconds <= 10 => logger.info(_)
        case gap if gap.toSeconds < 60  => logger.warn(_)
        case _                          => logger.error(_)
      }
      _ <- loggerf(s"init message not received in ${renderDuration(gap)}")
    } yield ()

  private def renderDuration(duration: Duration): String = {
    val seconds = duration.toSeconds()
    val minutes = seconds / 60
    val hours   = minutes / 60
    val days    = hours / 24
    val years   = days / 365
    val months  = years / 12

    val remainingSeconds = seconds % 60
    val remainingMinutes = minutes % 60
    val remainingHours   = hours   % 24
    val remainingDays    = days    % 365
    val remainingMonths  = months  % 12

    val parts = Seq(
      (remainingMonths, "month"),
      (remainingDays, "day"),
      (remainingHours, "hour"),
      (remainingMinutes, "minute"),
      (remainingSeconds, "second")
    )

    val nonZeroParts = parts.filter(_._1 > 0)

    if (nonZeroParts.isEmpty) {
      "0 seconds"
    } else {
      nonZeroParts
        .map { case (value, unit) =>
          s"$value ${unit}${if (value > 1) "s" else ""}"
        }
        .mkString(" ")
    }
  }

  val initialize: ZIO[Scope, Nothing, Initialisation] =
    for
      warningFiber <- preInitMessages
      _            <- ZIO.addFinalizer(warningFiber.interrupt)
      inputs       <- inputChannel.readInputs
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
      MaelstromInitOk(message.body.msg_id)
    )
    for {
      _ <- outputChannel.transport(replyMessage)
      _ <- logger.info("initialised")
    } yield ()

  private def handleInitDecodingError(genericMessage: GenericMessage) =
    logger.error(s"could not decode init message from ${genericMessage.raw}")
      *> logger.warn("please check maelstrom documentation about initialisation message format")
      *> logger.info(
        "https://github.com/jepsen-io/maelstrom/blob/main/doc/protocol.md#initialization"
      )
      *> genericMessage
        .makeError(ErrorCode.MalformedRequest, "init message is malformed")
        .fold(ZIO.unit)(outputChannel.transport(_))

private case class TestInitializer(inputChannel: InputChannel):
  def initialize(context: Context): ZIO[Scope, Nothing, Initialisation] =
    for inputs <- inputChannel.readInputs
    yield Initialisation(context, inputs)
