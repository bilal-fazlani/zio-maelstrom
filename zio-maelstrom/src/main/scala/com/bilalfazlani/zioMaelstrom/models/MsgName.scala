package com.bilalfazlani.zioMaelstrom.models

import scala.compiletime.erasedValue
import scala.deriving.Mirror

// opaque alias – at runtime it is just a String
opaque type MsgName[T] = String

object MsgName:

  /** summon the snake‑case name for type `T` */
  transparent inline def apply[T](using inline m: MsgName[T]): String = m

  /** automatic derivation for any case‑class `T` */
  inline given derived[T <: Product](using m: Mirror.ProductOf[T]): MsgName[T] =
    inline m match
      case m: Mirror.ProductOf[T] =>
        inline erasedValue[m.MirroredLabel] match
          case _: String => compiletime.constValue[m.MirroredLabel].asInstanceOf[String].toSnakeCase

  // extension methods --------------------------------------------------

  extension [T](m: MsgName[T]) inline def value: String = m // same as apply[T]

  // helpers ------------------------------------------------------------

  extension (s: String)
    /** minimal Camel → snake transformer */
    def toSnakeCase: String =
      s.headOption.fold("")(_.toLower.toString) ++
        s.tail.flatMap { c =>
          if c.isUpper then "_" + c.toLower
          else c.toString
        }
