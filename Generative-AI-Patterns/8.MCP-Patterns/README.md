# MCP Patterns — A Practical, Production-Grade Series

This series teaches the **Model Context Protocol (MCP)** one pattern at a time, in depth, with
production-ready Python code for realistic enterprise problems. Every pattern uses
[LangChain](https://python.langchain.com/), [LangGraph](https://langchain-ai.github.io/langgraph/),
and **local LLMs via `langchain-ollama`** — so you can run every example without an API key.

Each pattern gets its own file so you can jump straight to the one you need. Every file follows
the same teaching structure:

1. **Introduce the pattern** — what it is, in plain language
2. **The problem it solves** — why you'd reach for it
3. **A realistic production/enterprise scenario** — not a toy example
4. **Architecture / flow diagrams** — Mermaid diagrams you can render anywhere
5. **The complete request-to-response flow** — step by step, no hand-waving
6. **Why this pattern fits** — trade-offs vs. alternatives
7. **Production-quality Python** — real error handling, typing, and structure (code lives inline
   in the pattern's markdown file, not in separate `.py` files)

## Tech stack used throughout

| Component | Package | Version used in these examples |
|---|---|---|
| Language | Python | 3.12+ |
| Local LLM runtime | [Ollama](https://ollama.com/) | latest |
| LLM integration | `langchain-ollama` | latest stable (0.3.x) |
| Orchestration | `langgraph` | latest stable (1.2.x) |
| Core framework | `langchain` / `langchain-core` | latest stable |
| MCP protocol SDK | `mcp` (official Python SDK) | latest stable 1.x (`mcp[cli]>=1.28,<2`) |
| MCP ⇄ LangChain bridge | `langchain-mcp-adapters` | latest stable (0.2.x) |

> **A note on MCP SDK versions:** the MCP ecosystem is mid-transition to a new "v2" wire protocol
> (stateless, no `initialize` handshake) that started rolling out through 2026. That generation is
> still stabilizing across the tooling this series depends on (`langchain-mcp-adapters`, Ollama
> tool-calling, etc.), so every pattern here is built on the widely-deployed, fully stable **v1.x**
> MCP SDK (`FastMCP`, stdio/Streamable HTTP transports). The concepts — resources, tools, prompts,
> discovery, routing, security — carry over directly once you migrate.

## Prerequisites (install once)

```bash
# Core stack
pip install "langchain>=0.3" "langgraph>=1.2" "langchain-ollama>=0.3" \
            "langchain-mcp-adapters>=0.2" "mcp[cli]>=1.28,<2"

# Pull a tool-calling-capable local model (used across all examples)
ollama pull llama3.1
```

## The 16 patterns

| # | Pattern | File | Status |
|---|---|---|---|
| 1 | MCP Fundamentals | [`01-mcp-fundamentals.md`](./01-mcp-fundamentals.md) | ✅ |
| 2 | MCP Client | [`02-mcp-client.md`](./02-mcp-client.md) | ✅ |
| 3 | MCP Server | [`03-mcp-server.md`](./03-mcp-server.md) | ✅ |
| 4 | MCP Resources | [`04-mcp-resources.md`](./04-mcp-resources.md) | ✅ |
| 5 | MCP Tools | [`05-mcp-tools.md`](./05-mcp-tools.md) | ✅ |
| 6 | MCP Prompts | [`06-mcp-prompts.md`](./06-mcp-prompts.md) | ✅ |
| 7 | MCP Architecture | [`07-mcp-architecture.md`](./07-mcp-architecture.md) | ✅ |
| 8 | Tool Discovery | [`08-tool-discovery.md`](./08-tool-discovery.md) | ✅ |
| 9 | Dynamic Tool Discovery | [`09-dynamic-tool-discovery.md`](./09-dynamic-tool-discovery.md) | ✅ |
| 10 | MCP Security | [`10-mcp-security.md`](./10-mcp-security.md) | ✅ |
| 11 | MCP Authorization | [`11-mcp-authorization.md`](./11-mcp-authorization.md) | ✅ |
| 12 | MCP Tool Routing | [`12-mcp-tool-routing.md`](./12-mcp-tool-routing.md) | ✅ |
| 13 | MCP with Agents | [`13-mcp-with-agents.md`](./13-mcp-with-agents.md) | ✅ |
| 14 | MCP with RAG | [`14-mcp-with-rag.md`](./14-mcp-with-rag.md) | ✅ |
| 15 | MCP with Enterprise Systems | [`15-mcp-with-enterprise-systems.md`](./15-mcp-with-enterprise-systems.md) | ✅ |
| 16 | Multi-MCP Architecture | [`16-multi-mcp-architecture.md`](./16-multi-mcp-architecture.md) | ✅ |

We'll build these one at a time, each in its own file, starting with the fundamentals.

**Status: all 16 patterns complete.** Pattern 16 (Multi-MCP Architecture) is the capstone and
includes a field guide mapping each production need back to the pattern that solves it.
