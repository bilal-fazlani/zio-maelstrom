package com.bilalfazlani.zioMaelstrom

import AnsiColors.*

private object AnsiColors:
  val redColor    = RGB(230, 20, 20)
  val amberColor  = RGB(220, 130, 0)
  val yellowColor = RGB(200, 200, 0)
  val greenColor  = RGB(20, 180, 20)
  val grayColor   = RGB(150, 150, 150)

  case class RGB(r: Int, g: Int, b: Int)

  def colored(rgb: RGB, str: String): String =
    val RGB(r, g, b) = rgb
    val color        = s"\u001B[38;2;$r;$g;$b;6m"
    s"$color$str\u001B[0m"

extension (s: String)
  def gray   = colored(grayColor, s)
  def red    = colored(redColor, s)
  def amber  = colored(amberColor, s)
  def yellow = colored(yellowColor, s)
  def green  = colored(greenColor, s)
