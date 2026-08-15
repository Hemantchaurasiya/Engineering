# Knowledge & Retrieval Patterns

A deep, production-grade study series covering the 15 core patterns that power modern retrieval systems — from raw text to a working RAG/knowledge pipeline. This series follows the same discipline as the earlier **RAG Patterns** and **Memory Patterns** series: one pattern at a time, explained in simple language, backed by production-quality Python.

## How this series works

- We go **one pattern at a time**. Say `next` and I'll build the next file.
- Every pattern gets its **own markdown file** (`NN_Pattern_Name.md`).
- **Code lives inside the markdown file itself** (in fenced code blocks) — no separate `.py` files, so each `.md` is a self-contained study document you can read top to bottom.
- Every file follows the same 8-part structure (see below), explained in plain language first, then backed by real, runnable code.
- Examples use **local models via `langchain-ollama`** — no cloud API keys needed.

## The 8-part structure (every pattern file)

1. **Introduce the pattern** — what it is, in plain words.
2. **The problem it solves** — what goes wrong without it.
3. **A realistic enterprise scenario** — a concrete business situation we solve throughout the file.
4. **Architecture / flow diagram** — ASCII diagram of how data moves.
5. **Request-to-response walkthrough** — step-by-step trace of a real request.
6. **Why this pattern is the right fit** — trade-offs vs. alternatives.
7. **Production-quality Python implementation** — full, runnable code, inline in the markdown.
8. **Pinned dependency versions** — exact versions used, so the code keeps working.

## Tech stack (fixed for this whole series)

| Component | Choice | Why |
|---|---|---|
| LLM (generation) | `langchain-ollama` → `ChatOllama` with `llama3.1` | Local, free, no API key, good enough reasoning for demos |
| Embeddings | `langchain-ollama` → `OllamaEmbeddings` with `nomic-embed-text` | Fast local embedding model, 768-dim |
| Vector store | `langchain-chroma` (Chroma) | Simple, file-backed, production-viable for small/medium scale |
| Hybrid / keyword search | `rank_bm25` (BM25) combined with Chroma vectors | Classic sparse+dense hybrid retrieval |
| Knowledge graph | `networkx` (+ optional `neo4j` notes for real production scale) | Lightweight, no external DB needed for the demo, concepts transfer directly to Neo4j |
| Orchestration (where relevant) | `langgraph` with `SqliteSaver` checkpointing | Matches the orchestration style used across the whole GenAI pattern series |
| Re-ranking | Cross-encoder via `sentence-transformers` (local) | No paid re-ranking API needed |
| Document parsing | `langchain-community` document loaders + `unstructured` (light use) | Realistic parsing of PDFs/HTML/Markdown |

> Note: unlike the RAG Patterns / Memory Patterns series (pure LLM-orchestration focus), this series is *retrieval-infrastructure* focused, so a few extra local-only libraries (`networkx`, `rank_bm25`, `sentence-transformers`) are introduced only where the pattern genuinely needs them. Nothing requires a paid API.

## Prerequisites

```bash
# Ollama models (pulled once, used across the whole series)
ollama pull llama3.1
ollama pull nomic-embed-text

# Python environment
python -m venv venv
source venv/bin/activate   # Windows: venv\Scripts\activate
```

## Patterns in this series

| # | Pattern | File | Status |
|---|---|---|---|
| 1 | Vector embeddings | `01_Vector_Embeddings.md` | ✅ Done |
| 2 | Embedding generation | `02_Embedding_Generation.md` | ✅ Done |
| 3 | Chunking | `03_Chunking.md` | ✅ Done |
| 4 | Semantic chunking | `04_Semantic_Chunking.md` | ✅ Done |
| 5 | Recursive chunking | `05_Recursive_Chunking.md` | ✅ Done |
| 6 | Document parsing | `06_Document_Parsing.md` | ✅ Done |
| 7 | Metadata extraction | `07_Metadata_Extraction.md` | ✅ Done |
| 8 | Knowledge graphs | `08_Knowledge_Graphs.md` | ✅ Done |
| 9 | Vector databases | `09_Vector_Databases.md` | ✅ Done |
| 10 | Hybrid databases | `10_Hybrid_Databases.md` | ✅ Done |
| 11 | Graph + Vector retrieval | `11_Graph_Vector_Retrieval.md` | ✅ Done |
| 12 | Re-ranking | `12_Reranking.md` | ✅ Done |
| 13 | Retrieval filters | `13_Retrieval_Filters.md` | ✅ Done |
| 14 | Similarity search | `14_Similarity_Search.md` | ✅ Done |
| 15 | Approximate nearest neighbor (ANN) search | `15_ANN_Search.md` | ✅ Done |

I'll update the status column (⬜ Pending → ✅ Done) as each file is completed.

## Enterprise scenario used throughout the series

To keep things realistic and consistent, most patterns are demonstrated against the same running scenario:

> **"DocuMind" — an internal enterprise knowledge assistant** for a mid-size software company. It ingests engineering runbooks, HR policies, product PDFs, and Confluence-style pages, and must answer employee questions accurately, cite sources, respect document access levels (metadata filters), and scale to hundreds of thousands of chunks.

Each pattern file will show how that specific pattern makes DocuMind better — e.g. chunking affects answer quality, hybrid search fixes keyword-miss cases, re-ranking fixes "close but wrong" retrieval, knowledge graphs answer multi-hop questions, ANN search keeps latency low at scale, etc.

---

**Next step:** say `next` and I'll create `01_Vector_Embeddings.md`.
