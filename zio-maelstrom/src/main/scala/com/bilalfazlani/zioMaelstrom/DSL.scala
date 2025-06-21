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
  infix def send[A: {MsgName, JsonEncoder}](payload: A) = MessageSender.send(payload, nodeId)
  infix def ask[Res]                                    = AskPartiallyApplied[Res](nodeId)

def receive[I]: ReceivePartiallyApplied[I] = new ReceivePartiallyApplied[I]

//RECEIVE CONTEXTFUL - BEGIN
def me(using Context): NodeId          = summon[Context].me
def others(using Context): Set[NodeId] = summon[Context].others
def src(using MessageSource): NodeId   = summon[MessageSource].nodeId
def reply[B: {JsonEncoder, MsgName}](out: B)(using MessageSource, Option[MessageId]) = {
  summon[Option[MessageId]] match {
    case Some(messageId) =>
      MessageSender.sendRaw(Body(MsgName[B], out, None, Some(messageId)), src)
    case None =>
      ZIO.logWarning(
        "there is no messageId present in the context, " +
          s"cannot reply to node $src with message type ${MsgName[B]}"
      )
  }
}
//RECEIVE CONTEXTFUL - END
private[zioMaelstrom] final class AskPartiallyApplied[Res](private val remote: NodeId)
    extends AnyVal {
  def apply[Req: {JsonEncoder, MsgName}](body: Req, timeout: Duration)(using
      JsonDecoder[Res]
  ): ZIO[MessageSender, AskError, Res] =
    MessageSender.ask[Req, Res](body, remote, timeout)
}

private[zioMaelstrom] final class ReceivePartiallyApplied[I](private val dummy: Boolean = false)
    extends AnyVal {
  def apply[Env](handler: Handler[Env, I])(using
      JsonDecoder[I]
  ): ZIO[MaelstromRuntime & Env, Nothing, Unit] = RequestHandler.handle(handler)
}
