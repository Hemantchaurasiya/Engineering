# Spring AI Recursive Advisors — Effective Notes

> Simple language notes. Each concept has: **Key Points**, **Use Cases**, **Where to Use**, **Problem Solved**, and **Java Example**.

---

## Table of Contents

1. [What is a recursive advisor?](#1-what-is-a-recursive-advisor)
2. [The key utility: `CallAdvisorChain.copy(this)`](#2-the-key-utility-calladvisorchaincopythis)
3. [Built-in: ToolCallingAdvisor](#3-built-in-toolcallingadvisor)
4. [Built-in: StructuredOutputValidationAdvisor](#4-built-in-structuredoutputvalidationadvisor)
5. [Accumulating token usage (UsageAccumulator)](#5-accumulating-token-usage-usageaccumulator)
6. [Build your own recursive advisor (retry example)](#6-build-your-own-recursive-advisor-retry-example)
7. [Recursive vs normal advisor](#7-recursive-vs-normal-advisor)
8. [Quick cheat sheet](#8-quick-cheat-sheet)
9. [Common mistakes](#9-common-mistakes)
10. [Interview quick Q&A](#10-interview-quick-qa)

---

## 1. What is a recursive advisor?

**Key Points**
- A **recursive advisor** is a special advisor that can **loop through the downstream advisor chain more than once**.
- A normal advisor calls the next advisor **one time**. A recursive advisor calls it **again and again** until a condition is met.
- Typical reasons to loop:
  1. **Tool calling**: run tool calls in a loop until no more tools are needed.
  2. **Structured output validation**: check JSON, retry if it is invalid.
  3. **Evaluation logic**: check the answer and change the request, then call again.
  4. **Retry logic**: retry with a modified request.

**Use Cases**
- Agent loops, self-correcting JSON, "check answer quality then retry", prompt refinement.

**Where to Use**
- When the **LLM must be called multiple times** for one user request, and you want the other advisors (memory, logging, observability) to still work.

**Problem Solved**
- Putting loop logic **inside the advisor chain** instead of hiding it inside each `ChatModel` (like Spring AI 1.x). Other advisors can see every iteration.

**Simple picture**
```
Normal advisor   : request → next → response
Recursive advisor: request → next → check → (not good?) change request → next → check → ... → response
```

---

## 2. The key utility: `CallAdvisorChain.copy(this)`

**Key Points**
- `CallAdvisorChain.copy(CallAdvisor after)` creates a **new chain** containing **only the advisors that come after** the given advisor.
- The recursive advisor calls this **sub-chain** as many times as it needs.
- This gives 4 benefits:
  1. The recursive advisor can loop through **the remaining advisors**.
  2. **Other advisors can observe and intercept each iteration.**
  3. The chain keeps **proper ordering and observability**.
  4. It **does not re-run advisors that came before it** (no double work like memory loading or auth).
- The usual call: `callAdvisorChain.copy(this).nextCall(request)`.

```
Chain:  [A: memory] → [R: recursive advisor] → [B: logger] → [LLM]

R loops:  copy(R) = [B → LLM]
          Iteration 1: B → LLM
          Iteration 2: B → LLM
A runs only ONCE (it is before R). B runs on EVERY iteration.
```

**Use Cases**
- Memory advisor placed **outside** (before) the loop: runs once. Logger placed **inside** (after): logs each LLM call.

**Where to Use**
- Inside `adviseCall` of every recursive advisor.

**Problem Solved**
- Safe repeating of the downstream chain without repeating upstream advisors.

**Java Example**
```java
@Override
public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain callAdvisorChain) {
    // Each call goes ONLY through advisors AFTER this one
    ChatClientResponse response = callAdvisorChain.copy(this).nextCall(request);
    return response;
}
```
> Tip: advisor **order** decides who is "before" and who is "after". See the Advisors API notes.

---

## 3. Built-in: ToolCallingAdvisor

**Key Points**
- Runs the **tool calling loop inside the advisor chain** (not inside each `ChatModel`).
- **Auto-registered by `DefaultChatClient` whenever tools are present.** It is the default way `ChatClient` runs tool-based conversations.
- Highlights:
  - **Loops until `ToolExecutionEligibilityChecker`** says there are no more tool calls.
  - Uses **`callAdvisorChain.copy(this)`** → other advisors **see every iteration**.
  - **Return-direct** support: if a tool has `returnDirect = true`, the loop **stops** and returns the tool result **without sending it back to the LLM**.
  - Implements **`ToolAdvisor`** (marker interface) → `DefaultChatClient` enforces **only one tool advisor**; custom subclasses **replace** the default.
  - **Accumulates token usage** across all iterations, so `ChatResponse` shows the **total** of all model calls.
- Default order used in the example: `BaseAdvisor.HIGHEST_PRECEDENCE + 300`.
- More details: *ToolCallingAdvisor* page (builder API, hooks, memory ordering, user-controlled execution, custom subclass). Example of a custom subclass: *Tool Search Tool*.

**Use Cases**
- Agents with tools (weather, DB, search, actions).

**Where to Use**
- Automatically with `.tools(...)`. Register manually only to customize (e.g., custom `ToolCallingManager`, custom order).

**Problem Solved**
- You do not write the "call model → run tool → call again" loop.

**Java Example**
```java
var toolCallingAdvisor = ToolCallingAdvisor.builder()
    .toolCallingManager(toolCallingManager)
    .advisorOrder(BaseAdvisor.HIGHEST_PRECEDENCE + 300)
    .build();

var chatClient = ChatClient.builder(chatModel)
    .defaultAdvisors(toolCallingAdvisor)
    .build();
```

---

## 4. Built-in: StructuredOutputValidationAdvisor

**Key Points**
- **Validates structured JSON output** against a **JSON schema**. If invalid → **retries** (default up to **3 attempts**).
- Features:
  - Derives the schema from the expected **output type**, or accepts a **pre-supplied schema string**.
  - Validates the LLM response against the schema.
  - **Adds the validation error message to the prompt** on retry, so the model can **self-correct**.
  - Uses `callAdvisorChain.copy(this)` for recursive calls.
  - **Accumulates token usage** across all attempts (total of all retries).
  - Optional custom `JsonMapper`.
- Configure with **`outputType`** (schema auto-derived) **or** **`outputJsonSchema`** (ready schema). They are **mutually exclusive**.
- Easier option: use **`entity(..., spec -> spec.validateSchema())`** and the advisor is added for you. See *Schema Validation & Self-Correction*.

**Use Cases**
- Extraction pipelines where wrong JSON breaks downstream code.

**Where to Use**
- When correctness matters more than a little extra latency and token cost.

**Problem Solved**
- Random malformed JSON from LLMs, without writing retry loops.

**Java Example**
```java
// With outputType
var validationAdvisor = StructuredOutputValidationAdvisor.builder()
    .outputType(MyResponseType.class)
    .maxRepeatAttempts(3)
    .build();

var chatClient = ChatClient.builder(chatModel)
    .defaultAdvisors(validationAdvisor)
    .build();

// With a pre-supplied schema
var validationAdvisor2 = StructuredOutputValidationAdvisor.builder()
    .outputJsonSchema(myConverter.getJsonSchema())
    .build();

// Shortcut on the call itself
ActorFilms actorFilms = chatClient.prompt()
    .user("Generate the filmography for a random actor.")
    .call()
    .entity(ActorFilms.class, spec -> spec.validateSchema());
```

---

## 5. Accumulating token usage (UsageAccumulator)

**Key Points**
- A recursive advisor makes **more than one model call** per invocation. If you do nothing, the caller sees **only the last call's usage** (wrong cost number).
- Use **`org.springframework.ai.chat.client.advisor.UsageAccumulator`**:
  1. Create **one instance per `adviseCall`** (or **per stream subscription**, inside `Flux.defer`, to keep it subscription-local).
  2. After each round: **`addRoundResponse(response.chatResponse())`**.
  3. At the end: **`applyAccumulatedUsage(response)`** to stamp the **cumulative total** on the final response.
- Under the hood: `org.springframework.ai.support.UsageCalculator` (`accumulateResponseUsage`, `withUsage`) does the math; `UsageAccumulator` wraps it.

**Use Cases**
- Correct cost tracking for agents, retries, and evaluation loops.

**Where to Use**
- Every **custom** recursive advisor.

**Problem Solved**
- Under-reporting tokens (and cost) when the LLM is called several times.

**Java Example**
```java
UsageAccumulator usage = new UsageAccumulator();   // new per adviseCall
ChatClientResponse response;
do {
    response = callAdvisorChain.copy(this).nextCall(request);
    usage.addRoundResponse(response.chatResponse());
    // ... decide whether to loop again ...
}
while (loopAgain);
return usage.applyAccumulatedUsage(response);
```

---

## 6. Build your own recursive advisor (retry example)

**Key Points**
- Steps:
  1. Implement `CallAdvisor` (and `StreamAdvisor` if you also need streaming).
  2. In `adviseCall`, create a `UsageAccumulator`.
  3. Loop: `callAdvisorChain.copy(this).nextCall(request)`.
  4. Check the response. If bad: **change the request** (e.g., add feedback) and loop again.
  5. **Set a maximum number of attempts** (always).
  6. Return `usage.applyAccumulatedUsage(response)`.
- Other advisors **after** yours see every iteration. Advisors **before** yours run once.

**Use Cases**
- "Evaluate and improve": ask the model to critique its answer, retry with the critique.
- "Answer must be under 100 words, else retry".

**Problem Solved**
- Custom self-correcting behavior that works with memory, logging, and observability.

**Java Example (answer length check, my own example)**
```java
public class ShortAnswerAdvisor implements CallAdvisor {

    private static final int MAX_ATTEMPTS = 3;

    @Override public String getName() { return "ShortAnswerAdvisor"; }
    @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 500; }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        UsageAccumulator usage = new UsageAccumulator();     // one per adviseCall
        ChatClientRequest current = request;
        ChatClientResponse response;
        int attempt = 0;

        do {
            attempt++;
            response = chain.copy(this).nextCall(current);   // only advisors AFTER this one
            usage.addRoundResponse(response.chatResponse());

            String text = response.chatResponse().getResult().getOutput().getText();
            boolean tooLong = text != null && text.split("\\s+").length > 100;

            if (!tooLong) break;                             // good answer, stop

            // Change the request: ask for a shorter answer
            current = current.mutate()
                .prompt(current.prompt().augmentUserMessage(
                    current.prompt().getUserMessage().getText()
                        + "\nYour previous answer was too long. Answer in under 100 words."))
                .build();
        } while (attempt < MAX_ATTEMPTS);

        return usage.applyAccumulatedUsage(response);
    }
}
```
> This is a sketch built from the pieces in your document (`copy(this)`, `UsageAccumulator`, and the `mutate()` / `augmentUserMessage` pattern from the Advisors API notes). Check exact method names in your Spring AI version.

---

## 7. Recursive vs normal advisor

| Point | Normal advisor | Recursive advisor |
|---|---|---|
| Calls next advisor | **Once** | **Many times** |
| Uses | `chain.nextCall(request)` | `chain.copy(this).nextCall(request)` in a loop |
| Re-runs earlier advisors? | No | **No** (sub-chain has only later advisors) |
| Later advisors see | One request | **Every iteration** |
| Token usage | Single call | Must **accumulate** (`UsageAccumulator`) |
| Examples | Logger, memory, safety | `ToolCallingAdvisor`, `StructuredOutputValidationAdvisor` |
| Risk | Low | **Infinite loops, cost growth** → always cap attempts |

---

## 8. Quick cheat sheet

```java
// Loop skeleton
UsageAccumulator usage = new UsageAccumulator();
ChatClientResponse response;
do {
    response = callAdvisorChain.copy(this).nextCall(request);
    usage.addRoundResponse(response.chatResponse());
    // decide: loopAgain? (and change request if needed)
} while (loopAgain);
return usage.applyAccumulatedUsage(response);
```

| Need | Use |
|---|---|
| Loop through remaining advisors | `callAdvisorChain.copy(this)` |
| Tool loop | `ToolCallingAdvisor` (auto-registered) |
| JSON validation + retry | `StructuredOutputValidationAdvisor` or `.entity(..., spec -> spec.validateSchema())` |
| Correct total tokens | `UsageAccumulator` |
| Only one tool advisor | Implement `ToolAdvisor` marker when replacing |
| Stop tool loop early | Tool with `returnDirect = true` |
| Max retries | `maxRepeatAttempts(n)` |

---

## 9. Common mistakes

1. Using `chain.nextCall(request)` repeatedly (instead of `chain.copy(this).nextCall(request)`) → the chain is already consumed or earlier advisors run again.
2. **No attempt limit** in a custom loop → infinite loop and huge cost.
3. Forgetting **`UsageAccumulator`** → the caller sees only the last call's tokens.
4. Creating **one `UsageAccumulator` and sharing it** across requests/subscriptions → mixed numbers. Create **one per `adviseCall`** (or per subscription in `Flux.defer`).
5. Placing the memory advisor **inside** the loop by accident → duplicate writes. See *Tool Calling: Memory and the Tool Loop*.
6. Registering a **second tool advisor** → error; only one `ToolAdvisor` allowed.
7. Setting **both `outputType` and `outputJsonSchema`** → they are mutually exclusive.
8. Not changing the request on retry (same prompt again) → same wrong answer. Add the error or feedback to the prompt.
9. Forgetting streaming: `StructuredOutputValidationAdvisor` and `validateSchema()` need the **complete response**, so they do not support streaming.
10. Ignoring cost: each loop iteration is a **full LLM call**.

---

## 10. Interview quick Q&A

**Q1. What is a recursive advisor?**
An advisor that can loop through the downstream advisor chain multiple times, for example to run tool calls or retry until a condition is met.

**Q2. What does `CallAdvisorChain.copy(advisor)` do?**
Creates a new chain with only the advisors after the given advisor, so the recursive advisor can call them repeatedly without re-running earlier advisors.

**Q3. Why is this good for observability?**
Other advisors after the recursive advisor see and can intercept every iteration.

**Q4. Name the two built-in recursive advisors.**
`ToolCallingAdvisor` and `StructuredOutputValidationAdvisor`.

**Q5. When does `ToolCallingAdvisor` stop looping?**
When `ToolExecutionEligibilityChecker` reports no more tool calls, or when a called tool has `returnDirect = true`.

**Q6. What is the `ToolAdvisor` marker for?**
Lets `DefaultChatClient` enforce a single tool advisor and accept custom subclasses as replacements.

**Q7. How does `StructuredOutputValidationAdvisor` self-correct?**
It validates the JSON against the schema; on failure it appends the validation error to the prompt and retries (default 3 attempts).

**Q8. `outputType` vs `outputJsonSchema`?**
Derive the schema from a Java type, or pass a ready schema string. Mutually exclusive.

**Q9. What is the easy way to turn on validation?**
`entity(MyType.class, spec -> spec.validateSchema())`.

**Q10. Why do we need `UsageAccumulator`?**
A recursive advisor makes several model calls; without accumulation only the last call's token usage would be reported.

**Q11. How do you use `UsageAccumulator`?**
Create one per `adviseCall` (or per subscription), call `addRoundResponse(...)` after each round, then `applyAccumulatedUsage(finalResponse)`.

**Q12. Which classes do the token math?**
`UsageAccumulator` wraps `org.springframework.ai.support.UsageCalculator` (`accumulateResponseUsage`, `withUsage`).

**Q13. How do recursive advisors differ from 1.x tool loops?**
In 1.x each `ChatModel` had a hidden internal loop. In 2.0 the loop is an advisor in the chain, so memory, observability, and custom logic compose with it.

**Q14. What risks does recursion add?**
Infinite loops and cost growth. Always set a max number of attempts and track cumulative usage.

**Q15. Can recursive advisors repeat advisors placed before them?**
No. `copy(this)` contains only advisors after the recursive advisor.
