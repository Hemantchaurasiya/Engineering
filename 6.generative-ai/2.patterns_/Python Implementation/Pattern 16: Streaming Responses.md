# Pattern 16: Streaming Responses — Python Version

Waiting for a full multi-paragraph response before showing anything feels slow and broken to users — they're used to ChatGPT-style token-by-token rendering. The OpenAI Python SDK supports this natively via `stream=True`, returning an iterator of text chunks as the model generates them, which FastAPI's `StreamingResponse` can pipe straight out as Server-Sent Events.

This is a Python/FastAPI translation of the original Java/Spring AI pattern, preserving the same structure and behavior.

---

## What's happening

`stream=True` is a drop-in flag on the same `chat.completions.create(...)` call you've used since Pattern 1 — not a different method, just a different parameter that changes what comes back: instead of one complete `ChatCompletion` object, you get an iterator of small `ChatCompletionChunk` objects, one per token (or small group of tokens) as the provider generates them. Python's `yield` inside a **generator function** plays the role Spring WebFlux's `Flux<String>` plays: both let you produce values one at a time and hand each one to the client the moment it's ready, rather than building the whole response in memory first.

### The core concept — why streaming needs a different shape of function, not just a different response

Every call so far in this series has been the same shape: call the model, wait, get one complete answer back, return it. Streaming breaks that shape on purpose — you don't want to wait for the whole answer, you want to forward each piece **as it arrives**. Python's `yield` keyword is exactly the tool for that: a function with `yield` in it doesn't run to completion and return one value — it pauses at each `yield`, hands a value out to whoever's consuming it, and only resumes when they ask for the next one. That's precisely the behavior you want here: as soon as the OpenAI API sends one token, your generator yields it immediately to FastAPI, which forwards it immediately to the browser — nobody in that chain waits for the full response to exist.

**Real-world analogy:** think of the difference between a chef who plates the entire seven-course tasting menu on one giant tray and brings it out all at once at the end of the meal, versus a chef who sends each course out to the table the moment it's ready. The second approach is what a restaurant actually does — and it's why you feel like something's happening throughout the meal instead of sitting in silence for forty minutes and then being overwhelmed with everything simultaneously. Streaming is the API equivalent: the "kitchen" (the LLM provider) sends each small piece of the response out as it's cooked, instead of holding everything back until the very last token is ready.

---

## Setup

### `requirements.txt`

```
fastapi==0.115.0
uvicorn==0.32.0
openai==1.54.0
python-dotenv==1.0.1
```

No new dependencies — `stream=True` is a parameter on the same `openai` client you've already installed, and `StreamingResponse` is built into FastAPI (no WebFlux-equivalent framework swap needed, unlike the Java version's `spring-boot-starter-webflux` addition).

---

## Code

### 1. Basic text streaming endpoint

```python
# main.py
import os
from dotenv import load_dotenv
from fastapi import FastAPI, Query
from fastapi.responses import StreamingResponse
from openai import OpenAI

load_dotenv()

app = FastAPI()
client = OpenAI(api_key=os.getenv("OPENAI_API_KEY"))


def stream_chat_response(message: str):
    stream = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=[{"role": "user", "content": message}],
        stream=True,   # <-- the only change from every earlier pattern's call
    )
    for chunk in stream:
        delta = chunk.choices[0].delta.content
        if delta:
            yield delta   # hand this piece to the client immediately


@app.get("/api/stream/chat")
def chat(message: str = Query(...)):
    return StreamingResponse(
        stream_chat_response(message),
        media_type="text/event-stream",
    )
```

`stream_chat_response` is a **generator function** (it contains `yield`) — calling it doesn't run any code yet, it just returns an iterator. `StreamingResponse` pulls one chunk at a time from that iterator and writes it straight to the HTTP response as it arrives, exactly like `Flux<String>` being forwarded by Spring WebFlux.

---

## Try it

```bash
curl -N "http://localhost:8080/api/stream/chat?message=Explain%20the%20CAP%20theorem%20in%20detail"
```

The `-N` flag disables curl's output buffering so you see chunks arrive incrementally rather than all at once — identical curl usage to the Java version, since the wire protocol (chunked HTTP / SSE) is the same regardless of which server framework produced it.

---

### 2. Streaming with access to metadata (finish reason, etc.)

Useful when you need metadata alongside the text, not just raw strings — each `chunk` object carries more than just the text delta.

```python
def stream_chat_detailed(message: str):
    stream = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=[{"role": "user", "content": message}],
        stream=True,
    )
    for chunk in stream:
        choice = chunk.choices[0]
        if choice.delta.content:
            yield choice.delta.content
        if choice.finish_reason is not None:
            # Last chunk carries the finish reason (e.g. "stop", "length")
            # instead of text — you could log it, or yield a sentinel here.
            pass


@app.get("/api/stream/chat-detailed")
def chat_detailed(message: str = Query(...)):
    return StreamingResponse(
        stream_chat_detailed(message),
        media_type="text/event-stream",
    )
```

This mirrors the Java version's distinction between `.stream().content()` (plain text `Flux<String>`) and `.stream().chatResponse()` (full `Flux<ChatResponse>` with metadata) — in Python, both live in the same `chunk` object; you simply choose whether to read only `.delta.content` or also inspect `.finish_reason`, `.usage`, etc.

---

### 3. Streaming combined with memory (Pattern 15) and tools (Pattern 5) — they compose normally

```python
from chat_memory import ChatMemory  # from Pattern 15

chat_memory = ChatMemory()


def stream_chat_with_memory(message: str, conversation_id: str):
    history = chat_memory.get_messages(conversation_id)
    messages = history + [{"role": "user", "content": message}]

    stream = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=messages,
        stream=True,
    )

    full_reply = []
    for chunk in stream:
        delta = chunk.choices[0].delta.content
        if delta:
            full_reply.append(delta)
            yield delta

    # Persist both turns only after the full response has streamed through
    chat_memory.add_message(conversation_id, "user", message)
    chat_memory.add_message(conversation_id, "assistant", "".join(full_reply))


@app.get("/api/stream/chat-with-memory")
def chat_with_memory(message: str = Query(...), conversation_id: str = Query(...)):
    return StreamingResponse(
        stream_chat_with_memory(message, conversation_id),
        media_type="text/event-stream",
    )
```

Note the small but important detail: since `chat_memory.add_message()` needs the *complete* assistant reply to persist, the generator accumulates each `delta` into `full_reply` as it streams them out, then saves the joined string only after the loop finishes — streaming to the client and persisting to memory happen on different timelines within the same function.

> **Tool calling + streaming:** combining Pattern 5's tool loop with streaming requires restructuring the loop from Pattern 5 so that each *non-tool-call* turn streams its tokens out via `yield`, while turns where the model requests a tool call are executed silently in between (no useful tokens to stream during a tool execution — see Production notes below for how to surface that gap to the UI).

---

### 4. Frontend consumption (vanilla JS, fetch with a reader)

**Identical to the Java version** — the frontend has no idea whether the server is Spring WebFlux or FastAPI, because both speak the same chunked-HTTP wire protocol:

```javascript
const response = await fetch('/api/stream/chat?message=' + encodeURIComponent(userMessage));
const reader = response.body.getReader();
const decoder = new TextDecoder();

while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    const chunk = decoder.decode(value);
    appendToChatUI(chunk);   // render incrementally as it arrives
}
```

---

## Mapping Java/Spring AI concepts to Python

| Spring AI / Java | Python | What it's really doing |
|---|---|---|
| `.stream()` instead of `.call()` | `stream=True` instead of the default `stream=False` (or omitted) | Same request, but the response comes back incrementally instead of all at once |
| `Flux<String>` | Generator function (`def ...(): yield ...`), consumed by `StreamingResponse` | Both represent "a sequence of values produced over time," pulled by the consumer one at a time |
| `spring-boot-starter-webflux` (reactive stack) | `fastapi.responses.StreamingResponse` (built into FastAPI, no extra framework) | Both are the plumbing that forwards each emitted chunk to the HTTP client as it's produced |
| `.stream().content()` — plain text | `chunk.choices[0].delta.content` | Just the text delta from each streamed piece |
| `.stream().chatResponse()` — full response objects | The full `chunk` object (`.delta`, `.finish_reason`, etc.) | Access to metadata alongside text, when you need more than raw strings |
| `MediaType.TEXT_EVENT_STREAM_VALUE` | `media_type="text/event-stream"` | Declares the response as Server-Sent Events so the browser handles it correctly |
| `.onErrorResume()` on the `Flux` | `try`/`except` inside the generator function, yielding a fallback string | Both let the stream degrade gracefully instead of dropping the connection silently on a mid-stream failure |
| `curl -N` | `curl -N` (unchanged) | Same flag, same reason — disables buffering so you see the streamed chunks arrive live |

**Key insight:** streaming isn't a new *capability* layered on top of everything else in this series — it's the *same* `chat.completions.create()` call from Pattern 1, with one parameter flipped (`stream=True`), consumed through Python's native mechanism for producing values incrementally (`yield`) instead of all at once (`return`). Composing it with memory (Pattern 15) or tools (Pattern 5) works exactly like composing any two patterns in this series always has — you're just doing it inside a generator function instead of a regular one, and being careful about *when* you persist things that need the complete text (like memory) versus what you can forward immediately (the raw deltas).

---

## Production notes

- Streaming + tool calling has a wrinkle: when the model decides to call a tool mid-generation, the stream effectively pauses while the tool executes (no useful tokens to show during that gap) before resuming — your UI should handle that with a "thinking" / "using a tool" indicator rather than treating a pause as a stall. In Python, this means restructuring Pattern 5's `run_with_tools()` loop to `yield` a sentinel value (e.g. `{"type": "tool_start", "name": fn_name}`) right before executing a tool, so the frontend can render an indicator during that gap.
- Backpressure is handled differently than in reactive WebFlux: a plain Python generator is naturally "pull-based" (the consumer calls `next()` when it wants more), so there's no unbounded buffering risk by default — but if you wrap the stream in `asyncio` for a fully async FastAPI app, make sure you're using an async generator (`async def ... yield`) with `AsyncOpenAI` so a slow client doesn't block your event loop the way a slow synchronous generator would.
- Error handling: wrap the loop body in `try`/`except` and `yield` a graceful fallback chunk (e.g. `"\n\n[Response interrupted — please retry]"`) rather than letting an unhandled exception kill the generator and silently truncate the stream mid-sentence — the direct equivalent of `.onErrorResume()`.
- Streaming structured output (Pattern 2) isn't directly supported the same way — you can't meaningfully parse partial JSON token-by-token, since `Recipe.model_validate_json(partial_text)` will simply fail on every incomplete chunk until the very last one. For structured output, stick to the non-streaming `.parse()` call; reserve `stream=True` for free-text generation, exactly as the Java version recommends staying with `.call()` for `.entity()`.

---

## Project structure

```
pattern16-streaming/
├── main.py
├── chat_memory.py    # reused from Pattern 15, if combining with memory
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
| 16 | Streaming Responses | ✅ Token-by-token delivery via generator functions (this doc) |

*(To be filled in as each pattern's source doc is provided.)*