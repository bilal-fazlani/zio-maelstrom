package com.bilalfazlani.gossipGloomersScala
package zioMaelstrom

import protocol.*
import zio.Task
import zio.ZIO
import zio.stream.ZStream
import zio.Scope
import zio.json.JsonDecoder
import zio.stream.ZSink
import zio.ZLayer

trait Initializer:
  def initialize(inputStream: ZStream[Any, Throwable, String]): ZIO[Scope, Throwable, (Context, ZStream[Any, Throwable, GenericMessage])]

object Initializer:
  val live = ZLayer.fromFunction(InitializerLive.apply)

case class InitializerLive(debugger: Debugger, transport: MessageTransport) extends Initializer:
  def initialize(inputStream: ZStream[Any, Throwable, String]): ZIO[Scope, Throwable, (Context, ZStream[Any, Throwable, GenericMessage])] =
    inputStream
      .map(str => (str, JsonDecoder[GenericMessage].decodeJson(str)))
      .collectZIO {
        // right means a valid json of a generic message
        case (str, Right(genericMessage)) =>
          if genericMessage isOfType "init"
          then // if init, then decode it as init message
            val initMessage = JsonDecoder[Message[MaelstromInit]].decodeJson(str)
            initMessage match
              // when decoded, send it to init handler
              case Right(initMessage) => handleInit(initMessage) as Some(genericMessage)
              // if decoding failed, send an error message to sender and log error
              case Left(error) => handleInitDecoding(genericMessage) as None
          else // else send an error message to sender and log error
            handleMessageOtherThanInit(genericMessage) as None

        // left means invalid json or missing bare minimum fields
        // cant reply to anyone. just log the error
        // in this case, this stream will never end and node will be in initialising state forever
        case (input, Left(error)) =>
          debugger.debugMessage(s"expected init message. recieved invalid input. error: $error, input: $input") as None
      }
      .takeUntil(_.isDefined)
      .collectSome // keep only init message
      .peel(ZSink.head)
      // stream is now completed.
      // if we don't have a sum yet, it means we didn't get any init message
      // since we can't proceed without init message, its safe to fail the program
      .collect(Exception("no init message received")) { case (Some(genericMessage), remainder) =>
        (Context(MaelstromInit.parseInitUnsafe(genericMessage)), remainder)
      }

  private def handleInit(message: Message[MaelstromInit]) =
    val replyMessage: Message[MaelstromInitOk] = Message[MaelstromInitOk](message.destination, message.source, MaelstromInitOk(message.body.msg_id))
    for {
      _ <- debugger.debugMessage(s"handling init message: $message")
      _ <- transport.transport(replyMessage)
      _ <- debugger.debugMessage("initialised")
    } yield ()

  private def handleInitDecoding(genericMessage: GenericMessage) =
    debugger.debugMessage(s"could not decode init message $genericMessage") *>
      genericMessage
        .makeError(StandardErrorCode.MalformedRequest, "init message is malformed")
        .fold(ZIO.unit)(transport.transport(_))

  private def handleMessageOtherThanInit(message: GenericMessage) =
    debugger.debugMessage(s"could not process message $message because node ${message.dest} is not initialised yet") *>
      message
        .makeError(StandardErrorCode.TemporarilyUnavailable, s"node ${message.dest} is not initialised yet")
        .fold(ZIO.unit)(transport.transport(_))
