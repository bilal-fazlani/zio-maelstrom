package com.example.uniqueIds

//imports {
import com.bilalfazlani.zioMaelstrom.*
import zio.json.*
import zio.*
//}

//messages{
case class Generate() derives JsonDecoder
case class GenerateOk(id: String) derives JsonEncoder
//}

object Main extends MaelstromNode {
  val program = receive[Generate] { _ =>
    for {
      generated <- ZIO.serviceWithZIO[Ref[Int]](_.getAndIncrement)
      me        <- MaelstromRuntime.me                // (1)!
      combinedId = s"${me}_${generated}"
      _         <- reply(GenerateOk(id = combinedId)) // (2)!
    } yield ()
  }.provideSome[MaelstromRuntime](ZLayer(Ref.make(0)))
}
