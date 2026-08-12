# Pattern 19: Human-in-the-Loop (HITL)

This is the final safety net for everything agentic we've covered — ReAct agents (11), planning (13), multi-agent systems (14), and tool calling (5) can all take real-world actions with real consequences: sending emails, charging cards, deleting records, deploying code. HITL pauses execution before a consequential action, presents it for explicit human approval, and only proceeds once approved — turning an autonomous agent into a supervised one for the actions that matter.

---

# What's happening

Instead of executing every tool call immediately (as in Pattern 5/11), you intercept high-risk tool invocations, persist them in a **PENDING_APPROVAL** state, and return control to the human before the real side effect happens. Once a human approves (via an API call, dashboard click, Slack button, etc.), the actual action executes. This is essentially Pattern 5's tool calling, but with a risk gate and an async approval step wedged between **"model decides"** and **"action executes."**

---

# Code

## 1. The approval request model

```java
package com.example.genai.pattern19;

import java.time.Instant;

public enum ApprovalStatus { PENDING, APPROVED, REJECTED, EXECUTED }

public record ApprovalRequest(
        String id,
        String actionDescription,
        String toolName,
        String toolArgumentsJson,
        ApprovalStatus status,
        Instant createdAt
) {}
```

---

## 2. A registry that holds pending approvals and lets you resolve them

```java
package com.example.genai.pattern19;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ApprovalRegistry {

    // In production: back this with a database table, not an in-memory map
    private final Map<String, ApprovalRequest> pending = new ConcurrentHashMap<>();

    public ApprovalRequest create(String actionDescription, String toolName, String argsJson) {
        String id = UUID.randomUUID().toString();
        ApprovalRequest request = new ApprovalRequest(
                id, actionDescription, toolName, argsJson, ApprovalStatus.PENDING, Instant.now());
        pending.put(id, request);
        return request;
    }

    public ApprovalRequest get(String id) {
        ApprovalRequest request = pending.get(id);
        if (request == null) throw new IllegalArgumentException("Unknown approval id: " + id);
        return request;
    }

    public ApprovalRequest resolve(String id, boolean approved) {
        ApprovalRequest existing = get(id);
        ApprovalRequest updated = new ApprovalRequest(
                existing.id(), existing.actionDescription(), existing.toolName(),
                existing.toolArgumentsJson(),
                approved ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED,
                existing.createdAt());
        pending.put(id, updated);
        return updated;
    }

    public void markExecuted(String id) {
        ApprovalRequest existing = get(id);
        pending.put(id, new ApprovalRequest(
                existing.id(), existing.actionDescription(), existing.toolName(),
                existing.toolArgumentsJson(), ApprovalStatus.EXECUTED, existing.createdAt()));
    }
}
```

---

## 3. Risk-gated tools — high-risk actions create an approval request instead of executing immediately

```java
package com.example.genai.pattern19;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class GatedAccountTools {

    private final ApprovalRegistry approvalRegistry;
    private final RefundExecutor refundExecutor; // your actual side-effecting logic

    public GatedAccountTools(ApprovalRegistry approvalRegistry, RefundExecutor refundExecutor) {
        this.approvalRegistry = approvalRegistry;
        this.refundExecutor = refundExecutor;
    }

    @Tool(description = "Look up a customer's account balance — safe, read-only")
    public double getAccountBalance(@ToolParam(description = "Customer account ID") String accountId) {
        return 154.30; // stubbed read-only lookup, executes immediately, no approval needed
    }

    @Tool(description = "Issue a refund to a customer. This requires human approval before executing.")
    public String requestRefund(
            @ToolParam(description = "Customer account ID") String accountId,
            @ToolParam(description = "Refund amount in USD") double amount,
            @ToolParam(description = "Reason for the refund") String reason) {

        // Don't execute the refund — create a pending approval instead
        ApprovalRequest request = approvalRegistry.create(
                "Refund $%.2f to account %s. Reason: %s".formatted(amount, accountId, reason),
                "requestRefund",
                """
                {"accountId": "%s", "amount": %.2f, "reason": "%s"}
                """.formatted(accountId, amount, reason));

        return "This refund requires approval. Approval ID: " + request.id() +
               ". It will not be processed until a human approves it.";
    }
}
```

---

## 4. Approval endpoints — where a human actually approves/rejects

```java
package com.example.genai.pattern19;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalRegistry approvalRegistry;
    private final RefundExecutor refundExecutor;

    public ApprovalController(ApprovalRegistry approvalRegistry, RefundExecutor refundExecutor) {
        this.approvalRegistry = approvalRegistry;
        this.refundExecutor = refundExecutor;
    }

    @GetMapping("/{id}")
    public ApprovalRequest getApproval(@PathVariable String id) {
        return approvalRegistry.get(id);   // human reviews this before deciding
    }

    @PostMapping("/{id}/decide")
    public ApprovalRequest decide(@PathVariable String id, @RequestParam boolean approved) {

        ApprovalRequest resolved = approvalRegistry.resolve(id, approved);

        if (approved && resolved.status() == ApprovalStatus.APPROVED) {
            // Only now does the real side effect actually happen
            refundExecutor.executeFromApproval(resolved);
            approvalRegistry.markExecuted(id);
        }

        return approvalRegistry.get(id);
    }
}
```

---

## 5. Wiring the agent up with the gated tools

```java
@Configuration
public class GatedAgentConfig {

    @Bean
    public ChatClient supportAgentClient(ChatClient.Builder builder, GatedAccountTools tools) {
        return builder.clone()
                .defaultSystem("""
                        You are a customer support agent. You can check balances freely.
                        For refunds, explain to the customer that approval is required
                        and share the approval ID so they know it's being processed.
                        """)
                .defaultTools(tools)
                .build();
    }
}
```

---

# Try it end-to-end

```bash
# 1. Agent call — refund is requested but NOT executed
curl "http://localhost:8080/api/agent/chat?message=Please%20refund%20%2450%20to%20account%20A123%2C%20item%20arrived%20damaged"
# → "I've requested a $50 refund for account A123. Approval ID: 7f3e...
#     It will be processed once approved."

# 2. Human reviews it
curl "http://localhost:8080/api/approvals/7f3e..."

# 3. Human approves — only NOW does the refund actually execute
curl -X POST "http://localhost:8080/api/approvals/7f3e.../decide?approved=true"
```

---

# Deciding what needs approval

```java
public enum RiskLevel { LOW, MEDIUM, HIGH }

// A simple static mapping is often enough — reserve LLM judgment for genuinely ambiguous cases
private static final Map<String, RiskLevel> TOOL_RISK = Map.of(
        "getAccountBalance", RiskLevel.LOW,     // read-only, always auto-execute
        "requestRefund", RiskLevel.HIGH,        // financial, always require approval
        "sendCustomerEmail", RiskLevel.MEDIUM   // could auto-approve under some threshold
);
```

A common refinement: auto-approve below a threshold (e.g. refunds under **$20**) and require human approval only above it — full HITL on every action doesn't scale, so gate it to genuinely consequential ones.

---

# Production notes

- Persist approval state in a real database, not in-memory — a server restart shouldn't lose track of pending high-stakes actions.

- Notify humans actively (Slack webhook, email, dashboard alert) rather than requiring them to poll — the whole point is fast human response without the agent silently stalling.

- Add expiry — a refund approval sitting pending for a week is often no longer valid; expire stale requests rather than letting them execute unexpectedly later.

- Audit trail is essential — log who approved what, when, and why; this is often a compliance requirement once agents touch money, PII, or infrastructure.

- Combine with Pattern 18's guardrails: even approved actions should still pass through output/safety checks — human approval isn't a substitute for validating the action's parameters are sane.