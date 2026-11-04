# Pattern 7: Routing

Routing solves a different problem than chaining: instead of a fixed sequence of steps, you have different kinds of requests that each deserve specialized handling. A router call classifies the incoming request first, then dispatches it to whichever specialized prompt, model, or tool-set fits best — rather than forcing one generic prompt to handle every case mediocrely.

## What's happening

The router call is a cheap, fast classification step — usually returning a simple enum via `.entity()` from Pattern 2. Based on that classification, you dispatch to a separate `ChatClient` configuration (different system prompt, different tools, sometimes even a different underlying model — e.g. a fast/cheap model for simple queries, a stronger model for complex ones). This is more reliable than one generic prompt trying to be good at everything simultaneously.

---

## Code

### 1. Define the routing categories

```java
package com.example.genai.pattern7;

public enum QueryCategory {
    BILLING,
    TECHNICAL,
    GENERAL
}
```

### 2. The router service — classify, then dispatch

```java
package com.example.genai.pattern7;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class SupportRouterService {

    private final ChatClient routerClient;
    private final ChatClient billingClient;
    private final ChatClient technicalClient;
    private final ChatClient generalClient;

    public SupportRouterService(ChatClient.Builder builder) {
        // Router: minimal system prompt, only job is classification
        this.routerClient = builder.clone()
                .defaultSystem("Classify the support query into exactly one category.")
                .build();

        // Each branch gets its own specialized system prompt
        this.billingClient = builder.clone()
                .defaultSystem("""
                        You are a billing support specialist. Be precise about
                        charges, refunds, and subscription terms. Never guess
                        at account-specific numbers — ask for an account ID if needed.
                        """)
                .build();

        this.technicalClient = builder.clone()
                .defaultSystem("""
                        You are a technical support engineer. Give step-by-step
                        troubleshooting instructions. Ask for error messages or
                        logs if the issue description is vague.
                        """)
                .build();

        this.generalClient = builder.clone()
                .defaultSystem("You are a friendly general support assistant.")
                .build();
    }

    public String handle(String userQuery) {

        // Step 1: route
        QueryCategory category = routerClient.prompt()
                .user(userQuery)
                .call()
                .entity(QueryCategory.class);

        // Step 2: dispatch to the specialized handler
        ChatClient targetClient = switch (category) {
            case BILLING -> billingClient;
            case TECHNICAL -> technicalClient;
            case GENERAL -> generalClient;
        };

        return targetClient.prompt()
                .user(userQuery)
                .call()
                .content();
    }
}
```

### 3. Controller

```java
@RestController
@RequestMapping("/api/route")
public class SupportRouterController {

    private final SupportRouterService routerService;

    public SupportRouterController(SupportRouterService routerService) {
        this.routerService = routerService;
    }

    @GetMapping("/support")
    public String support(@RequestParam String query) {
        return routerService.handle(query);
    }
}
```

---

## Try it

```bash
curl "http://localhost:8080/api/route/support?query=I%20was%20charged%20twice%20this%20month"
```

```text
→ routed to billingClient
```

```bash
curl "http://localhost:8080/api/route/support?query=My%20app%20crashes%20on%20startup"
```

```text
→ routed to technicalClient
```

---

## `ChatClient.Builder.clone()` — why it matters here

Each branch needs its own independent system prompt and configuration without re-declaring shared setup (model, default advisors, etc.). `.clone()` lets you fork the builder per branch while keeping common configuration centralized — far cleaner than building four unrelated `ChatClient`s from scratch.

---

## Production notes

- The router call itself can use a cheaper/faster model than the specialized handlers — classification is a simpler task than generating the final answer, so you don't need your most expensive model for it.
- For low-stakes routing, you can skip an LLM call entirely and use rule-based routing (regex, keyword matching) when categories are simple and well-defined — reserve LLM-based routing for genuinely ambiguous classification.
- Always include a fallback branch (`GENERAL` above) — the classifier will occasionally be wrong or the query won't cleanly fit any bucket.