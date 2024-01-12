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

The first thing to do is to [download and setup](https://github.com/jepsen-io/maelstrom/blob/main/doc/01-getting-ready/index.md#getting-ready) the Maelstrom CLI. It can be downloaded from [here](https://github.com/jepsen-io/maelstrom/releases/latest)

Since Maelstrom is Closure program, you will need JVM installed on your machine. You will also need graphviz & gnuplot.

```bash
brew install openjdk graphviz gnuplot
```

## Get started using template

The easiest way to get started is to create a new project using zio-maelstrom github template

[View template :simple-github:](https://github.com/bilal-fazlani/gossip-glomers-scala-template){ .md-button .md-button--primary }

The template contains

- [x] sbt project with zio-maelstrom dependency
- [x] empty directory structure for all the solutions
- [x] scripts to compile solutions using graalvm native image
- [x] scripts to run solutions with expected workload
 
You can find the instructions to use it in the [README](https://github.com/bilal-fazlani/gossip-glomers-scala-template#readme) of the template project

---

## Get started from scratch 

Create an sbt project with Scala 3 and add the following dependency:

![Sonatype Nexus (Releases)](https://img.shields.io/nexus/r/com.bilal-fazlani/zio-maelstrom_3?color=%23099C05&label=STABLE%20VERSION&server=https%3A%2F%2Foss.sonatype.org&style=for-the-badge)

```scala
libraryDependencies += "com.bilal-fazlani" %% "zio-maelstrom" % "<VERSION>"
```

**Creating a node**

A node is a process that can receive messages and respond to them. It can also send messages to other nodes. Take a look at [Challenge #1: Echo](https://fly.io/dist-sys/1/) to understand the basics of a node. This node is implemented in Go. You can find an equivalent implementation in Scala [here](echo.md)

**Running a node**

Maelstrom requires a binary executable file to launch a node. There are several ways to create a binary executable file

***Option 1. Create a fat JAR using [sbt-assembly](https://www.baeldung.com/scala/sbt-fat-jar) plugin***
  
This is very simple to use, but the resulting JAR does not contain any preamble so you have to run it using `java -jar` command. This is not ideal for Maelstrom because it requires a binary executable file. Because of this reason, we have to create wrapper scripts like:

```bash
#!/bin/bash
if [[ $BASH_SOURCE = */* ]]; then
DIR=${BASH_SOURCE%/*}/
else
DIR=./
fi
exec java -jar "$DIR/echo.jar"
``` 

And then using this script to run maelstrom simulation
  
***Create a fat JAR using [Coursier bootstrap](https://get-coursier.io/docs/cli-bootstrap)***
  
I have tried using coursier bootstrap and it is better than sbt-assembly because it creates a preamble in the JAR file. This means that you can run the JAR file directly without any wrapper scripts. But we still have a problem. Challenges which require a lot of nodes, for example [Challenge #3d](https://fly.io/dist-sys/3d/), don't work well because of JVM startup overhead time.

***Create an OS native app using [sbt-native-image](https://github.com/scalameta/sbt-native-image) plugin***

This is the best option. Unfortunately also the most complicated one among the three. sbt-native-image does help you create a native executable file, but it also requires reflection configurations. native image agent can help you generate the configurations automatically, but there is still some work to be done. You first have to run the solution with agent with basic load to generate the configs. Then you can create the native executable file. The [template project](#get-started-using-template) has scripts to do automate this process.


Once you have an executable file, you can run it using the Maelstrom CLI. 

```bash
./maelstrom test -w echo \ # (1)!
  --bin ./target/echo \ # (2)!
  --node-count 1 \ # (3)!
  --time-limit 10 \ # (4)!
```

1.  `-w` is the workload. Each challenge may have a different workload
2.  `--bin` is the path to the executable file
3.  `--node-count` is the number of nodes to run
4.  `--time-limit` specifies the duration of simulation in seconds