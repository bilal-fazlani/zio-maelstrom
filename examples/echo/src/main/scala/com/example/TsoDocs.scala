package com.example.tso

import com.bilalfazlani.zioMaelstrom.*
import zio.*

object TsoExample {
  val timestamp: ZIO[LinTso, AskError, Int] = LinTso.ts(5.seconds)
}
