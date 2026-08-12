## Pattern 13 — Tool Calling (Multi-Step Loop)

**Depends on:** Pattern 12 (Function Calling)

### Problem
Pattern 12 handled exactly one tool call: propose → execute → final answer. Real tasks often need *several* tool calls chained together, where the result of one informs whether/which tool to call next — "look up the order, and if it hasn't shipped, check inventory, and if inventory is low, notify the warehouse team." A single-round function call can't express that; you need a loop that keeps going until the model decides it has enough information to stop.

### Motivation
This is the mechanical foundation underneath every "AI agent" you've heard about — an agent, at its core, is largely this loop plus some added judgment (planning, reflection, memory). Understanding the raw loop *before* reaching for an agent framework is exactly the "explain the underlying concept before the framework" principle from this course's teaching philosophy — LangGraph and similar frameworks automate this loop, but you should be able to write it yourself first.

### Core Idea
Repeat: send the conversation (including all prior tool results) → check if the model wants to call a tool → if yes, execute it, append the result, loop again → if no (`stop_reason != "tool_use"`), the model is done, return its final answer. Always bound the loop with a maximum iteration count — an unbounded loop is a real production failure mode (an "agent infinite loop," covered explicitly later in Real-World Engineering Problems).

### Architecture

```text
                    ┌─────────────────────────┐
                    │   messages = [user_msg]    │
                    └───────────┬───────────┘
                                ▼
        ┌──────────────▶ Claude.messages.create(tools, messages) ◀───────────┐
        │                       │                                              │
        │            stop_reason == "tool_use"?                                │
        │              ┌────────┴────────┐                                   │
        │             YES                NO                                   │
        │              ▼                  ▼                                   │
        │     Execute tool(s)      Return final answer                        │
        │              │                                                        │
        │     Append tool_result                                               │
        │     to messages, loop  ──────────────────────────────────────────────┘
        │     (up to max_iterations)
        └── loop back
```

### Internal Flow
1. Initialize `messages` with the user's request.
2. Call the model with the full `tools` list and current `messages`.
3. If `stop_reason == "tool_use"`: extract the tool call(s) (a single response can request more than one tool call in parallel — handle all of them), execute each, append all results as `tool_result` blocks in a single follow-up message, and loop.
4. If `stop_reason != "tool_use"`: the model produced its final text answer — stop and return it.
5. Enforce a `max_iterations` ceiling; if hit, stop gracefully and either return a partial answer or escalate (Human Escalation, later) rather than looping forever.
6. Log every iteration (which tool, what arguments, what result) — this trace is essential for debugging agent behavior later (Agent Tracing, Observability section).

### Simple Implementation

```python
import anthropic

client = anthropic.Anthropic()

def get_order_status(order_id: str) -> dict:
    return {"order_id": order_id, "status": "processing", "warehouse": "WH-East"}

def check_warehouse_inventory(warehouse: str, sku: str) -> dict:
    return {"warehouse": warehouse, "sku": sku, "in_stock": False}

TOOLS = {"get_order_status": get_order_status, "check_warehouse_inventory": check_warehouse_inventory}

tool_schemas = [
    {"name": "get_order_status", "description": "Get order status and warehouse.",
     "input_schema": {"type": "object", "properties": {"order_id": {"type": "string"}}, "required": ["order_id"]}},
    {"name": "check_warehouse_inventory", "description": "Check if a SKU is in stock at a warehouse.",
     "input_schema": {"type": "object", "properties": {
         "warehouse": {"type": "string"}, "sku": {"type": "string"}}, "required": ["warehouse", "sku"]}},
]

messages = [{"role": "user", "content": "Order ORD-99 hasn't shipped — is the SKU 'WIDGET-1' in stock where it's held?"}]

for _ in range(5):  # max_iterations
    response = client.messages.create(model="claude-sonnet-5", max_tokens=800, tools=tool_schemas, messages=messages)
    if response.stop_reason != "tool_use":
        print(response.content[0].text)
        break

    messages.append({"role": "assistant", "content": response.content})
    tool_results = []
    for block in response.content:
        if block.type == "tool_use":
            output = TOOLS[block.name](**block.input)
            tool_results.append({"type": "tool_result", "tool_use_id": block.id, "content": str(output)})
    messages.append({"role": "user", "content": tool_results})
```

### Production Implementation

```python
import json
import logging
from dataclasses import dataclass
from typing import Any, Callable

import anthropic

logger = logging.getLogger("genai.tool_loop")


@dataclass
class ToolDefinition:
    name: str
    description: str
    input_schema: dict
    handler: Callable[..., Any]

    def to_api_schema(self) -> dict:
        return {"name": self.name, "description": self.description, "input_schema": self.input_schema, "strict": True}


class MaxIterationsExceeded(Exception):
    pass


class ToolLoopRunner:
    """The general-purpose multi-step tool-calling loop. This exact class
    is what grows into a 'Tool-Using Agent' in the Agent Patterns section —
    the only difference an agent typically adds on top is planning/reflection
    logic around this same core loop, not a different mechanism."""

    def __init__(self, tools: list[ToolDefinition], model: str = "claude-sonnet-5", max_iterations: int = 6):
        self.tools = {t.name: t for t in tools}
        self.client = anthropic.AsyncAnthropic()
        self.model = model
        self.max_iterations = max_iterations

    def _execute(self, name: str, arguments: dict) -> str:
        tool = self.tools[name]
        try:
            result = tool.handler(**arguments)
            return json.dumps(result) if not isinstance(result, str) else result
        except Exception as e:
            logger.error("tool_failed name=%s error=%s", name, e)
            return json.dumps({"error": str(e)})

    async def run(self, user_message: str) -> str:
        messages = [{"role": "user", "content": user_message}]
        tool_schemas = [t.to_api_schema() for t in self.tools.values()]

        for iteration in range(1, self.max_iterations + 1):
            response = await self.client.messages.create(
                model=self.model, max_tokens=1500, tools=tool_schemas, messages=messages,
            )

            if response.stop_reason != "tool_use":
                logger.info("tool_loop_completed iterations=%d", iteration)
                return response.content[0].text

            tool_calls = [b for b in response.content if b.type == "tool_use"]
            logger.info("tool_loop_step iteration=%d tools=%s",
                        iteration, [c.name for c in tool_calls])

            messages.append({"role": "assistant", "content": response.content})

            tool_results = []
            for call in tool_calls:
                output = self._execute(call.name, call.input)
                tool_results.append({"type": "tool_result", "tool_use_id": call.id, "content": output})
            messages.append({"role": "user", "content": tool_results})

        logger.warning("tool_loop_max_iterations_hit max=%d", self.max_iterations)
        raise MaxIterationsExceeded(
            f"Did not reach a final answer within {self.max_iterations} tool-calling steps"
        )
```

### Real-World Use Case
An internal ops assistant handles "why hasn't order ORD-99 shipped, and can we expedite it?" by chaining: `get_order_status` → sees it's stuck at "processing" → `check_warehouse_inventory` → sees the SKU is out of stock at that warehouse → `find_alternate_warehouse` → finds stock elsewhere → `create_transfer_request`. No human wrote an if/elif chain covering this exact sequence; the model composed it from the available tools based on each intermediate result, which is exactly the value multi-step tool calling adds over a single-round function call.

### Advantages
- Handles genuinely multi-step tasks without hardcoded control flow — the model composes tool calls based on live intermediate results.
- The same loop mechanism scales from "call 2 tools" to "call 10 tools across a complex investigation," with no structural change needed.
- A clean iteration trace (tool, args, result per step) is inherently self-documenting — valuable for debugging and audit.

### Disadvantages / Failure Modes
- **Agent infinite loops**: without a hard `max_iterations` ceiling, a model that keeps deciding "one more tool call would help" can loop indefinitely, burning cost with no progress — always bound this.
- **Incorrect tool selection** compounds across steps — a wrong choice at step 2 can send the whole chain down an irrelevant path; this is why Tool Validation and Reflection (both later patterns) matter more as chains get longer.
- Cost and latency scale with the number of iterations — a 6-step tool chain is roughly 6x the round-trip cost of Pattern 12's single call; not every task justifies that.
- Parallel tool calls in one response need careful handling — if two tools have a dependency (tool B needs tool A's result), the model may still request them together; your execution code needs to either handle true independence or the model needs to be prompted to sequence dependent calls correctly.

### When NOT to Use It
- The task is genuinely single-step (Pattern 12 covers it) — don't build a loop for something that never needs more than one tool call.
- The exact sequence of steps is always the same, known in advance, and never varies by input — that's just a deterministic pipeline; write it as regular code, don't pay LLM round-trip costs to "decide" a decision that isn't actually a decision.
- Extremely tight latency budgets where multiple sequential model round-trips aren't acceptable.

### Comparison: Single-Round Function Calling vs Multi-Step Tool Calling
| Pattern | Problem Solved | Complexity | Cost | Latency | Best Use Case |
|---|---|---|---|---|---|
| Function Calling (Pattern 12) | One decision, one action, done | Low | ~2x a plain call | ~2x a plain call | Simple lookups, single actions |
| Tool Calling loop (this pattern) | Multi-step tasks needing chained decisions | Medium-High | Scales with iteration count | Scales with iteration count | Investigations, multi-system workflows |

### Exercise
Add a `find_alternate_warehouse(sku: str, exclude: str) -> dict` tool to `ToolLoopRunner`'s tool list, and extend the scenario above so the model, upon finding the primary warehouse out of stock, calls this new tool to locate an alternative. Add a counter that logs the total number of tool calls made across the whole run, and think about what a reasonable `max_iterations` value would be for this kind of task in production — too low cuts off legitimate multi-step reasoning, too high risks runaway cost. This exact "how many steps is too many" judgment call becomes central once we reach **Pattern: Long-Running Agent** and **Pattern: Stateful Agent** in the Agent Patterns section.

---
