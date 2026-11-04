# Pattern 14: Multi-Agent Collaboration

This extends Orchestrator-Workers in one key way: instead of workers being single, stateless LLM calls, each "worker" here is a full specialist agent — with its own role, system prompt, and possibly its own tools — and a supervisor dynamically decides which specialist to invoke next based on the evolving conversation, looping until the task is genuinely done (not just dispatching once and synthesizing).

## What's happening

The supervisor doesn't dispatch all workers once like Orchestrator-Workers — it makes one routing decision at a time, observes the result, and decides again, looping until it judges the task complete. Each specialist agent is a fully independent `ChatClient` (potentially with its own tools, as in Pattern 5) — the supervisor is just choosing which expert should take the next turn, based on a shared progress log everyone reads from and writes to.

---

## Code

### 1. The supervisor's decision type

```java
package com.example.genai.pattern14;

public enum NextAgent {
    RESEARCHER,
    WRITER,
    CRITIC,
    DONE
}

public record SupervisorDecision(NextAgent nextAgent, String instructionForAgent) {}
```

### 2. The agent team — supervisor plus three specialists

```java
package com.example.genai.pattern14;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class MultiAgentSupervisorService {

    private static final int MAX_TURNS = 8;

    private final ChatClient supervisorClient;
    private final ChatClient researcherClient;
    private final ChatClient writerClient;
    private final ChatClient criticClient;

    public MultiAgentSupervisorService(ChatClient.Builder builder /*, ResearchTools researchTools */) {

        this.supervisorClient = builder.clone()
                .defaultSystem("""
                        You coordinate a team: RESEARCHER gathers facts, WRITER drafts
                        content, CRITIC reviews quality. Given the goal and progress so
                        far, decide which agent should act next and what exactly they
                        should do. Choose DONE only when the final output is genuinely
                        complete and has passed critic review.
                        """)
                .build();

        this.researcherClient = builder.clone()
                .defaultSystem("You are a researcher. Gather and state relevant facts concisely.")
                // .defaultTools(researchTools)  // could have web search, DB lookup, etc.
                .build();

        this.writerClient = builder.clone()
                .defaultSystem("You are a writer. Produce clear, well-structured content from the research provided.")
                .build();

        this.criticClient = builder.clone()
                .defaultSystem("""
                        You are a strict critic. Review the current draft and report
                        specific issues, or explicitly state 'No issues — ready to ship'
                        if it genuinely meets a high bar.
                        """)
                .build();
    }

    public String runTask(String goal) {

        List<String> progressLog = new ArrayList<>();
        progressLog.add("GOAL: " + goal);

        for (int turn = 0; turn < MAX_TURNS; turn++) {

            String progressSoFar = String.join("\n\n", progressLog);

            SupervisorDecision decision = supervisorClient.prompt()
                    .user("Progress so far:\n" + progressSoFar)
                    .call()
                    .entity(SupervisorDecision.class);

            if (decision.nextAgent() == NextAgent.DONE) {
                return progressLog.get(progressLog.size() - 1); // last meaningful output
            }

            ChatClient agentToCall = switch (decision.nextAgent()) {
                case RESEARCHER -> researcherClient;
                case WRITER -> writerClient;
                case CRITIC -> criticClient;
                case DONE -> throw new IllegalStateException("unreachable");
            };

            String agentOutput = agentToCall.prompt()
                    .user("""
                          Context so far:
                          %s

                          Your task: %s
                          """.formatted(progressSoFar, decision.instructionForAgent()))
                    .call()
                    .content();

            progressLog.add("[%s]: %s".formatted(decision.nextAgent(), agentOutput));
        }

        throw new IllegalStateException("Task did not converge within " + MAX_TURNS + " turns");
    }
}
```

### 3. Controller

```java
@RestController
@RequestMapping("/api/multi-agent")
public class MultiAgentController {

    private final MultiAgentSupervisorService supervisorService;

    public MultiAgentController(MultiAgentSupervisorService supervisorService) {
        this.supervisorService = supervisorService;
    }

    @GetMapping("/run")
    public String run(@RequestParam String goal) {
        return supervisorService.runTask(goal);
    }
}
```

---

## Try it

```bash
curl "http://localhost:8080/api/multi-agent/run?goal=Write%20a%20short%2C%20accurate%20explainer%20on%20how%20mRNA%20vaccines%20work"
```

A plausible trace:

- Supervisor → RESEARCHER: "gather key facts about mRNA vaccine mechanism"
- Researcher produces a fact list
- Supervisor → WRITER: "draft an explainer using this research"
- Writer produces a draft
- Supervisor → CRITIC: "review this draft"
- Critic: "Issue — oversimplifies the lipid nanoparticle delivery step"
- Supervisor → WRITER: "revise to address the critic's feedback"
- Writer produces revision
- Supervisor → CRITIC: "No issues — ready to ship"
- Supervisor → DONE

---

## Multi-Agent vs. Orchestrator-Workers — the real distinction

|  | Orchestrator-Workers (9) | Multi-Agent (14) |
|---|---|---|
| **Planning** | One upfront plan, dispatched at once | One decision at a time, re-evaluated each turn |
| **Workers** | Stateless, single-purpose calls | Full agents — can have their own tools, persona, multi-turn behavior |
| **Loop** | Fan-out → synthesize (one pass) | Genuine loop until convergence |
| **Best for** | Decomposable tasks where subtask list is fairly clear once planned | Tasks needing iterative cross-checking between specialized perspectives |

---

## Production notes

- This is the most expensive and slowest pattern so far — potentially many sequential LLM calls (supervisor + specialist, repeated). Reserve it for tasks where quality genuinely benefits from specialist cross-review; don't reach for it as a default.
- Convergence isn't guaranteed — always cap `MAX_TURNS`; a critic and writer can in principle loop indefinitely if the critic's bar is miscalibrated.
- In production multi-agent systems, specialists are often deployed as separate services communicating over a message bus rather than in-process method calls — useful when specialists need independent scaling, different infra, or are owned by different teams. The pattern's logic is identical; only the transport changes.