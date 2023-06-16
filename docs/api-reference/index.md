---
icon: material/api
hide:
  - toc
  - navigation
---

# API Reference

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
