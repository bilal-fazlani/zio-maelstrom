---
icon: material/play-outline
hide:
  - toc
  - navigation
---

# Getting started

ZIO-Maelstrom is makes it easier to solve [Gossip Glomers](https://fly.io/dist-sys/) challenges in [:simple-scala: Scala](https://www.scala-lang.org/) using [ZIO](https://zio.dev/)

[Gossip Glomers](https://fly.io/dist-sys/) is a series of distributed systems challenges by [Fly.io](https://fly.io/) in collaboration with [Kyle Kingsbury](https://aphyr.com/about), author of [Jepsen](https://jepsen.io/). It's a great way to learn distributed systems by writing your own.

The challenges are built on top of a platform called [:simple-github: Maelstrom](https://github.com/jepsen-io/maelstrom). Maelstrom simulates network traffic using stdin and stdout of node processes. 

<div markdown="1" class="quote">
<div markdown="1" class="quotation-mark">“</div>
<div markdown="1" class="quote-content">
ZIO-Maelstrom is a high level Scala driver for Maelstrom which abstracts away the low level details of the platform and let you focus on the challenges
</div>
</div>

!!! note "Alternatives"
    Maelstrom has an official Go library called [:fontawesome-brands-golang: maelstrom-go](https://pkg.go.dev/github.com/jepsen-io/maelstrom/demo/go)

    There is also a java open-source library called [:fontawesome-brands-java: maelstrom-java](https://github.com/lant/maelstrom-java)

## Prerequisites

The first thing to do is to [download and setup](https://github.com/jepsen-io/maelstrom/blob/main/doc/01-getting-ready/index.md#getting-ready) the Maelstrom CLI. It can be downloaded from [here](https://github.com/jepsen-io/maelstrom/releases/latest)

Since Maelstrom is Closure program, you will need JVM installed on your machine. You will also need graphviz & gnuplot.

```bash
brew install openjdk graphviz gnuplot
```

## Installation

Create an sbt project with Scala 3 and add the following dependency:

```scala
libraryDependencies += "com.bilal-fazlani" %% "zio-maelstrom" % "0.1.1"
```

## Creating a node

A node is a process that can receive messages and respond to them. It can also send messages to other nodes. Take a look at [Challenge #1: Echo](https://fly.io/dist-sys/1/) to understand the basics of a node. This node is implemented in Go. You can find an equivalent implementation in Scala [here](echo.md)

## Running a node

Maelstrom requires a binary executable file to launch a node. There are several ways to create a binary executable file:

- Create a fat JAR using [sbt-assembly](https://www.baeldung.com/scala/sbt-fat-jar) plugin
- Create a fat JAR using [Coursier bootstrap](https://get-coursier.io/docs/cli-bootstrap)
- Create an OS native app using [sbt-native-image](https://github.com/scalameta/sbt-native-image) plugin

I have tested with Coursier bootstrap and it works well. But sbt-assembly should be even more simple to work with.

Once you have an executable file, you can run it using the Maelstrom CLI. 

```bash
./maelstrom test -w echo \ # (1)!
  --bin ./echo.jar \ # (2)!
  --node-count 1 \ # (3)!
  --time-limit 10 \ # (4)!
```

1.  `-w` is the workload. Each challenge may have a different workload
2.  `--bin` is the path to the executable file
3.  `--node-count` is the number of nodes to run
4.  `--time-limit` specifies the duration of simulation in seconds