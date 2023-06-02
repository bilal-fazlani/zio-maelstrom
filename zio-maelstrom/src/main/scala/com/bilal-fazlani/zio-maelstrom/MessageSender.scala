package com.bilalfazlani.zioMaelstrom

import protocol.*
import zio.*
import zio.json.{JsonEncoder, JsonDecoder}

type ResponseError = ErrorMessage | DecodingFailure | Timeout

case class Timeout(messageId: MessageId, remote: NodeId, timeout: Duration)
case class DecodingFailure(error: String, message: GenericMessage)

trait MessageSender:
  def send[A <: Sendable: JsonEncoder](body: A, to: NodeId): UIO[Unit]

  def ask[Req <: Sendable & NeedsReply: JsonEncoder, Res <: Reply: JsonDecoder](
      body: Req,
      to: NodeId,
      timeout: Duration
  ): IO[ResponseError, Res]

  def reply[Req <: NeedsReply, Res <: Sendable & Reply: JsonEncoder](message: Message[Req], reply: Res): UIO[Unit]

  // def broadcastAll[A <: MessageBody: JsonEncoder](body: A): UIO[Unit]

  // def broadcastTo[A <: MessageBody: JsonEncoder](others: Seq[NodeId], body: A): UIO[Unit]

private[zioMaelstrom] object MessageSender:
  val live: ZLayer[Initialisation & MessageTransport & Hooks, Nothing, MessageSender] = ZLayer.fromFunction(MessageSenderLive.apply)

  def send[A <: Sendable: JsonEncoder](body: A, to: NodeId): URIO[MessageSender, Unit] =
    ZIO.serviceWithZIO[MessageSender](_.send(body, to))

  def ask[Req <: Sendable & NeedsReply: JsonEncoder, Res <: Reply: JsonDecoder](
      body: Req,
      to: NodeId,
      timeout: Duration
  ): ZIO[MessageSender, ResponseError, Res] =
    ZIO.serviceWithZIO[MessageSender](_.ask(body, to, timeout))

  def reply[Req <: NeedsReply, Res <: Sendable & Reply: JsonEncoder](message: Message[Req], reply: Res): URIO[MessageSender, Unit] =
    ZIO.serviceWithZIO[MessageSender](_.reply(message, reply))

  // def broadcastAll[A <: MessageBody: JsonEncoder](body: A): URIO[MessageSender, Unit] =
  //   ZIO.serviceWithZIO[MessageSender](_.broadcastAll(body))

  // def broadcastTo[A <: MessageBody: JsonEncoder](others: Seq[NodeId], body: A): URIO[MessageSender, Unit] =
  //   ZIO.serviceWithZIO[MessageSender](_.broadcastTo(others, body))

private case class MessageSenderLive(init: Initialisation, transport: MessageTransport, hooks: Hooks) extends MessageSender:
  def send[A <: Sendable: JsonEncoder](body: A, to: NodeId) =
    val message: Message[A] = Message[A](
      source = init.context.me,
      destination = to.nodeId,
      body = body
    )
    transport.transport(message)

  def ask[Req <: Sendable & NeedsReply: JsonEncoder, Res <: Reply: JsonDecoder](
      body: Req,
      to: NodeId,
      timeout: Duration
  ): IO[ResponseError, Res] =
    for {
      _              <- send(body, to)
      genericMessage <- hooks.awaitRemote(body.msg_id, to, timeout)
      decoded <-
        if genericMessage.isError then {
          val error = JsonDecoder[Message[ErrorMessage]]
            .fromJsonAST(genericMessage.raw)
            .map(_.body)
            .left
            .map(e => DecodingFailure(e, genericMessage))
          error.fold(ZIO.fail, ZIO.fail)
        } else {
          ZIO
            .fromEither(JsonDecoder[Message[Res]].fromJsonAST(genericMessage.raw))
            .mapError(e => DecodingFailure(e, genericMessage))
        }
    } yield decoded.body

  def reply[Req <: NeedsReply, Res <: Sendable & Reply: JsonEncoder](message: Message[Req], reply: Res): UIO[Unit] =
    send(reply, message.source)

  // def broadcastAll[A <: MessageBody: JsonEncoder](body: A) =
  //   broadcastTo(init.context.others, body)

  // def broadcastTo[A <: MessageBody: JsonEncoder](others: Seq[NodeId], body: A) =
  //   ZIO.foreachPar(others)(to => send(body, to)).unit
