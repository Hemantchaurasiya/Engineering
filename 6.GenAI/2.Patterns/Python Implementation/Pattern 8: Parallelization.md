# Pattern 8: Parallelization — Python Version

Sequential chaining is wasteful when subtasks don't depend on each other. Parallelization fires off multiple LLM calls concurrently and aggregates the results — either **sectioning** (split one task into independent pieces, run them all at once) or **voting** (run the same task multiple times and aggregate for consensus, improving reliability on judgment calls).

This is a Python/FastAPI translation of the original Java/Spring AI pattern, preserving the same structure and behavior.

---

## What's happening

Each independent LLM call runs in its own thread, and you wait for all to complete before aggregating. In Python, `concurrent.futures.ThreadPoolExecutor` plays the role Java 21's virtual threads play: cheap, blocking-style code, no async/await or reactive complexity required just to parallelize a handful of network calls. Total latency becomes roughly the slowest single call, not the sum of all of them — identical to the Java version's outcome.

### The core concept — why threads work here despite Python's GIL

A common Python misconception is "threads don't help because of the GIL (Global Interpreter Lock)." That's true for *CPU-bound* work (crunching numbers), but **not** for *I/O-bound* work — and an LLM API call is almost entirely I/O-bound: your program spends nearly all its time waiting on a network response, not computing anything. During that wait, Python releases the GIL, so other threads run freely. This is exactly analogous to why Java's virtual threads work well here too: a virtual thread blocked on network I/O doesn't tie up a real OS thread either. Both languages are exploiting the same underlying fact — waiting for a network response doesn't require the CPU, so you can have many calls "in flight" cheaply regardless of how the underlying concurrency primitive is implemented.

**Real-world analogy:** think of a manager who needs feedback from four different specialists — a copyeditor, a fact-checker, a tone reviewer, and a legal reviewer — on the same document. The manager doesn't hand the document to the copyeditor, wait for it to come back, *then* hand it to the fact-checker, and so on, one at a time. They hand a copy to all four **at the same time** and wait for whichever one takes longest. The total time spent is roughly "however long the slowest reviewer takes," not "the sum of all four reviewers' time." That's sectioning. Voting is the same idea applied differently: instead of asking four different questions, you ask three different people the *exact same* judgment call ("is this appropriate?") and go with whatever the majority says — because any single person's judgment on a borderline case might be wrong, but three independent opinions converging is much more trustworthy than one.

---

## Setup

### `requirements.txt`

```
fastapi==0.115.0
uvicorn==0.32.0
openai==1.54.0
pydantic==2.9.2
python-dotenv==1.0.1
```

No new dependencies — `concurrent.futures` is part of the Python standard library, just like `java.util.concurrent` is part of the JDK.

---

## Code

### 1. Sectioning — split a document review into independent parallel checks

```python
# parallel_review.py
from dataclasses import dataclass
from concurrent.futures import ThreadPoolExecutor, as_completed
from openai import OpenAI


@dataclass
class ReviewResult:
    dimension: str
    feedback: str


@dataclass
class Check:
    dimension: str
    instruction: str


class ParallelReviewService:
    def __init__(self, client: OpenAI, max_workers: int = 8):
        self.client = client
        # Thread pool: cheap, blocking-style code, no async/await needed —
        # the Python equivalent of Executors.newVirtualThreadPerTaskExecutor().
        self.executor = ThreadPoolExecutor(max_workers=max_workers)

    def _run_check(self, check: Check, document: str) -> ReviewResult:
        response = self.client.chat.completions.create(
            model="gpt-4o-mini",
            messages=[{
                "role": "user",
                "content": f"{check.instruction}\n\nText:\n{document}",
            }],
        )
        return ReviewResult(
            dimension=check.dimension,
            feedback=response.choices[0].message.content,
        )

    def review_document(self, document: str) -> list[ReviewResult]:
        checks = [
            Check("grammar", "Review this text for grammar and spelling issues only."),
            Check("clarity", "Review this text for clarity and readability only."),
            Check("factual_accuracy", "Review this text for any factual claims that seem questionable."),
            Check("tone", "Review this text's tone — is it appropriate for a professional audience?"),
        ]

        # Fan out: submit all calls concurrently
        futures = [
            self.executor.submit(self._run_check, check, document)
            for check in checks
        ]

        # Fan in: wait for all to complete, then collect
        return [future.result() for future in futures]
```

`self.executor.submit(...)` returning a `Future` is the direct equivalent of `CompletableFuture.supplyAsync(...)`; calling `.result()` on each is the equivalent of `.join()`. The list comprehension over `futures` after they're all submitted is the "fan out, then fan in" shape from the Java version, expressed with plain list comprehensions instead of streams.

### 2. Voting — run the same judgment call 3 times, aggregate for reliability

```python
# voting_moderation.py
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from enum import Enum
from pydantic import BaseModel
from openai import OpenAI


class ModerationVerdict(str, Enum):
    SAFE = "SAFE"
    UNSAFE = "UNSAFE"


class ModerationResult(BaseModel):
    verdict: ModerationVerdict


class VotingModerationService:
    def __init__(self, client: OpenAI, max_workers: int = 8):
        self.client = client
        self.executor = ThreadPoolExecutor(max_workers=max_workers)
        self.system_prompt = "You are a content moderator. Classify content strictly."

    def _cast_vote(self, content: str) -> ModerationVerdict:
        completion = self.client.chat.completions.parse(
            model="gpt-4o-mini",
            messages=[
                {"role": "system", "content": self.system_prompt},
                {"role": "user", "content": f"Classify this content as SAFE or UNSAFE: {content}"},
            ],
            response_format=ModerationResult,
        )
        return completion.choices[0].message.parsed.verdict

    def moderate(self, content: str) -> ModerationVerdict:
        vote_count = 3

        futures = [
            self.executor.submit(self._cast_vote, content)
            for _ in range(vote_count)
        ]
        votes = [future.result() for future in futures]

        tally = Counter(votes)

        # Majority wins; ties or any UNSAFE vote could be treated conservatively
        return (
            ModerationVerdict.UNSAFE
            if tally[ModerationVerdict.UNSAFE] >= 2
            else ModerationVerdict.SAFE
        )
```

`collections.Counter` plays the role of `Collectors.groupingBy(v -> v, Collectors.counting())` — both tally occurrences of each distinct value from the completed futures.

### 3. FastAPI endpoint

```python
# main.py
import os
from dotenv import load_dotenv
from fastapi import FastAPI, Body
from openai import OpenAI

from parallel_review import ParallelReviewService

load_dotenv()

app = FastAPI()
client = OpenAI(api_key=os.getenv("OPENAI_API_KEY"))
review_service = ParallelReviewService(client)


@app.post("/api/parallel/review")
def review(document: str = Body(..., embed=True)):
    results = review_service.review_document(document)
    return [{"dimension": r.dimension, "feedback": r.feedback} for r in results]
```

Run it:

```bash
uvicorn main:app --reload --port 8080
```

---

## Try it

```bash
curl -X POST "http://localhost:8080/api/parallel/review" \
  -H "Content-Type: application/json" \
  -d '{"document": "Our new product launches next month and will def improve sales by like a lot."}'
```

All four checks (grammar, clarity, factual_accuracy, tone) fire at the same time. Total wait time is roughly however long the slowest one of the four takes — not four calls' worth of time added together.

---

## Mapping Java/Spring AI concepts to Python

| Spring AI / Java | Python | What it's really doing |
|---|---|---|
| `Executors.newVirtualThreadPerTaskExecutor()` | `ThreadPoolExecutor(max_workers=N)` | A pool of cheap workers for blocking, I/O-bound calls — no reactive/async complexity required |
| `CompletableFuture.supplyAsync(fn, executor)` | `executor.submit(fn, *args)` | Fans out one async unit of work onto the executor, returns a handle to the eventual result |
| `future.join()` | `future.result()` | Blocks until that specific unit of work completes, then returns its value |
| `futures.stream().map(CompletableFuture::join).toList()` | `[future.result() for future in futures]` | Fan-in: wait for every submitted call to finish, collect them all |
| `Collectors.groupingBy(v -> v, Collectors.counting())` | `collections.Counter(votes)` | Tallies how many times each distinct value appeared among the votes |
| `record ReviewResult(String dimension, String feedback)` | `@dataclass class ReviewResult` | A lightweight typed container for one branch's result |
| `.call().entity(ModerationVerdict.class)` | `.parse(response_format=ModerationResult)` → `.parsed.verdict` | Each vote is still a structured-output call (Pattern 2), just run N times in parallel |

**Key insight:** parallelization doesn't change *what* each call is — every branch is still an ordinary Pattern 1 or Pattern 2 call. The only new idea is *when* those calls are dispatched: instead of `call() → wait → call() → wait`, you dispatch all of them first (`submit()` in a loop, or `supplyAsync` in a stream) and *then* wait for all of them together (`.result()` / `.join()` in a second loop). Both languages solve this with the same two-phase "fan out, then fan in" shape — Python's `ThreadPoolExecutor` and Java's virtual-thread executor are different implementations of the identical idea: make blocking network waits cheap enough to run many of them concurrently without rewriting your code in an async style.

---

## Sectioning vs. voting — when to use which

|  | Sectioning | Voting |
|---|---|---|
| **Goal** | Speed — do independent things at once | Reliability — reduce variance on one judgment |
| **Calls** | Different prompts/instructions | Same prompt, run N times |
| **Aggregation** | Concatenate/combine distinct results | Majority vote or averaging |
| **Good for** | Multi-dimensional reviews, multi-source summarization | Moderation, safety checks, ambiguous classifications |

**Real-world example of voting's value:** imagine asking a single content moderator to make a snap judgment on a borderline joke — sarcastic, culturally specific, right at the edge of acceptable. One moderator might call it fine; a different moderator on a different day might flag it. Neither is "wrong" exactly — it's a genuinely ambiguous case. Asking three independent moderators and going with the majority smooths out that individual variance, the same way asking three different doctors for a second opinion on an ambiguous diagnosis is more trustworthy than asking just one. If the case isn't ambiguous (an essay's spelling errors, say), one reviewer is already reliable — voting would just triple the cost for no benefit.

---

## Production notes

- Watch your rate limits — fanning out 10+ concurrent calls to the same provider can trip per-minute token/request limits. Cap concurrency with `max_workers=N` on the `ThreadPoolExecutor` (shown above), which is the direct equivalent of bounding the Java executor or using a semaphore.
- `ThreadPoolExecutor` makes this code look synchronous and simple, but each call still makes a real network request — failures need handling. `future.result()` re-raises any exception that occurred inside the worker function, exactly like `.join()` does for a failed `CompletableFuture` — wrap it in `try`/`except` per-future if partial results should still return instead of the whole batch failing.
- Voting is most valuable when the task has genuine ambiguity (e.g. borderline moderation calls) — for tasks the model gets right consistently, voting just triples your cost for no accuracy gain, in Python exactly as in Java.
- If you're already using `AsyncOpenAI` and `async`/`await` elsewhere in your FastAPI app, `asyncio.gather(*tasks)` is a valid alternative to `ThreadPoolExecutor` for this same fan-out/fan-in shape — but for a codebase that's otherwise synchronous, `ThreadPoolExecutor` keeps the code "blocking-style and simple," matching the spirit of why the Java version reaches for virtual threads instead of a reactive stack.

---

## Project structure

```
pattern8-parallelization/
├── main.py
├── parallel_review.py
├── voting_moderation.py
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
| 8 | Parallelization | ✅ Concurrent sectioning and voting via thread pools (this doc) |
| 9 | Agents | Looped call/decide/act cycle with self-correction |

*(To be filled in as each pattern's source doc is provided.)*