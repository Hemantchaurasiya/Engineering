# Mastering Agent Patterns — A Production-Ready Guide

This repo teaches you all major **AI agent design patterns**, one at a time, using
**LangChain + LangGraph + Ollama** (local, free, open-weight models — no API keys needed).

Each pattern gets its **own file** under `patterns/`. Every file is self-contained and includes:

1. What the pattern is (in plain language)
2. The problem it solves
3. A realistic production/enterprise use case
4. An architecture / flow diagram (Mermaid)
5. A walk-through of the request → response flow
6. Why the pattern fits that problem (and when it doesn't)
7. Full production-quality Python code (inline in the file, not a separate `.py`)
8. Notes on the exact library versions used

We go **one pattern at a time**, in this order, so concepts build on each other
(a "Router Agent" is easier to understand once you've built a "Tool-Using Agent", etc.).

## Progress Tracker

| # | Pattern | Status | File |
|---|---------|--------|------|
| 1 | Basic Agent | ✅ Done | [patterns/01-basic-agent.md](patterns/01-basic-agent.md) |
| 2 | Tool-Using Agent | ✅ Done | [patterns/02-tool-using-agent.md](patterns/02-tool-using-agent.md) |
| 3 | ReAct | ✅ Done | [patterns/03-react.md](patterns/03-react.md) |
| 4 | Planning Agent | ✅ Done | [patterns/04-planning-agent.md](patterns/04-planning-agent.md) |
| 5 | Reflection Agent | ✅ Done | [patterns/05-reflection-agent.md](patterns/05-reflection-agent.md) |
| 6 | Self-Critique Agent | ✅ Done | [patterns/06-self-critique-agent.md](patterns/06-self-critique-agent.md) |
| 7 | Self-Improving Agent | ✅ Done | [patterns/07-self-improving-agent.md](patterns/07-self-improving-agent.md) |
| 8 | Router Agent | ✅ Done | [patterns/08-router-agent.md](patterns/08-router-agent.md) |
| 9 | Supervisor Agent | ✅ Done | [patterns/09-supervisor-agent.md](patterns/09-supervisor-agent.md) |
| 10 | Worker Agent | ✅ Done | [patterns/10-worker-agent.md](patterns/10-worker-agent.md) |
| 11 | Hierarchical Agent | ✅ Done | [patterns/11-hierarchical-agent.md](patterns/11-hierarchical-agent.md) |
| 12 | Multi-Agent System | ✅ Done | [patterns/12-multi-agent-system.md](patterns/12-multi-agent-system.md) |
| 13 | Debate Pattern | ✅ Done | [patterns/13-debate-pattern.md](patterns/13-debate-pattern.md) |
| 14 | Sequential Agent | ✅ Done | [patterns/14-sequential-agent.md](patterns/14-sequential-agent.md) |
| 15 | Parallel Agent | ✅ Done | [patterns/15-parallel-agent.md](patterns/15-parallel-agent.md) |
| 16 | Human-in-the-Loop Agent | ✅ Done | [patterns/16-human-in-the-loop-agent.md](patterns/16-human-in-the-loop-agent.md) |
| 17 | Agent Handoff | ✅ Done | [patterns/17-agent-handoff.md](patterns/17-agent-handoff.md) |
| 18 | Agent Memory | ✅ Done | [patterns/18-agent-memory.md](patterns/18-agent-memory.md) |
| 19 | Agent Planning | ✅ Done | [patterns/19-agent-planning.md](patterns/19-agent-planning.md) |
| 20 | Agentic RAG | ✅ Done | [patterns/20-agentic-rag.md](patterns/20-agentic-rag.md) |
| 21 | Agentic Workflow | ✅ Done | [patterns/21-agentic-workflow.md](patterns/21-agentic-workflow.md) |
| 22 | Autonomous Agent | ✅ Done | [patterns/22-autonomous-agent.md](patterns/22-autonomous-agent.md) |
| 23 | Event-Driven Agent | ✅ Done | [patterns/23-event-driven-agent.md](patterns/23-event-driven-agent.md) |
| 24 | Long-Running Agent | ✅ Done | [patterns/24-long-running-agent.md](patterns/24-long-running-agent.md) |
| 25 | Stateful Agent | ✅ Done | [patterns/25-stateful-agent.md](patterns/25-stateful-agent.md) |
| 26 | Stateless Agent | ✅ Done | [patterns/26-stateless-agent.md](patterns/26-stateless-agent.md) |

## 🎉 Series complete!

All 26 agent patterns are done. Start at [Pattern 1: Basic Agent](patterns/01-basic-agent.md)
and work through in order — each pattern builds on the ones before it, from a single validated
LLM call through tool use, reasoning loops, planning, self-verification, multi-agent
coordination, and the operational patterns (memory, state, durability, event-driven and
autonomous operation) that separate a working demo from a production system.

## Prerequisites (same for every pattern)

**1. Install Ollama** (runs models locally): https://ollama.com/download

**2. Pull a model** — we use `llama3.1:8b` for reasoning-heavy patterns and `qwen2.5:7b` or
`llama3.2:3b` for lighter/faster ones. Pick whichever fits your machine:

```bash
ollama pull llama3.1:8b
ollama pull llama3.2:3b
```

**3. Python environment** — Python 3.11+ recommended.

```bash
python -m venv .venv
source .venv/bin/activate        # Windows: .venv\Scripts\activate

pip install -U langchain langchain-core langchain-ollama langgraph pydantic
```

Versions used throughout this repo (as of writing):

- Python `3.11+`
- `langchain` `0.3.x`
- `langchain-core` `0.3.x`
- `langchain-ollama` `0.2.x`
- `langgraph` `0.2.x`
- `pydantic` `2.x`

Each pattern file re-states any extra dependency it needs (e.g. a web-search tool package),
but the above covers ~90% of what you need.

## How to use this repo

Open `patterns/01-basic-agent.md` and read top to bottom — it's designed to be read like a
tutorial chapter, then have its code copy-pasted and run directly against your local Ollama
server. Each subsequent pattern assumes you've understood the previous ones, but you can also
jump straight to the pattern you need for a project.

---
