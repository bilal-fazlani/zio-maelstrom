package com.example.uniqueIds

//imports {
import com.bilalfazlani.zioMaelstrom.*
import zio.json.{JsonDecoder, JsonEncoder}
import zio.{ZIO, Ref, ZLayer}
//}

//messages{
case class Generate() derives JsonDecoder
case class GenerateOk(id: String) derives JsonEncoder
//}

object Main extends MaelstromNode {

  val program = receive[Generate] { case request =>
    for {
      generated <- ZIO.serviceWithZIO[Ref[Int]](_.updateAndGet(_ + 1))
      combinedId = s"${me}_${generated}" // (1)!
      _ <- reply(GenerateOk(id = combinedId))
    } yield ()
  }.provideSome[MaelstromRuntime](ZLayer(Ref.make(0)))
}
