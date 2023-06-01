package com.bilalfazlani.zioMaelstrom

import java.nio.file.Path
import protocol.*
import zio.json.*
import zio.*

extension (s: String) infix def /(string: String): Path = Path.of(s, string)
extension (p: Path) infix def /(string: String): Path = p resolve string

extension [A <: MessageWithId](message: Message[A])
  def reply[B <: MessageWithReply: JsonEncoder](out: B) = MessageSender.send(out, message.source)

extension (nodeId: NodeId)
  def ask[A <: MessageWithId: JsonEncoder, B <: MessageWithReply: JsonDecoder](body: A, timeout: Duration) = MessageSender.ask(body, nodeId, timeout)
  def send[A <: MessageBody: JsonEncoder](body: A) = MessageSender.send(body, nodeId)

def broadcastAll[A <: MessageBody: JsonEncoder](body: A) = MessageSender.broadcastAll(body)
def broadcastTo[A <: MessageBody: JsonEncoder](others: Seq[NodeId], body: A) = MessageSender.broadcastTo(others, body)

def me = ZIO.service[Initialisation].map(_.context.me)
def others = ZIO.service[Initialisation].map(_.context.others)

def logInfo(message: => String) = Logger.info(message)
def logError(message: => String) = Logger.error(message)
