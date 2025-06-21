package com.bilalfazlani.zioMaelstrom

import java.nio.file.Path
import zio.json.*
import zio.*

import scala.annotation.targetName
import com.bilalfazlani.zioMaelstrom.models.{MsgName, Body}

extension (s: String)
  @targetName("compose")
  infix def /(string: String): Path = Path.of(s, string)
extension (p: Path)
  @targetName("compose")
  infix def /(string: String): Path = p.resolve(string)

extension (nodeId: NodeId)
  infix def ask[Res <: Reply]                           = AskPartiallyApplied[Res](nodeId)
  infix def send[A <: Sendable: JsonEncoder](body: A)   = MessageSender.send(body, nodeId)
  infix def sendV2[A: MsgName: JsonEncoder](payload: A) = MessageSender.sendV2(payload, nodeId)

def receive[I]: ReceivePartiallyApplied[I] = new ReceivePartiallyApplied[I]

//RECEIVE CONTEXTFUL - BEGIN
def me(using Context): NodeId          = summon[Context].me
def others(using Context): Set[NodeId] = summon[Context].others
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
