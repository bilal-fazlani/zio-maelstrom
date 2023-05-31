package com.bilalfazlani.zioMaelstrom

import java.nio.file.Path
import protocol.*
import zio.json.*
import zio.*

extension (s: String) infix def /(string: String): Path = Path.of(s, string)
extension (p: Path) infix def /(string: String): Path = p resolve string

extension [A <: MessageWithId](message: Message[A])
  def reply[B <: MessageWithReply: JsonEncoder](out: B) = ZIO.serviceWithZIO[MessageSender](_.send(out, message.source))

extension (nodeId: NodeId)
  def ask[A <: MessageWithId: JsonEncoder, B <: MessageWithReply: JsonDecoder](body: A, timeout: Duration) = MessageSender.ask(body, nodeId, timeout)
  def send[A <: MessageBody: JsonEncoder](body: A) = MessageSender.send(body, nodeId)

def broadcastAll[A <: MessageBody: JsonEncoder](body: A) = ZIO.serviceWithZIO[MessageSender](_.broadcastAll(body))
def broadcastTo[A <: MessageBody: JsonEncoder](others: Seq[NodeId], body: A) = ZIO.serviceWithZIO[MessageSender](_.broadcastTo(others, body))

def me = ZIO.service[Context].map(_.me)
def others = ZIO.service[Context].map(_.others)

def logInfo(message: => String) = ZIO.serviceWithZIO[Logger](_.info(message))
def logError(message: => String) = ZIO.serviceWithZIO[Logger](_.error(message))
