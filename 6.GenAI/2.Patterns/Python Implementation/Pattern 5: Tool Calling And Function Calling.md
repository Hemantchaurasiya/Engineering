# Pattern 5: Tool Calling / Function Calling — Python Version

This is the unlock that turns an LLM from "a thing that writes text" into "a thing that can take actions." You expose Python functions as tools; the model decides when to call them based on the conversation, **your code** executes the actual function, and the result flows back into the model so it can use it to compose the final answer.

This is a Python/FastAPI translation of the original Java/Spring AI pattern, preserving the same structure and behavior — with one important difference called out below: **the raw OpenAI SDK does not run the execution loop for you the way Spring AI does.** You write it yourself, which is actually the best way to understand what's really happening.

---

## What's happening

The model never executes code itself — it can only output structured intent: *"call `get_current_weather` with `city='Tokyo'`."* Someone still has to actually run that function. In Spring AI, `ChatClient` does this invisibly for you as part of `.call()`. In raw Python, **you** are that someone — you write a small loop that:

1. Sends the conversation + tool definitions to the model.
2. Checks whether the model asked to call a tool instead of answering directly.
3. If so, executes the real Python function with the arguments the model provided.
4. Appends the function's result back into the conversation as a new message.
5. Calls the model again — now it has the tool's result and can either call another tool or compose the final answer.

From the outside this can still look synchronous (one function call from your controller), but underneath it's a multi-turn exchange — exactly as the Java doc describes, just with the loop made explicit instead of hidden inside a framework.

### The core concept — why tool calling matters

An LLM is fundamentally a text predictor. It has no thermometer, no database connection, no ability to check today's weather or your account balance — it only knows what's in its training data (which is frozen and general) plus whatever you put in the prompt. Tool calling lets the model say, in effect, "I don't know this — go find out and tell me," and your code is the one that goes and finds out.

**Real-world analogy:** think of a trip-planning consultant who's excellent at putting together an itinerary but has no way to check real-time flight prices themselves — they don't have access to the airline's booking system. So they turn to their assistant and say "check flight prices from JFK to Tokyo for these dates" — a precise, structured request. The assistant (your code) actually queries the airline's system, gets a real number back, and hands it to the consultant, who then uses that real number to finish the itinerary. The consultant never touched the booking system directly; they just knew *when* to ask and *what* to ask for. That's exactly the model's role in tool calling — it decides when to delegate and with what arguments, but it never executes anything itself.

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

---

## Code

### 1. Define tools as plain Python functions + a JSON schema

Spring AI's `@Tool` annotation auto-generates a JSON schema from the method signature. In Python there's no annotation processor doing that for you at runtime, so you describe the schema explicitly — either by hand (shown here) or by generating it from a Pydantic model (shown in "Alternative" below). Either way, this schema is what the model actually sees; it never sees your Python source.

```python
# weather_tools.py

def get_current_weather(city: str) -> str:
    """In real life this calls a weather API. Stubbed for demo."""
    return {
        "tokyo": "18°C, light rain",
        "san francisco": "16°C, foggy",
    }.get(city.lower(), "22°C, clear skies")


def celsius_to_fahrenheit(celsius: float) -> float:
    return celsius * 9.0 / 5.0 + 32


# --- Tool registry: maps a tool name the model can call to the real function ---
TOOL_REGISTRY = {
    "get_current_weather": get_current_weather,
    "celsius_to_fahrenheit": celsius_to_fahrenheit,
}

# --- JSON schemas describing each tool, sent to the model on every call ---
# This is the direct equivalent of what @Tool + @ToolParam generate automatically
# in Spring AI — here it's written explicitly so you can see exactly what the
# model receives.
TOOL_SCHEMAS = [
    {
        "type": "function",
        "function": {
            "name": "get_current_weather",
            "description": "Get the current weather conditions for a given city",
            "parameters": {
                "type": "object",
                "properties": {
                    "city": {
                        "type": "string",
                        "description": "The city name, e.g. Tokyo",
                    }
                },
                "required": ["city"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "celsius_to_fahrenheit",
            "description": "Convert a temperature from Celsius to Fahrenheit",
            "parameters": {
                "type": "object",
                "properties": {
                    "celsius": {
                        "type": "number",
                        "description": "Temperature in Celsius",
                    }
                },
                "required": ["celsius"],
            },
        },
    },
]
```

### 2. The tool-calling loop

This is the part Spring AI hides inside `.call()`. Writing it out makes the "model requests → app executes → model composes final answer" cycle from the doc's opening paragraph completely explicit.

```python
# tool_loop.py
import json
from openai import OpenAI
from weather_tools import TOOL_REGISTRY, TOOL_SCHEMAS


def run_with_tools(client: OpenAI, user_question: str, max_turns: int = 5) -> str:
    messages = [{"role": "user", "content": user_question}]

    for _ in range(max_turns):
        response = client.chat.completions.create(
            model="gpt-4o-mini",
            messages=messages,
            tools=TOOL_SCHEMAS,
        )
        message = response.choices[0].message

        # No tool call requested -> the model is ready to answer. Stop here.
        if not message.tool_calls:
            return message.content

        # The model asked to call one or more tools. Append its request to
        # the conversation, then actually execute each one.
        messages.append(message)

        for tool_call in message.tool_calls:
            fn_name = tool_call.function.name
            fn_args = json.loads(tool_call.function.arguments)

            fn = TOOL_REGISTRY[fn_name]
            result = fn(**fn_args)   # <-- your real Python code runs here

            # Feed the result back into the conversation as a "tool" message,
            # tagged with the call it's answering.
            messages.append({
                "role": "tool",
                "tool_call_id": tool_call.id,
                "content": json.dumps(result),
            })

    return "Sorry, I couldn't complete that after several tool calls."
```

### 3. The FastAPI endpoint

```python
# main.py
import os
from dotenv import load_dotenv
from fastapi import FastAPI, Query
from openai import OpenAI

from tool_loop import run_with_tools

load_dotenv()

app = FastAPI()
client = OpenAI(api_key=os.getenv("OPENAI_API_KEY"))


@app.get("/api/tools/ask")
def ask(question: str = Query(...)):
    answer = run_with_tools(client, question)
    return {"answer": answer}
```

Run it:

```bash
uvicorn main:app --reload --port 8080
```

---

## Try it

```bash
curl "http://localhost:8080/api/tools/ask?question=What%27s%20the%20weather%20in%20Tokyo%20in%20Fahrenheit%3F"
```

Trace through `run_with_tools`, the model will:

- Turn 1: request `get_current_weather(city="Tokyo")` → loop executes it → gets `"18°C, light rain"`
- Turn 2: request `celsius_to_fahrenheit(celsius=18)` → loop executes it → gets `64.4`
- Turn 3: no more tool calls — composes: `"It's currently 64.4°F and lightly raining in Tokyo."`

— chaining two tool calls across three round trips, entirely on the model's own initiative. Your `for` loop just keeps feeding it results until it stops asking for tools.

---

## Mapping Java/Spring AI concepts to Python

| Spring AI / Java | Python | What it's really doing |
|---|---|---|
| `@Tool(description = "...")` method | Entry in `TOOL_SCHEMAS` (JSON schema dict) | Describes a callable capability to the model — name, purpose, parameters |
| `@ToolParam(description = "...")` | `"properties": {"city": {"description": "..."}}` in the schema | Documents an individual parameter so the model fills it in correctly |
| `.defaultTools(weatherTools)` on `ChatClient.Builder` | `TOOL_REGISTRY` dict + `tools=TOOL_SCHEMAS` passed to `.create()` | Registers which tools are available for the model to choose from |
| Spring AI's internal execute-and-feed-back loop (hidden inside `.call()`) | The explicit `for` loop in `run_with_tools()` | Runs the actual function, appends the result as a message, calls the model again |
| Return value auto-serialized and matched back to the call | `tool_call.id` matched to `"tool_call_id"` in the response message | Ties a specific tool's result back to the specific request that asked for it, since the model may call several tools in one turn |
| `FunctionToolCallback.builder(...).inputType(WeatherRequest.class)` | Pydantic model → `.model_json_schema()` (see Alternative below) | Generates a JSON schema from a typed request object instead of hand-writing it |

**Key insight:** Spring AI's `.call()` *looks* like a single request because the framework hides a loop inside it. In raw Python you can see that loop directly — every "tool call" is really just another ordinary `chat.completions.create()` request (the same call from Pattern 1!) with two additions: a `tools=[...]` schema list on the way in, and `role: "tool"` messages appended to the history on the way back. Nothing new is happening at the API level — it's the same request/response shape from Pattern 1, just looped, with the model's tool requests and your function's results woven into the `messages` list alongside the regular conversation.

---

## Alternative: generating the schema from a Pydantic model

Instead of hand-writing the JSON schema dict (shown above, matching the Java doc's "no annotations, dynamic tools" style), you can derive it from a Pydantic model — closer to the ergonomics of `FunctionToolCallback.builder(...).inputType(WeatherRequest.class)`.

```python
from pydantic import BaseModel, Field


class WeatherRequest(BaseModel):
    city: str = Field(description="The city name, e.g. Tokyo")


def build_tool_schema(name: str, description: str, model: type[BaseModel]) -> dict:
    return {
        "type": "function",
        "function": {
            "name": name,
            "description": description,
            "parameters": model.model_json_schema(),
        },
    }


weather_tool_schema = build_tool_schema(
    "get_current_weather", "Get current weather for a city", WeatherRequest
)

# Pass a different tool set per call instead of a fixed module-level list —
# the same idea as .tools(weatherTool) per-request instead of .defaultTools(...)
response = client.chat.completions.create(
    model="gpt-4o-mini",
    messages=[{"role": "user", "content": question}],
    tools=[weather_tool_schema],
)
```

This mirrors the Java doc's distinction between `defaultTools()` (fixed set, registered once on the builder) and `.tools(...)` per call (different tools per request) — in Python, that distinction is just "module-level `TOOL_SCHEMAS` passed every time" versus "build and pass a custom list inside a specific endpoint."

---

## Production notes

- Keep tool descriptions precise — the model picks tools based on the description text alone, so vague descriptions ("does stuff with weather") cause wrong or missed calls. This is true identically in both ecosystems, since the description text is what actually ships to the provider either way.
- Tools should be idempotent or side-effect-aware — the model may call a tool speculatively or (on retries) more than once; don't put unguarded payment charges behind a tool without confirmation logic.
- For dangerous actions (deletes, payments, sending emails), pair tool calling with a **human-in-the-loop** step — pause the loop after the model requests the tool call, surface it to a human for approval, and only then execute the real function and continue the loop. That's a straightforward extension of `run_with_tools()`: instead of always executing immediately, check an "approval needed" flag on certain tool names first.
- Set a `max_turns` guard (shown above) — without one, a model that keeps requesting tools (or a buggy tool that keeps returning something the model doesn't like) can loop indefinitely and burn API calls.
- The explicit loop shown here is exactly the shape libraries like the OpenAI Agents SDK or LangGraph automate for you once things get more complex — understanding it by hand first is what makes those frameworks legible instead of magical.

This pattern is the foundation for ReAct agents, orchestrator-workers, and multi-agent systems — they're all built from LLM calls with progressively richer toolsets and control loops around them.

---

## Project structure

```
pattern5-tool-calling/
├── main.py
├── tool_loop.py
├── weather_tools.py
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
| 5 | Tool Calling | ✅ Model-initiated function execution via an explicit loop (this doc) |
| 6 | Agents | Looped call/decide/act cycle with self-correction |

*(To be filled in as each pattern's source doc is provided.)*