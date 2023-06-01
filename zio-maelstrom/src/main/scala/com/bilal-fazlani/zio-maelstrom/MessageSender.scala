package com.bilalfazlani.zioMaelstrom

import protocol.*
import zio.*
import zio.json.{EncoderOps, JsonEncoder, JsonDecoder}

enum ResponseError:
  case MessageTimeoutError(messageId: MessageId, remote: DestinationId, timeout: Duration)
  case DecodingError(error: String, message: GenericMessage)

trait MessageSender:
  def send[A <: MessageBody: JsonEncoder](body: A, to: DestinationId): UIO[Unit]

  def ask[I <: MessageWithId: JsonEncoder, O <: MessageWithReply: JsonDecoder](
      body: I,
      to: DestinationId,
      timeout: Duration
  ): IO[ResponseError, O]

  def reply[I <: MessageWithId, O <: MessageWithReply: JsonEncoder](message: Message[I], reply: O): UIO[Unit]

  // def broadcastAll[A <: MessageBody: JsonEncoder](body: A): UIO[Unit]

  // def broadcastTo[A <: MessageBody: JsonEncoder](others: Seq[DestinationId], body: A): UIO[Unit]

object MessageSender:
  val live: ZLayer[Initialisation & MessageTransport & Hooks, Nothing, MessageSender] = ZLayer.fromFunction(MessageSenderLive.apply)

  private[zioMaelstrom] def send[A <: MessageBody: JsonEncoder](body: A, to: DestinationId): URIO[MessageSender, Unit] =
    ZIO.serviceWithZIO[MessageSender](_.send(body, to))

  private[zioMaelstrom] def ask[I <: MessageWithId: JsonEncoder, O <: MessageWithReply: JsonDecoder](
      body: I,
      to: DestinationId,
      timeout: Duration
  ): ZIO[MessageSender, ResponseError, O] =
    ZIO.serviceWithZIO[MessageSender](_.ask(body, to, timeout))

  private[zioMaelstrom] def reply[I <: MessageWithId, O <: MessageWithReply: JsonEncoder](message: Message[I], reply: O): URIO[MessageSender, Unit] =
    ZIO.serviceWithZIO[MessageSender](_.reply(message, reply))

  // private[zioMaelstrom] def broadcastAll[A <: MessageBody: JsonEncoder](body: A): URIO[MessageSender, Unit] =
  //   ZIO.serviceWithZIO[MessageSender](_.broadcastAll(body))

  // private[zioMaelstrom] def broadcastTo[A <: MessageBody: JsonEncoder](others: Seq[DestinationId], body: A): URIO[MessageSender, Unit] =
  //   ZIO.serviceWithZIO[MessageSender](_.broadcastTo(others, body))

private[zioMaelstrom] case class MessageSenderLive(init: Initialisation, transport: MessageTransport, hooks: Hooks) extends MessageSender:
  def send[A <: MessageBody: JsonEncoder](body: A, to: DestinationId) =
    val message: Message[A] = Message[A](
      source = init.context.me,
      destination = to.nodeId,
      body = body
    )
    transport.transport(message)

  def ask[I <: MessageWithId: JsonEncoder, O <: MessageWithReply: JsonDecoder](
      body: I,
      to: DestinationId,
      timeout: Duration
  ): IO[ResponseError, O] =
    for {
      _ <- send(body, to)
      response <- hooks.awaitMessage(body.msg_id, to, timeout)
      decoded <- ZIO.fromEither(JsonDecoder[Message[O]].fromJsonAST(response.raw)).mapError(e => ResponseError.DecodingError(e, response))
    } yield decoded.body

  def reply[I <: MessageWithId, O <: MessageWithReply: JsonEncoder](message: Message[I], reply: O): UIO[Unit] =
    send(reply, message.source.asDestination)

  // def broadcastAll[A <: MessageBody: JsonEncoder](body: A) =
  //   broadcastTo(init.context.others, body)

  // def broadcastTo[A <: MessageBody: JsonEncoder](others: Seq[DestinationId], body: A) =
  //   ZIO.foreachPar(others)(to => send(body, to)).unit
