# Week 4 Action Plan: The MCP Client

**Goal:** Transform your Week 2 bare-metal agent into a proper MCP host. Instead of hardcoded tool schemas, it will spawn `McpServer.java` as a child process, discover its tools dynamically over the protocol, and route Gemini's tool calls through JSON-RPC. By the end of this week, the agent has zero hardcoded tool logic.  
**Time Commitment:** 20–30 minutes per day.

---

## The Big Picture

This week connects the two things you've already built:

```
Week 2 Agent (McpClientAgent.java)
    │
    │  spawns via ProcessBuilder
    ▼
Week 3 Server (McpServer.java)
    │
    │  talks JSON-RPC over stdin/stdout
    ▼
Real world: Docker + Immich REST API
```

The agent becomes the **MCP host**. The server becomes a **black box** — the agent doesn't know or care what tools it contains. It asks, and the server tells it.

---

### Day 19: Spawn the Server Process
- [ ] Create a new class `McpClientAgent.java` alongside your existing `AgentApplication.java`.
- [ ] Write a `spawnMcpServer()` method that uses `ProcessBuilder` to launch `McpServer.java` as a child process.
- [ ] Wire up the streams: get `OutputStream` (→ server's stdin) and `InputStream` (← server's stdout) from the process.
- [ ] Wrap them in a `BufferedWriter` and `BufferedReader` for line-by-line communication.
- [ ] Verify the process starts: write a raw line to the server and confirm you can read a response back.

**Key concept:** The same `ProcessBuilder` you used in Week 2 to run `docker ps` is now used to run your own Java server. The streams work identically — you write lines in, you read lines out.

---

### Day 20: The Initialize Handshake
- [ ] Write a `sendRequest(BufferedWriter writer, String json)` helper that writes a JSON line and flushes.
- [ ] Write a `readResponse(BufferedReader reader)` helper that reads the next non-empty line.
- [ ] Send the `initialize` JSON-RPC message to the server.
- [ ] Parse the response with Jackson and log the server's `name` and `protocolVersion` to confirm the handshake succeeded.

**Key concept:** You are now on the *other side* of the protocol you built last week. Everything you designed in `McpServer.java` — the flush, the single-line output, the id field — you'll feel the consequences of here as the client.

---

### Day 21: Dynamic Tool Discovery
- [ ] Send `tools/list` to the server.
- [ ] Parse the response: extract the `tools` array.
- [ ] Write a converter method: `mcpToolsToGeminiFunctionDeclarations(JsonNode tools)` that transforms each MCP `inputSchema` into a Gemini `functionDeclaration`.
- [ ] Log the converted declarations to confirm the shapes are correct.
- [ ] Pass the converted declarations into the Gemini request payload as the `tools` array — replacing the hardcoded schema from Week 2.

**Key concept:** This is the payoff of learning both sides. You know the MCP shape (`inputSchema`) and the Gemini shape (`functionDeclaration`) by hand — so the conversion is mechanical, not magical.

---

### Day 22: Routing Tool Calls to the Server
- [ ] In the agent's main loop, when Gemini returns a `functionCall`, instead of executing it locally, build a `tools/call` JSON-RPC request and send it to the MCP server.
- [ ] Read the server's response and extract the `content[0].text` result.
- [ ] Return that result string back into the agent loop as the tool output.

**Key concept:** The agent becomes a router. It no longer knows what tools do — it only knows how to ask the server to do them and pass the result back.

---

### Day 23: Full Round-Trip
- [ ] Wire everything together into the complete loop:
  1. User types a question
  2. Agent sends it to Gemini with dynamically loaded tool schemas
  3. Gemini returns a `functionCall`
  4. Agent routes it to `McpServer` via JSON-RPC
  5. Server executes the tool (Docker or Immich)
  6. Agent receives the result, injects it into conversation history
  7. Agent sends history back to Gemini for the human-readable answer
  8. Agent prints the answer
- [ ] Confirm the loop handles both tool and non-tool responses correctly.

**Key concept:** This is the same two-pass loop you built in Week 2 — but now the tool execution layer is completely decoupled. The agent doesn't import, reference, or know about `executeLocalCommand` or `listImmichAlbums`.

---

### Day 24: End-to-End Test
- [ ] Run `McpClientAgent` with no manual intervention.
- [ ] Test the non-tool path: ask "What is Docker?" — agent answers without touching the MCP server.
- [ ] Test the Docker tool: ask "Is my Immich container running?" — verify it routes through the MCP server and returns real status.
- [ ] Test the Immich tool: ask "What albums do I have in Immich?" — verify it calls the Immich REST API via the server and returns your real album list.
- [ ] Test resilience: ask something that would trigger an unknown tool — confirm the error response doesn't crash the agent loop.

**Exit criteria:** Your agent has zero hardcoded tool logic. All tool execution flows through the MCP protocol. Adding a new tool to `McpServer.java` requires zero changes to `McpClientAgent.java`.

---

## What You'll Have Built by End of Week 4

A fully decoupled, protocol-native AI agent system:

```
McpClientAgent.java     — MCP host + Gemini orchestration
McpServer.java          — MCP server + tool execution
JSON-RPC 2.0 over stdio — the only thing connecting them
```

This is the architecture that Spring AI, Claude Desktop, and every production MCP integration uses under the hood — you'll have built it entirely from scratch.
