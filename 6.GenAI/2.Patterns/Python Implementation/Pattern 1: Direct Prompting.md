# Pattern 1: Direct Prompting (Basic LLM Call) — Python Version

This is the foundation everything else builds on: your app sends a prompt to the model through the OpenAI Python SDK, and gets a response back. No memory, no tools, no chaining — just a single round trip.

This is a Python/FastAPI translation of the original Java/Spring AI pattern, preserving the same structure and behavior.

---

## What's happening

A user sends a prompt (as an HTTP query parameter). Your FastAPI route handler passes it straight to the OpenAI client — the Python equivalent of Spring AI's `ChatClient`. The client builds the request, sends it to the provider (OpenAI, Anthropic, Ollama, etc. — any OpenAI-compatible endpoint), and hands back the plain text response.

No state, no tools, no retries — it's the "hello world" of GenAI engineering, but everything else you'll build (memory, chaining, RAG, tool calling, agents) is layered on top of this single call.

### The core concept

Underneath any framework, an "LLM call" is just an HTTP request/response cycle:

```
Your app  →  HTTP POST (JSON: model, messages, temperature...)  →  LLM provider
Your app  ←  HTTP response (JSON: generated text + metadata)    ←  LLM provider
```

**Real-world analogy:** ordering food through a delivery app. You don't cook the food yourself — you send a structured order (items, quantity, instructions) to a restaurant's system, and a meal comes back. The LLM is the restaurant; your prompt is the order; the *system message* is a standing instruction you give every time ("no onions, always"); the *user message* is today's specific order.

---

## Setup

### `requirements.txt`

```
fastapi==0.115.0
uvicorn==0.32.0
openai==1.54.0
python-dotenv==1.0.1
```

Install with:

```bash
pip install -r requirements.txt
```

### `.env`

```
OPENAI_API_KEY=sk-your-key-here
```

This plays the same role as Spring's `application.yml` — externalizing config (API key, model name, temperature) so it isn't hardcoded and can differ between dev/prod.

---

## Code

### `main.py`

```python
import os
from dotenv import load_dotenv
from fastapi import FastAPI, Query
from openai import OpenAI

load_dotenv()  # loads OPENAI_API_KEY from .env into the environment

app = FastAPI()

# Constructed once at module load and reused — the SDK client is
# thread-safe and connection-pooled, equivalent to Spring AI's
# auto-configured ChatClient.Builder bean.
client = OpenAI(api_key=os.getenv("OPENAI_API_KEY"))

SYSTEM_PROMPT = "You are a concise, helpful assistant."


@app.get("/api/direct/ask")
def ask(question: str = Query(..., description="The user's question")):
    response = client.chat.completions.create(
        model="gpt-4o-mini",
        temperature=0.7,
        messages=[
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": question},
        ],
    )
    return {"answer": response.choices[0].message.content}
```

Run it:

```bash
uvicorn main:app --reload --port 8080
```

That's it — the three real lines of logic:

- `messages=[...]` builds the conversation (system instruction + user question).
- `client.chat.completions.create(...)` executes the call synchronously.
- `response.choices[0].message.content` extracts the plain text answer.

---

## Try it

```bash
curl "http://localhost:8080/api/direct/ask?question=What%20is%20a%20vector%20embedding%3F"
```

Expected response shape:

```json
{
  "answer": "A vector embedding is a numerical representation of data..."
}
```

---

## Mapping Java/Spring AI concepts to Python

| Spring AI / Java | Python / FastAPI | What it's really doing |
|---|---|---|
| `ChatClient.Builder` injected via DI | `OpenAI()` client instantiated once at import time | Both create a reusable, pooled HTTP client configured with your API key and base URL |
| `.defaultSystem("...")` | `{"role": "system", "content": SYSTEM_PROMPT}` in the messages list | Sets standing instructions for every call — Spring bakes it into the builder; the raw API requires it in every request's message array |
| `@RestController` / `@GetMapping` | `@app.get(...)` | Both map an HTTP route to a handler function |
| `.prompt().user(question).call().content()` | `client.chat.completions.create(messages=[...])` → `.choices[0].message.content` | The fluent builder vs. the raw dict-based API — same underlying request, different ergonomics |
| `@RequestParam String question` | `question: str = Query(...)` | Both bind a query string parameter to a typed function argument |

**Key insight:** Spring AI's `ChatClient` is a convenience wrapper around exactly the same JSON structure visible in Python's `messages=[...]` list. Spring hides the `role: "system"` / `role: "user"` dictionary shape behind `.defaultSystem()` / `.user()` method calls. The raw Python SDK shows you the wire format directly — useful pedagogically, because now you *see* what a "conversation" is to an LLM: an ordered list of `{role, content}` turns.

---

## Why "no memory, no tools, no chaining" matters

This pattern is deliberately the most minimal possible LLM integration, and understanding *why* it has no memory is the important part.

**The API call is stateless.** Every call to `chat.completions.create()` is a fresh, independent HTTP request. The model has zero awareness that you called it five seconds ago. If you ask "What's 2+2?" and then ask "What did I just ask you?", the second call won't know — because the `messages` list you send contains only the *current* question, nothing before it.

**Real-world example:** calling a customer support hotline where every call connects you to a brand-new agent who has never spoken to you before and has no file on you. If you want them to remember your previous question, *you* have to repeat it yourself. That's exactly what "adding memory" to an LLM app means — the application code keeps a running list of `messages` and resends the *entire history* on every call, because the model itself remembers nothing between requests.

This is why every later pattern is a variation on this same shape:

- **Memory / conversation** — append to the `messages` list across calls instead of sending one `user` message each time.
- **Chaining** — `response.choices[0].message.content` from call 1 becomes part of the `messages` list sent in call 2.
- **RAG (retrieval-augmented generation)** — before calling the API, fetch relevant documents (e.g., from a vector database) and inject their text into the `user` or `system` message as context.
- **Tool calling** — pass a `tools=[...]` parameter describing functions the model can request (e.g., `get_weather`); your app executes them and feeds results back in another round trip.
- **Agents** — a loop wrapped around this call → parse → decide cycle, where the app decides whether to call again, invoke a tool, or stop, based on the model's output.

Every one of those is still, underneath, the same `client.chat.completions.create(messages=[...])` call — just with a richer `messages` list or extra parameters. Once you understand this single request/response shape, every advanced pattern becomes "what do we put into the request, and what do we do with the response" — not fundamentally new machinery.

---

## Project structure

```
pattern1-direct-prompting/
├── main.py
├── requirements.txt
├── .env
└── README.md
```

---

## Next patterns (planned)

| # | Pattern | Adds |
|---|---|---|
| 1 | Direct Prompting | ✅ Single stateless call (this doc) |
| 2 | Conversation Memory | Running `messages` history across calls |
| 3 | Chaining | Output of one call feeds the next call's input |
| 4 | RAG | Retrieved context injected into the prompt |
| 5 | Tool Calling | Model requests function execution, app runs it |
| 6 | Agents | Looped call/decide/act cycle with self-correction |

*(To be filled in as each pattern's source doc is provided.)*