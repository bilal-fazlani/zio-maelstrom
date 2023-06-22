package com.bilalfazlani.zioMaelstrom

import java.nio.file.Path
import protocol.*
import zio.json.*
import zio.*

extension (s: String) infix def /(string: String): Path = Path.of(s, string)
extension (p: Path) infix def /(string: String): Path   = p resolve string

extension (nodeId: NodeId)
  def ask[Res <: Reply]                         = new AskPartiallyApplied[Res](nodeId)
  def send[A <: Sendable: JsonEncoder](body: A) = MessageSender.send(body, nodeId)

def getMyNodeId     = ZIO.service[Initialisation].map(_.context.me)
def getOtherNodeIds = ZIO.service[Initialisation].map(_.context.others)

def logDebug(message: => String) = Logger.debug(message)
def logInfo(message: => String)  = Logger.info(message)
def logWarn(message: => String)  = Logger.warn(message)
def logError(message: => String) = Logger.error(message)

def receive[I]: ReceivePartiallyApplied[I] = new ReceivePartiallyApplied[I]

//RECEIVE CONTEXTFUL - BEGIN
def me(using Context): NodeId          = summon[Context].me
def others(using Context): Seq[NodeId] = summon[Context].others
def src(using MessageSource): NodeId   = summon[MessageSource].nodeId
def reply[B <: Sendable & Reply: JsonEncoder](out: B)(using MessageSource) =
  MessageSender.send(out, src)
//RECEIVE CONTEXTFUL - END

private[zioMaelstrom] final class AskPartiallyApplied[Res <: Reply](private val remote: NodeId)
    extends AnyVal {
  def apply[Req <: Sendable & NeedsReply: JsonEncoder](body: Req, timeout: Duration)(using
      JsonDecoder[Res]
  ): ZIO[MessageSender, AskError, Res] = MessageSender.ask[Req, Res](body, remote, timeout)
}

private[zioMaelstrom] final class ReceivePartiallyApplied[I](private val dummy: Boolean = false)
    extends AnyVal {
  def apply[Env](handler: Handler[Env, I])(using
      JsonDecoder[I]
  ): ZIO[MaelstromRuntime & Env, Nothing, Unit] = RequestHandler.handle(handler)
}
