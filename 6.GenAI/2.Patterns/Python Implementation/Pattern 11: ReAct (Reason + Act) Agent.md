# Pattern 11: ReAct (Reason + Act) Agent — Python Version

Tool calling (Pattern 5) handled single-step "model calls a tool, gets a result, answers." ReAct generalizes that into a genuine loop: the model reasons about what it knows, decides on an action (often a tool call), observes the result, and reasons again — repeating as many times as needed until it has enough information to give a final answer. It's the difference between a function call and an autonomous problem-solving loop.

This is a Python/FastAPI translation of the original Java/Spring AI pattern, preserving the same structure and behavior.

---

## Important nuance: in Python, you're *already* running this loop yourself

The Java doc's key point is that Spring AI runs the reason→act→observe loop internally, automatically, whenever tools are registered — it's invisible unless you go looking for it. In Python, there's no equivalent invisibility: back in **Pattern 5**, you wrote `run_with_tools()` — an explicit `for` loop that calls the model, checks for `tool_calls`, executes them, feeds results back, and repeats. **That loop already was a ReAct loop.** Nothing mechanically new needs to be built here.

What actually distinguishes "ReAct agent" from "simple tool calling" isn't the code — it's the *task shape*: whether the model needs to chain several tool calls, using the result of one to decide the next action, rather than always making the same one or two calls in a row. Pattern 5's weather example only ever needed two calls in a fixed order (get weather, then convert units) — barely a loop at all. This pattern's travel-planning example genuinely doesn't know in advance how many tool calls it'll need, or which cities it'll investigate, until it sees the first observations — that's what makes it a *ReAct agent* rather than "a two-step tool call with extra steps."

### The core concept — reasoning about what to do next, based on what you just learned

**Real-world analogy:** think of a detective investigating a case, versus someone following a fixed checklist. A checklist follower always does step 1, then step 2, then step 3, regardless of what they find along the way. A detective doesn't know in advance how many leads they'll need to chase — they check one alibi, and *based on what that reveals*, decide whether to check a second alibi, pull phone records, or conclude the case is solved. Each finding (an "observation") changes what they decide to investigate next (the next "action"). That's exactly the shape of the ReAct loop: the model isn't following a script you wrote — it's deciding, one step at a time, what it still needs to find out, based on what it's already learned.

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

No new dependencies — this reuses exactly Pattern 5's tool-loop mechanics with a richer toolset.

---

## Code

### 1. Tools that require multi-step reasoning to chain correctly

Same structure as Pattern 5's `weather_tools.py`: a registry mapping tool name → real function, plus JSON schemas describing each tool to the model.

```python
# travel_tools.py
import datetime

def get_weather(city: str) -> str:
    return {
        "lisbon": "24°C, sunny",
        "reykjavik": "8°C, windy, light rain",
        "bangkok": "33°C, humid, thunderstorms",
    }.get(city.lower(), "20°C, mild")


def get_flight_price(origin: str, destination: str) -> float:
    # stubbed lookup
    return 420.0 + (len(origin) + len(destination)) * 3.5


def get_local_time(city: str) -> str:
    return datetime.datetime.now().astimezone().isoformat()


TOOL_REGISTRY = {
    "get_weather": get_weather,
    "get_flight_price": get_flight_price,
    "get_local_time": get_local_time,
}

TOOL_SCHEMAS = [
    {
        "type": "function",
        "function": {
            "name": "get_weather",
            "description": "Get the current weather for a city",
            "parameters": {
                "type": "object",
                "properties": {"city": {"type": "string", "description": "City name"}},
                "required": ["city"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_flight_price",
            "description": "Get the average flight price in USD between two cities",
            "parameters": {
                "type": "object",
                "properties": {
                    "origin": {"type": "string", "description": "Origin city"},
                    "destination": {"type": "string", "description": "Destination city"},
                },
                "required": ["origin", "destination"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_local_time",
            "description": "Get the current local time for a city as an ISO-8601 string",
            "parameters": {
                "type": "object",
                "properties": {"city": {"type": "string", "description": "City name"}},
                "required": ["city"],
            },
        },
    },
]
```

### 2. The agent loop — Pattern 5's loop, unchanged in shape, with a `max_iterations` safety valve

```python
# travel_agent.py
import json
from openai import OpenAI
from travel_tools import TOOL_REGISTRY, TOOL_SCHEMAS

SYSTEM_PROMPT = (
    "You are a travel planning assistant. Use the available tools "
    "to gather real information before answering. Don't guess "
    "values you can look up."
)


def plan_trip(client: OpenAI, question: str, max_iterations: int = 8) -> str:
    messages = [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": question},
    ]

    for _ in range(max_iterations):
        response = client.chat.completions.create(
            model="gpt-4o-mini",
            messages=messages,
            tools=TOOL_SCHEMAS,
        )
        message = response.choices[0].message

        # No tool call requested -> the model has enough information. Stop.
        if not message.tool_calls:
            return message.content

        messages.append(message)

        for tool_call in message.tool_calls:
            fn_name = tool_call.function.name
            fn_args = json.loads(tool_call.function.arguments)

            fn = TOOL_REGISTRY[fn_name]
            result = fn(**fn_args)   # <-- Action + Observation happen here

            messages.append({
                "role": "tool",
                "tool_call_id": tool_call.id,
                "content": json.dumps(result),
            })

    return (
        "I wasn't able to reach a confident recommendation within the "
        "allotted number of steps — try narrowing the question."
    )
```

This is **word-for-word the same loop as Pattern 5's `run_with_tools()`**, with one addition: `max_iterations` is now an explicit, tunable parameter (Pattern 5 hardcoded `max_turns=5`) — the direct equivalent of Spring AI's `ToolCallingChatOptions.builder().maxIterations(8)`.

### 3. FastAPI endpoint

```python
# main.py
import os
from dotenv import load_dotenv
from fastapi import FastAPI, Query
from openai import OpenAI

from travel_agent import plan_trip

load_dotenv()

app = FastAPI()
client = OpenAI(api_key=os.getenv("OPENAI_API_KEY"))


@app.get("/api/agent/plan")
def plan(question: str = Query(...)):
    return {"answer": plan_trip(client, question)}
```

Run it:

```bash
uvicorn main:app --reload --port 8080
```

---

## Try it

```bash
curl "http://localhost:8080/api/agent/plan?question=I%20want%20warm%2C%20dry%20weather%20under%20%24500%20flight%20from%20Lisbon.%20Should%20I%20go%20to%20Bangkok%20or%20Reykjavik%3F"
```

Trace of what actually happens inside `plan_trip`'s loop — this is the ReAct cycle, playing out as literal iterations of the `for` loop:

```text
Iteration 1
Thought (implicit in the model's tool choice): "I need weather for both candidate cities."
Action: get_weather("Bangkok")   → appended as a tool message
Action: get_weather("Reykjavik") → appended as a tool message
(model requested both tool_calls in the same response — a real batch of parallel actions)

Iteration 2
Thought: "Bangkok is warm but stormy; Reykjavik is cold and wet — neither is clean.
Let me check flight cost before deciding."
Action: get_flight_price("Lisbon", "Bangkok")
Observation: $469 (fn returns a float, serialized into the tool message)

Iteration 3
Thought: "Under budget. Bangkok is warmer despite storms — better match for 'warm.'"
message.tool_calls is empty -> loop exits, message.content holds the final answer

Final answer:
Recommends Bangkok, citing weather and price, while noting the storm caveat.
```

You didn't write any of that branching logic — `messages` is just a growing list, and the model decides at each iteration whether it has enough to answer or needs another tool call. Note that a single iteration of the `for` loop can contain *multiple* tool calls (as in Iteration 1 above) when the model requests several tools at once — the inner `for tool_call in message.tool_calls` loop handles that without any special-casing.

---

## Mapping Java/Spring AI concepts to Python

| Spring AI / Java | Python | What it's really doing |
|---|---|---|
| Internal loop hidden inside `.call()` when tools are registered | The explicit `for` loop in `plan_trip()` (same as Pattern 5's `run_with_tools()`) | Both repeat reason → act → observe until the model stops requesting tools |
| `ToolCallingChatOptions.builder().maxIterations(8)` | `max_iterations: int = 8` parameter | Both cap how many reasoning/action rounds the loop can run before giving up |
| `internalToolExecutionEnabled(true)` | N/A — Python always executes tools explicitly in the loop | Spring AI has a toggle for automatic vs. manual execution; raw Python has no "automatic" mode to toggle, since you always write the loop |
| `@Tool` methods on `TravelPlanningTools` | `TOOL_REGISTRY` dict + `TOOL_SCHEMAS` list in `travel_tools.py` | The available actions the model can choose from at each step |
| The literal ReAct paper's `Thought:`/`Action:`/`Observation:` text | The structured `tool_calls` on `message`, and the `role: "tool"` messages appended in response | Modern function-calling APIs replaced the *text* protocol with a *structured* one — the underlying loop shape (shown in the "Important nuance" section above) is identical either way |

**Key insight:** this pattern doesn't introduce a single new mechanical idea beyond Pattern 5 — it's the exact same loop, run against a toolset where the *right sequence* of calls genuinely depends on what earlier calls returned. The Java doc's core point ("Spring AI already runs this loop for you") flips in Python to: "you already wrote this loop, back in Pattern 5, and didn't even realize you'd built a ReAct agent." The distinguishing feature was never the mechanism — it's whether the task requires the model to *decide* its next move based on an *observation*, rather than always executing the same fixed handful of calls.

---

## Controlling the loop

```python
def plan_trip(client: OpenAI, question: str, max_iterations: int = 8) -> str:
    ...
```

`max_iterations` is your safety valve — without it, a model stuck in an ambiguous multi-tool task could loop far longer (and cost far more) than intended. This is the direct equivalent of `.maxIterations(8)` in the Java version — both simply bound the `for` loop.

---

## Production notes

- Observability matters here more than anywhere so far — log every `(iteration, tool_call, result)` triple inside the loop (a simple `logging.info(...)` call right after `result = fn(**fn_args)` is enough). When an agent gives a wrong answer, you need the full reason→act→observe trace to debug why, not just the final output — this is even more important in Python since there's no framework logging it for you automatically.
- Tool descriptions are your only steering mechanism for the loop's behavior — vague `description` fields in `TOOL_SCHEMAS` cause the model to either under-use or misuse them mid-loop, identically to how vague `@Tool(description=...)` text misleads the Java version.
- This pattern is genuinely more failure-prone than the fixed workflows (Patterns 6-10) — the model is improvising the control flow. Reserve it for tasks where the right sequence of steps truly can't be predicted in advance; use a fixed workflow pattern whenever you can predict it, since it's more reliable and debuggable — true regardless of which language runs the loop.

---

## Project structure

```
pattern11-react-agent/
├── main.py
├── travel_agent.py
├── travel_tools.py
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
| 11 | ReAct Agent | ✅ Multi-hop reason → act → observe loop with model-driven branching (this doc) |

*(To be filled in as each pattern's source doc is provided.)*