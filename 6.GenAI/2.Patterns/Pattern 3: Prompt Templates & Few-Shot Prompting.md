# Pattern 3: Prompt Templates & Few-Shot Prompting

Hardcoded prompt strings don't scale — you need reusable templates with variable substitution, and for many tasks you need to show the model examples of correct input→output behavior rather than just describe it. Few-shot prompting consistently improves accuracy on classification, formatting, and style-matching tasks where instructions alone are ambiguous.

## What's happening

A `PromptTemplate` separates the prompt's structure (reusable, often loaded from a `.st` resource file) from its data (the variables filled in per-request). Few-shot prompting layers on top: you prepend a handful of `UserMessage`/`AssistantMessage` pairs demonstrating the exact input→output behavior you want, before the real user message — the model pattern-matches against them.

---

## Code

### 1. Template with inline variables (simplest case)

```java
package com.example.genai.pattern3;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/template")
public class PromptTemplateController {

    private final ChatClient chatClient;

    public PromptTemplateController(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @GetMapping("/summarize")
    public String summarize(@RequestParam String text, @RequestParam String tone) {
        PromptTemplate template = new PromptTemplate("""
                Summarize the following text in a {tone} tone.
                Keep it to 2 sentences maximum.

                Text: {text}
                """);

        String renderedPrompt = template.render(Map.of("text", text, "tone", tone));

        return chatClient.prompt(renderedPrompt).call().content();
    }
}
```

### 2. Template loaded from an external resource file

(Keeps prompts out of Java source, lets non-devs edit copy.)

**`src/main/resources/prompts/classify-ticket.st`**

```text
You are a support ticket triager.
Classify the ticket into exactly one category: BILLING, BUG, FEATURE_REQUEST, OTHER.
Respond with only the category name.

Ticket: {ticket}
```

```java
@Value("classpath:/prompts/classify-ticket.st")
private Resource classifyTicketResource;

@GetMapping("/classify")
public String classify(@RequestParam String ticket) {
    PromptTemplate template = new PromptTemplate(classifyTicketResource);
    String prompt = template.render(Map.of("ticket", ticket));
    return chatClient.prompt(prompt).call().content();
}
```

### 3. Few-shot prompting

Build an explicit message list with example pairs before the real question.

```java
package com.example.genai.pattern3;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/fewshot")
public class FewShotController {

    private final ChatClient chatClient;

    public FewShotController(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @GetMapping("/extract-sentiment")
    public String extractSentiment(@RequestParam String review) {

        var messages = List.of(
            new SystemMessage("Extract sentiment as exactly one word: POSITIVE, NEGATIVE, or NEUTRAL."),

            // --- few-shot examples ---
            new UserMessage("The delivery was late but the product quality is excellent."),
            new AssistantMessage("POSITIVE"),

            new UserMessage("Worst purchase I've made all year, broke in two days."),
            new AssistantMessage("NEGATIVE"),

            new UserMessage("It does what it says on the box, nothing more nothing less."),
            new AssistantMessage("NEUTRAL"),
            // --- end examples ---

            new UserMessage(review)
        );

        return chatClient.prompt(new Prompt(messages))
                .call()
                .content();
    }
}
```

---

## Try it

```bash
curl "http://localhost:8080/api/fewshot/extract-sentiment?review=Customer%20service%20never%20replied%20to%20my%20emails"
```

```text
NEGATIVE
```

---

## Why few-shot beats pure instructions here

"Extract sentiment" sounds unambiguous, but real reviews are mixed (e.g. example 1 above has both a complaint and praise). Telling the model the rule isn't as reliable as showing it 2-3 edge cases of how to resolve ambiguity — the examples anchor exactly which signal should dominate the classification, something natural-language instructions struggle to fully specify.

---

## Production tips

- Keep few-shot examples diverse and representative of edge cases, not just the easy cases — 3-5 examples is usually the sweet spot; more adds latency and cost without much accuracy gain.
- `.st` template files use StringTemplate syntax under the hood (Spring AI's default), so you also get conditionals and iteration available for more complex templates.
- For templates reused across many endpoints, define a single `@Bean PromptTemplate` and inject it rather than re-instantiating per request.