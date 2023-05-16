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

object Initializer:
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
          ZIO.serviceWithZIO[Debugger](_.debugMessage(s"expected init message. recieved invalid input. error: $error, input: $input") as None)
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
      .provideSome[Scope](Debugger.live, MessageTransport.live)

  private def handleInit(message: Message[MaelstromInit]) =
    val replyMessage: Message[MaelstromInitOk] = Message[MaelstromInitOk](message.destination, message.source, MaelstromInitOk(message.body.msg_id))
    for {
      debugger <- ZIO.service[Debugger]
      transport <- ZIO.service[MessageTransport]
      _ <- debugger.debugMessage(s"handling init message: $message")
      _ <- transport.transport(replyMessage)
      _ <- debugger.debugMessage("initialised")
    } yield ()

  private def handleInitDecoding(genericMessage: GenericMessage) =
    for {
      debugger <- ZIO.service[Debugger]
      transport <- ZIO.service[MessageTransport]
      _ <- debugger.debugMessage(s"could not decode init message $genericMessage")
      _ <- genericMessage
        .makeError(StandardErrorCode.MalformedRequest, "init message is malformed")
        .fold(ZIO.unit)(transport.transport(_))
    } yield ()

  private def handleMessageOtherThanInit(message: GenericMessage) =
    for {
      debugger <- ZIO.service[Debugger]
      transport <- ZIO.service[MessageTransport]
      _ <- debugger.debugMessage(s"could not process message $message because node ${message.dest} is not initialised yet")
      _ <- message
        .makeError(StandardErrorCode.TemporarilyUnavailable, s"node ${message.dest} is not initialised yet")
        .fold(ZIO.unit)(transport.transport(_))
    } yield ()
