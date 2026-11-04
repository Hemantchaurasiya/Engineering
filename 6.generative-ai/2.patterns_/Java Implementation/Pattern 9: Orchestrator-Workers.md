# Pattern 9: Orchestrator-Workers

This looks like sectioning (Pattern 8), but with one critical difference: in sectioning, you hardcode the subtasks in Java. Here, an orchestrator LLM call decides the subtasks at runtime based on the specific input — because for many tasks, you can't know the right breakdown in advance. The orchestrator plans, dispatches worker calls (often in parallel), then a synthesizer combines their outputs into the final result.

## What's happening

The orchestrator makes one LLM call that returns a structured plan (a list of subtasks, typed via `.entity()` from Pattern 2) — the number and nature of subtasks isn't fixed in your code, the model decides based on the specific input. You then dispatch a worker call per planned subtask (often in parallel, using the virtual-thread approach from Pattern 8), and a final synthesis call combines all worker outputs into a coherent result.

---

## Code

Use case: write a comprehensive report on any topic — the orchestrator decides what sections are needed, since that varies wildly by topic.

```java
package com.example.genai.pattern9;

import java.util.List;

public record SubtaskPlan(List<Subtask> subtasks) {
    public record Subtask(String section, String instruction) {}
}

public record WorkerOutput(String section, String content) {}
```

```java
package com.example.genai.pattern9;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.*;

@Service
public class ReportOrchestratorService {

    private final ChatClient orchestratorClient;
    private final ChatClient workerClient;
    private final ChatClient synthesizerClient;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ReportOrchestratorService(ChatClient.Builder builder) {
        this.orchestratorClient = builder.clone()
                .defaultSystem("""
                        You are a report planner. Break the requested report topic
                        into 3-6 distinct sections that together cover it well.
                        Each section needs a clear, specific writing instruction.
                        """)
                .build();

        this.workerClient = builder.clone()
                .defaultSystem("You are a subject-matter writer. Write only the section requested, no preamble.")
                .build();

        this.synthesizerClient = builder.clone()
                .defaultSystem("You are an editor. Combine these sections into one cohesive report with smooth transitions.")
                .build();
    }

    public String generateReport(String topic) {

        // 1. Orchestrator plans the subtasks dynamically
        SubtaskPlan plan = orchestratorClient.prompt()
                .user("Plan a report on: " + topic)
                .call()
                .entity(SubtaskPlan.class);

        // 2. Dispatch workers in parallel — one per planned subtask
        List<CompletableFuture<WorkerOutput>> futures = plan.subtasks().stream()
                .map(subtask -> CompletableFuture.supplyAsync(() -> {
                    String content = workerClient.prompt()
                            .user(subtask.instruction())
                            .call()
                            .content();
                    return new WorkerOutput(subtask.section(), content);
                }, executor))
                .toList();

        List<WorkerOutput> sections = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        // 3. Synthesize all worker outputs into one cohesive report
        String combinedDraft = sections.stream()
                .map(s -> "## " + s.section() + "\n" + s.content())
                .collect(java.util.stream.Collectors.joining("\n\n"));

        return synthesizerClient.prompt()
                .user("Combine and polish these sections into a cohesive report:\n\n" + combinedDraft)
                .call()
                .content();
    }
}
```

```java
@RestController
@RequestMapping("/api/orchestrator")
public class ReportController {

    private final ReportOrchestratorService orchestratorService;

    public ReportController(ReportOrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    @GetMapping("/report")
    public String generate(@RequestParam String topic) {
        return orchestratorService.generateReport(topic);
    }
}
```

---

## Try it

```bash
curl "http://localhost:8080/api/orchestrator/report?topic=the%20rise%20of%20edge%20computing"
```

```text
Orchestrator might plan:
- History
- Key drivers
- Use cases
- Challenges
- Future outlook
```

```bash
curl "http://localhost:8080/api/orchestrator/report?topic=how%20coffee%20is%20processed"
```

```text
Orchestrator plans entirely different sections:
- Harvesting
- Processing methods
- Roasting
- Brewing
```

The same code handles both — the plan adapts to the topic.

---

## Orchestrator-Workers vs. Parallelization (Pattern 8) — the key distinction

|  | Parallelization (sectioning) | Orchestrator-Workers |
|---|---|---|
| **Subtasks defined** | Hardcoded by you, upfront | Decided by the LLM, at runtime |
| **Subtask count** | Fixed | Variable, input-dependent |
| **Use when** | You know the breakdown in advance (e.g. always check grammar + tone + facts) | The right breakdown genuinely varies per input (e.g. report sections differ wildly by topic) |

---

## Production notes

- Cap the orchestrator's subtask count (e.g. via the system prompt: "3-6 sections") — an unconstrained planner can spiral into excessive worker calls and runaway cost.
- This pattern is more expensive and slower than chaining or routing (orchestrator call + N worker calls + synthesizer call), so reserve it for genuinely open-ended tasks where the structure can't be predicted.
- Consider validating the orchestrator's plan before dispatching workers (e.g. reject empty/duplicate sections) — garbage plans produce garbage worker calls.