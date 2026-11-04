# Pattern 15: Memory — Python Version

Without memory, every request to the OpenAI client is amnesia — the model has zero awareness of anything said before. Memory comes in two flavors: **short-term** (the current conversation's message history, so the model remembers what you just said) and **long-term** (durable facts that persist across sessions — preferences, history, profile info — retrieved like RAG, but about the user/conversation rather than documents).

This is a Python/FastAPI translation of the original Java/Spring AI pattern, preserving the same structure and behavior.

---

## What's happening

Short-term memory is just the conversation's recent message window, stored per `conversation_id` and automatically re-injected into every call — we build a small `ChatMemory` class to do transparently in Python what Spring AI's `MessageChatMemoryAdvisor` does behind an annotation. Long-term memory is different: it's specific facts worth keeping forever (a user's stated preferences, recurring constraints), extracted out of conversations and stored in a vector store, retrieved by similarity **exactly like RAG (Pattern 4)** — except the "documents" are facts about the user, not company docs, reusing the same `VectorStore` class Pattern 4 already built.

### The core concept — why two completely different mechanisms for "remembering"

It's tempting to think "memory" is one feature, but short-term and long-term memory solve genuinely different problems and need genuinely different storage:

- **Short-term memory** needs to preserve *exact wording and order* — "what did we just say to each other" — because conversational coherence depends on the precise back-and-forth. This is naturally a **list**, replayed in full, every time.
- **Long-term memory** needs to survive across sessions that have nothing else in common, and there could be thousands of accumulated facts by the time a new session starts — you can't replay the full history of everything the user has ever told you into every prompt (it wouldn't fit, and most of it would be irrelevant to the current question anyway). This is naturally a **similarity search problem** — exactly RAG's problem, just over "facts about this user" instead of "company documents."

**Real-world analogy:** think of the difference between a conversation you're having with a friend right now versus what your friend generally knows about you. Mid-conversation, your friend remembers everything you said five minutes ago, in order — that's short-term memory, a simple replay. But your friend also knows, from *months* of prior conversations, that you're vegetarian, that you hate small talk, that you're saving for a house — facts they don't replay in full every time you talk, but that surface naturally and selectively when relevant ("oh, you're vegetarian, right? let me suggest somewhere else"). Nobody recites your entire life story to themselves before every conversation — they recall the *specific relevant fact* at the moment it matters. That selective recall is exactly what a similarity search over long-term memory gives the model.

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

Long-term memory reuses the exact `VectorStore` wrapper introduced in Pattern 4 — no new dependency needed beyond what RAG already required.

---

## Short-term memory

### 1. A `ChatMemory` class — the Python equivalent of `ChatMemory` + `MessageChatMemoryAdvisor`

Spring AI's `JdbcChatMemoryRepository` persists history to a real database so it survives restarts and works across scaled instances. The Python version below uses SQLite for the same durability guarantee in a single file — swap the `sqlite3` calls for any real database client (Postgres, Redis) the same way Spring AI swaps `ChatMemoryRepository` implementations.

```python
# chat_memory.py
import sqlite3
import json


class ChatMemory:
    """
    Equivalent to Spring AI's ChatMemory + MessageChatMemoryAdvisor:
    stores a sliding window of recent messages per conversation_id and
    replays them into every call automatically.
    """

    def __init__(self, db_path: str = "chat_memory.db", max_messages: int = 20):
        self.max_messages = max_messages
        self._conn = sqlite3.connect(db_path, check_same_thread=False)
        self._conn.execute("""
            CREATE TABLE IF NOT EXISTS messages (
                conversation_id TEXT,
                turn_index INTEGER,
                role TEXT,
                content TEXT
            )
        """)
        self._conn.commit()

    def add_message(self, conversation_id: str, role: str, content: str) -> None:
        cursor = self._conn.execute(
            "SELECT COALESCE(MAX(turn_index), -1) + 1 FROM messages WHERE conversation_id = ?",
            (conversation_id,),
        )
        next_index = cursor.fetchone()[0]
        self._conn.execute(
            "INSERT INTO messages (conversation_id, turn_index, role, content) VALUES (?, ?, ?, ?)",
            (conversation_id, next_index, role, content),
        )
        self._conn.commit()
        self._trim(conversation_id)

    def _trim(self, conversation_id: str) -> None:
        # Sliding window: keep only the most recent `max_messages` turns —
        # equivalent to MessageWindowChatMemory.builder().maxMessages(20)
        self._conn.execute("""
            DELETE FROM messages
            WHERE conversation_id = ? AND turn_index NOT IN (
                SELECT turn_index FROM messages
                WHERE conversation_id = ?
                ORDER BY turn_index DESC LIMIT ?
            )
        """, (conversation_id, conversation_id, self.max_messages))
        self._conn.commit()

    def get_messages(self, conversation_id: str) -> list[dict]:
        cursor = self._conn.execute(
            "SELECT role, content FROM messages WHERE conversation_id = ? ORDER BY turn_index",
            (conversation_id,),
        )
        return [{"role": role, "content": content} for role, content in cursor.fetchall()]
```

### 2. FastAPI endpoint using it

```python
# main.py (short-term memory section)
import os
from dotenv import load_dotenv
from fastapi import FastAPI, Query
from openai import OpenAI

from chat_memory import ChatMemory

load_dotenv()

app = FastAPI()
client = OpenAI(api_key=os.getenv("OPENAI_API_KEY"))
chat_memory = ChatMemory(max_messages=20)


@app.get("/api/memory/chat")
def chat(message: str = Query(...), conversation_id: str = Query(...)):
    # Automatically re-inject prior history for this conversation_id
    history = chat_memory.get_messages(conversation_id)
    messages = history + [{"role": "user", "content": message}]

    response = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=messages,
    )
    answer = response.choices[0].message.content

    # Persist both turns for next time
    chat_memory.add_message(conversation_id, "user", message)
    chat_memory.add_message(conversation_id, "assistant", answer)

    return {"answer": answer}
```

Run it:

```bash
uvicorn main:app --reload --port 8080
```

---

## Try it

```bash
curl "http://localhost:8080/api/memory/chat?message=My%20name%20is%20Priya&conversation_id=session-1"
curl "http://localhost:8080/api/memory/chat?message=What%27s%20my%20name%3F&conversation_id=session-1"
```

```json
{"answer": "Your name is Priya"}
```

The `conversation_id` is your partitioning key — different users/sessions get fully isolated histories, all transparently managed by `ChatMemory.get_messages()`.

---

## Long-term memory

Long-term memory reuses **Pattern 4's `VectorStore` class unmodified** — this is the clearest illustration in the whole series that RAG and long-term memory are the same retrieval mechanism applied to different content.

### 1. The long-term memory service

```python
# long_term_memory.py
import uuid
from vector_store import VectorStore  # reused directly from Pattern 4


class LongTermMemoryService:
    def __init__(self, vector_store: VectorStore):
        self.vector_store = vector_store

    # Call this after a conversation to persist durable facts worth remembering
    def remember(self, user_id: str, fact: str) -> None:
        self.vector_store.add_with_metadata(
            texts=[fact],
            metadatas=[{"user_id": user_id, "type": "long_term_fact"}],
            ids=[str(uuid.uuid4())],
        )

    # Call this before answering, to recall relevant facts about this user
    def recall(self, user_id: str, current_message: str, top_k: int = 3) -> list[str]:
        return self.vector_store.similarity_search(
            current_message,
            top_k=top_k,
            where={"user_id": user_id},   # metadata filter, equivalent to filterExpression
        )
```

This requires two small additions to Pattern 4's `VectorStore` class — a metadata-aware `add_with_metadata()` and a `where` filter on `similarity_search()`:

```python
# vector_store.py — additions to Pattern 4's VectorStore class

    def add_with_metadata(self, texts: list[str], metadatas: list[dict], ids: list[str]) -> None:
        embeddings = self._embed(texts)
        self._collection.add(ids=ids, documents=texts, embeddings=embeddings, metadatas=metadatas)

    def similarity_search(
        self, query: str, top_k: int = 4, similarity_threshold: float = 0.0, where: dict | None = None
    ) -> list[str]:
        query_embedding = self._embed([query])[0]
        results = self._collection.query(
            query_embeddings=[query_embedding], n_results=top_k, where=where,
        )
        docs = results["documents"][0]
        distances = results["distances"][0]
        return [doc for doc, dist in zip(docs, distances) if (1 - dist) >= similarity_threshold]
```

> **Why `where={"user_id": user_id}`?** This is the direct equivalent of Spring AI's `.filterExpression("userId == '" + userId + "'")` — both scope the similarity search down to only *this user's* stored facts, so one user's preferences never leak into another user's recalled context.

### 2. FastAPI endpoint

```python
# main.py (long-term memory section)
from long_term_memory import LongTermMemoryService
from vector_store import VectorStore

vector_store = VectorStore(client, collection_name="long_term_memory")
long_term_memory = LongTermMemoryService(vector_store)


@app.get("/api/memory/chat-with-recall")
def chat_with_recall(user_id: str = Query(...), message: str = Query(...)):

    relevant_facts = long_term_memory.recall(user_id, message)

    if relevant_facts:
        contextual_prompt = (
            f"Known facts about this user:\n{chr(10).join(relevant_facts)}\n\n"
            f"User says: {message}"
        )
    else:
        contextual_prompt = message

    response = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=[{"role": "user", "content": contextual_prompt}],
    )
    answer = response.choices[0].message.content

    # Optionally: ask the model whether this exchange contains a durable fact worth saving
    # (a simple heuristic version is shown — production systems often use a dedicated
    # extraction call, similar to Pattern 2's structured output)
    if "i prefer" in message.lower() or "i am" in message.lower():
        long_term_memory.remember(user_id, message)

    return {"answer": answer}
```

---

## Try it

```bash
curl "http://localhost:8080/api/memory/chat-with-recall?user_id=u123&message=I%20prefer%20concise%20answers%20with%20no%20fluff"
```

Weeks later, in a brand new conversation:

```bash
curl "http://localhost:8080/api/memory/chat-with-recall?user_id=u123&message=Explain%20how%20Kubernetes%20works"
```

The model recalls the preference and answers tersely, unprompted — `recall()` found the stored preference fact via similarity search (the current question doesn't share any words with "I prefer concise answers," but they're semantically related enough to surface) and it got woven into the prompt automatically.

---

## Mapping Java/Spring AI concepts to Python

| Spring AI / Java | Python | What it's really doing |
|---|---|---|
| `ChatMemory` + `MessageWindowChatMemory.builder().maxMessages(20)` | `ChatMemory` class with SQLite storage + `_trim()` | Stores and windows the recent message history per conversation |
| `JdbcChatMemoryRepository` | SQLite (`sqlite3.connect(...)`) | Durable persistence for short-term memory across restarts |
| `MessageChatMemoryAdvisor` (auto-injects history into every call) | `chat_memory.get_messages(conversation_id)` prepended to `messages` manually | Both re-inject the full recent window before every call — Spring AI hides this inside an advisor, Python does it explicitly (same theme as Pattern 5's tool loop) |
| `ChatMemory.CONVERSATION_ID` param | `conversation_id` query parameter | The partitioning key isolating different conversations' histories |
| `VectorStore.add(List.of(new Document(...)))` | `vector_store.add_with_metadata(texts, metadatas, ids)` | Persisting a durable fact, tagged with the owning user's ID |
| `SearchRequest.builder().filterExpression("userId == '...'")` | `similarity_search(..., where={"user_id": user_id})` | Scoping similarity search to only one user's facts |
| `vectorStore.similaritySearch(...)` (same interface as RAG, Pattern 4) | `vector_store.similarity_search(...)` (same `VectorStore` class as RAG, Pattern 4) | Confirms long-term memory *is* RAG, just over facts instead of documents |

**Key insight:** short-term memory is nothing more than **Pattern 1, called repeatedly against a persisted, growing `messages` list** — mechanically identical to what Pattern 12 (Reflection) already did with its in-process `conversation` list, except now the list survives across requests by living in a database instead of a Python variable. Long-term memory is nothing more than **Pattern 4 (RAG), unmodified**, pointed at a different kind of content. Nothing new is invented in this pattern at all — it's a demonstration that "memory" was already fully explained by two earlier patterns; this doc just names the combination and shows the plumbing that makes both durable and per-user.

---

## Short-term vs. long-term

|  | Short-term (`ChatMemory`) | Long-term (vector-backed) |
|---|---|---|
| **Scope** | One conversation thread | Across all sessions, forever |
| **Storage** | Recent message window (sliding), SQLite | Selectively extracted durable facts, ChromaDB |
| **Retrieval** | Always included, in order | Similarity-searched, only what's relevant |
| **Python mechanism** | `ChatMemory` class + manual prepend to `messages` | `VectorStore` (identical class reused from Pattern 4) |

---

## Production notes

- Don't dump everything into long-term memory — extract selectively (ideally via a dedicated structured-output call, like Pattern 2, asking "is there a durable fact worth remembering here?") rather than naive keyword matching (`"i prefer" in message.lower()`) as shown above; naive heuristics both over- and under-capture. A `FactExtraction(BaseModel)` with a `worth_remembering: bool` and `fact: str | None` field, produced via `.parse()`, is the natural upgrade.
- SQLite is fine for a single-instance prototype; use Postgres or Redis for anything horizontally scaled, exactly as the Java doc recommends `JdbcChatMemoryRepository` or a Redis-backed equivalent over in-memory storage — in-memory (or single-file SQLite on ephemeral disk) loses everything on restart and doesn't work across multiple app instances.
- Always cap the message window (`max_messages` in `ChatMemory.__init__`) — unbounded history grows token cost linearly with conversation length and eventually exceeds context limits. This is enforced by `_trim()` after every `add_message()` call above.

---

## Project structure

```
pattern15-memory/
├── main.py
├── chat_memory.py
├── long_term_memory.py
├── vector_store.py          # reused/extended from Pattern 4
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
| 15 | Memory | ✅ Persisted short-term history + RAG-style long-term fact recall (this doc) |

*(To be filled in as each pattern's source doc is provided.)*