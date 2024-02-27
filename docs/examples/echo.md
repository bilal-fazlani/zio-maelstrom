---
icon: fontawesome/solid/network-wired
hide:
  - toc
---

# Echo

!!! info
    This example is for [Challenge #1: Echo](https://fly.io/dist-sys/1/)

It demonstrates how to create a simple node that echoes messages back to the sender

<!--codeinclude-->
[Imports](../../examples/echo/src/main/scala/com/example/Main.scala) inside_block:imports
<!--/codeinclude-->

Here, we define the protocol of the node. It includes messages which the node can handle and messages it can send. Create data classes only for the body part of a maelstrom message. The top level fields (i.e. src, dest) are handled by the library

<!--codeinclude-->
[Message definitions](../../examples/echo/src/main/scala/com/example/Main.scala) inside_block:messages
<!--/codeinclude-->

1.  Every messages that needs an acknowledgement or reply should extend `NeedsReply`
2.  All input messages need a way to decode them. We use `zio-json` to derive `JsonDecoder`
3.  Messages that need to be sent out need to extend from `Sendable`. If they are a reply to some other message, they should also extend from `Reply`
4.  Outgoing messages need `JsonEncoders`

!!! note
    Refer to [Maelstrom documentation](https://github.com/jepsen-io/maelstrom/blob/main/doc/protocol.md#message-bodies) for more information on the standard message fields like `msg_id`, `type`, `in_reply_to` etc

A node is defined by extending the `MaelstromNode` trait and providing a `program` value. The `program` value is a ZIO effect that defines the behavior of the node. The `receive` function is used to define a handler for incoming request messages.

<!--codeinclude-->
[Node application](../../examples/echo/src/main/scala/com/example/Main.scala) block:Main
<!--/codeinclude-->

1. Note that definition needs to be an `object` for it to be runnable
2. `reply` is a function that sends a reply to the sender of the message. It takes a `Sendable` & `Reply` message as an argument. You can only call reply on messages that extend `NeedsReply`

Using maelstrom DSL functions such as `receive` and `reply` requires `MaelstromRuntime` which is automatically provided my `MaelstromNode`.

!!! note
    Source code for this example can be found on [:simple-github: Github](https://github.com/bilal-fazlani/zio-maelstrom/blob/main/examples/echo/src/main/scala/com/example/Main.scala)
