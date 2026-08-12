# Pattern 10: Evaluator-Optimizer

This is the first explicitly self-correcting pattern: one LLM call generates a solution, a second LLM call critiques it against specific criteria, and if it doesn't pass, the feedback gets fed back into another generation attempt. This loop continues until the evaluator approves or a max-iteration limit is hit — useful anywhere "good enough on the first try" isn't reliable enough (translations, code, anything with clear quality criteria).

## What's happening

The generator and evaluator are two distinct `ChatClient` calls with different jobs and different system prompts. The evaluator returns a structured verdict (`.entity()` again) — pass/fail plus specific feedback — rather than free text, so your loop logic can branch on it reliably. On failure, the original task and the evaluator's feedback get fed back into the generator for the next attempt, so each iteration genuinely improves rather than blindly retrying.

---

## Code

Use case: generate a SQL query that must satisfy specific correctness and style requirements, looping until it passes review.

```java
package com.example.genai.pattern10;

public record EvaluationResult(
        boolean passed,
        String feedback   // specific, actionable critique if not passed
) {}
```

```java
package com.example.genai.pattern10;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class SqlEvaluatorOptimizerService {

    private static final int MAX_ITERATIONS = 4;

    private final ChatClient generatorClient;
    private final ChatClient evaluatorClient;

    public SqlEvaluatorOptimizerService(ChatClient.Builder builder) {
        this.generatorClient = builder.clone()
                .defaultSystem("""
                        You write SQL queries. Output only the SQL, no explanation.
                        If feedback from a previous attempt is provided, fix exactly
                        what it flags.
                        """)
                .build();

        this.evaluatorClient = builder.clone()
                .defaultSystem("""
                        You are a strict SQL reviewer. Check the query against the
                        requirements. Flag: missing requirements, SQL injection risk
                        from string concatenation patterns, inefficient patterns
                        (e.g. SELECT * on large tables), and incorrect joins.
                        Be specific and actionable in feedback — name the exact issue.
                        """)
                .build();
    }

    public String generateQuery(String requirements) {

        String currentQuery = null;
        String feedback = null;

        for (int attempt = 1; attempt <= MAX_ITERATIONS; attempt++) {

            // Generate (or regenerate with feedback from the previous round)
            String generationPrompt = feedback == null
                    ? "Requirements:\n" + requirements
                    : """
                      Requirements:
                      %s

                      Previous attempt:
                      %s

                      Feedback to fix:
                      %s
                      """.formatted(requirements, currentQuery, feedback);

            currentQuery = generatorClient.prompt()
                    .user(generationPrompt)
                    .call()
                    .content();

            // Evaluate
            EvaluationResult evaluation = evaluatorClient.prompt()
                    .user("""
                          Requirements:
                          %s

                          Query to review:
                          %s
                          """.formatted(requirements, currentQuery))
                    .call()
                    .entity(EvaluationResult.class);

            if (evaluation.passed()) {
                return currentQuery;   // success — exit the loop
            }

            feedback = evaluation.feedback();
        }

        // Exhausted attempts — return the best effort, but let the caller know
        throw new IllegalStateException(
                "Could not produce a passing query after " + MAX_ITERATIONS +
                " attempts. Last feedback: " + feedback);
    }
}
```

```java
@RestController
@RequestMapping("/api/eval-optimize")
public class SqlEvaluatorController {

    private final SqlEvaluatorOptimizerService service;

    public SqlEvaluatorController(SqlEvaluatorOptimizerService service) {
        this.service = service;
    }

    @PostMapping("/sql")
    public String generate(@RequestBody String requirements) {
        return service.generateQuery(requirements);
    }
}
```

---

## Try it

```bash
curl -X POST "http://localhost:8080/api/eval-optimize/sql" \
  -d "Get all orders over $100 from the last 30 days, joined with customer name, sorted by date descending"
```

Round 1 might use `SELECT *` — the evaluator flags it as inefficient — round 2 generates explicit columns and passes.

---

## When this pattern earns its cost

Anthropic's guidance on this is precise: use evaluator-optimizer when you have clear evaluation criteria and when iterative refinement provides measurable value — code correctness, translation fidelity, structured document compliance. It's expensive (2× calls per iteration, up to `MAX_ITERATIONS` rounds) — don't reach for it on tasks where a single careful generation call already does well, or where "good" is too subjective for the evaluator to judge consistently.

---

## Production notes

- Always cap iterations — an evaluator that's too strict (or miscalibrated) can loop forever without the limit.
- Log every `(attempt, feedback)` pair — this is gold for debugging why the generator keeps failing, and often reveals the evaluator's criteria need tightening, not the generator.
- The evaluator's system prompt is the highest-leverage part of this pattern — vague criteria produce vague, unhelpful feedback that doesn't actually improve the next attempt.