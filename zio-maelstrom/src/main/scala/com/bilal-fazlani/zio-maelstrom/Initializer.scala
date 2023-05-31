package com.bilalfazlani.zioMaelstrom

import protocol.*
import zio.*
import zio.stream.{ZStream, ZSink}
import zio.json.JsonDecoder
import zio.stream.ZPipeline

case class Inputs(
    responseStream: MessageStream,
    messageStream: MessageStream
)

case class InitResult(context: Context, inputs: Inputs)

trait Initializer:
  def initialize[R](inputs: Inputs): ZIO[R & Scope, Nothing, InitResult]

object Initializer:
  def initialize[R](
      inputs: Inputs
  ): ZIO[R & Scope & Initializer, Nothing, InitResult] =
    ZIO.serviceWithZIO[Initializer](_.initialize(inputs))

  val live: ZLayer[Logger & MessageTransport, Nothing, Initializer] = ZLayer.fromFunction(InitializerLive.apply)

case class InitializerLive(logger: Logger, transport: MessageTransport) extends Initializer:

  def initialize[R](inputs: Inputs): ZIO[R & Scope, Nothing, InitResult] =
    inputs.messageStream
      .peel(ZSink.head)
      .flatMap {
        // happy case: found init message
        case (Some(genericMessage), remainder) =>
          val initMessage = JsonDecoder[Message[MaelstromInit]].fromJsonAST(genericMessage.raw)
          initMessage match
            // when decoded, send it to init handler
            case Right(initMessage) =>
              handleInit(initMessage) as InitResult(Context(initMessage), Inputs(inputs.responseStream, remainder))

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

  private def handleInit(message: Message[MaelstromInit]) =
    val replyMessage: Message[MaelstromInitOk] = Message[MaelstromInitOk](message.destination, message.source, MaelstromInitOk(message.body.msg_id))
    for {
      _ <- logger.info(s"received ${message.body} from ${message.source}")
      _ <- transport.transport(replyMessage)
      _ <- logger.info("initialised")
    } yield ()

  private def handleInitDecodingError(genericMessage: GenericMessage) =
    logger.error(s"could not decode init message $genericMessage") *>
      genericMessage
        .makeError(StandardErrorCode.MalformedRequest, "init message is malformed")
        .fold(ZIO.unit)(transport.transport(_))

  private def splitStream(messageStream: MessageStream): ZIO[Scope, Nothing, Inputs] =
    def isResponse(message: GenericMessage) = message.details.in_reply_to.isDefined
    messageStream.partition(isResponse, 1024).map(inputs => Inputs(inputs._1, inputs._2))
