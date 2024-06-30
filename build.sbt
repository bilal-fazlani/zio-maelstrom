import sbtwelcome._
import scala.sys.process._

val scala3Version = "3.4.2"

ThisBuild / scalaVersion           := scala3Version
ThisBuild / organization           := "com.bilal-fazlani"
ThisBuild / organizationName       := "Bilal Fazlani"
ThisBuild / sonatypeCredentialHost := Sonatype.sonatype01

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/bilal-fazlani/zio-maelstrom"),
    "https://github.com/bilal-fazlani/zio-maelstrom.git"
  )
)
ThisBuild / developers := List(
  Developer(
    "bilal-fazlani",
    "Bilal Fazlani",
    "bilal.m.fazlani@gmail.com",
    url("https://bilal-fazlani.com")
  )
)
ThisBuild / licenses :=
  List("MIT License" -> url("https://github.com/bilal-fazlani/zio-maelstrom/blob/main/LICENSE"))
ThisBuild / homepage := Some(url("https://zio-maelstrom.bilal-fazlani.com/"))

logo := {
  if (isCI) ""
  else {
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
  }
}

logoColor        := scala.Console.BLUE
aliasColor       := scala.Console.GREEN
descriptionColor := scala.Console.YELLOW

usefulTasks := {
  if (isCI) usefulTasks.value
  else
    Seq(
      UsefulTask("publishLocal;bootstrap", "Create a fat jar file"),
      UsefulTask("test", "Run unit tests")
    )
}

lazy val bootstrap = taskKey[Unit]("Create a fat jar file")

def isCI: Boolean = sys.env.get("CI").nonEmpty

bootstrap := {
  val projectsToPlublish = Seq("echo", "unique-ids", "broadcast")

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
  .settings(name := "zio-maelstrom-root", publish / skip := true)
  .aggregate(zioMaelstrom, echo, uniqueIds, broadcast)

lazy val zioMaelstrom = project
  .in(file("zio-maelstrom"))
  .settings(
    name := "zio-maelstrom",
    logo := "",
    scalacOptions += "-Wunused:all",
    sonatypeCredentialHost := Sonatype.sonatype01,
    libraryDependencies ++=
      Seq(
        Libs.zio,
        Libs.zioConcurrent,
        Libs.zioStreams,
        Libs.zioJson,
        Libs.zioTest    % Test,
        Libs.zioTestSbt % Test
      )
  )

lazy val echo = project
  .in(file("examples/echo"))
  .settings(
    name := "echo",
    logo := "",
    scalacOptions += "-Wunused:all",
    version             := "0.1.0-SNAPSHOT",
    publish / skip      := isCI,
    Compile / mainClass := Some("com.example.echo.Main")
  )
  .dependsOn(zioMaelstrom)

lazy val uniqueIds = project
  .in(file("examples/unique-ids"))
  .settings(
    name := "unique-ids",
    logo := "",
    scalacOptions += "-Wunused:all",
    version             := "0.1.0-SNAPSHOT",
    publish / skip      := isCI,
    Compile / mainClass := Some("com.example.uniqueIds.Main")
  )
  .dependsOn(zioMaelstrom)

lazy val broadcast = project
  .in(file("examples/broadcast"))
  .settings(
    name := "broadcast",
    logo := "",
    scalacOptions += "-Wunused:all",
    version             := "0.1.0-SNAPSHOT",
    publish / skip      := isCI,
    Compile / mainClass := Some("com.example.broadcast.Main")
  )
  .dependsOn(zioMaelstrom)
