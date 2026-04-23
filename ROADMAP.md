# AI Agent Learning Roadmap: Bare Metal to Framework
**Stack:** Java · Gemini API · MCP · Spring AI  
**Pace:** 20–30 minutes/day  
**Home Lab Context:** Ubuntu · Docker · Immich *(Paperless-ngx planned for later)*

---

## Assessment: Where You Stand Right Now

### ✅ Completed
| Milestone | Status | Evidence |
|-----------|--------|---------|
| M1 — Theory & Hello World Agent | **Done** | Week 1 all checked. Google Agent Fundamentals course passed. Native `HttpClient` sending prompts, system persona injection working. |
| M2 — Bare Metal Agent Loop & Tool Calling | **Done** | Week 2 all checked. `while(true)` loop, Jackson memory (`ArrayNode`), `check_container_status` tool schema, `functionCall` parsing, `ProcessBuilder` execution, two-pass LLM round-trip, live testing with Immich container confirmed. |

### 🔄 In Progress
| Milestone | Status | Notes |
|-----------|--------|-------|
| M3 — Raw MCP Server | **Week 3 started** | `McpServer.java` structure planned. Days 13–18 not yet started. |

### ⏳ Upcoming
- M4 — MCP Client & External Integration
- M5 — Framework Leap (Spring AI)

---

## Corrected & Refined Goal

> **Original goal:** Build a vendor-agnostic understanding of AI agents and MCP using native Java.  
> **Refined goal:** Progress from a working bare-metal agent to a production-grade, Spring AI-powered MCP system — while deeply understanding every abstraction layer *before* using it. Use your Immich home lab as the real integration target throughout, with Paperless-ngx added when it's deployed.

**What stays the same:** Bare-metal-first discipline — understand the protocol by hand before any framework touches it.  
**What is added:** After M5, a practical DevOps capstone where the agent can autonomously manage your home lab services via MCP.

---

## Full Roadmap

### ✅ MILESTONE 1 — Theory & Hello World Agent *(Complete)*
> Foundations: LLM API, HTTP, system prompts

- Google Agent Fundamentals course (3 modules + quiz)
- Native `java.net.http.HttpClient` → Gemini API
- System persona injection
- Raw JSON response printing

**You proved:** You can talk to an LLM programmatically and shape its behavior.

---

### ✅ MILESTONE 2 — Bare Metal Agent Loop & Tool Calling *(Complete)*
> The agent loop: memory, intent parsing, local execution, response synthesis

- `while(true)` loop + `Scanner` input
- Jackson `ArrayNode` conversation history
- `check_container_status` tool schema (JSON function declaration)
- `functionCall` detection and argument extraction
- `ProcessBuilder` → `docker ps` → output capture
- `functionResponse` injected back into history
- Second LLM round-trip for human-readable answer
- Live test: Immich container watchdog

**You proved:** You can build a full ReAct-style agent loop from scratch with zero frameworks.

---

### 🔄 MILESTONE 3 — Raw MCP Server *(Current — Week 3)*
> Decoupling: JSON-RPC 2.0 over stdio, MCP protocol by hand

**Why this matters:** Before using any MCP library, you'll have handwritten every message the protocol sends. This gives you permanent intuition for debugging any MCP integration later.

**Target tool to expose:** `list_immich_albums` — calls the Immich REST API and returns a list of albums.

| Day | Task | Key Concept |
|-----|------|------------|
| 13 | New `McpServer.java`, `while(true)` stdio loop. **Switch all debug to `System.err`** | stdio protocol discipline |
| 14 | Parse incoming JSON-RPC. Handle `initialize` → respond with server capabilities JSON | Handshake |
| 15 | Handle `tools/list` → return `check_container_status` schema (reuse from Day 8) | Tool advertisement |
| 16 | Handle `tools/call` → extract args, run `executeLocalCommand()`, respond | Tool execution |
| 17 | Error handling: unknown method → JSON-RPC error `-32601` | Protocol robustness |
| 18 | Manual terminal test: type raw JSON-RPC, verify correct responses | Integration confidence |

**Exit criteria:** You can type a raw JSON-RPC message into your terminal and the server responds correctly every time.

---

### ⏳ MILESTONE 4 — MCP Client Integration *(~Week 4)*
> Your agent becomes an MCP host: process spawning, dynamic tool discovery

**The transformation:** Your Week 2 agent stops hardcoding tools. Instead it spawns `McpServer.java` as a child process, discovers its tools via the protocol, and passes them dynamically to Gemini.

| Day | Task | Key Concept |
|-----|------|------------|
| 19 | Agent spawns `McpServer` via `ProcessBuilder`. Wire `stdin`/`stdout` streams | Process lifecycle |
| 20 | Agent sends `initialize` JSON-RPC → reads and parses server's capability response | Protocol negotiation |
| 21 | Agent sends `tools/list` → parses tool schemas → converts to Gemini `functionDeclaration` format | Dynamic capability loading |
| 22 | Agent detects `functionCall` from Gemini → routes to MCP server via `tools/call` JSON-RPC | Client routing |
| 23 | Wire full round-trip: Gemini → MCP call → result → second Gemini pass → human answer | Full decoupled loop |
| 24 | End-to-end test: ask "check immich" with agent running MCP server as separate process | Integration proof |

**Exit criteria:** Your agent has zero hardcoded tool logic. It discovers and executes tools entirely via protocol messages.

---

### ⏳ MILESTONE 5 — Framework Leap: Spring AI *(~Week 5)*
> Replace boilerplate with Spring AI. Understand what the framework is doing under the hood.

**The transition:** You've already handwritten everything Spring AI does automatically. Now you'll see it collapse into configuration — and you'll understand exactly why it works.

| Day | Task | Key Concept |
|-----|------|------------|
| 25 | New Maven project. Add `spring-ai-mcp-client-spring-boot-starter` dependency | Framework setup |
| 26 | Configure `application.yml` with MCP server process path. Let Spring boot the client | Auto-configuration |
| 27 | Replace manual `HttpClient` with `ChatClient` bean. Remove JSON assembly boilerplate | Framework abstraction |
| 28 | Add a second MCP tool: `list_immich_albums` (Immich REST API). Register via `@Tool` | Multi-tool scaling |
| 29 | Add a third MCP tool: `get_immich_asset_count` (Immich REST API). Observe zero agent-side changes needed | Protocol value proof |
| 30 | Side-by-side comparison: bare-metal vs Spring AI. Document what the framework handles | Consolidation |

**Exit criteria:** You can add a new tool to the MCP server without touching the agent code at all.

---

### 🚀 MILESTONE 6 — Home Lab Capstone *(~Week 6–7, optional but recommended)*
> Apply everything. Build something real.

**Goal:** An autonomous home lab assistant that can answer questions about your infrastructure, check service health, and surface recent documents or photos — all via a properly structured MCP server fleet.

Potential capabilities:
- `check_container_status` — existing tool, now via Spring AI
- `list_immich_albums` — Immich REST API
- `get_immich_asset_count` — Immich REST API
- `get_disk_usage` — `df -h` via ProcessBuilder
- `restart_container` — `docker restart <n>` (with confirmation step)
- `query_recent_documents` — Paperless-ngx REST API *(add when Paperless-ngx is deployed)*

This capstone is also the ideal point to introduce **Claude Code** as a development tool — by then your mental model of agents, tools, and protocols will be solid enough to use it without the abstraction becoming a black box.

---

## Key Principles to Carry Through

1. **Stderr for debug, stdout for protocol.** Any `System.out.println` that isn't a JSON-RPC message is a protocol corruption bug waiting to happen.
2. **Test manually before wiring.** Every milestone ends with a manual integration test before connecting the next layer.
3. **Understand before using.** You build the thing by hand before the framework does it for you. This is what separates a developer who uses MCP from one who understands it.
4. **Real integration targets only.** Immich is your test surface right now — not a toy example. Paperless-ngx joins when it's deployed.

---

## Repository Recommendation

This roadmap is for the existing `ai-agent-01` repo. Suggested structure:

```
ai-agent-01/
├── ROADMAP.md              <- this file
├── milestones.md           <- existing
├── week-1.md               <- existing
├── week-2.md               <- existing
├── week-3.md               <- existing (MCP Server bare metal)
├── week-4.md               <- to be created (MCP Client)
├── week-5.md               <- to be created (Spring AI)
├── src/
│   └── main/java/
│       ├── Agent.java              <- M1/M2 (done)
│       ├── McpServer.java          <- M3 (in progress)
│       └── McpClientAgent.java     <- M4 (upcoming)
└── pom.xml
```

No need for a new repo — the learning progression lives cleanly in one place.

---

*Last updated: April 2026 · Current position: Start of Milestone 3, Day 13*
