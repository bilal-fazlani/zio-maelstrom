package com.bilalfazlani.zioMaelstrom

import protocol.*
import zio.*
import zio.stream.{ZStream, ZSink}
import zio.json.JsonDecoder
import zio.stream.ZPipeline

trait Initializer:
  def initialize[R](inputStream: ZStream[R, Nothing, String]): ZIO[R & Scope, Nothing, (Context, ZStream[Any, Nothing, GenericMessage])]

object Initializer:
  def initialize[R](
      inputStream: ZStream[R, Nothing, String]
  ): ZIO[R & Scope & Initializer, Nothing, (Context, ZStream[Any, Nothing, GenericMessage])] =
    ZIO.serviceWithZIO[Initializer](_.initialize(inputStream))

  val live = ZLayer.fromFunction(InitializerLive.apply)

case class InitializerLive(logger: Logger, transport: MessageTransport) extends Initializer:

  def initialize[R](inputStream: ZStream[R, Nothing, String]): ZIO[R & Scope, Nothing, (Context, ZStream[Any, Nothing, GenericMessage])] =
    inputStream
      .map(str => (str, JsonDecoder[GenericMessage].decodeJson(str)))
      .map[(String, Either[String, GenericMessage], Boolean)] {
        case (str, Right(genericMessage)) => (str, Right(genericMessage), genericMessage isOfType "init")
        case (str, Left(error))           => (str, Left(error), false)
      }
      .peel(ZSink.head)
      .flatMap {
        // happy case: found init message
        case (Some(input, Right(genericMessage), _), remainder) =>
          val initMessage = JsonDecoder[Message[MaelstromInit]].decodeJson(input)
          initMessage match
            // when decoded, send it to init handler
            case Right(initMessage) =>
              handleInit(initMessage) as (Context(initMessage), remainder.collectZIO {
                case (_, Right(genericMessage), _) => ZIO.succeed(Some(genericMessage))
                case (str, Left(error), _)         =>
                  // this item is after init message,
                  // but it could be a valid json of generic message
                  // so we can only log a message and ignore it
                  logger.error(s"expected a valid input message message. recieved invalid input. error: $error, input: $str") *> ZIO.none
              }.collectSome)
            // if decoding failed, send an error message to sender and log error
            // because this was promised to be a init message, but was not,
            // we will have to shut down the node
            case Left(error) =>
              handleInitDecodingError(genericMessage) *>
                ZIO.die(new Exception("init message decoding failed"))
        case (Some(input, Left(error), _), remainder) =>
          // same case as above, but this time, we dont have a generic message
          // so we cant send an error message to sender
          // we will have to shut down the node
          logger.error(s"expected init message. recieved invalid input. error: $error, input: $input") *>
            ZIO.die(new Exception("init message decoding failed"))
        case (None, _) =>
          // if we don't have a some yet, it means we didn't get any init message
          // since we can't proceed without init message, its safe to fail the program
          ZIO.die(new Exception("no init message received"))
      }

  private def handleInit(message: Message[MaelstromInit]) =
    val replyMessage: Message[MaelstromInitOk] = Message[MaelstromInitOk](message.destination, message.source, MaelstromInitOk(message.body.msg_id))
    for {
      _ <- logger.info(s"handling init message: $message")
      _ <- transport.transport(replyMessage)
      _ <- logger.info("initialised")
    } yield ()

  private def handleInitDecodingError(genericMessage: GenericMessage) =
    logger.error(s"could not decode init message $genericMessage") *>
      genericMessage
        .makeError(StandardErrorCode.MalformedRequest, "init message is malformed")
        .fold(ZIO.unit)(transport.transport(_))
