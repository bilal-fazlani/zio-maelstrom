package com.bilalfazlani.zioMaelstrom

import protocol.*
import java.nio.file.Path

enum NodeInput:
  case StdIn
  case FilePath(path: Path)

  override def toString(): String = this match
    case StdIn          => "standard input"
    case FilePath(path) => s"file: $path"
