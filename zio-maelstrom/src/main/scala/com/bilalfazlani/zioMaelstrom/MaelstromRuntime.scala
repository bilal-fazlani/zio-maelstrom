package com.bilalfazlani.zioMaelstrom

import com.bilalfazlani.zioMaelstrom.services.LinKv
import com.bilalfazlani.zioMaelstrom.services.LinTso
import com.bilalfazlani.zioMaelstrom.services.LwwKv
import com.bilalfazlani.zioMaelstrom.services.SeqKv
import zio.Scope
import zio.ZLayer

type Services = LinKv & SeqKv & LwwKv & LinTso

// definition {
type MaelstromRuntime = Initialisation & RequestHandler & MessageSender & MessageIdStore &
  Services & Settings
// }

object MaelstromRuntime:
  // doc_incluide {
  private[zioMaelstrom] def live(
      settings: Settings
  ): ZLayer[Any, Nothing, MaelstromRuntime] = {

    ZLayer.make[MaelstromRuntime](
      // pure layers
      Scope.default,
      ZLayer.succeed(settings),
      MessageSender.live,
      RequestHandler.live,
      InputChannel.live,
      InputStream.stdIn,
      OutputChannel.stdOut,
      CallbackRegistry.live,
      MessageIdStore.live,

      // Services
      LinKv.live,
      SeqKv.live,
      LwwKv.live,
      LinTso.live,

      // effectful layers
      Initialisation.run,
      ResponseHandler.start
    )
  }
  // }

  val live: ZLayer[Any, Nothing, MaelstromRuntime] = live(Settings())

  private[zioMaelstrom] def static(
      settings: Settings,
      inputStream: ZLayer[Any, Nothing, InputStream],
      context: Context
  ): ZLayer[Any, Nothing, MaelstromRuntime] = {

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
      Initialisation.fake(context),
      ResponseHandler.start
    )
  }
