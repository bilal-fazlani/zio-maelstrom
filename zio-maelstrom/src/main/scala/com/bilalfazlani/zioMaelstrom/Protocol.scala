package com.bilalfazlani.zioMaelstrom

import zio.json.*
import scala.annotation.targetName
import zio.*
import com.bilalfazlani.zioMaelstrom.models.Body

private[zioMaelstrom] case class Message[+A](
    @jsonField("src")
    source: NodeId,
    @jsonField("dest")
    destination: NodeId,
    body: Body[A]
) derives JsonDecoder,
      JsonEncoder

private[zioMaelstrom] case class MaelstromInit(
    node_id: NodeId,
    node_ids: Set[NodeId]
) derives JsonDecoder

private[zioMaelstrom] object MaelstromInit {
  private def parseInit(msg: GenericMessage): Either[String, Message[MaelstromInit]] =
    if msg.isOfType("init") then
      msg.body.toRight("init body is missing").flatMap { body =>
        JsonDecoder[MaelstromInit].fromJsonAST(body).map { init =>
          Message(
            source = msg.src,
            destination = msg.dest,
            body = Body("init", init, msg.messageId, msg.inReplyTo)
          )
        }
      }
    else Left("message is not of type 'init'")

  def parseInitUnsafe(msg: GenericMessage): Message[MaelstromInit] = parseInit(msg)
    .getOrElse(throw new Exception("message is not of type 'init'"))
}

private[zioMaelstrom] case class MaelstromInitOk() derives JsonEncoder

// errorMessage {
case class Error(
    code: ErrorCode,
    text: String
) derives JsonCodec
// }

opaque type NodeId = String

object NodeId:
  def apply(id: String): NodeId  = id
  given JsonEncoder[NodeId]      = JsonEncoder.string.contramap(identity)
  given JsonDecoder[NodeId]      = JsonDecoder.string.map(NodeId(_))
  given JsonFieldEncoder[NodeId] = JsonFieldEncoder.string.contramap(identity)
  given JsonFieldDecoder[NodeId] = JsonFieldDecoder.string.map(NodeId(_))

opaque type MessageId = Int
object MessageId:
  def apply(id: Int): MessageId     = id
  given JsonEncoder[MessageId]      = JsonEncoder.int.contramap(identity)
  given JsonDecoder[MessageId]      = JsonDecoder.int.map(MessageId(_))
  given JsonFieldEncoder[MessageId] = JsonFieldEncoder.int.contramap(identity)
  given JsonFieldDecoder[MessageId] = JsonFieldDecoder.int.map(MessageId(_))
  val next                          = MessageIdStore.next

  extension (id: MessageId) def toInt: Int = id

// format: off  
 // errorCodes {
sealed trait ErrorCode(private val code1: Int, val definite: Boolean) {
  def code: Int = code1
  override def toString: String = s"error: ${this.getClass.getSimpleName.replace("$","")}, code: $code"
}

object ErrorCode:
  /**
    * Indicates that the requested operation could not be completed within a timeout.
    */
  object Timeout extends ErrorCode(0, false)
  /**
    * Thrown when a client sends an RPC request to a node which does not exist.
    */
  object NodeNotFound extends ErrorCode(1, true)
  /**
    * Use this error to indicate that a requested operation is not supported by the current implementation. Helpful for stubbing out APIs during development.
    */
  object NotSupported extends ErrorCode(10, true)
  /**
    * Indicates that the operation definitely cannot be performed at this time--perhaps because the server is in a read-only state, has not yet been initialized, believes its peers to be down, and so on. Do not use this error for indeterminate cases, when the operation may actually have taken place.
    */
  object TemporarilyUnavailable extends ErrorCode(11, true)
  /**
    * The client's request did not conform to the server's expectations, and could not possibly have been processed.
    */
  object MalformedRequest extends ErrorCode(12, true)
  /**
    * Indicates that some kind of general, indefinite error occurred. Use this as a catch-all for errors you can't otherwise categorize, or as a starting point for your error handler: it's safe to return internal-error for every problem by default, then add special cases for more specific errors later.
    */
  object Crash extends ErrorCode(13, false)
  /**
    * Indicates that some kind of general, definite error occurred. Use this as a catch-all for errors you can't otherwise categorize, when you specifically know that the requested operation has not taken place. For instance, you might encounter an indefinite failure during the prepare phase of a transaction: since you haven't started the commit process yet, the transaction can't have taken place. It's therefore safe to return a definite abort to the client.
    */
  object Abort extends ErrorCode(14, true)
  /**
    * The client requested an operation on a key which does not exist (assuming the operation should not automatically create missing keys).
    */
  object KeyDoesNotExist extends ErrorCode(20, true)
  /**
    * The client requested the creation of a key which already exists, and the server will not overwrite it.
    */
  object KeyAlreadyExists extends ErrorCode(21, true)
  /**
    * The requested operation expected some conditions to hold, and those conditions were not met. For instance, a compare-and-set operation might assert that the value of a key is currently 5; if the value is 3, the server would return precondition-failed.
    */
  object PreconditionFailed extends ErrorCode(22, true)
  /**
    * The requested transaction has been aborted because of a conflict with another transaction. Servers need not return this error on every conflict: they may choose to retry automatically instead.
    */
  object TxnConflict extends ErrorCode(30, true)
  /**
    * Custom error code
    *
    * @param code the error code
    */
  case class Custom(override val code: Int) extends ErrorCode(code, false)
  // }
  // format: on

  given JsonEncoder[ErrorCode] = JsonEncoder.int.contramap(_.code)
  given JsonDecoder[ErrorCode] = JsonDecoder.int.map {
    case 0  => Timeout
    case 1  => NodeNotFound
    case 10 => NotSupported
    case 11 => TemporarilyUnavailable
    case 12 => MalformedRequest
    case 13 => Crash
    case 14 => Abort
    case 20 => KeyDoesNotExist
    case 21 => KeyAlreadyExists
    case 22 => PreconditionFailed
    case 30 => TxnConflict
    case c  => Custom(c)
  }
