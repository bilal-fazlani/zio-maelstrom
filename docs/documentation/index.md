---
icon: material/file-document
hide:
  - toc
  - navigation
---

# Documentation

ZIO-Maelstrom is makes it easier to solve [Gossip Glomers](https://fly.io/dist-sys/) challenges in [:simple-scala: Scala](https://www.scala-lang.org/) using [ZIO](https://zio.dev/)

[Gossip Glomers](https://fly.io/dist-sys/) is a series of distributed systems challenges brought to you by [Fly.io](https://fly.io/) in collaboration with [Kyle Kingsbury](https://aphyr.com/about), author of [Jepsen](https://jepsen.io/). It's a great way to learn distributed systems by writing your own.

The challenges are built on top of a platform called [:simple-github: Maelstrom](https://github.com/jepsen-io/maelstrom). Maelstrom simulates network traffic using stdin and stdout of node processes. 

<div markdown="1" class="quote">
<div markdown="1" class="quotation-mark">“</div>
<div markdown="1" class="quote-content">
ZIO-Maelstrom is a high level Scala driver for Maelstrom which abstracts away the low level details of the platform and let you focus on the challenges
</div>
</div>

!!! note "Other libraries"
    There is also an official Go library for Maelstrom called [:fontawesome-brands-golang: maelstrom-go](https://pkg.go.dev/github.com/jepsen-io/maelstrom/demo/go)

    There a java open-source library called [:fontawesome-brands-java: maelstrom-java](https://github.com/lant/maelstrom-java)

## Prerequisites

The first thing to do is to [download and setup](https://github.com/jepsen-io/maelstrom/blob/main/doc/01-getting-ready/index.md#getting-ready) the Maelstrom CLI.

Since Maelstrom is Closure program, you will need JVM installed on your machine. You will also need graphviz & gnuplot.

```bash
brew install openjdk graphviz gnuplot
```

## Getting started

Create an sbt project with Scala 3 and add the following dependency:

```scala
libraryDependencies += "com.bilal-fazlani" %% "zio-maelstrom" % "0.1.1"
```
