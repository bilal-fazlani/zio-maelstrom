---
icon: fontawesome/solid/network-wired
hide:
  - toc
---

# Unique ID Generation

!!! info
    This example is for [Challenge #2: Unique ID Generation](https://fly.io/dist-sys/2/)

In this challenge we generate globally-unique IDs in a distributed network

<!--codeinclude-->
[Imports](../../examples/unique-ids/src/main/scala/com/example/Main.scala) inside_block:imports
<!--/codeinclude-->

Here, we define the protocol of the node. It includes messages which the node can handle and messages it can send

<!--codeinclude-->
[Message definitions](../../examples/unique-ids/src/main/scala/com/example/Main.scala) inside_block:messages
<!--/codeinclude-->

Unlike [echo](echo.md), this node has some state which we have modeled using `Ref[Int]`. We increment the `Int` every time we generate a new Id. To make it unique across the cluster, we append the node Id to the generated id.

!!! tip "Why use a `Ref`?"
    Using a `Ref` ensures that I can update the state in a thread-safe manner. This is important because the **messages are received and processed concurrently**

<!--codeinclude-->
[Node application](../../examples/unique-ids/src/main/scala/com/example/Main.scala) block:Main
<!--/codeinclude-->

1.  `me` method returns the `NodeId` of self node. It's a [context function](https://docs.scala-lang.org/scala3/reference/contextual/context-functions.html) which is only available inside receive block

I have used `ZLayer` to inject the state and also to make sure the same `Ref` is used across the codebase in case we want to make the code more modular.

!!! tip
    Note the use of `.provideRemaining` to provide `Ref[Int]` layer to the program. This method is use to provide all the layers except `MaelstromRuntime` & `Scope` which are provided by the framework.

!!! note
    Source code for this example can be found on [:simple-github: Github](https://github.com/bilal-fazlani/zio-maelstrom/blob/main/examples/unique-ids/src/main/scala/com/example/Main.scala)