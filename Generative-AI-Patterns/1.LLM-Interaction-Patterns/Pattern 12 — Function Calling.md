## Pattern 12 — Function Calling

**Depends on:** Pattern 11 (Structured Output / JSON Output)

### Problem
An LLM can only generate text — it can't check today's weather, query a database, or run a calculation on its own. But many real tasks require exactly that: the model needs to decide *that* an external function should be called and *with what arguments*, then hand control back to your code to actually run it. Function Calling is the mechanism that lets a model participate in that decision without you hardcoding "if user asks about weather, call weather API" logic yourself.

### Motivation
Before function calling existed, developers tried to get this behavior by asking the model to output a JSON blob describing "what it wants to do" via prompting alone — brittle, exactly the problem Pattern 11 already solved for output shape in general. Function Calling is that same schema-constrained-generation idea, applied specifically to the *decision of which function to call and with what arguments* — the model doesn't guess a JSON shape from prose instructions, it selects from tool definitions you provide and its arguments are validated against a schema (optionally strictly, via `strict: true`, exactly as covered in Pattern 11).

### Core Idea
You describe available functions to the model as `tools` (name, description, JSON Schema for parameters). The model, given a user request, decides whether calling a tool would help, and if so returns a `tool_use` content block with the function name and arguments — **it does not execute anything**. Your application code executes the actual function and sends the result back as a `tool_result`, and the model uses that to continue.

This is a single-call-and-respond pattern here — the fuller multi-step loop (call → execute → call again → ... until done) is Pattern 13: Tool Calling, next, which builds directly on this.

### Architecture

```text
┌────────────┐   tools=[...]    ┌──────────────┐
│    User      │ ───────────────▶│    Claude      │
│   request     │                 │  (decides IF    │
└────────────┘                 │  and WHICH tool  │
                                │  to call)         │
                                └──────┬───────┘
                                       ▼
                          tool_use block: {name, input}
                                       │
                                       ▼
                          ┌─────────────────────┐
                          │  Your application code  │  ← actually executes
                          │  runs the real function  │     the function
                          └─────────┬───────────┘
                                    ▼
                          tool_result → sent back to Claude
                                    ▼
                          Claude produces final answer
```

### Internal Flow
1. Define one or more tools: `name`, `description` (critical — this is how the model decides *when* to use it), and `input_schema` (JSON Schema for the arguments).
2. Send the user's message plus the `tools` list.
3. Claude responds with `stop_reason: "tool_use"` and a content block containing the tool name + arguments — if it decides a tool is needed. If not, it just answers normally.
4. Your code executes the actual function using the provided arguments.
5. You send a new message with a `tool_result` block containing the function's output, continuing the same conversation.
6. Claude incorporates the result into its final natural-language response.

### Simple Implementation

```python
import anthropic

client = anthropic.Anthropic()

def get_order_status(order_id: str) -> dict:
    # Stand-in for a real database/API call
    return {"order_id": order_id, "status": "shipped", "eta_days": 2}

tools = [{
    "name": "get_order_status",
    "description": "Look up the current shipping status of a customer order by its ID.",
    "input_schema": {
        "type": "object",
        "properties": {"order_id": {"type": "string", "description": "The order ID, e.g. ORD-4821"}},
        "required": ["order_id"],
    },
}]

messages = [{"role": "user", "content": "What's the status of order ORD-4821?"}]

response = client.messages.create(
    model="claude-sonnet-5", max_tokens=500, tools=tools, messages=messages,
)

if response.stop_reason == "tool_use":
    tool_call = next(b for b in response.content if b.type == "tool_use")
    result = get_order_status(**tool_call.input)  # execute the real function

    messages.append({"role": "assistant", "content": response.content})
    messages.append({"role": "user", "content": [
        {"type": "tool_result", "tool_use_id": tool_call.id, "content": str(result)}
    ]})

    final = client.messages.create(model="claude-sonnet-5", max_tokens=500, tools=tools, messages=messages)
    print(final.content[0].text)
```

### Production Implementation

```python
import json
import logging
from dataclasses import dataclass
from typing import Any, Callable

import anthropic

logger = logging.getLogger("genai.function_calling")


@dataclass
class ToolDefinition:
    name: str
    description: str
    input_schema: dict
    handler: Callable[..., Any]
    strict: bool = True  # enforce exact schema-matching arguments (Pattern 11's strict tool use)

    def to_api_schema(self) -> dict:
        schema = {"name": self.name, "description": self.description, "input_schema": self.input_schema}
        if self.strict:
            schema["strict"] = True
        return schema


class ToolExecutionError(Exception):
    pass


class FunctionCallingClient:
    """Wraps a single round of function calling: propose → execute → respond.
    Keeps tool execution and error handling centralized so every tool gets
    the same logging, timeout, and failure-reporting behavior — the
    foundation Tool-Use Patterns (validation, retry, timeout, fallback)
    build on in the Tool-Use Patterns section."""

    def __init__(self, tools: list[ToolDefinition], model: str = "claude-sonnet-5"):
        self.tools = {t.name: t for t in tools}
        self.client = anthropic.AsyncAnthropic()
        self.model = model

    def _tool_schemas(self) -> list[dict]:
        return [t.to_api_schema() for t in self.tools.values()]

    async def _execute_tool(self, name: str, arguments: dict) -> str:
        tool = self.tools.get(name)
        if tool is None:
            raise ToolExecutionError(f"Unknown tool requested: {name}")
        try:
            result = tool.handler(**arguments)
            logger.info("tool_executed name=%s args=%s", name, arguments)
            return json.dumps(result) if not isinstance(result, str) else result
        except Exception as e:
            logger.error("tool_execution_failed name=%s error=%s", name, e)
            raise ToolExecutionError(f"Tool '{name}' failed: {e}")

    async def run(self, user_message: str) -> str:
        messages = [{"role": "user", "content": user_message}]

        response = await self.client.messages.create(
            model=self.model, max_tokens=1000, tools=self._tool_schemas(), messages=messages,
        )

        if response.stop_reason != "tool_use":
            return response.content[0].text

        tool_call = next(b for b in response.content if b.type == "tool_use")

        try:
            tool_output = await self._execute_tool(tool_call.name, tool_call.input)
            result_block = {"type": "tool_result", "tool_use_id": tool_call.id, "content": tool_output}
        except ToolExecutionError as e:
            # Report the failure back to Claude rather than crashing — it can
            # then decide how to respond to the user given the failed tool.
            result_block = {"type": "tool_result", "tool_use_id": tool_call.id,
                             "content": str(e), "is_error": True}

        messages.append({"role": "assistant", "content": response.content})
        messages.append({"role": "user", "content": [result_block]})

        final = await self.client.messages.create(
            model=self.model, max_tokens=1000, tools=self._tool_schemas(), messages=messages,
        )
        return final.content[0].text


def get_order_status(order_id: str) -> dict:
    return {"order_id": order_id, "status": "shipped", "eta_days": 2}


order_tool = ToolDefinition(
    name="get_order_status",
    description="Look up the current shipping status of a customer order by its ID.",
    input_schema={
        "type": "object",
        "properties": {"order_id": {"type": "string"}},
        "required": ["order_id"],
    },
    handler=get_order_status,
)

fc_client = FunctionCallingClient(tools=[order_tool])
```

### Real-World Use Case
A customer support chat widget gives the model tools for `get_order_status`, `check_return_eligibility`, and `initiate_return`. The model decides, per user message, whether a tool call is even needed ("what's your return policy?" needs none) versus which specific tool applies ("where's my order?" → `get_order_status`), and with what arguments — extracted directly from the natural-language message. The business logic (auth checks, actual DB queries, whether a return is truly eligible) lives entirely in your `handler` functions, never in the model itself.

### Advantages
- Lets the model act as a natural-language front-end to real systems, without you writing intent-classification/routing logic by hand.
- `strict: true` (built on Pattern 11's constrained decoding) guarantees arguments match your schema exactly — no more defensive argument-parsing code.
- Cleanly separates "decide what to call" (the model) from "actually call it" (your code) — the model never has direct system access, which is a genuine security property, not just an implementation detail.

### Disadvantages / Failure Modes
- The model can select the *wrong* tool, or the *right* tool with subtly wrong arguments — schema conformance guarantees shape, not correctness of the decision (Tool Validation, later, addresses this further).
- Tool descriptions matter enormously — a vague description leads to the model either never using a tool that would help, or using it inappropriately; treat tool descriptions with the same care as prompt instructions.
- Every tool you expose is an increase in attack surface — a tool that can, say, issue refunds needs authorization checks in the handler itself, never trust "the model decided to call it" as sufficient authorization (Tool Authorization, Guardrails section).
- Multi-tool prompts increase token cost (every tool schema is sent with every request) and can slow tool selection if there are too many overlapping/similar tools available.

### When NOT to Use It
- The task never needs external data or actions — plain generation (Pattern 1) or RAG (later section) is simpler and cheaper.
- You already know deterministically which function needs to run — just call it directly in your code; don't route a known, fixed operation through the model just to "use function calling."
- Extremely latency-sensitive paths — function calling requires at least two model round-trips (decide → respond after result), roughly doubling latency versus a direct call.

### Trade-off vs Direct Structured Output (Pattern 11)
| Aspect | Structured Output (Pattern 11) | Function Calling (this pattern) |
|---|---|---|
| Purpose | Shape the model's final answer | Let the model request an *action*, then use its result |
| Execution | None — you just parse the response | Your code executes something real in between |
| Round trips | One | At least two (propose → result → final answer) |
| Best for | Data extraction, formatted reports | Actions with side effects or live data lookups |

### Exercise
Add a second tool, `check_return_eligibility(order_id: str) -> dict`, to `FunctionCallingClient`'s tool list. Then send a message like *"Can I return order ORD-4821?"* and trace through the logs to confirm the model picks `check_return_eligibility` rather than `get_order_status` — this is exactly the tool-selection judgment that Pattern 13 (Tool Calling) extends into a full multi-step loop across several tool calls in sequence.

---
