# Pattern 4: Semantic Memory

> Part of the **Memory Patterns** series (Section 6). Stack: `langchain-ollama` (ChatOllama + OllamaEmbeddings), `langgraph`, `langchain-chroma`, local models (`llama3.1`, `nomic-embed-text`).

---

## 1. Introduce the Pattern

**Semantic Memory** stores **general facts and knowledge** as free-text statements, indexed by **meaning** (via embeddings) rather than by exact key — and retrieves them by *similarity to the current query*, not by exact lookup.

This is a direct evolution of Pattern 3 (Long-Term Memory). Long-Term Memory, as we built it, stores a small, fixed set of **flat key-value facts** (`dietary: vegetarian`) that you retrieve by exact key (`store.get(namespace, "profile")`). That works great when you know in advance exactly *what kind* of fact you're storing.

But real knowledge that accumulates about a user, a codebase, or a domain doesn't always fit neat key-value pairs. Consider facts like:
- *"This team deploys on Fridays only with a director's sign-off."*
- *"The user once mentioned they're migrating off MongoDB to Postgres."*
- *"This client's contract has a custom SLA of 4 hours, not the standard 24."*

These are free-form, numerous, and you don't know ahead of time what "key" they'd belong under. Semantic Memory handles this by embedding each fact into a vector, storing it in a vector store, and — when a new query comes in — retrieving the facts that are **semantically closest** to what's being discussed right now, regardless of exact wording.

---

## 2. Problem It Solves

Long-Term Memory (Pattern 3) breaks down as the number and variety of durable facts grows:

1. **Unbounded, unpredictable fact types.** You can't design a fixed schema (`dietary`, `allergy`, ...) for every possible fact a user or system might reveal over months of use. A rigid key-value store forces you to either ignore facts that don't fit a key, or invent new keys forever.
2. **Exact-key retrieval misses relevant context.** If a fact was stored under a key you didn't think to query, you never see it — even if it's highly relevant to the current conversation. A support agent asking about "SLA" needs the fact stored as "4-hour custom response time," even though the words don't match exactly.
3. **No sense of relevance ranking.** With growing fact counts (hundreds, thousands), you need a way to pull back only the *top few most relevant* facts for the current context — not dump everything into the prompt (which reintroduces the context-window problem from Pattern 2).

Semantic Memory solves this by:
- Storing facts as **free text**, with **no predefined schema**
- Embedding each fact (via `OllamaEmbeddings`) into a vector space
- At query time, embedding the *current user message* and running a **similarity search** to retrieve only the most relevant facts
- Scaling gracefully — retrieval cost and relevance don't degrade as the number of stored facts grows into the thousands, unlike scanning/guessing keys

---

## 3. Realistic Production/Enterprise Scenario

**Scenario: "SupportGenie" — an enterprise support assistant that accumulates knowledge about each client account over time**

- Support agents (and the AI assistant) interact with dozens of enterprise clients. Over months, many small, free-form facts get revealed about each account: custom SLAs, known integration quirks, prior escalations, specific technical constraints ("this client's firewall blocks webhooks, always suggest polling instead").
- These facts don't fit a fixed schema — they're heterogeneous, numerous, and open-ended.
- When a new support ticket comes in, the assistant should **automatically surface the 2-4 most relevant known facts about that account** for the current issue, without a human manually recalling or searching for them.
- The company doesn't want to hand-engineer a taxonomy for every possible client fact — new categories of facts appear constantly (this is a business reality, not just a technical inconvenience).

This is a textbook Semantic Memory use case: **open-ended, growing, free-text knowledge, retrieved by meaning-based relevance to the current situation** — exactly what embeddings + vector similarity search are built for.

---

## 4. Architecture / Flow Diagram

```
                         ┌───────────────────────────────────────────┐
                         │              Client (Support UI)             │
                         │  {account_id, thread_id, message}             │
                         └───────────────────┬───────────────────────────┘
                                              │
                                              ▼
                         ┌───────────────────────────────────────────┐
                         │              LangGraph StateGraph             │
                         │                                                │
                         │  ┌─────────────────────┐                      │
                         │  │ retrieve_semantic_     │   embeds current    │
                         │  │ memory_node             │   message, runs     │
                         │  │                        │   similarity search  │
                         │  └──────────┬─────────────┘                      │
                         │             │  top-k relevant facts               │
                         │             ▼                                     │
                         │  ┌─────────────────────┐                        │
                         │  │  chat_node            │───▶  ChatOllama        │
                         │  │  (facts injected into  │      (llama3.1)        │
                         │  │  system prompt)         │                        │
                         │  └──────────┬─────────────┘                        │
                         │             │                                       │
                         │             ▼                                       │
                         │  ┌─────────────────────┐                          │
                         │  │ store_new_facts_node   │  embeds + stores any    │
                         │  │                        │  new durable facts       │
                         │  └──────────┬─────────────┘                          │
                         └─────────────┼───────────────────────────────────────┘
                                        ▼
                         ┌───────────────────────────────────────────┐
                         │        Chroma vector store (persistent)      │
                         │  collection: "account_facts"                  │
                         │  filter: account_id="acct_777"                │
                         │                                                │
                         │  [embedding] "custom 4hr SLA"                  │
                         │  [embedding] "firewall blocks webhooks"        │
                         │  [embedding] "prior escalation: billing"       │
                         └───────────────────────────────────────────┘
```

**Key idea:** unlike Pattern 3's exact-key `store.get`, retrieval here is **`vectorstore.similarity_search(query_embedding, k=4, filter={"account_id": ...})`** — the system doesn't need to know in advance what kind of fact it's looking for, only what's semantically relevant right now.

---

## 5. Complete Request-to-Response Flow

For `account_id = "acct_777"`, a new support ticket comes in:

1. **Client sends** `{"account_id": "acct_777", "thread_id": "ticket_9042", "message": "Our webhook integration isn't receiving events."}`
2. **`retrieve_semantic_memory_node`** embeds this message using `OllamaEmbeddings(model="nomic-embed-text")`, then runs `Chroma.similarity_search(query, k=4, filter={"account_id": "acct_777"})`.
3. The vector store returns the top-4 semantically closest stored facts for this account — e.g., **"this client's firewall blocks webhooks, suggest polling instead"** ranks highly even though the ticket text never says "firewall" — because the *meaning* (webhook delivery failure) is close in embedding space.
4. Those facts are formatted into the system prompt.
5. **`chat_node`** calls `ChatOllama.invoke(...)`. The model correctly explains that webhooks are likely blocked by the client's known firewall configuration and suggests the polling alternative — a specific, account-aware answer a stock model could never give.
6. **`store_new_facts_node`** checks if this ticket revealed anything new and durable (e.g., "the client confirmed they're open to allowing our IP range through the firewall") — if so, it's embedded and added to the vector store for future tickets.
7. **Response returned**, and the semantic memory for `acct_777` is now slightly richer for the next ticket.

---

## 6. Why This Pattern Is Appropriate

| Requirement | How Semantic Memory satisfies it |
|---|---|
| Open-ended, unpredictable fact types | Facts are free text — no schema to design or maintain |
| Relevant-fact surfacing without exact keys | Similarity search finds facts related *in meaning*, not just matching words |
| Scales to large fact volumes per account | Vector search stays fast and relevant at scale (indexes, not linear key scans) |
| Multi-tenant isolation | Metadata filtering (`account_id`) scopes retrieval per account |

**Trade-offs / when it's not enough:**
- Semantic Memory retrieves facts **out of context of time** — it doesn't inherently know "this fact happened last week" vs. "two years ago," or capture a specific *event* with a timeline. That's **Episodic Memory** (Pattern 5) — sequences of dated experiences, not standalone facts.
- Because retrieval is similarity-based, it's probabilistic — a highly relevant fact phrased very differently from the query might rank low and get missed. Production systems often combine Semantic Memory with **Memory Retrieval** strategies (Pattern 13) like hybrid search (keyword + vector) or re-ranking to improve recall.
- Semantic Memory doesn't compress/merge older facts — a fact that later contradicts an older one (e.g., "SLA updated from 4hr to 2hr") can coexist with the stale one unless actively reconciled — this is what **Memory Consolidation** (Pattern 14) is for.

---

## 7. Production-Quality Python Implementation

### Requirements

```bash
pip install -U langchain-ollama langgraph langchain-core langchain-chroma chromadb
# Ollama models used locally:
#   ollama pull llama3.1
#   ollama pull nomic-embed-text
```

### `semantic_memory.py`

```python
"""
Pattern 4: Semantic Memory
Open-ended, free-text facts retrieved by meaning via embeddings +
similarity search, built on langchain-chroma + langchain-ollama.

Run:
    python semantic_memory.py
"""

from __future__ import annotations

import json
import logging
import uuid
from typing import Annotated, TypedDict

from langchain_chroma import Chroma
from langchain_core.documents import Document
from langchain_core.messages import (
    AIMessage,
    BaseMessage,
    HumanMessage,
    SystemMessage,
)
from langchain_ollama import ChatOllama, OllamaEmbeddings
from langgraph.graph import StateGraph, START, END
from langgraph.graph.message import add_messages

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
)
logger = logging.getLogger("semantic_memory")


# --------------------------------------------------------------------------
# 1. Graph state
# --------------------------------------------------------------------------
class SupportState(TypedDict):
    messages: Annotated[list[BaseMessage], add_messages]
    account_id: str
    retrieved_facts: list[str]


BASE_SYSTEM_PROMPT = (
    "You are SupportGenie, an enterprise technical support assistant. "
    "Use any known account-specific facts to tailor your answer precisely. "
    "If no relevant fact is known, answer generally but say you're doing so."
)


def build_llm(model: str = "llama3.1", temperature: float = 0.2) -> ChatOllama:
    return ChatOllama(model=model, temperature=temperature, keep_alive="10m")


def build_embeddings(model: str = "nomic-embed-text") -> OllamaEmbeddings:
    return OllamaEmbeddings(model=model)


def build_vectorstore(embeddings: OllamaEmbeddings, persist_dir: str = "./semantic_memory_db") -> Chroma:
    return Chroma(
        collection_name="account_facts",
        embedding_function=embeddings,
        persist_directory=persist_dir,
    )


# --------------------------------------------------------------------------
# 2. Node: retrieve semantically relevant facts for THIS account, given
#    the current message.
# --------------------------------------------------------------------------
def make_retrieve_node(vectorstore: Chroma, k: int = 4):
    def retrieve_semantic_memory_node(state: SupportState) -> SupportState:
        last_human = next(
            (m for m in reversed(state["messages"]) if isinstance(m, HumanMessage)),
            None,
        )
        if last_human is None:
            return {"retrieved_facts": []}

        results = vectorstore.similarity_search(
            last_human.content,
            k=k,
            filter={"account_id": state["account_id"]},
        )
        facts = [doc.page_content for doc in results]

        logger.info(
            "Retrieved %d semantic fact(s) for account=%s: %s",
            len(facts), state["account_id"], facts,
        )
        return {"retrieved_facts": facts}

    return retrieve_semantic_memory_node


# --------------------------------------------------------------------------
# 3. Node: chat, with retrieved facts injected into the system prompt.
# --------------------------------------------------------------------------
def make_chat_node(llm: ChatOllama):
    def chat_node(state: SupportState) -> SupportState:
        if state["retrieved_facts"]:
            facts_block = "\n".join(f"- {f}" for f in state["retrieved_facts"])
        else:
            facts_block = "(no relevant account-specific facts on file)"

        system_prompt = SystemMessage(
            content=f"{BASE_SYSTEM_PROMPT}\n\nKnown facts about this account:\n{facts_block}"
        )

        try:
            response: AIMessage = llm.invoke([system_prompt, *state["messages"]])
        except Exception:
            logger.exception("LLM call failed")
            response = AIMessage(content="Sorry, I hit an error. Please try again.")

        return {"messages": [response]}

    return chat_node


# --------------------------------------------------------------------------
# 4. Node: detect and store any NEW durable, account-specific fact
#    revealed in this exchange.
# --------------------------------------------------------------------------
EXTRACTION_PROMPT = """You extract durable, account-specific technical support \
facts from a support exchange (things worth remembering for FUTURE tickets \
from the same account -- e.g. infra quirks, SLAs, known constraints, prior \
resolutions). Ignore anything purely about this one ticket that won't matter \
again.

Respond ONLY with a JSON list of short fact strings, e.g.:
["Client's firewall blocks outbound webhooks", "Custom SLA: 4 hour response"]
If nothing durable was revealed, respond with [].

User message: "{user_msg}"
Assistant reply: "{ai_msg}"
JSON:"""


def make_store_facts_node(llm: ChatOllama, vectorstore: Chroma):
    def store_new_facts_node(state: SupportState) -> SupportState:
        last_human = next(
            (m for m in reversed(state["messages"]) if isinstance(m, HumanMessage)),
            None,
        )
        last_ai = state["messages"][-1] if state["messages"] else None
        if last_human is None or not isinstance(last_ai, AIMessage):
            return {}

        extraction_llm = llm.bind(format="json")
        prompt = EXTRACTION_PROMPT.format(user_msg=last_human.content, ai_msg=last_ai.content)

        try:
            raw = extraction_llm.invoke(prompt).content
            new_facts = json.loads(raw) if raw.strip() else []
        except Exception:
            logger.warning("Semantic fact extraction failed to parse; skipping.")
            new_facts = []

        if not new_facts:
            return {}

        docs = [
            Document(
                page_content=fact,
                metadata={"account_id": state["account_id"]},
                id=str(uuid.uuid4()),
            )
            for fact in new_facts
        ]
        vectorstore.add_documents(docs)
        logger.info("Stored %d new semantic fact(s) for account=%s", len(docs), state["account_id"])

        return {}

    return store_new_facts_node


# --------------------------------------------------------------------------
# 5. Assemble the graph
# --------------------------------------------------------------------------
def build_graph(persist_dir: str = "./semantic_memory_db"):
    llm = build_llm()
    embeddings = build_embeddings()
    vectorstore = build_vectorstore(embeddings, persist_dir)

    graph_builder = StateGraph(SupportState)
    graph_builder.add_node("retrieve_semantic_memory_node", make_retrieve_node(vectorstore))
    graph_builder.add_node("chat_node", make_chat_node(llm))
    graph_builder.add_node("store_new_facts_node", make_store_facts_node(llm, vectorstore))

    graph_builder.add_edge(START, "retrieve_semantic_memory_node")
    graph_builder.add_edge("retrieve_semantic_memory_node", "chat_node")
    graph_builder.add_edge("chat_node", "store_new_facts_node")
    graph_builder.add_edge("store_new_facts_node", END)

    return graph_builder.compile()


# --------------------------------------------------------------------------
# 6. Service wrapper
# --------------------------------------------------------------------------
class SemanticMemoryService:
    def __init__(self, persist_dir: str = "./semantic_memory_db"):
        self.graph = build_graph(persist_dir)

    def send(self, account_id: str, user_message: str) -> str:
        result = self.graph.invoke(
            {
                "messages": [HumanMessage(content=user_message)],
                "account_id": account_id,
                "retrieved_facts": [],
            }
        )
        return result["messages"][-1].content


# --------------------------------------------------------------------------
# 7. Demo: seed a fact in one ticket, retrieve it by MEANING in a later,
#    differently-worded ticket.
# --------------------------------------------------------------------------
if __name__ == "__main__":
    service = SemanticMemoryService()
    account = "acct_777"

    print("--- Ticket 1 ---")
    print("User:", "Heads up, our corporate firewall blocks any outbound webhook calls from your platform.")
    print("Bot: ", service.send(account, "Heads up, our corporate firewall blocks any outbound webhook calls from your platform."))

    print("\n--- Ticket 2 (weeks later, DIFFERENT wording, no mention of 'firewall') ---")
    print("User:", "Our webhook integration isn't receiving any events, can you help?")
    print("Bot: ", service.send(account, "Our webhook integration isn't receiving any events, can you help?"))
    print("(^ note: the bot should proactively surface the firewall fact via semantic similarity)")
```

### Notes on production-readiness in this code

- **Metadata filtering (`filter={"account_id": ...}`)** enforces multi-tenant isolation at the vector-store level — account A's facts can never leak into account B's retrieval, even though they share one Chroma collection.
- **Persistent Chroma (`persist_directory=`)** — facts survive process restarts, unlike an in-memory-only vector store.
- **Separate extraction step**, same discipline as Pattern 3 — facts are only stored if the LLM judges them *durable and account-specific*, not every passing remark.
- **`k=4` bounded retrieval** keeps the number of injected facts small and relevant, avoiding the same context bloat problem Pattern 2 addressed for raw conversation history.
- **Graceful retrieval failure**: if `last_human` isn't found or the store is empty, the node returns an empty fact list rather than raising — the chat still proceeds with a general answer.

---

## 8. Version Pins (latest stable, compatible, at time of writing)

```
python            >= 3.11
langchain-core    >= 0.3.0
langchain-ollama  >= 0.2.0
langchain-chroma  >= 0.1.4
chromadb          >= 0.5.0
langgraph         >= 0.2.0
```

```bash
pip install -U "langchain-core>=0.3.0" "langchain-ollama>=0.2.0" "langchain-chroma>=0.1.4" "chromadb>=0.5.0" "langgraph>=0.2.0"
```

> Requires both Ollama models pulled locally: `ollama pull llama3.1` (generation) and `ollama pull nomic-embed-text` (embeddings for the vector store).

---

**Next up → Pattern 5: Episodic Memory** (remembering specific, time-stamped *events/experiences* — "what happened" sequences — rather than standalone generalized facts).
