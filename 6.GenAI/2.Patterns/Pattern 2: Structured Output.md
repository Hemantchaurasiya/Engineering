# Pattern 2: Structured Output

Raw text is useless to a program — you need typed Java objects you can store, validate, and pass downstream. Spring AI solves this with `.entity()`, which auto-generates a format instruction, appends it to your prompt, sends it to the model, then deserializes the JSON response straight into your Java type.

## What's happening

`ChatClient` has an `.entity()` terminal method (instead of `.content()`) that does three things automatically:

1. Inspects your target Java type (record, class, or `List<T>`).
2. Generates a JSON-schema-style instruction and appends it to the prompt behind the scenes.
3. Parses the model's JSON response into that type via Jackson, so you get a compiled, type-safe object — no manual parsing, no regex on the response.

---

## Code

### 1. Define your target type as a Java record

```java
package com.example.genai.pattern2;

import java.util.List;

public record Recipe(
        String title,
        List<String> ingredients,
        List<String> steps,
        int prepTimeMinutes
) {}
```

### 2. Use `.entity()` to get it back typed

```java
package com.example.genai.pattern2;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/structured")
public class StructuredOutputController {

    private final ChatClient chatClient;

    public StructuredOutputController(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @GetMapping("/recipe")
    public Recipe getRecipe(@RequestParam String dish) {
        return chatClient.prompt()
                .user(u -> u.text("Generate a simple recipe for {dish}.")
                             .param("dish", dish))
                .call()
                .entity(Recipe.class);   // <-- typed deserialization happens here
    }
}
```

### 3. For collections, use a `ParameterizedTypeReference`

(Plain `.entity(List.class)` would erase the generic type.)

```java
@GetMapping("/recipes")
public List<Recipe> getRecipes(@RequestParam String cuisine) {
    return chatClient.prompt()
            .user("Give me 3 distinct recipes for " + cuisine + " cuisine.")
            .call()
            .entity(new org.springframework.ai.converter.ParameterizedTypeReference<List<Recipe>>() {});
}
```

---

## Try it

```bash
curl "http://localhost:8080/api/structured/recipe?dish=margherita%20pizza"
```

Returns:

```json
{
  "title": "Margherita Pizza",
  "ingredients": [
    "Pizza dough",
    "Tomato sauce",
    "Fresh mozzarella",
    "Basil",
    "Olive oil"
  ],
  "steps": [
    "Preheat oven to 250°C",
    "Spread sauce on dough",
    "Add mozzarella",
    "Bake 8-10 min",
    "Top with basil"
  ],
  "prepTimeMinutes": 20
}
```

---

## Notes from production use

- If you need fine control over the schema description, annotate fields with `@JsonPropertyDescription("...")` from Jackson — Spring AI includes these in the generated format instructions, which meaningfully improves output quality on ambiguous fields.
- Some providers (OpenAI, recent Anthropic models) support native structured-output / JSON-mode enforcement at the API level — Spring AI will use that when the provider's `ChatOptions` exposes it, falling back to prompt-based instructions otherwise.
- This pattern is the dependency every later pattern leans on: routing decisions, evaluator scores, planning steps — all of those are structured outputs under the hood.