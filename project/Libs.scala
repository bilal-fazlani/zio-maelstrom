import sbt._

object Libs {
  lazy val zioVersion = "2.0.18"
  private val ZIO     = "dev.zio"

  lazy val zio           = ZIO %% "zio"            % zioVersion
  lazy val zioConcurrent = ZIO %% "zio-concurrent" % zioVersion
  lazy val zioStreams    = ZIO %% "zio-streams"    % zioVersion
  lazy val zioTest       = ZIO %% "zio-test"       % zioVersion
  lazy val zioTestSbt    = ZIO %% "zio-test-sbt"   % zioVersion
  lazy val zioJson       = ZIO %% "zio-json"       % "0.6.2"
}
