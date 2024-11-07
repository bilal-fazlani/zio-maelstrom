package com.bilalfazlani.zioMaelstrom

import com.bilalfazlani.zioMaelstrom.services.{LinKv, LinTso, LwwKv, SeqKv}
import zio.{Scope, ZLayer}

type Services = LinKv & SeqKv & LwwKv & LinTso

// definition {
type MaelstromRuntime = Initialisation & RequestHandler & MessageSender & MessageIdStore &
  Services & Settings
// }

object MaelstromRuntime:
  // doc_incluide {
  private[zioMaelstrom] def live(
      settings: Settings,
      inputStream: ZLayer[Any, Nothing, InputStream],
      initContext: Option[Context]
  ): ZLayer[Any, Nothing, MaelstromRuntime] = {
    val contextLayer = initContext.fold(Initialisation.run)(Initialisation.fake)
    ZLayer.make[MaelstromRuntime](
      // pure layers
      Scope.default,
      ZLayer.succeed(settings),
      MessageSender.live,
      RequestHandler.live,
      InputChannel.live,
      inputStream,
      OutputChannel.stdOut,
      CallbackRegistry.live,
      MessageIdStore.live,

      // Services
      LinKv.live,
      SeqKv.live,
      LwwKv.live,
      LinTso.live,

      // effectful layers
      contextLayer,
      ResponseHandler.start
    )
  }
  // }

  val live: ZLayer[Any, Nothing, MaelstromRuntime] = live(Settings(), InputStream.stdIn, None)
