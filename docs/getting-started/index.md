---
icon: material/play-outline
hide:
  - toc
  - navigation
---

# Getting started

ZIO-Maelstrom makes it easier to solve [Gossip Glomers](https://fly.io/dist-sys/) challenges in [:simple-scala: Scala](https://www.scala-lang.org/) using [ZIO](https://zio.dev/)

[Gossip Glomers](https://fly.io/dist-sys/) is a series of distributed systems challenges by [Fly.io](https://fly.io/) in collaboration with [Kyle Kingsbury](https://aphyr.com/about), author of [Jepsen](https://jepsen.io/). It's a great way to learn distributed systems by writing your own.

The challenges are built on top of a platform called [:simple-github: Maelstrom](https://github.com/jepsen-io/maelstrom). Maelstrom simulates network traffic using stdin and stdout of node processes. 

<div markdown="1" class="quote">
<div markdown="1" class="quotation-mark">“</div>
<div markdown="1" class="quote-content">
ZIO-Maelstrom is a high level Scala driver for Maelstrom which abstracts away the low level details of the platform and let's you focus on solving distributed systems challenges
</div>
</div>

!!! note "Alternatives"
    Maelstrom has an official Go library called [maelstrom-go :fontawesome-brands-golang:](https://pkg.go.dev/github.com/jepsen-io/maelstrom/demo/go)

    There is also a java open-source library called [maelstrom-java :fontawesome-brands-java:](https://github.com/lant/maelstrom-java)

## Prerequisites

### 1. Java

Since Maelstrom is Closure program, you will need JVM installed on your machine. This templates makes use of graalvm native image to create OS native executable files for improved node performance. I have used [sdkman](https://sdkman.io) to install java.

```bash
sdk install java 24.0.1-graalce
```

### 2. SBT

SBT is a build tool for Scala projects.

```bash
brew install sbt
```

### 3. Maelstrom

It can be downloaded from [here](https://github.com/jepsen-io/maelstrom/releases/latest)

!!! important warning
    Ensure that maelstrom is added in your `PATH` variable so it can be run using `maelstrom` command from anywhere

You will also need graphviz & gnuplot. These are maelstrom dependencies for visualizing the simulation results.

```bash
brew install graphviz gnuplot
```

### 4. Coursier

Coursier is the Scala application and artifact manager. You can download it from [here](https://get-coursier.io/docs/cli-installation)

```bash
brew install coursier/formulas/coursier
```

Verify that it is available in your `PATH` variable and you can run it using `coursier` command

## Get started using template

The easiest way to get started is to create a new project using zio-maelstrom GitHub template

[View template :simple-github:](https://github.com/bilal-fazlani/gossip-glomers-scala-template){ .md-button .md-button--primary }

The template contains

- [x] sbt project with zio-maelstrom dependency
- [x] empty directory structure for all the solutions
- [x] scripts to compile solutions using graalvm native image
- [x] scripts to run solutions with expected workload

Create a new project using the template repository or clone the repository

!!! important warning
    If you have not installed graalvm using [sdkman](https://sdkman.io), please update "nativeImageGraalHome" in build.sbt to point to your graalvm installation directory. You may have to install `native-image` component if it is not already installed

Echo challenge for example, has `1-echo/src/main/scala/gossipGlomers/Main.scala` as shown below

=== "Template"
    ```scala
    package gossipGlomers

    import com.bilalfazlani.zioMaelstrom.*

    object Main extends MaelstromNode:

      val program = ???
    ```
=== "Solution"
    ```scala
    package gossipGlomers

    import com.bilalfazlani.zioMaelstrom.*

    import zio.*
    import zio.json.*
    import com.bilalfazlani.zioMaelstrom.*

    case class Echo(echo: String, msg_id: MessageId) extends NeedsReply derives JsonDecoder

    case class EchoOk(echo: String, in_reply_to: MessageId, `type`: String = "echo_ok") 
        extends Sendable, Reply
        derives JsonEncoder

    object Main extends MaelstromNode:

      val program = receive[Echo] { 
        case Echo(echo, msg_id) => reply(EchoOk(msg_id, echo))
      }
    ```

After writing the solution, we can run the solution using either using a JVM process or a native image

## Running the simulation

### Create and run a fat JAR

=== "Commands"
    ```bash
    # This command will compile and create a fat jar and a runner script
    sbt "echo/bootstrap"

    # This command will run maelstraom with appropriate work load arguments
    ./1-echo/target/jvm-simulation.sh
    ```
=== "Output"
    ```
    jepsen.core {:perf {:latency-graph {:valid? true},
            :rate-graph {:valid? true},
            :valid? true},
    :timeline {:valid? true},
    :exceptions {:valid? true},
    :stats {:valid? true,
            :count 45,
            :ok-count 45,
            :fail-count 0,
            :info-count 0,
            :by-f {:echo {:valid? true,
                          :count 45,
                          :ok-count 45,
                          :fail-count 0,
                          :info-count 0}}},
    :availability {:valid? true, :ok-fraction 1.0},
    :net {:all {:send-count 92,
                :recv-count 92,
                :msg-count 92,
                :msgs-per-op 2.0444446},
          :clients {:send-count 92, :recv-count 92, :msg-count 92},
          :servers {:send-count 0,
                    :recv-count 0,
                    :msg-count 0,
                    :msgs-per-op 0.0},
          :valid? true},
    :workload {:valid? true, :errors ()},
    :valid? true}
    
    
    Everything looks good! ヽ(‘ー`)ノ
    ```

### Create and run a graalvm native image

=== "Commands"
    ```bash
    # This command will run a reduced workload to generate graalvm native reflection configurations
    # Those configurations will be stored in resources/META-INF/native-image dir of the project
    sbt "echo/maelstromRunAgent"

    # This command will compile and create a native image and a runner script
    # It will use the reflection configurations generated in previous step
    sbt "echo/nativePackage"

    # This command will run maelstraom with appropriate work load arguments
    ./1-echo/target/native-simulation.sh
    ```
=== "Output"
    ```
    jepsen.core {:perf {:latency-graph {:valid? true},
            :rate-graph {:valid? true},
            :valid? true},
    :timeline {:valid? true},
    :exceptions {:valid? true},
    :stats {:valid? true,
            :count 44,
            :ok-count 44,
            :fail-count 0,
            :info-count 0,
            :by-f {:echo {:valid? true,
                          :count 44,
                          :ok-count 44,
                          :fail-count 0,
                          :info-count 0}}},
    :availability {:valid? true, :ok-fraction 1.0},
    :net {:all {:send-count 90,
                :recv-count 90,
                :msg-count 90,
                :msgs-per-op 2.0454545},
          :clients {:send-count 90, :recv-count 90, :msg-count 90},
          :servers {:send-count 0,
                    :recv-count 0,
                    :msg-count 0,
                    :msgs-per-op 0.0},
          :valid? true},
    :workload {:valid? true, :errors ()},
    :valid? true}


    Everything looks good! ヽ(‘ー`)ノ
    ```

## Maelstrom reports

To view run results, first go the target directory

```bash
cd 1-echo/target
```

Then run the following command to serve report in browser

```bash
maelstrom serve
```
