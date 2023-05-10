package com.bilalfazlani.gossipGloomersScala

import java.nio.file.Path

extension (s: String) infix def /(string: String): Path = Path.of(s, string)
extension (p: Path) infix def /(string: String): Path = p resolve string
