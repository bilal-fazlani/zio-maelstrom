PENDING: 

- [ ] Test kit docs
- [ ] Api ref docs
- [ ] Tests
  - [X] Test overlap in console output manually tested
  - [X] Test resource safety in callbackRegistry manually tested
  - [ ] Automated tests
- [ ] Out messages should not need to define types. Use class name. May need to use macros

TBD:

- [ ] Create Maelstrom node parent class
  - Assess importance
- [ ] Multiple request-response can be started. There should be only one handler for a node
  - Not a V1 requirement
- [ ] User messages should not be restricted to a msg_id of Int
  - Is it worth the complexity?
- [ ] If a message is awaited, and another callback is is being registered with same messages id from same remote, then it results in unintended behavior
  - Does this need to be solved in this library?
  
DONE:

- [X] Error code hierarchy needs to be simplified
- [X] File input is not working
- [X] Make internal  types/apis private
- [X] Add concurrency in settings
- [X] Ask pattern
- [X] Use intersection types for message hierarchy. IN messages don't need "type"
- [X] Separate init from receive messages
- [X] Create documentation
- [X] Add more examples
- [X] Test kit
- [X] Fix the dam blinking debug messages