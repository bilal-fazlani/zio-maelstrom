package com.bilalfazlani.zioMaelstrom

import protocol.*
import zio.*
import zio.stream.{ZStream, ZSink}
import zio.json.JsonDecoder

private[zioMaelstrom] type MessageStream = ZStream[Any, Nothing, GenericMessage]

private[zioMaelstrom] case class Inputs(
    responseStream: MessageStream,
    messageStream: MessageStream
)

private[zioMaelstrom] case class Initialisation(context: Context, inputs: Inputs)

private[zioMaelstrom] object Initialisation:
  val live: ZLayer[Scope & Initializer, Nothing, Initialisation] = ZLayer.fromZIO(Initializer.initialize)

trait Initializer:
  def initialize: ZIO[Scope, Nothing, Initialisation]

private[zioMaelstrom] object Initializer:
  val initialize: ZIO[Scope & Initializer, Nothing, Initialisation] =
    ZIO.serviceWithZIO[Initializer](_.initialize)

  val live: ZLayer[Logger & MessageTransport, Nothing, Initializer] = ZLayer.fromFunction(InitializerLive.apply)

private case class InitializerLive(logger: Logger, transport: MessageTransport) extends Initializer:

  val initialize: ZIO[Scope, Nothing, Initialisation] =
    for
      inputs <- transport.readInputs
      init <- inputs.messageStream
        .peel(ZSink.head)
        .flatMap {
          // happy case: found init message
          case (Some(genericMessage), remainder) =>
            val initMessage = JsonDecoder[Message[MaelstromInit]].fromJsonAST(genericMessage.raw)
            initMessage match
              // when decoded, send it to init handler
              case Right(initMessage) =>
                handleInit(initMessage) as Initialisation(Context(initMessage), Inputs(inputs.responseStream, remainder))

              // if decoding failed, send an error message to sender and log error
              // because this was promised to be a init message, but was not,
              // we will have to shut down the node
              case Left(error) =>
                handleInitDecodingError(genericMessage) *>
                  ZIO.die(new Exception("init message decoding failed"))
          case (None, _) =>
            // if we don't have a some yet, it means we didn't get any init message
            // since we can't proceed without init message, its safe to fail the program
            ZIO.die(new Exception("no init message received"))
        }
    yield init

  private def handleInit(message: Message[MaelstromInit]) =
    val replyMessage: Message[MaelstromInitOk] = Message[MaelstromInitOk](message.destination, message.source, MaelstromInitOk(message.body.msg_id))
    for {
      _ <- transport.transport(replyMessage)
      _ <- logger.info("initialised")
    } yield ()

  private def handleInitDecodingError(genericMessage: GenericMessage) =
    logger.error(s"could not decode init message $genericMessage") *>
      genericMessage
        .makeError(StandardErrorCode.MalformedRequest, "init message is malformed")
        .fold(ZIO.unit)(transport.transport(_))
