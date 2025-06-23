---
icon: fontawesome/solid/network-wired
hide:
  - toc
---

# Broadcast

!!! info
    his example is for [Challenge #3b: Multi-Node Broadcast](https://fly.io/dist-sys/3b/)

The goal is to make nodes gossip messages with each other so that "eventually" all nodes receive all messages.

<!--codeinclude-->
[Imports](../../examples/broadcast/src/main/scala/com/example/Main.scala) inside_block:imports
<!--/codeinclude-->

As part of the broadcast challenge, nodes will receive work with below messages

**Topology**

:   `topology` message contains the list of all nodes in the cluster mapped to their neighbors.

**Broadcast**

:   `broadcast` message has an Integer number in it. Not all the nodes will receive all the numbers in broadcast. The goal is to make sure that all nodes get all the numbers eventually using gossip.


**Read**

:   `read` Maelstrom will expect a reply to this message with all the number that that node knows about either via broadcast or gossip.

---

**Gossip**

:   Our nodes will send `gossip` messages to each other to gossip the numbers they have received via broadcast.

Because there are multiple messages, lets create a sealed trait to represent them. We will use `zio-json` annotations to make it easy to serialize and deserialize these messages.

<!--codeinclude-->
[Message definitions](../../examples/broadcast/src/main/scala/com/example/Main.scala) inside_block:input_messages
<!--/codeinclude-->

1.  `jsonDiscriminator` is required when want to receive multiple types of message. "type" field is part of standard [Maelstrom message format](https://github.com/jepsen-io/maelstrom/blob/main/doc/protocol.md#message-bodies) so we use the same field to differentiate between different messages.
2.  This derives decoders for all children of `InMessage` trait
3.  Since `Gossip` message is sent out from nodes, it also needs an Encoder

<!--codeinclude-->
[Reply messages](../../examples/broadcast/src/main/scala/com/example/Main.scala) inside_block:reply_messages
reply_messages
<!--/codeinclude-->

<!--codeinclude-->
[Node state](../../examples/broadcast/src/main/scala/com/example/Main.scala) inside_block:state
<!--/codeinclude-->

<!--codeinclude-->
[Node application](../../examples/broadcast/src/main/scala/com/example/Main.scala) block:Main
<!--/codeinclude-->

1.  This is a naive implementation of gossip protocol. We are sending all the number in a node's state to all its neighbors.
2.  The gossiping once started, will trigger every 500 milliseconds and keep happening forever
3.  Save the new number and reply OK to the sender
4.  `me` is the current node's id
5.  Add node's neighbors to the state
6.  Reply OK to the sender
7.  Start gossiping after arrival of `topology` message
8.  Add gossip received by other nodes to node's state
9.  Using the `provideSome` method to provide all the layers except `MaelstromRuntime` & `Scope`

!!! tip
    This is a naive implementation of gossip protocol. We are sending all the numbers in a node's state to all its neighbors. This will not scale well. Find a better way to do this.

!!! note
    Source code for this example can be found on [:simple-github: Github](https://github.com/bilal-fazlani/zio-maelstrom/blob/main/examples/broadcast/src/main/scala/com/example/Main.scala)
