package com.bilalfazlani.zioMaelstrom

import java.nio.file.Path
import protocol.*
import zio.json.*
import zio.*

extension (s: String) infix def /(string: String): Path = Path.of(s, string)
extension (p: Path) infix def /(string: String): Path = p resolve string

extension [A <: MessageWithId](message: Message[A]) def reply[B <: MessageWithReply: JsonEncoder](out: B) = MessageSender.send(out, message.source)

extension (nodeId: NodeId)
  def ask[B <: MessageWithReply] = new AskPartiallyApplied[B](nodeId)
  def send[A <: MessageBody: JsonEncoder](body: A) = MessageSender.send(body, nodeId)

def me = ZIO.service[Initialisation].map(_.context.me)
def others = ZIO.service[Initialisation].map(_.context.others)

def logInfo(message: => String) = Logger.info(message)
def logError(message: => String) = Logger.error(message)

def receive[I <: MessageBody: JsonDecoder](handler: Handler[Any, I]): ZIO[MaelstromRuntime, Nothing, Unit] = RequestHandler.handle(handler)
def receiveR[R, I <: MessageBody: JsonDecoder](handler: Handler[R, I]): ZIO[MaelstromRuntime & R, Nothing, Unit] = RequestHandler.handleR(handler)

final class AskPartiallyApplied[O <: MessageWithReply](private val remote: NodeId) extends AnyVal {
  def apply[I <: MessageWithId: JsonEncoder](body: I, timeout: Duration)(using JsonDecoder[O]): ZIO[MessageSender, ResponseError, O] =
    MessageSender.ask[I, O](body, remote, timeout)
}
