import sbtwelcome._
import scala.sys.process._

val scala3Version = "3.3.0"

ThisBuild / resolvers ++= Resolver.sonatypeOssRepos("snapshots")

ThisBuild / scalaVersion := scala3Version
ThisBuild / organization := "com.bilal-fazlani"

logo :=
  raw"""
     |
     |███████ ██  ██████        ███    ███  █████  ███████ ██      ███████ ████████ ██████   ██████  ███    ███ 
     |   ███  ██ ██    ██       ████  ████ ██   ██ ██      ██      ██         ██    ██   ██ ██    ██ ████  ████ 
     |  ███   ██ ██    ██ █████ ██ ████ ██ ███████ █████   ██      ███████    ██    ██████  ██    ██ ██ ████ ██ 
     | ███    ██ ██    ██       ██  ██  ██ ██   ██ ██      ██           ██    ██    ██   ██ ██    ██ ██  ██  ██ 
     |███████ ██  ██████        ██      ██ ██   ██ ███████ ███████ ███████    ██    ██   ██  ██████  ██      ██ 
     |                                                                                                          
     |${scala.Console.GREEN}Scala ${scalaVersion.value}${scala.Console.RESET}
     |
     |""".stripMargin

logoColor        := scala.Console.BLUE
aliasColor       := scala.Console.GREEN
descriptionColor := scala.Console.YELLOW

usefulTasks := Seq(
  UsefulTask(
    "publishLocal;bootstrap",
    "Create a fat jar file"
  ),
  UsefulTask("test", "Run unit tests")
)

lazy val bootstrap = taskKey[Unit]("Create a fat jar file")

lazy val isCI = sys.env.get("CI").nonEmpty

bootstrap := {
  val projectsToPlublish = Seq("echo", "unique-ids")

  projectsToPlublish.foreach { projectName =>
    val process = Process(
      Seq(
        "cs",
        "bootstrap",
        "-r",
        "sonatype:snapshots",
        "--standalone",
        s"com.bilal-fazlani:${projectName}_3:0.1.0-SNAPSHOT",
        "-f",
        "-o",
        s"$projectName.j"
      )
    )
    process !
  }
}

lazy val root = project
  .in(file("."))
  .settings(
    name           := "zio-maelstrom-root",
    publish / skip := true,
    scalacOptions += "-Wunused:all"
  )
  .aggregate(maelstrom, echo, uniqueIds)

lazy val maelstrom = project
  .in(file("zio-maelstrom"))
  .settings(
    name := "zio-maelstrom",
    scalacOptions += "-Wunused:all",
    libraryDependencies ++= Seq(
      Libs.zio,
      Libs.zioConcurrent,
      Libs.rainbowCli,
      Libs.zioStreams,
      Libs.zioJson,
      Libs.zioTest % Test
    )
  )

lazy val echo = project
  .in(file("examples/echo"))
  .settings(
    name := "echo",
    scalacOptions += "-Wunused:all",
    publish / skip      := isCI,
    Compile / mainClass := Some("com.example.echo.Main")
  )
  .dependsOn(maelstrom)

lazy val uniqueIds = project
  .in(file("examples/unique-ids"))
  .settings(
    name := "unique-ids",
    scalacOptions += "-Wunused:all",
    publish / skip      := isCI,
    Compile / mainClass := Some("com.example.uniqueIds.Main")
  )
  .dependsOn(maelstrom)
