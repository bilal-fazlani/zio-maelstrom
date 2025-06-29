package com.bilalfazlani.zioMaelstrom

import com.bilalfazlani.zioMaelstrom.services.{LinKv, LinTso, LwwKv, SeqKv}
import zio.{Scope, ZIO, ZLayer}

type Services = LinKv & SeqKv & LwwKv & LinTso

// definition {
type MaelstromRuntime = Initialisation & RequestHandler & MessageSender & MessageIdStore & Services & Settings
// }

object MaelstromRuntime:

  def me     = ZIO.serviceWith[Initialisation](_.context.me)
  def others = ZIO.serviceWith[Initialisation](_.context.others)
  def src    = ZIO.serviceWith[MessageContext](_.remote)

  // doc_include {
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

      // effect-ful layers
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

      // effect-ful layers
      Initialisation.fake(context),
      ResponseHandler.start
    )
  }
