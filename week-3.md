# Week 3 Action Plan: The MCP Server (Bare Metal)

**Goal:** Decouple your tools from your agent logic. Build a standalone Java application that acts as a Model Context Protocol (MCP) server, communicating entirely via standard input/output (`stdio`) using JSON-RPC 2.0.
**Time Commitment:** 20-30 minutes per day.

### Day 13: Project Setup & The Stdio Loop
- [x] Create a completely new, separate Java application class (e.g., `McpServer.java`).
- [x] Write a `while(true)` loop that uses `Scanner` to read lines from `System.in`. 
- [x] **Crucial:** From this point on, you can NEVER use `System.out.println` for debugging, as it will corrupt the protocol. Use `System.err.println` for all your logs.

### Day 14: The Initialization Handshake
- [x] Read incoming strings as JSON. Check if the JSON-RPC request method is `initialize`.
- [x] If it is, write the code to construct a JSON-RPC response confirming your server's capabilities.
- [x] Print this JSON response exactly to `System.out`.

### Day 15: Exposing Tools (`tools/list`)
- [x] Add an `if/else` block to check if the incoming request method is `tools/list`.
- [x] If it is, move the JSON schema you wrote on Day 8 (`check_container_status`) into this server. 
- [x] Format it according to the MCP specification and print it to `System.out`.

### Day 16: Executing Tools (`tools/call`)
- [x] Add a block to check if the incoming method is `tools/call`.
- [x] If it is, extract the `name` (check_container_status) and `arguments` (immich).
- [x] Copy your `executeLocalCommand(containerName)` method from your agent into this server. Run it here, and output the result as a JSON-RPC response to `System.out`.

### Day 17: JSON-RPC Error Handling
- [x] Implement basic error handling. If the client requests a tool that doesn't exist, return a JSON-RPC error object (code `-32601` for Method Not Found) to `System.out`.

### Day 18: Manual Server Testing
- [ ] Run your `McpServer` application.
- [ ] Type a raw JSON-RPC string into your terminal (e.g., `{"jsonrpc": "2.0", "id": 1, "method": "initialize"}`) and hit enter.
- [ ] Verify your server instantly spits back the correct JSON initialization response to the terminal.
