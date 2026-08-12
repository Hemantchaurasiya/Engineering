# Pattern 13: Planning

Planning makes the model's strategy explicit and inspectable before any action happens — instead of improvising step-by-step like ReAct, or having the orchestrator immediately dispatch parallel workers, a dedicated planning call produces a full ordered plan first. That plan can be logged, validated, shown to a human for approval, or revised — all before you spend money or take any real-world action executing it.

## What's happening

The planner makes one call that returns a full `Plan` — an ordered list of structured steps — before any execution begins. Each step is then executed independently (often via a tool-equipped `ChatClient`, reusing Pattern 5), in order. Critically, the plan is a regular Java object you can log, validate, show to a user for approval, or reject — something pure ReAct improvisation never gives you, since ReAct only reveals its next step right as it takes it.

---

## Code

### 1. The plan's shape

```java
package com.example.genai.pattern13;

import java.util.List;

public record Plan(String goal, List<PlanStep> steps) {
    public record PlanStep(int order, String description, String expectedOutcome) {}
}

public record StepResult(int order, String description, String outcome, boolean succeeded) {}
```

### 2. Planner — produces the full plan upfront

```java
package com.example.genai.pattern13;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class PlannerService {

    private final ChatClient plannerClient;

    public PlannerService(ChatClient.Builder builder) {
        this.plannerClient = builder.clone()
                .defaultSystem("""
                        You are a meticulous planner. Break the goal into an
                        ordered sequence of concrete, executable steps. Each step
                        should be independently actionable and state what
                        outcome it should produce. Avoid vague steps.
                        """)
                .build();
    }

    public Plan createPlan(String goal) {
        return plannerClient.prompt()
                .user("Goal: " + goal)
                .call()
                .entity(Plan.class);
    }
}
```

### 3. Executor — runs the approved plan step by step, with re-planning on failure

```java
package com.example.genai.pattern13;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PlanExecutorService {

    private final ChatClient executorClient;
    private final PlannerService plannerService;

    public PlanExecutorService(ChatClient.Builder builder, PlannerService plannerService,
                                Object... tools) {
        this.executorClient = builder.clone()
                .defaultSystem("Execute exactly the step described. Use tools as needed. Report the outcome plainly.")
                .defaultTools(tools)
                .build();
        this.plannerService = plannerService;
    }

    public List<StepResult> executeGoal(String goal) {

        Plan plan = plannerService.createPlan(goal);
        List<StepResult> results = new ArrayList<>();

        for (Plan.PlanStep step : plan.steps()) {
            try {
                String outcome = executorClient.prompt()
                        .user("""
                              Step %d: %s
                              Expected outcome: %s
                              """.formatted(step.order(), step.description(), step.expectedOutcome()))
                        .call()
                        .content();

                results.add(new StepResult(step.order(), step.description(), outcome, true));

            } catch (Exception ex) {
                results.add(new StepResult(step.order(), step.description(),
                        "Failed: " + ex.getMessage(), false));

                // Re-plan the remaining work given what's been done and what failed
                String remainingGoal = """
                        Original goal: %s
                        Completed so far: %s
                        Step that failed: %s (%s)
                        Produce a revised plan for the remaining work.
                        """.formatted(goal, results, step.description(), ex.getMessage()));

                Plan revisedPlan = plannerService.createPlan(remainingGoal);
                results.addAll(executeSteps(revisedPlan));
                break;
            }
        }

        return results;
    }

    private List<StepResult> executeSteps(Plan plan) {
        List<StepResult> results = new ArrayList<>();
        for (Plan.PlanStep step : plan.steps()) {
            String outcome = executorClient.prompt()
                    .user(step.description())
                    .call()
                    .content();
            results.add(new StepResult(step.order(), step.description(), outcome, true));
        }
        return results;
    }
}
```

### 4. Controller — exposing the plan for review before execution is the key UX win here

```java
@RestController
@RequestMapping("/api/plan")
public class PlanningController {

    private final PlannerService plannerService;
    private final PlanExecutorService executorService;

    public PlanningController(PlannerService plannerService, PlanExecutorService executorService) {
        this.plannerService = plannerService;
        this.executorService = executorService;
    }

    // Step 1: get the plan, show it to a human, don't execute yet
    @GetMapping("/preview")
    public Plan preview(@RequestParam String goal) {
        return plannerService.createPlan(goal);
    }

    // Step 2: once approved, actually run it
    @PostMapping("/execute")
    public List<StepResult> execute(@RequestParam String goal) {
        return executorService.executeGoal(goal);
    }
}
```

---

## Try it

```bash
curl "http://localhost:8080/api/plan/preview?goal=Migrate%20our%20user%20table%20to%20add%20a%20soft-delete%20column%20safely"
```

```json
{
  "goal": "Migrate our user table to add a soft-delete column safely",
  "steps": [
    {
      "order": 1,
      "description": "Check current row count and table size",
      "expectedOutcome": "Size estimate to judge migration risk"
    },
    {
      "order": 2,
      "description": "Write ALTER TABLE statement adding nullable deleted_at column",
      "expectedOutcome": "Migration SQL ready"
    },
    {
      "order": 3,
      "description": "Plan backfill strategy for existing rows",
      "expectedOutcome": "Backfill approach defined"
    },
    {
      "order": 4,
      "description": "Draft rollback plan",
      "expectedOutcome": "Safe rollback documented"
    }
  ]
}
```

A human reviews this before anything executes — exactly the visibility ReAct doesn't give you.

---

## Planning vs. ReAct vs. Orchestrator-Workers

|  | ReAct (11) | Planning (13) | Orchestrator-Workers (9) |
|---|---|---|---|
| **When is the plan visible** | Never upfront — revealed one action at a time | Fully upfront, before execution | Upfront, but immediately dispatched |
| **Human review possible** | Hard — would need to interrupt mid-loop | Easy — natural checkpoint | Hard — workers fire immediately |
| **Adapts mid-execution** | Yes, every step | Only via explicit re-planning | No, unless you add it |
| **Best for** | Exploratory tasks, unclear info needs | High-stakes or auditable multi-step work | Decomposable tasks safe to fully parallelize |

---

## Production notes

- The plan-preview endpoint is exactly where you'd hook in Human-in-the-Loop (Pattern 19, coming up) for approval gates on risky operations.
- Re-planning is expensive — it's a full extra planner call — so only trigger it on genuine step failures, not minor warnings.
- Validate the plan structurally before executing (non-empty steps, sane ordering) — a malformed plan from the LLM should fail fast rather than executing garbage steps.