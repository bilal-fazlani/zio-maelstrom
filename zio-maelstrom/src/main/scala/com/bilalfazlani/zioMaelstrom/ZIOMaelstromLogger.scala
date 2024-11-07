package com.bilalfazlani.zioMaelstrom

import zio.*

object ZIOMaelstromLogger {
  private object PlainLogger extends ZLogger[String, String] {
    override def apply(
        trace: Trace,
        fiberId: FiberId,
        logLevel: LogLevel,
        message: () => String,
        cause: Cause[Any],
        context: FiberRefs,
        spans: List[LogSpan],
        annotations: Map[String, String]
    ): String = s"[${logLevel.label}]\t${message()}"
  }

  private object ColoredLogger extends ZLogger[String, String] {
    val levelToColorMapping = Map(
      LogLevel.All     -> gray,
      LogLevel.None    -> gray,
      LogLevel.Trace   -> gray,
      LogLevel.Debug   -> gray,
      LogLevel.Info    -> yellow,
      LogLevel.Warning -> amber,
      LogLevel.Error   -> red,
      LogLevel.Fatal   -> red
    )

    override def apply(
        trace: Trace,
        fiberId: FiberId,
        logLevel: LogLevel,
        message: () => String,
        cause: Cause[Any],
        context: FiberRefs,
        spans: List[LogSpan],
        annotations: Map[String, String]
    ): String =
      levelToColorMapping(logLevel)(message())
  }

  private def makePrintErrLogger(
      logFormat: LogFormat,
      logLevel: LogLevel
  ): ZLogger[String, Any] = {
    val stream = java.lang.System.err
    val logger: ZLogger[String, String] = logFormat match
      case LogFormat.Colored => ColoredLogger
      case LogFormat.Plain   => PlainLogger
    logger
      .map { line =>
        try stream.println(line)
        catch {
          case t: VirtualMachineError => throw t
          case _: Throwable           => ()
        }
      }
      .filterLogLevel(_ >= logLevel)
  }

  def install(
      logFormat: LogFormat,
      logLevel: LogLevel
  ): ZLayer[Any, Nothing, Unit] =
    ZLayer.scoped {
      ZIO.withLoggerScoped(makePrintErrLogger(logFormat, logLevel))
    }
}

enum LogFormat:
  case Plain, Colored
