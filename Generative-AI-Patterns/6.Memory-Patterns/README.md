# Memory Patterns — Section 6 (Master Series)

A deep, production-focused, one-pattern-at-a-time curriculum on **AI Agent / LLM Memory Patterns**, built with **LangChain + LangGraph + langchain-ollama** (local models — no cloud LLM providers).

This series follows the same philosophy as the Section 4 RAG Patterns series: every pattern gets its **own standalone file**, explained in simple language, backed by a **realistic production/enterprise scenario**, an **architecture diagram**, and **complete, runnable, production-quality code embedded directly in the markdown**.

---

## 🧰 Tech Stack (fixed for the whole series)

| Component | Library | Model |
|---|---|---|
| LLM (generation) | `langchain-ollama` → `ChatOllama` | `llama3.1` |
| Embeddings | `langchain-ollama` → `OllamaEmbeddings` | `nomic-embed-text` |
| Orchestration / State | `langgraph` (StateGraph, checkpointers) | — |
| Vector store (where needed) | `langchain-chroma` | Chroma (local, persistent) |
| Language | Python 3.11+ | — |

> All code assumes a local Ollama server (`ollama serve`) with `llama3.1` and `nomic-embed-text` pulled.

---

## 📚 The 17 Memory Patterns

| # | Pattern | File | Status |
|---|---|---|---|
| 1 | Conversation Memory | `01_conversation_memory.md` | ✅ Done |
| 2 | Short-Term Memory | `02_short_term_memory.md` | ✅ Done |
| 3 | Long-Term Memory | `03_long_term_memory.md` | ✅ Done |
| 4 | Semantic Memory | `04_semantic_memory.md` | ✅ Done |
| 5 | Episodic Memory | `05_episodic_memory.md` | ✅ Done |
| 6 | Procedural Memory | `06_procedural_memory.md` | ✅ Done |
| 7 | Entity Memory | `07_entity_memory.md` | ✅ Done |
| 8 | Summary Memory | `08_summary_memory.md` | ✅ Done |
| 9 | Vector-Based Memory | `09_vector_based_memory.md` | ✅ Done |
| 10 | External Memory | `10_external_memory.md` | ✅ Done |
| 11 | Persistent Memory | `11_persistent_memory.md` | ✅ Done |
| 12 | Working Memory | `12_working_memory.md` | ✅ Done |
| 13 | Memory Retrieval | `13_memory_retrieval.md` | ✅ Done |
| 14 | Memory Consolidation | `14_memory_consolidation.md` | ✅ Done |
| 15 | Memory Compression | `15_memory_compression.md` | ✅ Done |
| 16 | Memory Forgetting | `16_memory_forgetting.md` | ✅ Done |
| 17 | User Profile Memory | `17_user_profile_memory.md` | ✅ Done |

---

## 📖 Structure followed in every pattern file

1. **Introduce the pattern** — what it is, in plain language
2. **Problem it solves** — why plain LLM calls aren't enough
3. **Realistic production/enterprise scenario** — a concrete business use case
4. **Architecture / flow diagram** — ASCII diagram of the system
5. **Complete request-to-response flow** — step-by-step trace of one interaction
6. **Why this pattern is appropriate** — trade-offs vs. other memory patterns
7. **Production-quality Python implementation** — full code, inline in the markdown
8. **Version pins** — latest stable, compatible LangChain / LangGraph / Python versions used

---

## ✅ How we'll proceed

One pattern per file, in order (1 → 17). Say **"next pattern"** (or name one directly) whenever you're ready and I'll build the next file in the same depth and format as Pattern 1.
