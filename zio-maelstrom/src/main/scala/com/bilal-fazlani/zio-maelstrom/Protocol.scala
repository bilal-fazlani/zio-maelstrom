package com.bilalfazlani.zioMaelstrom
package protocol

import zio.json.*
import zio.json.ast.Json
import scala.annotation.targetName

case class Message[+Body <: MessageBody](
    @jsonField("src")
    source: NodeId,
    @jsonField("dest")
    destination: NodeId,
    body: Body
) derives JsonDecoder,
      JsonEncoder

trait MessageBody:
  val `type`: String

trait MessageWithId extends MessageBody:
  val msg_id: MessageId

trait MessageWithReply extends MessageBody:
  val in_reply_to: MessageId

@jsonHint("init")
case class MaelstromInit(
    msg_id: MessageId,
    node_id: NodeId,
    node_ids: Seq[NodeId],
    `type`: String = "init"
) extends MessageWithId
    derives JsonDecoder

object MaelstromInit {
  private def parseInit(msg: GenericMessage): Either[String, Message[MaelstromInit]] =
    if msg.isOfType("init")
    then
      msg.body.toRight("init body is missing").flatMap { body =>
        JsonDecoder[MaelstromInit].fromJsonAST(body).map { init =>
          Message(
            source = msg.src,
            destination = msg.dest,
            body = init
          )
        }
      }
    else Left("message is not of type 'init'")

  def parseInitUnsafe(msg: GenericMessage): Message[MaelstromInit] =
    parseInit(msg).getOrElse(throw new Exception("message is not of type 'init'"))
}

@jsonHint("init_ok")
case class MaelstromInitOk(
    in_reply_to: MessageId,
    `type`: String = "init_ok"
) extends MessageWithReply
    derives JsonEncoder

@jsonHint("error")
case class MaelstromError(
    in_reply_to: MessageId,
    code: Int,
    text: String,
    `type`: String = "error"
) extends MessageWithReply
    derives JsonCodec

opaque type NodeId = String

object NodeId:
  def apply(id: String): NodeId = id
  given JsonEncoder[NodeId] = JsonEncoder.string.contramap(identity)
  given JsonDecoder[NodeId] = JsonDecoder.string.map(NodeId(_))

opaque type MessageSource = NodeId

extension (s: MessageSource)
  @targetName("messageSourceAsNodeId")
  def nodeId: NodeId = s

object MessageSource:
  @targetName("messageSourceFromNodeId")
  def apply(id: NodeId): MessageSource = id
  given JsonEncoder[MessageSource] = JsonEncoder.string.contramap(identity)
  given JsonDecoder[MessageSource] = JsonDecoder.string.map(MessageSource(_))

opaque type MessageId = Int
object MessageId:
  def apply(id: Int): MessageId = id
  given JsonEncoder[MessageId] = JsonEncoder.int.contramap(identity)
  given JsonDecoder[MessageId] = JsonDecoder.int.map(MessageId(_))

sealed trait ErrorCode(val code: Int, val definite: Boolean)

object ErrorCode:
  def fromCode(code: Int): ErrorCode =
    StandardErrorCode.values.find(_.code == code).getOrElse(CustomErrorCode(code))

// format: off
enum StandardErrorCode(code: Int, name: String, definite: Boolean, description: String) extends ErrorCode(code, definite):
  case Timeout extends StandardErrorCode(0, "timeout", false, "Indicates that the requested operation could not be completed within a timeout.")
  case NodeNotFound extends StandardErrorCode(1, "node-not-found", true, "Thrown when a client sends an RPC request to a node which does not exist.")
  case NotSupported extends StandardErrorCode(10, "not-supported", true, "Use this error to indicate that a requested operation is not supported by the current implementation. Helpful for stubbing out APIs during development.")
  case TemporarilyUnavailable extends StandardErrorCode(11, "temporarily-unavailable", true, "Indicates that the operation definitely cannot be performed at this time--perhaps because the server is in a read-only state, has not yet been initialized, believes its peers to be down, and so on. Do not use this error for indeterminate cases, when the operation may actually have taken place.")
  case MalformedRequest extends StandardErrorCode(12, "malformed-request", true, "The client's request did not conform to the server's expectations, and could not possibly have been processed.")
  case Crash extends StandardErrorCode(13, "crash", false, "Indicates that some kind of general, indefinite error occurred. Use this as a catch-all for errors you can't otherwise categorize, or as a starting point for your error handler: it's safe to return internal-error for every problem by default, then add special cases for more specific errors later.")
  case Abort extends StandardErrorCode(14, "abort", true, "Indicates that some kind of general, definite error occurred. Use this as a catch-all for errors you can't otherwise categorize, when you specifically know that the requested operation has not taken place. For instance, you might encounter an indefinite failure during the prepare phase of a transaction: since you haven't started the commit process yet, the transaction can't have taken place. It's therefore safe to return a definite abort to the client.")
  case KeyDoesNotExist extends StandardErrorCode(20, "key-does-not-exist", true, "The client requested an operation on a key which does not exist (assuming the operation should not automatically create missing keys).")
  case KeyAlreadyExists extends StandardErrorCode(21, "key-already-exists", true, "The client requested the creation of a key which already exists, and the server will not overwrite it.")
  case PreconditionFailed extends StandardErrorCode(22, "precondition-failed", true, "The requested operation expected some conditions to hold, and those conditions were not met. For instance, a compare-and-set operation might assert that the value of a key is currently 5; if the value is 3, the server would return precondition-failed.")
  case TxnConflict extends StandardErrorCode(30, "txn-conflict", true, "The requested transaction has been aborted because of a conflict with another transaction. Servers need not return this error on every conflict: they may choose to retry automatically instead.")
// format: on

case class CustomErrorCode(override val code: Int) extends ErrorCode(code, false)

extension (m: Message[MessageWithId])
  def makeErrorMessage(code: ErrorCode, text: String): Message[MaelstromError] =
    Message[MaelstromError](
      source = m.destination,
      destination = m.source,
      body = MaelstromError(
        in_reply_to = m.body.msg_id,
        code = code.code,
        text = text
      )
    )
