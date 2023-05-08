val scala3Version = "3.2.2"

ThisBuild / scalaVersion := scala3Version

lazy val root = project
  .in(file("."))
  .settings(
    name := "gossip-glomers-scala",
    publish / skip := true
  )
  .aggregate(maelstrom, echo)

lazy val maelstrom = project
  .in(file("zio-maelstrom"))
  .settings(
    name := "zio-maelstrom",
    IDE
    libraryDependencies ++= Seq(
      Libs.zio,
      Libs.zioStreams,
      Libs.zioJson,
      Libs.zioTest % Test
    )
  )

lazy val echo = project
  .in(file("echo"))
  .settings(
    name := "echo"
  )
  .dependsOn(maelstrom)
