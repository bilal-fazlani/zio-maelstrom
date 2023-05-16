package com.bilalfazlani.gossipGloomersScala
package zioMaelstrom

import java.nio.file.Path
import com.bilalfazlani.gossipGloomersScala.zioMaelstrom.protocol.*
import zio.ZIO
import com.bilalfazlani.gossipGloomersScala.zioMaelstrom.MessageSender
import zio.json.JsonEncoder

extension (s: String) infix def /(string: String): Path = Path.of(s, string)
extension (p: Path) infix def /(string: String): Path = p resolve string

extension [A <: MessageWithId](message: Message[A])
  def reply[B <: MessageWithReply: JsonEncoder](out: B) = ZIO.serviceWithZIO[MessageSender](_.send(out, message.source))
