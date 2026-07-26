# Pattern 11: ReAct (Reason + Act) Agent

Tool calling (Pattern 5) handled single-step "model calls a tool, gets a result, answers." ReAct generalizes that into a genuine loop: the model reasons about what it knows, decides on an action (often a tool call), observes the result, and reasons again — repeating as many times as needed until it has enough information to give a final answer. It's the difference between a function call and an autonomous problem-solving loop.

## Important nuance: Spring AI already runs this loop for you

The original ReAct paper used a text format (literal "Thought: ... Action: ... Observation: ..." in the completion). Modern function-calling APIs (OpenAI, Anthropic, etc.) replaced that text protocol with structured tool calls — but the underlying loop is identical: **reason → act → observe → repeat**. Spring AI's `ChatClient` runs this loop internally and automatically whenever you register tools (as you saw in Pattern 5) — it keeps calling tools and feeding results back to the model until the model decides it has enough information to answer, up to a configurable cap.

What makes something a ReAct agent rather than simple tool calling is really about the task shape: multi-hop questions where the model must chain several tool calls, using the result of one to decide the next action — not predetermined by you.

---

## Code

Tools that require multi-step reasoning to chain correctly:

```java
package com.example.genai.pattern11;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class TravelPlanningTools {

    @Tool(description = "Get the current weather for a city")
    public String getWeather(@ToolParam(description = "City name") String city) {
        return switch (city.toLowerCase()) {
            case "lisbon" -> "24°C, sunny";
            case "reykjavik" -> "8°C, windy, light rain";
            case "bangkok" -> "33°C, humid, thunderstorms";
            default -> "20°C, mild";
        };
    }

    @Tool(description = "Get the average flight price in USD between two cities")
    public double getFlightPrice(
            @ToolParam(description = "Origin city") String origin,
            @ToolParam(description = "Destination city") String destination) {
        // stubbed lookup
        return 420.0 + (origin.length() + destination.length()) * 3.5;
    }

    @Tool(description = "Get the current local time for a city as an ISO-8601 string")
    public String getLocalTime(@ToolParam(description = "City name") String city) {
        return java.time.OffsetDateTime.now().toString();
    }
}
```

Agent controller — the loop happens automatically inside `.call()`:

```java
package com.example.genai.pattern11;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
public class TravelAgentController {

    private final ChatClient chatClient;

    public TravelAgentController(ChatClient.Builder builder, TravelPlanningTools tools) {
        this.chatClient = builder
                .defaultSystem("""
                        You are a travel planning assistant. Use the available tools
                        to gather real information before answering. Don't guess
                        values you can look up.
                        """)
                .defaultTools(tools)
                .defaultOptions(ToolCallingChatOptions.builder()
                        .internalToolExecutionEnabled(true)
                        .build())
                .build();
    }

    @GetMapping("/plan")
    public String plan(@RequestParam String question) {
        return chatClient.prompt()
                .user(question)
                .call()
                .content();
    }
}
```

---

## Try it

```bash
curl "http://localhost:8080/api/agent/plan?question=I%20want%20warm%2C%20dry%20weather%20under%20%24500%20flight%20from%20Lisbon.%20Should%20I%20go%20to%20Bangkok%20or%20Reykjavik%3F"
```

Trace of what actually happens internally (this is the ReAct loop, just structured instead of text-based):

```text
Thought: "I need weather and flight price for both candidate cities."

Action: getWeather("Bangkok")
Observation: 33°C, humid, thunderstorms

Action: getWeather("Reykjavik")
Observation: 8°C, windy, light rain

Thought: "Bangkok is warm but stormy; Reykjavik is cold and wet — neither is clean.
Let me check flight cost before deciding."

Action: getFlightPrice("Lisbon", "Bangkok")
Observation: $469

Thought: "Under budget. Bangkok is warmer despite storms — better match for 'warm.'"

Final answer:
Recommends Bangkok, citing weather and price, while noting the storm caveat.
```

You didn't write any of that branching logic — the model planned it dynamically based on what the first observations told it.

---

## Controlling the loop

```java
.defaultOptions(ToolCallingChatOptions.builder()
        .internalToolExecutionEnabled(true)
        .maxIterations(8)   // cap to prevent runaway loops on confusing tasks
        .build())
```

`maxIterations` is your safety valve — without it, a model stuck in an ambiguous multi-tool task could loop far longer (and cost far more) than intended.

---

## Production notes

- Observability matters here more than anywhere so far — wrap tool methods with logging, or use Spring AI's `Advisor` interface to log every prompt/response in the loop. When an agent gives a wrong answer, you need the full Thought→Action→Observation trace to debug why, not just the final output.
- Tool descriptions are your only steering mechanism for the loop's behavior — vague tools cause the model to either under-use or misuse them mid-loop.
- This pattern is genuinely more failure-prone than the fixed workflows (Patterns 6-10) — the model is improvising the control flow. Reserve it for tasks where the right sequence of steps truly can't be predicted in advance; use a fixed workflow pattern whenever you can predict it, since it's more reliable and debuggable.