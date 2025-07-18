package com.bilalfazlani.zioMaelstrom

import java.nio.file.Path
import zio.json.*
import zio.*

import scala.annotation.targetName
import com.bilalfazlani.zioMaelstrom.models.MsgName

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

def reply[B: {JsonEncoder, MsgName}](out: B) = MessageSender.reply(out)

def replyError(code: ErrorCode, text: String) = reply[Error](Error(code, text))

extension [R, A](effect: ZIO[R, AskError, A])
  def defaultAskHandler: ZIO[R, Error, A] =
    effect.orElseFail(Error(ErrorCode.Crash, "ask operation failed at remote node for another node"))

private[zioMaelstrom] final class AskPartiallyApplied[Res](private val remote: NodeId) extends AnyVal {
  def apply[Req: {JsonEncoder, MsgName}](body: Req)(using
      JsonDecoder[Res]
  ): ZIO[MessageSender, AskError, Res] =
    MessageSender.ask[Req, Res](body, remote)
}

private[zioMaelstrom] final class ReceivePartiallyApplied[I](private val dummy: Boolean = false) extends AnyVal {
  def apply[R: Tag](handler: Handler[R, I])(using JsonDecoder[I]): ZIO[R & MaelstromRuntime, Nothing, Unit] =
    given Tag[R & MaelstromRuntime] = Tag[R & MaelstromRuntime]
    RequestHandler.handle(handler)
}
