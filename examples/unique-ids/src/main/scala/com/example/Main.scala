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
  val program = receive[Generate] ( _ =>
    for {
      generated <- ZIO.serviceWithZIO[Ref[Int]](_.getAndIncrement)
      combinedId = s"${me}_${generated}" // (1)!
      _ <- reply(GenerateOk(id = combinedId)) // (2)!
    } yield ()
  ).provideSome[MaelstromRuntime](ZLayer(Ref.make(0)))
}
