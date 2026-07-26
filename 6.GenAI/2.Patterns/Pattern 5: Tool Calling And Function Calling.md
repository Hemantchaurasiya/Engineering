# Pattern 5: Tool Calling / Function Calling

This is the unlock that turns an LLM from "a thing that writes text" into "a thing that can take actions." You expose Java methods as tools; the model decides when to call them based on the conversation, Spring AI executes the actual method, and the result flows back into the model so it can use it to answer.

## What's happening

The model never executes code itself — it can only output structured intent ("call `getWeather` with `city='Tokyo'`"). Spring AI handles the rest of the loop: it executes your actual Java method with those arguments, feeds the result back to the model as a new message, and the model uses that result to compose its final answer. From your perspective as a developer, it looks synchronous — but under the hood it's a multi-turn exchange.

---

## Code

### 1. Define tools using the `@Tool` annotation

(Spring AI 1.0's preferred style — auto-generates the tool's JSON schema from method signature + annotations.)

```java
package com.example.genai.pattern5;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class WeatherTools {

    @Tool(description = "Get the current weather conditions for a given city")
    public String getCurrentWeather(
            @ToolParam(description = "The city name, e.g. Tokyo") String city) {

        // In real life this calls a weather API. Stubbed for demo:
        return switch (city.toLowerCase()) {
            case "tokyo" -> "18°C, light rain";
            case "san francisco" -> "16°C, foggy";
            default -> "22°C, clear skies";
        };
    }

    @Tool(description = "Convert a temperature from Celsius to Fahrenheit")
    public double celsiusToFahrenheit(
            @ToolParam(description = "Temperature in Celsius") double celsius) {
        return celsius * 9.0 / 5.0 + 32;
    }
}
```

### 2. Register the tools on `ChatClient` and let the model use them

```java
package com.example.genai.pattern5;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tools")
public class ToolCallingController {

    private final ChatClient chatClient;

    public ToolCallingController(ChatClient.Builder builder, WeatherTools weatherTools) {
        this.chatClient = builder
                .defaultTools(weatherTools)   // registers all @Tool methods on this bean
                .build();
    }

    @GetMapping("/ask")
    public String ask(@RequestParam String question) {
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
curl "http://localhost:8080/api/tools/ask?question=What%27s%20the%20weather%20in%20Tokyo%20in%20Fahrenheit%3F"
```

The model will:

- Call `getCurrentWeather("Tokyo")`
- Get `"18°C, light rain"`
- Call `celsiusToFahrenheit(18)`
- Get `64.4`
- Compose: `"It's currently 64.4°F and lightly raining in Tokyo."`

— chaining two tool calls in one exchange, entirely on its own.

---

## Alternative: function-based tools

(No annotations, useful for dynamic/per-request tools.)

```java
import org.springframework.ai.tool.function.FunctionToolCallback;
import java.util.function.Function;

record WeatherRequest(String city) {}

var weatherTool = FunctionToolCallback.builder(
                "getCurrentWeather",
                (Function<WeatherRequest, String>) req -> getWeatherFor(req.city()))
        .description("Get current weather for a city")
        .inputType(WeatherRequest.class)
        .build();

chatClient.prompt()
        .user(question)
        .tools(weatherTool)   // per-call instead of defaultTools
        .call()
        .content();
```

---

## Production notes

- Keep tool descriptions precise — the model picks tools based on the description text alone, so vague descriptions ("does stuff with weather") cause wrong or missed calls.
- Tools should be idempotent or side-effect-aware — the model may call a tool speculatively or retry; don't put unguarded payment charges behind a tool without confirmation logic.
- For dangerous actions (deletes, payments, sending emails), pair tool calling with the Human-in-the-Loop pattern (#19) — require explicit confirmation before the tool actually executes.
- `defaultTools()` applies to every call from that `ChatClient`; use `.tools(...)` per-request when different endpoints need different tool sets.

This pattern is the foundation for ReAct agents, orchestrator-workers, and multi-agent systems — they're all built from LLM calls with progressively richer toolsets and control loops around them.