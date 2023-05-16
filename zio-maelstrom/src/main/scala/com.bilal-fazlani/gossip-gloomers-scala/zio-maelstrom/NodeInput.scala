package com.bilalfazlani.gossipGloomersScala
package zioMaelstrom

import protocol.*
import java.nio.file.Path

enum NodeInput:
  case StdIn
  case FilePath(path: Path)
