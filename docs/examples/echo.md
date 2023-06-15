---
icon: fontawesome/solid/network-wired
hide:
  - toc
---

# Echo

!!! info
    Examples are from [Gossip Glomers](https://fly.io/dist-sys/) - A series of distributed systems challenges brought to you by Fly.io

This example is for [Challenge #1: Echo](https://fly.io/dist-sys/1/)

It demonstrates how to create a simple node that echoes messages back to the sender

<!--codeinclude-->
[Imports](../../examples/echo/src/main/scala/com/example/Main.scala) inside_block:imports
<!--/codeinclude-->

Here, we define the protocol of the node. It includes messages which the node can handle and messages it can send

<!--codeinclude-->
[Message definitions](../../examples/echo/src/main/scala/com/example/Main.scala) inside_block:messages
<!--/codeinclude-->

This is the Node definition. It defines the behavior of the node and is a typical ZIO application. We use the `recieve` function to define a handler for incoming request messages

<!--codeinclude-->
[Node application](../../examples/echo/src/main/scala/com/example/Main.scala) block:Main
<!--/codeinclude-->

Using maelstrom DSL functions such as `receive` and `reply` requires `MaelstromRuntime` which can be provided as a `ZLayer` to the application as shown   above