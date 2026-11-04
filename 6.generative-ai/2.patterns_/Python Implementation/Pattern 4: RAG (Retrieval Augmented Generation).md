# Pattern 4: RAG (Retrieval Augmented Generation) — Python Version

This is the big one — the pattern that lets an LLM answer questions about your private data without retraining it. The model doesn't know your company's docs, so before calling it, you retrieve the most relevant chunks from a vector store and stuff them into the prompt as context. The model then answers grounded in that context instead of guessing.

This is a Python/FastAPI translation of the original Java/Spring AI pattern, preserving the same structure and behavior.

---

## What's happening

Two separate pipelines — same as the Java version:

### Ingestion (offline)

- Load documents
- Split into chunks
- Embed each chunk into a vector
- Store in a vector database

### Query (runtime)

- Embed the user's question
- Find the most similar stored chunks
- Inject them into the prompt as context
- Call the LLM

In Python, we build both halves ourselves using the raw OpenAI SDK for embeddings + chat, and **ChromaDB** as the vector store — the direct equivalent of Spring AI's `SimpleVectorStore` (in-memory, dev-friendly, swappable for a production store behind the same interface).

### The core concept — why RAG exists at all

An LLM's knowledge is frozen at training time and is *general* — it has never seen your company's internal handbook, your product's latest changelog, or last week's support tickets. Fine-tuning a whole model on your private data is slow, expensive, and has to be redone every time the data changes. RAG sidesteps all of that: instead of teaching the model your facts, you **hand it the facts at question time**, like an open-book exam instead of a memorization test.

**Real-world analogy:** think of a new employee's first week versus a senior employee. A brand-new hire (the raw LLM) is smart and knows general things about the industry, but doesn't know your company's specific PTO policy. You wouldn't retrain their entire brain — you'd hand them the employee handbook and say "look this up before you answer." That's RAG: instead of the model reciting from memory (and possibly making something up — hallucinating), it's handed the *relevant page* of the handbook right before answering, and told to answer from that page specifically.

### Why "embed" and "similarity search" instead of keyword search?

Embeddings turn text into a list of numbers (a vector) that captures *meaning*, not just literal words. "How much time off do new parents get?" and "What is our parental leave policy?" share almost no words in common, but they mean nearly the same thing — their embedding vectors end up close together in that numeric space. A plain keyword search (`Ctrl+F`) would miss this connection; a vector similarity search finds it because it's comparing meaning, not spelling.

**Real-world analogy:** think of a reference librarian versus a card catalog that only matches exact titles. Ask the librarian "I need something about a whale that hunts a man" and they'll hand you *Moby-Dick* even though you never said the title. That's what vector similarity search does — it retrieves by meaning, not exact string match.

---

## Setup

### `requirements.txt`

```
fastapi==0.115.0
uvicorn==0.32.0
openai==1.54.0
python-dotenv==1.0.1
chromadb==0.5.20
pypdf==5.0.1
tiktoken==0.8.0
python-multipart==0.0.12
```

- **chromadb** — the embedded, in-memory vector database. Equivalent to `SimpleVectorStore`.
- **pypdf** — PDF text extraction, playing the narrower role Tika plays in the Java version (Tika handles many formats; pypdf covers the common PDF case simply — swap in `unstructured` or similar for broader format support).
- **tiktoken** — OpenAI's official tokenizer, used to measure chunk size in *tokens* (not characters), matching how `TokenTextSplitter` measures chunks in the Java version.

### `vector_store.py` — the `SimpleVectorStore` equivalent

```python
import chromadb
from openai import OpenAI


class VectorStore:
    """
    Thin wrapper around ChromaDB's in-memory client, mirroring Spring AI's
    VectorStore interface: add(documents) and similarity_search(query, k, threshold).
    Swap the internals for a persistent client (Chroma server, pgvector, Pinecone,
    etc.) without changing this interface — same idea as swapping SimpleVectorStore
    for PgVectorStore behind a single @Bean in the Java version.
    """

    def __init__(self, openai_client: OpenAI, collection_name: str = "docs"):
        self._openai = openai_client
        self._chroma = chromadb.Client()  # in-memory, dev-friendly
        self._collection = self._chroma.get_or_create_collection(collection_name)

    def _embed(self, texts: list[str]) -> list[list[float]]:
        response = self._openai.embeddings.create(
            model="text-embedding-3-small",
            input=texts,
        )
        return [item.embedding for item in response.data]

    def add(self, chunks: list[str]) -> None:
        embeddings = self._embed(chunks)
        ids = [f"chunk-{i}-{hash(c) & 0xfffffff}" for i, c in enumerate(chunks)]
        self._collection.add(ids=ids, documents=chunks, embeddings=embeddings)

    def similarity_search(
        self, query: str, top_k: int = 4, similarity_threshold: float = 0.75
    ) -> list[str]:
        query_embedding = self._embed([query])[0]
        results = self._collection.query(
            query_embeddings=[query_embedding], n_results=top_k
        )

        # Chroma returns cosine *distance*; convert to similarity (1 - distance)
        # to match Spring AI's similarityThreshold semantics.
        docs = results["documents"][0]
        distances = results["distances"][0]

        return [
            doc
            for doc, dist in zip(docs, distances)
            if (1 - dist) >= similarity_threshold
        ]
```

---

## Code

### 1. Ingestion service — runs once to populate the store

```python
# ingestion.py
import tiktoken
from pypdf import PdfReader
from vector_store import VectorStore

_encoding = tiktoken.get_encoding("cl100k_base")


def _load_pdf_text(file_path: str) -> str:
    reader = PdfReader(file_path)
    return "\n".join(page.extract_text() or "" for page in reader.pages)


def _split_into_chunks(
    text: str, target_tokens: int = 800, min_tokens: int = 350
) -> list[str]:
    """
    Token-aware splitter, equivalent in purpose to Spring AI's TokenTextSplitter.
    Splits on paragraph boundaries first, then packs paragraphs into chunks up
    to target_tokens, discarding any final fragment smaller than min_tokens.
    """
    paragraphs = [p.strip() for p in text.split("\n\n") if p.strip()]

    chunks, current, current_tokens = [], [], 0
    for para in paragraphs:
        para_tokens = len(_encoding.encode(para))
        if current_tokens + para_tokens > target_tokens and current:
            chunks.append("\n\n".join(current))
            current, current_tokens = [], 0
        current.append(para)
        current_tokens += para_tokens

    if current and current_tokens >= min_tokens:
        chunks.append("\n\n".join(current))

    return chunks


class DocumentIngestionService:
    def __init__(self, vector_store: VectorStore):
        self.vector_store = vector_store

    def ingest(self, file_path: str) -> int:
        raw_text = _load_pdf_text(file_path)          # 1. Load raw content
        chunks = _split_into_chunks(raw_text)          # 2. Split into chunks
        self.vector_store.add(chunks)                  # 3. Embed + store
        return len(chunks)
```

### 2. Query endpoint — the easy way, using a `QuestionAnswerAdvisor` equivalent

Spring AI's `QuestionAnswerAdvisor` is a reusable "wrapper" that automatically retrieves context before every call. In Python, we get the same behavior with a small class that wraps the OpenAI client the same way — configure it once, call it like a normal chat client from then on.

```python
# qa_advisor.py
from openai import OpenAI
from vector_store import VectorStore


class QuestionAnswerAdvisor:
    """
    Equivalent to Spring AI's QuestionAnswerAdvisor: wraps chat calls and
    automatically injects retrieved context before every call, so callers
    never have to hand-write the "stuff context into prompt" logic.
    """

    def __init__(
        self,
        openai_client: OpenAI,
        vector_store: VectorStore,
        top_k: int = 4,
        similarity_threshold: float = 0.75,
    ):
        self._client = openai_client
        self._vector_store = vector_store
        self._top_k = top_k
        self._similarity_threshold = similarity_threshold

    def ask(self, question: str) -> str:
        chunks = self._vector_store.similarity_search(
            question, top_k=self._top_k, similarity_threshold=self._similarity_threshold
        )
        context = "\n---\n".join(chunks)

        prompt = f"""Answer the question using ONLY the context below.
If the answer isn't in the context, say you don't know.

Context:
{context}

Question: {question}
"""
        response = self._client.chat.completions.create(
            model="gpt-4o-mini",
            messages=[{"role": "user", "content": prompt}],
        )
        return response.choices[0].message.content
```

```python
# main.py
import os
import shutil
from dotenv import load_dotenv
from fastapi import FastAPI, Query, UploadFile, File
from openai import OpenAI

from vector_store import VectorStore
from ingestion import DocumentIngestionService
from qa_advisor import QuestionAnswerAdvisor

load_dotenv()

app = FastAPI()
openai_client = OpenAI(api_key=os.getenv("OPENAI_API_KEY"))

vector_store = VectorStore(openai_client)
ingestion_service = DocumentIngestionService(vector_store)
qa_advisor = QuestionAnswerAdvisor(openai_client, vector_store, top_k=4, similarity_threshold=0.75)


@app.post("/api/ingest")
async def ingest(file: UploadFile = File(...)):
    temp_path = f"/tmp/{file.filename}"
    with open(temp_path, "wb") as f:
        shutil.copyfileobj(file.file, f)

    chunk_count = ingestion_service.ingest(temp_path)
    return {"filename": file.filename, "chunks_ingested": chunk_count}


@app.get("/api/rag/ask")
def ask(question: str = Query(...)):
    answer = qa_advisor.ask(question)   # <-- auto-injects retrieved context
    return {"answer": answer}
```

### 3. The manual version

Useful when you want control over the augmentation prompt (e.g. citing sources) — this is the same logic `QuestionAnswerAdvisor` runs internally, just inlined so you can customize it.

```python
@app.get("/api/rag/ask-manual")
def ask_manual(question: str = Query(...)):
    chunks = vector_store.similarity_search(question, top_k=4)
    context = "\n---\n".join(chunks)

    prompt = f"""Answer the question using ONLY the context below.
If the answer isn't in the context, say you don't know.

Context:
{context}

Question: {question}
"""
    response = openai_client.chat.completions.create(
        model="gpt-4o-mini",
        messages=[{"role": "user", "content": prompt}],
    )
    return {"answer": response.choices[0].message.content}
```

Run it:

```bash
uvicorn main:app --reload --port 8080
```

---

## Try it

```bash
curl -X POST "http://localhost:8080/api/ingest" -F "file=@company-handbook.pdf"

curl "http://localhost:8080/api/rag/ask?question=What%20is%20our%20parental%20leave%20policy%3F"
```

---

## Mapping Java/Spring AI concepts to Python

| Spring AI / Java | Python | What it's really doing |
|---|---|---|
| `VectorStore` interface | `VectorStore` class wrapping ChromaDB | Storage + similarity search abstraction, swappable backend |
| `SimpleVectorStore` (in-memory) | `chromadb.Client()` (in-memory, embedded) | Dev/prototype-friendly store, not for production persistence |
| `TikaDocumentReader` | `pypdf.PdfReader` | Extracts raw text from source documents |
| `TokenTextSplitter` | Custom `_split_into_chunks()` using `tiktoken` | Splits raw text into token-bounded chunks for embedding |
| `EmbeddingModel` (auto-called by `VectorStore.add()`) | `client.embeddings.create(...)` inside `VectorStore._embed()` | Converts text into a numeric vector capturing meaning |
| `QuestionAnswerAdvisor` + `.defaultAdvisors(qaAdvisor)` | `QuestionAnswerAdvisor` class wrapping the OpenAI client | Automates embed-query → similarity-search → prompt-augmentation before every call |
| `SearchRequest.builder().topK(4).similarityThreshold(0.75)` | `top_k=4, similarity_threshold=0.75` constructor args | Controls how many chunks are retrieved and how relevant they must be |
| `vectorStore.similaritySearch(...)` (manual path) | `vector_store.similarity_search(...)` (manual path) | Same retrieval call, used directly when you want control over the prompt |

**Key insight:** RAG isn't a new kind of LLM call — it's Pattern 1 (`messages=[...]` → `.create()`) with an extra *data-fetching step* before the prompt is built. The "retrieval" half (embeddings + vector search) is a completely separate concern from the "generation" half (the chat completion call); Spring AI's `QuestionAnswerAdvisor` and this Python `QuestionAnswerAdvisor` class both exist purely to glue those two concerns together so callers don't have to.

---

## Production notes

- Chunk size matters a lot: too small loses context, too large dilutes relevance and burns tokens. 500–1000 tokens with slight overlap is a common starting point — tune against your own eval set. (The example splitter above doesn't add overlap; production splitters typically re-include the tail of the previous chunk at the start of the next one to avoid cutting a sentence's meaning in half at a chunk boundary.)
- `chromadb.Client()` is in-memory only and resets on restart — fine for prototypes, but use a persistent Chroma server, `pgvector`, Pinecone, Weaviate, or similar for anything durable or at scale. Because retrieval is isolated behind the `VectorStore` class, swapping the backend means changing only that one file — the same one-line-swap benefit Spring AI gets from everything implementing the `VectorStore` interface.
- `similarity_threshold` filters out weakly-related chunks instead of always forcing in the top K — important for "I don't know" honesty instead of hallucinated answers built from irrelevant context.
- This is "naive RAG." Advanced variants — re-ranking, hybrid search (combining keyword + vector search), query rewriting, multi-hop retrieval — build on this same foundation and are worth their own deep dive once the basics click.

---

## Project structure

```
pattern4-rag/
├── main.py
├── vector_store.py
├── ingestion.py
├── qa_advisor.py
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
| 4 | RAG | ✅ Retrieval-grounded answers from private data (this doc) |
| 5 | Tool Calling | Model requests function execution, app runs it |
| 6 | Agents | Looped call/decide/act cycle with self-correction |

*(To be filled in as each pattern's source doc is provided.)*