import sbt._

object Libs {
  lazy val zioVersion = "2.0.13"

  lazy val zio = "dev.zio" %% "zio" % zioVersion
  lazy val zioStreams = "dev.zio" %% "zio-streams" % zioVersion
  lazy val zioTest = "dev.zio" %% "zio-test" % zioVersion
}
