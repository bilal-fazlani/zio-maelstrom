package com.example.tso

import zio.*
import com.bilalfazlani.zioMaelstrom.*
import com.bilalfazlani.zioMaelstrom.services.LinTso

object TsoExample {
  val timestamp: ZIO[LinTso, AskError, Int] = LinTso.ts
}

object TsoCustomTimeout {
  // Custom timeout overrides the default timeout  
  val timestamp: ZIO[LinTso, AskError, Int] = LinTso.ts(2.seconds)
}
