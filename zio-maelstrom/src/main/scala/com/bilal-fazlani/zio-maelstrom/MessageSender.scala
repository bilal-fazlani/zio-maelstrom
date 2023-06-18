package com.bilalfazlani.zioMaelstrom

import protocol.*
import zio.*
import zio.json.{JsonEncoder, JsonDecoder}

// type AskError = ErrorMessage | DecodingFailure | Timeout | DuplicateCallbackAttempt
type AskError = ErrorMessage | DecodingFailure | Timeout

case class Timeout(messageId: MessageId, remote: NodeId, timeout: Duration)
case class DecodingFailure(error: String, message: GenericMessage)

private[zioMaelstrom] trait MessageSender:
  def send[A <: Sendable: JsonEncoder](body: A, to: NodeId): UIO[Unit]

  def ask[Req <: Sendable & NeedsReply: JsonEncoder, Res <: Reply: JsonDecoder](
      body: Req,
      to: NodeId,
      timeout: Duration
  ): IO[AskError, Res]

  def reply[Req <: NeedsReply, Res <: Sendable & Reply: JsonEncoder](
      message: Message[Req],
      reply: Res
  ): UIO[Unit]

private[zioMaelstrom] object MessageSender:
  val live: ZLayer[Initialisation & OutputChannel & Hooks & Logger, Nothing, MessageSender] = ZLayer
    .fromFunction(MessageSenderLive.apply)

  def send[A <: Sendable: JsonEncoder](body: A, to: NodeId): URIO[MessageSender, Unit] = ZIO
    .serviceWithZIO[MessageSender](_.send(body, to))

  def ask[Req <: Sendable & NeedsReply: JsonEncoder, Res <: Reply: JsonDecoder](
      body: Req,
      to: NodeId,
      timeout: Duration
  ): ZIO[MessageSender, AskError, Res] = ZIO.serviceWithZIO[MessageSender](_.ask(body, to, timeout))

  def reply[Req <: NeedsReply, Res <: Sendable & Reply: JsonEncoder](
      message: Message[Req],
      reply: Res
  ): URIO[MessageSender, Unit] = ZIO.serviceWithZIO[MessageSender](_.reply(message, reply))

private case class MessageSenderLive(
    init: Initialisation,
    stdout: OutputChannel,
    hooks: Hooks,
    logger: Logger
) extends MessageSender:
  def send[A <: Sendable: JsonEncoder](body: A, to: NodeId) =
    val message: Message[A] =
      Message[A](source = init.context.me, destination = to.nodeId, body = body)
    stdout.transport(message)

  def ask[Req <: Sendable & NeedsReply: JsonEncoder, Res <: Reply: JsonDecoder](
      body: Req,
      to: NodeId,
      timeout: Duration
  ): IO[AskError, Res] = for {
    _ <- send(body, to)
    _ <- logger.debug(s"waiting for reply from ${to} for message id: ${body.msg_id}...")
    genericMessage <- ZIO.scoped(hooks.awaitRemote(body.msg_id, to, timeout))
    decoded <-
      if genericMessage.isError then {
        val error = JsonDecoder[Message[ErrorMessage]]
          .fromJsonAST(genericMessage.raw)
          .map(_.body)
          .left
          .map(e => DecodingFailure(e, genericMessage))
        error.fold(ZIO.fail, ZIO.fail).tapError {
          case DecodingFailure(e, _) =>
            logger
              .error(s"decoding failed for response from ${to} for message id: ${body.msg_id}".red)
          case e: ErrorMessage =>
            logger.error(s"error response received from: ${to} for message id: ${body.msg_id}")
        }
      } else {
        ZIO
          .fromEither(JsonDecoder[Message[Res]].fromJsonAST(genericMessage.raw))
          .tap(x => logger.debug(s"received reply from ${to} for message id: ${body.msg_id}"))
          .mapError(e => DecodingFailure(e, genericMessage))
          .tapError(e =>
            logger.error(s"decoding failed for response from ${to} for message id: ${body.msg_id}")
          )
      }
  } yield decoded.body

  def reply[Req <: NeedsReply, Res <: Sendable & Reply: JsonEncoder](
      message: Message[Req],
      reply: Res
  ): UIO[Unit] = send(reply, message.source)
