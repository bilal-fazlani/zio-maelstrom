# zio-maelstrom

## Learn distributed systems in Scala using ZIO and Maelstrom

**Documentation: https://zio-maelstrom.bilal-fazlani.com/**

ZIO-Maelstrom is makes it easier to solve [Gossip Glomers](https://fly.io/dist-sys/) challenges in [Scala](https://www.scala-lang.org/) using [ZIO](https://zio.dev/)

[Gossip Glomers](https://fly.io/dist-sys/) is a series of distributed systems challenges by [Fly.io](https://fly.io/) in collaboration with [Kyle Kingsbury](https://aphyr.com/about), author of [Jepsen](https://jepsen.io/). It's a great way to learn distributed systems by writing your own.

The challenges are built on top of a platform called [Maelstrom](https://github.com/jepsen-io/maelstrom). Maelstrom simulates network traffic using stdin and stdout of nodes. **ZIO-Maelstrom is a high level Scala driver for Maelstrom which abstract away the low level details of the platform and let you focus on the challenges** 

