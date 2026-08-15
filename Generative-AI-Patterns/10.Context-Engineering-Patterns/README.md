# Context Engineering Patterns
### A production-grade curriculum — 12 patterns, one at a time, with runnable LangChain + Ollama code

---

## 1. Why "Context Engineering" and not "Prompt Engineering"?

Prompt engineering asks: *"How do I phrase this instruction well?"*

Context engineering asks a bigger question: *"Out of everything my system knows — documents, transaction logs, past conversations, tool outputs, policies, memory — what exactly should go into the LLM's limited context window for **this** request, in **what** order, in **what** form, and **when**?"*

An LLM's context window is a scarce, expensive, and easily-polluted resource. Production GenAI systems fail far more often because of **bad context** (irrelevant, stale, duplicated, poorly ordered, or overflowing context) than because of a badly worded prompt. Context engineering is the discipline of treating the context window like a piece of system architecture — with selection, filtering, ranking, compression, caching, and routing logic — instead of a scratchpad you dump everything into.

This series covers the 12 patterns that make that discipline concrete.

---

## 2. The running example: Helios

Every pattern in this series is taught against **Helios**, a fictional enterprise banking platform for **fraud operations and case management** (the same platform used across the rest of this curriculum). Concretely, we build a **Fraud Investigation Copilot** for Helios — an LLM assistant that helps fraud analysts investigate flagged transactions, using:

- Case metadata & transaction history (PostgreSQL)
- Customer profiles & KYC data
- Similar historical fraud cases (pgvector similarity search)
- Compliance / regulatory policy documents
- Analyst notes and prior investigation threads
- External tools (sanctions screening APIs, device-fingerprint services, etc.)

Each pattern solves a *real* problem that shows up when you try to make this copilot fast, accurate, cheap, and safe in production.

---

## 3. Patterns covered

| # | Pattern | Core Question It Answers | Status |
|---|---------|---------------------------|--------|
| 01 | [Context Selection](./01-context-selection.md) | Of all available context, what subset is even worth considering? | ✅ Done |
| 02 | [Context Filtering](./02-context-filtering.md) | Of the selected context, what must be removed (noise, PII, irrelevant)? | ✅ Done |
| 03 | [Context Compression](./03-context-compression.md) | How do I shrink context without losing meaning? | ✅ Done |
| 04 | [Context Ranking](./04-context-ranking.md) | In what order should context be scored against each other? | ✅ Done |
| 05 | [Context Prioritization](./05-context-prioritization.md) | Under a hard budget, what gets in first when everything can't fit? | ✅ Done |
| 06 | [Context Window Management](./06-context-window-management.md) | How do I manage the window across a long-running, multi-turn session? | ✅ Done |
| 07 | [Context Caching](./07-context-caching.md) | How do I avoid re-computing/re-sending context I've already paid for? | ✅ Done |
| 08 | [Context Isolation](./08-context-isolation.md) | How do I keep contexts from different tasks/tenants/agents from bleeding into each other? | ✅ Done |
| 09 | [Context Routing](./09-context-routing.md) | How do I send a query to the *right* context source/subsystem? | ✅ Done |
| 10 | [Context Summarization](./10-context-summarization.md) | How do I compress history into a durable, faithful summary? | ✅ Done |
| 11 | [Context Deduplication](./11-context-deduplication.md) | How do I stop paying (in tokens and confusion) for the same fact twice? | ✅ Done |
| 12 | [Contextual Retrieval](./12-contextual-retrieval.md) | How do I retrieve chunks that carry enough context to be useful standalone? | ✅ Done |

Files are added one at a time, in this order, as `NN-pattern-name.md`.

**Status: all 12 patterns complete.** See [Pattern 12's closing section](./12-contextual-retrieval.md#series-complete) for how they compose into one coherent pipeline.

---

## 4. Structure of every pattern file

Each `NN-pattern-name.md` follows the same 8-part structure, so the series stays predictable to study from:

1. **Introduce the pattern** — plain-language definition, one-paragraph mental model
2. **The problem it solves** — what breaks in production without it
3. **A realistic enterprise problem** — grounded in Helios fraud operations
4. **Architecture / flow diagram** — Mermaid diagrams (render natively on GitHub)
5. **Complete request-to-response flow** — step-by-step walkthrough
6. **Why this pattern is appropriate here** — trade-offs vs. alternative patterns
7. **Production-quality Python implementation** — full code, inline in the markdown (no separate `.py` files), using `langchain-ollama` + LangGraph where relevant
8. **Latest stable, compatible tooling** — pinned to the versions below

---

## 5. Tech stack (pinned, as of Aug 2026)

| Component | Version | Notes |
|---|---|---|
| Python | 3.12+ | Uses modern typing (`list[str]`, `X \| None`) |
| `langchain-core` | `>=0.3.60` | Core abstractions (messages, prompts, runnables) |
| `langchain-ollama` | `>=0.3.0` | `ChatOllama`, `OllamaEmbeddings` |
| `langgraph` | `>=0.2.60` | Used from Pattern 06 onward (stateful/multi-turn patterns) |
| `numpy` | `>=1.26` | Vector math for relevance scoring |
| Ollama runtime | latest | Local model server — [ollama.com](https://ollama.com) |
| Ollama chat model | `llama3.1` (8B) | Swap for `qwen2.5` / `mistral` etc. as you like |
| Ollama embedding model | `nomic-embed-text` | Good general-purpose local embedder |

### One-time setup

```bash
# 1. Install Ollama (see https://ollama.com/download)
# 2. Pull the models used across this series
ollama pull llama3.1
ollama pull nomic-embed-text

# 3. Python environment
python3 -m venv .venv && source .venv/bin/activate
pip install "langchain-core>=0.3.60" "langchain-ollama>=0.3.0" "langgraph>=0.2.60" "numpy>=1.26"
```

Every code sample in this series assumes the Ollama server is running locally (`ollama serve`, default `http://localhost:11434`).

---

## 6. How to use this series

Each file is self-contained: copy the Python block into a `.py` file (or a notebook cell) and run it against your local Ollama instance. Nothing in these examples calls an external paid API — everything runs locally so you can experiment freely.

Start with **[Pattern 01 — Context Selection](./01-context-selection.md)**.
