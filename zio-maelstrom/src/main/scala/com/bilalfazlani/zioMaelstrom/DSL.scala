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

def reply[B: {JsonEncoder, MsgName}](out: B) = 
  ZIO.serviceWithZIO[MessageContext]{ messageContext =>
    messageContext.messageId match {
      case Some(messageId) => MessageSender.reply(out, messageContext.remote, messageId)
      case None            =>
        ZIO.logWarning(
          "there is no messageId present in the context, " +
            s"cannot reply to node ${messageContext.remote} with message type ${MsgName[B]}"
        )
    }
  }

private[zioMaelstrom] final class AskPartiallyApplied[Res](private val remote: NodeId) extends AnyVal {
  def apply[Req: {JsonEncoder, MsgName}](body: Req, timeout: Duration)(using
      JsonDecoder[Res]
  ): ZIO[MessageSender, AskError, Res] =
    MessageSender.ask[Req, Res](body, remote, timeout)
}

private[zioMaelstrom] final class ReceivePartiallyApplied[I](private val dummy: Boolean = false) extends AnyVal {
  def apply[Env: Tag](handler: Handler[Env, I])(using
      JsonDecoder[I]
  )= RequestHandler.handle(handler)
}
