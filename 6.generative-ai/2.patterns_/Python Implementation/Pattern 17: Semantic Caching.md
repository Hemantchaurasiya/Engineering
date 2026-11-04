# Pattern 17: Semantic Caching — Python Version

A normal cache only matches identical keys — but **"What's the capital of France?"** and **"Tell me France's capital city"** mean the same thing and should hit the same cache entry, even though the strings differ completely. Semantic caching embeds the incoming query, searches past query embeddings for a near-identical match, and returns the cached response instead of calling the LLM at all — cutting cost and latency on repeated or rephrased questions.

This is a Python/FastAPI translation of the original Java/Spring AI pattern, preserving the same structure and behavior.

---

## What's happening

This reuses the exact same **`VectorStore`** infrastructure from **RAG (Pattern 4)** — and the metadata-filtering additions from **Long-Term Memory (Pattern 15)** — but instead of storing document chunks or user facts, you store *past queries* as embeddings, with the response text in metadata. On a new query, you do a similarity search against this cache first; if something above a very high threshold (e.g. **0.95+**) comes back, you return the cached response and skip the LLM call entirely. Otherwise you call the model normally and write the new pair into the cache for next time.

### The core concept — why an exact-match cache can't do this

A traditional cache (a Python `dict`, Redis, `functools.lru_cache`) works by hashing the *literal input string* and looking up an exact match. "What's the capital of France?" and "Tell me France's capital city" produce completely different hashes — they share almost no characters — so an exact-match cache would treat them as two unrelated questions and call the LLM twice for a question it already answered once. The fix requires comparing *meaning*, not *characters*, which is exactly what embeddings are for — the same insight Pattern 4 (RAG) already exploited when it retrieved document chunks that were relevant in meaning, not matching in keywords.

**Real-world analogy:** think of a customer support rep who's answered "how do I reset my password" a hundred times, phrased a hundred different ways — "I forgot my login," "can't sign in," "password isn't working." A rep who only recognized the *exact* phrase "how do I reset my password" would re-explain the same steps from scratch every single time someone phrased it slightly differently, which is absurd — a good rep recognizes it's *the same underlying question* regardless of the exact words used, and gives the same well-rehearsed answer. Semantic caching gives your application that same recognition — it's not matching strings, it's matching *what's actually being asked.*

---

## Setup

### `requirements.txt`

```
fastapi==0.115.0
uvicorn==0.32.0
openai==1.54.0
python-dotenv==1.0.1
chromadb==0.5.20
```

No new dependencies — this is Pattern 4's `VectorStore` class, given a second `chromadb` collection dedicated to caching instead of documents.

---

## Code

### `semantic_cache.py`

```python
import uuid
import datetime
from vector_store import VectorStore  # reused from Pattern 4 / 15

# High threshold deliberately: cache hits should only fire on near-identical
# meaning, not loosely related queries — false hits return wrong answers.
CACHE_SIMILARITY_THRESHOLD = 0.96


class SemanticCacheService:
    def __init__(self, cache_store: VectorStore):
        # A dedicated VectorStore/collection, separate from any RAG document
        # store — see Production notes on why these must not be mixed.
        self.cache_store = cache_store

    def check_cache(self, query: str) -> str | None:
        results = self.cache_store.similarity_search_with_metadata(
            query,
            top_k=1,
            similarity_threshold=CACHE_SIMILARITY_THRESHOLD,
        )

        if not results:
            return None

        return results[0]["metadata"].get("response")

    def store(self, query: str, response: str) -> None:
        self.cache_store.add_with_metadata(
            texts=[query],  # embedded for similarity search
            metadatas=[{
                "response": response,  # the actual cached answer
                "cached_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
            }],
            ids=[str(uuid.uuid4())],
        )
```

This needs one more small addition to Pattern 4's `VectorStore` class — a variant of `similarity_search` that returns metadata alongside the matched text, not just the text itself (the plain `similarity_search()` from Pattern 4 only ever returned document strings, which was enough for RAG, but here we specifically need the `response` field stored in metadata):

```python
# vector_store.py — one more addition, alongside Pattern 15's add_with_metadata()

    def similarity_search_with_metadata(
        self, query: str, top_k: int = 4, similarity_threshold: float = 0.0
    ) -> list[dict]:
        query_embedding = self._embed([query])[0]
        results = self._collection.query(
            query_embeddings=[query_embedding], n_results=top_k,
        )
        docs = results["documents"][0]
        metadatas = results["metadatas"][0]
        distances = results["distances"][0]

        return [
            {"text": doc, "metadata": meta}
            for doc, meta, dist in zip(docs, metadatas, distances)
            if (1 - dist) >= similarity_threshold
        ]
```

### `main.py`

```python
import os
from dotenv import load_dotenv
from fastapi import FastAPI, Query
from openai import OpenAI

from vector_store import VectorStore
from semantic_cache import SemanticCacheService

load_dotenv()

app = FastAPI()
client = OpenAI(api_key=os.getenv("OPENAI_API_KEY"))

# Separate collection from any RAG document store — see Production notes.
cache_vector_store = VectorStore(client, collection_name="semantic_cache")
cache = SemanticCacheService(cache_vector_store)


@app.get("/api/cached/ask")
def ask(question: str = Query(...)):

    # 1. Check cache first — skip the LLM entirely on a hit
    cached_response = cache.check_cache(question)
    if cached_response is not None:
        return {"answer": cached_response, "cache_hit": True}   # free, sub-millisecond response

    # 2. Cache miss — call the LLM normally
    response = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=[{"role": "user", "content": question}],
    )
    answer = response.choices[0].message.content

    # 3. Store for next time
    cache.store(question, answer)

    return {"answer": answer, "cache_hit": False}
```

Run it:

```bash
uvicorn main:app --reload --port 8080
```

---

## Try it

```bash
curl "http://localhost:8080/api/cached/ask?question=What%20is%20the%20capital%20of%20France%3F"
# {"answer": "...", "cache_hit": false}  — calls LLM, ~800ms, costs tokens

curl "http://localhost:8080/api/cached/ask?question=Tell%20me%20France%27s%20capital%20city"
# {"answer": "...", "cache_hit": true}  — different wording, same meaning, ~20ms, zero LLM cost
```

---

## Mapping Java/Spring AI concepts to Python

| Spring AI / Java | Python | What it's really doing |
|---|---|---|
| `VectorStore cacheStore` (a distinct bean from the RAG store) | `VectorStore(client, collection_name="semantic_cache")` (a distinct collection from any RAG store) | Both keep cached query/response pairs isolated from document chunks in a separate collection |
| `SearchRequest.builder().similarityThreshold(0.96).topK(1)` | `similarity_search_with_metadata(query, top_k=1, similarity_threshold=0.96)` | The same high-threshold, single-result cache lookup |
| `Document(id, query, Map.of("response", response, "cachedAt", ...))` | `add_with_metadata(texts=[query], metadatas=[{"response": ..., "cached_at": ...}], ids=[...])` | Storing the query as the embedded text, with the actual answer tucked into metadata rather than embedded itself |
| `matches.get(0).getMetadata().get("response")` | `results[0]["metadata"].get("response")` | Pulling the cached answer back out of the matched document's metadata |
| `Optional<String> checkCache(...)` | `str \| None` return type | Both represent "cache hit with a value" vs. "cache miss," Python's `None` playing the role of an empty `Optional` |
| `cache.checkCache(question).isPresent()` gate in the controller | `if cached_response is not None:` gate in the endpoint | The exact same short-circuit: skip the LLM call entirely on a hit |

**Key insight:** semantic caching adds **zero new mechanisms** to this series — it's Pattern 4's `VectorStore`, storing *questions* instead of *document chunks*, with the *answer* riding along in metadata instead of being embedded itself. The "cache" framing is really just RAG's retrieval step used as a **gate before calling the LLM at all**, rather than as *context fed into* an LLM call. Once you notice that, this pattern is best understood as "Pattern 4, plus an `if` statement that skips Pattern 1 entirely when the retrieval already answers the question."

---

## Production notes

- Threshold tuning is the whole game. Too low and you'll return cached answers to genuinely different questions (dangerous for anything factual or time-sensitive). Too high and you barely get any cache hits at all. Start around **0.95–0.97** (`CACHE_SIMILARITY_THRESHOLD` above) and validate against real query logs before loosening.

- Don't cache time-sensitive or personalized queries — **"what's the weather today"** or **"what's in my cart"** should never hit a semantic cache, since the "same" question has a different correct answer depending on when/who asked. Gate caching to genuinely stable Q&A (FAQs, documentation lookups, static factual queries) — in Python this is as simple as only calling `cache.check_cache(...)` / `cache.store(...)` from endpoints handling stable content, and never from ones handling live or per-user data.

- Add a TTL/expiry — even stable-seeming answers (pricing, policies, product specs) go stale. Store a `cached_at` timestamp (as shown above) and treat hits older than your freshness window as misses — a straightforward Python addition: after `check_cache()` returns a hit, also return its `cached_at` from metadata and compare against `datetime.now(timezone.utc)` in the caller before trusting it.

- Separate cache store from your RAG vector store — mixing document chunks and cached query/response pairs in one collection makes both retrieval paths noisier; keep them as distinct `chromadb` collections (`collection_name="semantic_cache"` vs. `collection_name="docs"`), exactly as the Java version keeps them as distinct `VectorStore` beans.

- For very high traffic, an exact-match cache (e.g. a `dict` or Redis keyed on a hash of the normalized query, `hashlib.sha256(query.lower().strip().encode()).hexdigest()`) layered in front of the semantic cache catches identical repeats even faster — no embedding call needed at all for a literal repeat — with semantic caching catching the rephrased ones behind it.

---

## Project structure

```
pattern17-semantic-caching/
├── main.py
├── semantic_cache.py
├── vector_store.py    # reused/extended from Pattern 4 & 15
├── requirements.txt
├── .env
└── README.md
```

---

## Pattern series progress

| # | Pattern | Adds |
|---|---|---|
| 1 | Direct Prompting | ✅ Single stateless call |
| 2 | Structured Output | ✅ Typed, validated responses via Pydantic |
| 3 | Prompt Templates & Few-Shot | ✅ Reusable templates + example-driven prompting |
| 4 | RAG | ✅ Retrieval-grounded answers from private data |
| 5 | Tool Calling | ✅ Model-initiated function execution via an explicit loop |
| 6 | Prompt Chaining | ✅ Sequential, focused calls with gate checks in between |
| 7 | Routing | ✅ Classify-then-dispatch to specialized handlers |
| 8 | Parallelization | ✅ Concurrent sectioning and voting via thread pools |
| 9 | Orchestrator-Workers | ✅ LLM-planned subtasks dispatched and synthesized dynamically |
| 10 | Evaluator-Optimizer | ✅ Self-correcting generate → critique → regenerate loop |
| 11 | ReAct Agent | ✅ Multi-hop reason → act → observe loop with model-driven branching |
| 12 | Reflection | ✅ Same-thread self-critique and revision |
| 13 | Planning | ✅ Full upfront strategy, inspectable before execution |
| 14 | Multi-Agent Collaboration | ✅ Routing decision looped across specialist agents until convergence |
| 15 | Memory | ✅ Persisted short-term history + RAG-style long-term fact recall |
| 16 | Streaming Responses | ✅ Token-by-token delivery via generator functions |
| 17 | Semantic Caching | ✅ Meaning-based cache lookup that skips the LLM entirely on a hit (this doc) |

*(To be filled in as each pattern's source doc is provided.)*