# Pattern 12: Reflection

Reflection is the model critiquing and improving its own work, in the same conversational thread — distinct from Evaluator-Optimizer's strict pass/fail loop with a dedicated, separately-prompted evaluator. Reflection works well for open-ended quality (writing, design, code style) where there's no crisp binary criterion, just "is this actually good," and the same model can usually catch its own mistakes when explicitly asked to look back at its own work.

## What's happening

Unlike Evaluator-Optimizer, there's no separate evaluator client with strict pass/fail output — it's the same model, same conversation thread, just prompted at each turn to look back critically at what it just wrote. The conversation history itself carries the context: the model sees its own draft as a prior message and is asked to find problems with it, then revise. This is cheaper to set up (no separate evaluator system prompt to design) but gives you less control than evaluator-optimizer's explicit criteria — good for creative/subjective quality, less ideal where you need deterministic gating.

---

## Code

```java
package com.example.genai.pattern12;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SelfReflectionService {

    private final ChatClient chatClient;

    public SelfReflectionService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public String writeWithReflection(String writingTask, int reflectionRounds) {

        List<org.springframework.ai.chat.messages.Message> conversation = new ArrayList<>();
        conversation.add(new SystemMessage(
                "You are a skilled writer who holds high standards for your own work."));
        conversation.add(new UserMessage(writingTask));

        // Initial draft
        String current = chatClient.prompt(new Prompt(conversation))
                .call()
                .content();
        conversation.add(new AssistantMessage(current));

        // Reflection rounds — same thread, model sees its own prior output
        for (int round = 1; round <= reflectionRounds; round++) {

            // Step A: ask the model to critique its own last message
            conversation.add(new UserMessage("""
                    Critically review what you just wrote. Identify specific
                    weaknesses: unclear sentences, weak word choices, logical
                    gaps, or anything that doesn't serve the reader. Be honest
                    and specific — don't just say it's fine if it isn't.
                    """));

            String critique = chatClient.prompt(new Prompt(conversation))
                    .call()
                    .content();
            conversation.add(new AssistantMessage(critique));

            // Step B: ask for a revision based on its own critique
            conversation.add(new UserMessage("""
                    Now rewrite your original piece, addressing the issues
                    you just identified. Return only the revised text.
                    """));

            current = chatClient.prompt(new Prompt(conversation))
                    .call()
                    .content();
            conversation.add(new AssistantMessage(current));
        }

        return current;
    }
}
```

```java
@RestController
@RequestMapping("/api/reflect")
public class ReflectionController {

    private final SelfReflectionService reflectionService;

    public ReflectionController(SelfReflectionService reflectionService) {
        this.reflectionService = reflectionService;
    }

    @GetMapping("/write")
    public String write(@RequestParam String task,
                         @RequestParam(defaultValue = "1") int rounds) {
        return reflectionService.writeWithReflection(task, rounds);
    }
}
```

---

## Try it

```bash
curl "http://localhost:8080/api/reflect/write?task=Write%20an%20opening%20paragraph%20for%20a%20blog%20post%20about%20remote%20work%20burnout&rounds=2"
```

Round-by-round, you'd typically see: draft 1 has generic openers ("In today's fast-paced world...") → self-critique flags the cliché → revision 1 opens with something more specific → second critique flags pacing → revision 2 tightens it further.

---

## Reflection vs. Evaluator-Optimizer — side by side

|  | Evaluator-Optimizer (Pattern 10) | Reflection (Pattern 12) |
|---|---|---|
| **Roles** | Separate generator + evaluator clients, distinct system prompts | Same client, same conversation thread |
| **Stop condition** | Explicit pass/fail from structured evaluator output | Fixed round count, or model self-reports "no more issues" |
| **Best for** | Tasks with checkable correctness (code, SQL, structured compliance) | Subjective quality (prose, tone, creative work) |
| **Control** | Tight — you can gate on specific criteria | Looser — depends on the model's own judgment of quality |

---

## Production notes

- Reflection rounds have diminishing returns fast — 1-2 rounds typically capture most of the gain; beyond that you're often just paying for cosmetic rewording.
- Because everything lives in one growing conversation, token cost compounds — each round resends the entire history. For long documents, consider truncating early critique turns once they've served their purpose.
- For tasks where you genuinely need a checkable pass/fail gate, prefer Evaluator-Optimizer — reflection's self-grading can be overly generous ("looks good to me") since the model is reviewing its own work without an independent, differently-instructed perspective.