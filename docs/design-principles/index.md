---
icon: fontawesome/solid/cubes
hide:
  - toc
  - navigation
---

### Principles

zio-maelstrom is implemented as a library (or a driver) and does not try become a framework. Its because the whole point of it is to learn and have fun. Frameworks are not bad but they box you and restrict what you can do. It makes you faster but you learn less and there is less scope for creativity and exploration.

I have tried to keep things as simple as possible. The library follows ZIO idiomatic practices and is functional in nature. 

**What this library does for you**

- [x] Abstracts STDIN and STDOUT and exposes a simple Network IO interface to send and receive messages
- [x] Handles `Init` message
- [x] Calls your message handlers concurrently when messages arrive
- [x] RPC messages - maintains correlation between requests and responses
- [x] Gives you traits - `Sendable`, `Reply` and `NeedsResponse` to help you define your own protocol

**What it does not do**

- [ ] Define messaging protocols
- [ ] Define message handlers
- [ ] Handle errors and timeouts
- [ ] Maintain node state

These are the things you need to do. Because you are using `ZIO`, you get super powers to deal with concurrency, domain errors, timeout errors, state management, retries and more. The possibilities are endless with `ZIO` ecosystem and primitives such as `Ref`, `Promise`, `Queue`, `STM` etc.

### Design

The whole runtime is implemented as `ZLayer`s. Because `ZLayer` allows creation from a `ZIO` effect, it is possible to run effects before message handlers are invoked. An example of this is the handling of `init` message. The library handles the `init` message first and then invokes the message handlers. This is done by creating a `ZLayer` that runs the effect to handle `init` message.

This is more evident if we look at the definition of type `MaelstromRuntime`

```scala title="MaelstromRuntime"
type MaelstromRuntime = Initialisation & MessageSender & Logger & Settings
```

`Initialisation` is a product of `Init` message

<!--codeinclude-->
[Layer construction](../../zio-maelstrom/src/main/scala/com/bilal-fazlani/zio-maelstrom/MaelstromRuntime.scala) block:live
<!--/codeinclude-->


