package com.bilalfazlani.zioMaelstrom

import java.nio.file.Path
import protocol.*
import zio.json.*
import zio.*

extension (s: String) infix def /(string: String): Path = Path.of(s, string)
extension (p: Path) infix def /(string: String): Path   = p resolve string

extension [A <: ReplyableTo](message: A)
  def reply[B <: Sendable & Reply: JsonEncoder](out: B)(using MessageSource) = MessageSender.send(out, NodeId(summon[MessageSource].nodeId))

extension (nodeId: NodeId)
  def ask[Res <: Reply]                         = new AskPartiallyApplied[Res](nodeId)
  def send[A <: Sendable: JsonEncoder](body: A) = MessageSender.send(body, nodeId)

def getMyNodeId     = ZIO.service[Initialisation].map(_.context.me)
def getOtherNodeIds = ZIO.service[Initialisation].map(_.context.others)

def logDebug(message: => String) = Logger.debug(message)
def logInfo(message: => String)  = Logger.info(message)
def logError(message: => String) = Logger.error(message)

def receive[I: JsonDecoder](handler: Handler[Any, I]): ZIO[MaelstromRuntime, Nothing, Unit]       = RequestHandler.handle(handler)
def receiveR[R, I: JsonDecoder](handler: Handler[R, I]): ZIO[MaelstromRuntime & R, Nothing, Unit] = RequestHandler.handleR(handler)

//CONTEXTFUL - BEGIN
def myNodeId(using Context): NodeId          = summon[Context].me
def otherNodeIds(using Context): Seq[NodeId] = summon[Context].others
def src(using MessageSource): NodeId         = summon[MessageSource].nodeId
//CONTEXTFUL - END

final class AskPartiallyApplied[Res <: Reply](private val remote: NodeId) extends AnyVal {
  def apply[Req <: Sendable & ReplyableTo: JsonEncoder](body: Req, timeout: Duration)(using
      JsonDecoder[Res]
  ): ZIO[MessageSender, ResponseError, Res] =
    MessageSender.ask[Req, Res](body, remote, timeout)
}
