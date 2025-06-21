package com.example.newdsl

import zio.ZIO
import zio.json.*
import com.bilalfazlani.zioMaelstrom.*

case class Echo(echo: String) derives JsonEncoder

object Main extends MaelstromNode {
  val program: ZIO[MaelstromRuntime, Nothing, Unit] =
    ZIO.serviceWithZIO[MessageSender](_.sendV2(Echo("Hello"), NodeId("123")))
}
