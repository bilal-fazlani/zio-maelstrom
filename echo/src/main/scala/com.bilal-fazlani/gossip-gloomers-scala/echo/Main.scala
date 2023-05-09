package com.bilalfazlani.gossipGloomersScala
package echo

import zio.json.JsonDecoder
import zio.json.JsonEncoder
import zioMaelstrom.*

object MyNode extends MaelstromNode[EchoInput, EchoOutput]:
  def handle(message: EchoInput) = 
    debugMessage(s"handling message: $message") *>
    send(EchoOutput(message.input))

case class EchoInput(input: String) derives JsonDecoder
case class EchoOutput(output: String) derives JsonEncoder    