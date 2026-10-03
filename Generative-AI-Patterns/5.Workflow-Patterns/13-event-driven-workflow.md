# 13. Event-Driven Workflow

## 13.1 What is it?

An **Event-Driven Workflow** doesn't wait for a direct client request the way
every previous pattern in this series has — instead, it's **triggered by an
event arriving from elsewhere** (a message queue, a webhook, another
system's "something happened" notification), and its job is often to react
by **publishing new events** of its own, rather than calling other systems
directly.

This is a different *triggering and integration style*, not just a different
graph shape. Earlier patterns assumed "a client calls this function and
waits for a result." An Event-Driven Workflow assumes "something happened
somewhere else, a message queue delivered it to us, and other systems are
waiting on messages *we* publish in response" — a decoupled, asynchronous
relationship between systems (often called **choreography**, since no single
system directs the whole process).

## 13.2 What problem does it solve?

In a system made of many independent services (inventory, orders, shipping,
notifications), having every service call every other service directly
creates a tangled web of dependencies — the inventory service would need to
know about, and call, every single system that might care about a stock
change. If a new team adds a new system that also needs to react to
"stock low" moments, every existing publisher would need to be updated to
call it too.

The Event-Driven Workflow pattern solves this by:

- **Decoupling producers from consumers** — a system that experiences
  something (like low stock) just publishes an event; it doesn't need to
  know or care who's listening.
- Letting a workflow **react to real-world happenings** it doesn't control
  the timing of, instead of being invoked on a fixed schedule or a direct
  request.
- Making it easy to **add new event types over time** without breaking
  existing processing — an unrecognized event type should be safely ignored,
  not crash the system.
- Allowing one triggering event to **fan out into further events**, letting
  other independent systems pick up the next step whenever they're ready.

## 13.3 Realistic production example: Warehouse Event Processor

A logistics platform (`WarehouseIQ`) has many independent systems
communicating purely through events on a shared message bus. This workflow
is one **consumer** on that bus, reacting to two event types (with graceful
handling for any others):

1. **`inventory.low_stock`** — published by the warehouse system whenever an
   item's count drops below its reorder point. The workflow checks reorder
   policy and, if a reorder is warranted, **publishes a new event**,
   `purchase_order.requested`, for the (completely separate) procurement
   system to pick up whenever it's ready — this workflow never calls that
   system directly.
2. **`shipment.delivered`** — published by the shipping carrier's webhook
   integration. The workflow drafts a friendly delivery confirmation message
   and **publishes** `customer.notify`, for the (again, separate)
   notification system to actually send.
3. **Any other event type** — safely logged and ignored. New event types get
   added to the platform regularly as other teams build new features; this
   workflow only needs to handle the ones it actually cares about, not
   crash on the rest.

## 13.4 Architecture / Flow Diagram

```mermaid
flowchart TD
    Bus([Message Bus: Incoming Event]) --> Dispatch{event_type?}
    Dispatch -->|inventory.low_stock| A[Handle Low Stock]
    Dispatch -->|shipment.delivered| B[Handle Shipment Delivered]
    Dispatch -->|anything else| C[Ignore Unknown Event Type]

    A -->|publishes| Out1[[purchase_order.requested]]
    B -->|publishes| Out2[[customer.notify]]

    A --> End([Processing Complete])
    B --> End
    C --> End

    style Dispatch fill:#F3E8FF,stroke:#8B5CF6
    style A fill:#DCEEFB,stroke:#3B82F6
    style B fill:#DCEEFB,stroke:#3B82F6
    style C fill:#FEF9C3,stroke:#EAB308
    style Out1 fill:#DCFCE7,stroke:#22C55E,stroke-dasharray: 5 5
    style Out2 fill:#DCFCE7,stroke:#22C55E,stroke-dasharray: 5 5
```

The dashed boxes represent events **published outward** to the message bus —
this workflow doesn't know or care which system(s) eventually consume them,
which is the essence of the decoupled, event-driven style.

## 13.5 Request-to-response flow, step by step

1. A message-queue consumer (outside this workflow entirely — e.g., an SQS
   or Kafka listener) receives a raw event and calls `process_event(event)`.
2. LangGraph builds the initial `EventState` from the event's `event_type`
   and `payload`, and enters at the dispatch point.
3. A **conditional edge from `START`** reads `event_type` and routes to
   `handle_low_stock`, `handle_shipment_delivered`, or
   `handle_unknown_event` — this is structurally similar to Pattern 5
   (Routing), but the thing being routed is an **external event**, not a
   customer's free-text message.
4. **`handle_low_stock`** applies reorder policy (quantity on hand vs.
   reorder point) and, if warranted, appends a
   `purchase_order.requested` event — with the relevant details — onto
   `state.outbound_events`.
5. **`handle_shipment_delivered`** makes an LLM call to draft a short
   delivery confirmation message, then appends a `customer.notify` event
   (containing that message) onto `state.outbound_events`.
6. **`handle_unknown_event`** simply logs that an unrecognized event type
   arrived and does nothing else — this workflow doesn't own every event
   type on the bus, and that's expected and fine.
7. After the handler node runs, `finalize_and_publish` reads
   `state.outbound_events` and (in this demo) prints each one — in
   production, this would be where each event is actually published to the
   real message bus (Kafka, SQS, etc.) for other systems to consume
   independently.
8. The graph reaches `END`. The caller (the message-queue consumer) simply
   acknowledges the original event as processed.

## 13.6 Why this pattern fits this problem

- **No direct coupling to procurement or notification systems** — this
  workflow publishes events and moves on; it never needs to know how
  procurement processes a purchase order request or how notifications
  actually get sent, and those systems can change independently.
- **New consumers can subscribe to `purchase_order.requested` or
  `customer.notify` later without this workflow changing at all** — that's
  the core benefit of choreography over direct service-to-service calls.
- **Gracefully ignoring unknown event types is essential on a shared bus** —
  a production event bus typically carries many event types from many teams;
  a consumer that crashes on an event type it doesn't recognize would be a
  serious reliability problem.
- **The workflow reacts on its own schedule**, driven entirely by when
  events actually arrive — there's no "polling" or fixed schedule involved,
  which keeps the system responsive without wasted work.

## 13.7 Production-quality implementation

```python
"""
Event-Driven Workflow — Warehouse Event Processor
Pattern: triggered by an external event, dispatches by event type, and
reacts by PUBLISHING new events rather than calling other systems directly

Run:
    pip install "langgraph==1.2.10" "langchain==1.3.14" "langchain-ollama==1.1.0" "pydantic==2.9.2"
    ollama pull llama3.1:8b     # make sure Ollama is running locally
    python warehouse_event_processor.py
"""

from __future__ import annotations

import logging
from typing import Optional

from langchain_ollama import ChatOllama
from langgraph.graph import StateGraph, START, END
from pydantic import BaseModel

# --------------------------------------------------------------------------
# 1. Logging
# --------------------------------------------------------------------------
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
)
logger = logging.getLogger("warehouse_event_processor")


# --------------------------------------------------------------------------
# 2. Shared state
# --------------------------------------------------------------------------
class EventState(BaseModel):
    event_type: str = ""
    payload: dict = {}

    outbound_events: list[dict] = []
    processing_status: Optional[str] = None


_llm = ChatOllama(model="llama3.1:8b", temperature=0.3)

_REORDER_POINT = 20  # units


# --------------------------------------------------------------------------
# 3. Dispatch function — routes purely by event_type.
#    An unrecognized event type routes to a safe no-op handler instead of
#    erroring, since this consumer doesn't own every event type on the bus.
# --------------------------------------------------------------------------
def dispatch_event(state: EventState) -> str:
    return {
        "inventory.low_stock": "handle_low_stock",
        "shipment.delivered": "handle_shipment_delivered",
    }.get(state.event_type, "handle_unknown_event")


# --------------------------------------------------------------------------
# 4. Handler — inventory.low_stock
#    Applies deterministic reorder policy, and PUBLISHES a new event rather
#    than calling the procurement system directly.
# --------------------------------------------------------------------------
def handle_low_stock(state: EventState) -> dict:
    sku = state.payload.get("sku", "unknown")
    quantity_on_hand = state.payload.get("quantity_on_hand", 0)
    logger.info("EVENT — inventory.low_stock for %s (qty=%d)", sku, quantity_on_hand)

    if quantity_on_hand < _REORDER_POINT:
        outbound_event = {
            "event_type": "purchase_order.requested",
            "payload": {
                "sku": sku,
                "requested_quantity": 100,  # simplified: fixed reorder quantity
                "reason": f"Stock ({quantity_on_hand}) below reorder point ({_REORDER_POINT}).",
            },
        }
        return {
            "outbound_events": state.outbound_events + [outbound_event],
            "processing_status": "reorder_requested",
        }

    return {"processing_status": "no_action_needed"}


# --------------------------------------------------------------------------
# 5. Handler — shipment.delivered
#    Drafts a message (LLM) and PUBLISHES a customer.notify event rather
#    than sending the notification itself.
# --------------------------------------------------------------------------
_DELIVERY_MESSAGE_PROMPT = """Write a short, friendly one-sentence delivery
confirmation message for a customer.

Order ID: {order_id}
Delivered at: {delivered_at}
"""


def handle_shipment_delivered(state: EventState) -> dict:
    order_id = state.payload.get("order_id", "unknown")
    delivered_at = state.payload.get("delivered_at", "just now")
    logger.info("EVENT — shipment.delivered for order %s", order_id)

    try:
        response = _llm.invoke(
            _DELIVERY_MESSAGE_PROMPT.format(order_id=order_id, delivered_at=delivered_at)
        )
        message = response.content.strip()
    except Exception as exc:  # noqa: BLE001
        logger.error("handle_shipment_delivered LLM call failed: %s", exc)
        message = f"Your order {order_id} has been delivered!"

    outbound_event = {
        "event_type": "customer.notify",
        "payload": {"order_id": order_id, "message": message},
    }
    return {
        "outbound_events": state.outbound_events + [outbound_event],
        "processing_status": "notification_queued",
    }


# --------------------------------------------------------------------------
# 6. Handler — anything else. Log and move on; do NOT raise.
# --------------------------------------------------------------------------
def handle_unknown_event(state: EventState) -> dict:
    logger.warning("EVENT — unrecognized event_type '%s', ignoring", state.event_type)
    return {"processing_status": "ignored_unknown_event"}


# --------------------------------------------------------------------------
# 7. Finalize — this is where outbound_events would actually be published
#    to the real message bus (Kafka, SQS, etc.). Simulated here as a print.
# --------------------------------------------------------------------------
def finalize_and_publish(state: EventState) -> dict:
    for event in state.outbound_events:
        # In production: message_bus.publish(event["event_type"], event["payload"])
        logger.info("PUBLISH — %s: %s", event["event_type"], event["payload"])
    return {}


# --------------------------------------------------------------------------
# 8. Build the graph — dispatch by event type, each handler may publish,
#    all paths converge to finalize_and_publish.
# --------------------------------------------------------------------------
def build_graph():
    graph = StateGraph(EventState)

    graph.add_node("handle_low_stock", handle_low_stock)
    graph.add_node("handle_shipment_delivered", handle_shipment_delivered)
    graph.add_node("handle_unknown_event", handle_unknown_event)
    graph.add_node("finalize_and_publish", finalize_and_publish)

    graph.add_conditional_edges(
        START,
        dispatch_event,
        {
            "handle_low_stock": "handle_low_stock",
            "handle_shipment_delivered": "handle_shipment_delivered",
            "handle_unknown_event": "handle_unknown_event",
        },
    )

    for handler in ("handle_low_stock", "handle_shipment_delivered", "handle_unknown_event"):
        graph.add_edge(handler, "finalize_and_publish")

    graph.add_edge("finalize_and_publish", END)

    return graph.compile()


# --------------------------------------------------------------------------
# 9. Public entry point — this is what a message-queue consumer loop calls
#    for every event it receives off the bus.
# --------------------------------------------------------------------------
def process_event(event_type: str, payload: dict) -> dict:
    app = build_graph()
    initial_state = EventState(event_type=event_type, payload=payload)
    final_state = app.invoke(initial_state)
    return final_state


# --------------------------------------------------------------------------
# 10. Demo — simulates a few events arriving off a message bus
# --------------------------------------------------------------------------
if __name__ == "__main__":
    events = [
        ("inventory.low_stock", {"sku": "SKU-4471", "quantity_on_hand": 12}),
        ("shipment.delivered", {"order_id": "ORD-8820", "delivered_at": "2026-08-14 14:32"}),
        ("warehouse.temperature_alert", {"zone": "B4", "temp_c": 31}),  # unrecognized type
    ]

    for event_type, payload in events:
        result = process_event(event_type, payload)
        print(f"[{event_type}] status={result['processing_status']}, "
              f"published={len(result['outbound_events'])} event(s)\n")
```

**Notes on production-readiness choices made above:**

- **Handlers publish events, they don't call other systems** — the
  distinction is deliberate: `handle_low_stock` never imports or calls
  procurement code directly, keeping this workflow's only real dependency on
  the message bus itself.
- **`handle_unknown_event` never raises** — this is essential for any
  consumer on a shared event bus, since other teams will add new event types
  over time that this workflow was never designed to handle, and a crash on
  an unrecognized type would be a serious production incident.
- **`finalize_and_publish` is the single place outbound events actually
  leave the system** — centralizing this (rather than publishing directly
  from inside each handler) makes it easy to add cross-cutting concerns
  later (e.g., event schema validation, adding a trace ID to every outbound
  event) in one place.
- **Each handler is independently testable** with a fixed `payload`, without
  needing a real message bus running — you can assert
  `handle_low_stock(state)` produces the right `outbound_events` entry
  completely offline.

---

⬅ [12. Human Approval Workflow](12-human-approval-workflow.md) | [Back to index](README.md) | Next: [14. Async Workflow](14-async-workflow.md) ➡

# Warehouse Event Processor — Event-Driven Workflow (Java + Spring AI)

A Java port of the LangGraph event-driven workflow — triggered by an external event, dispatched
by event type, and reacting by **publishing new events** rather than calling other systems
directly. Built on:

- **Java 25** (current LTS)
- **Spring Boot 4.1.0**
- **Spring AI 2.0.0** (GA) with the **Ollama** starter, mirroring `llama3.1:8b`

## Mapping the shape

Two things distinguish this from the earlier routing/branching ports: the dispatch key is an
**open-ended string** from an external bus (not a fixed enum Java could exhaustively switch over),
and an unrecognized type must be handled gracefully rather than erroring — this consumer doesn't
own every event type on the bus. That rules out a `sealed`/`enum` `switch` as the dispatch
mechanism (there's no way to enumerate "every possible external event type" at compile time), so
a `Map<String, EventHandler>` with an explicit fallback is the more honest translation of Python's
`dict.get(state.event_type, "handle_unknown_event")`.

| LangGraph concept | Spring / Java equivalent |
|---|---|
| `dispatch_event(state) -> str` via `dict.get(..., "handle_unknown_event")` | `Map<String, EventHandler>` lookup with `.getOrDefault(eventType, unknownEventHandler)` |
| `handle_low_stock` / `handle_shipment_delivered` / `handle_unknown_event` | `LowStockHandler` / `ShipmentDeliveredHandler` / `UnknownEventHandler`, all implementing `EventHandler` |
| `state.outbound_events + [outbound_event]` (event accumulation) | `List<OutboundEvent>` returned from each handler, collected by the orchestrator |
| `finalize_and_publish` (all paths converge here) | `EventPublisher.publish(...)`, called once after the handler returns |
| `message_bus.publish(...)` comment | A `EventPublisher` interface, with a logging implementation standing in for a real Kafka/SQS producer |

---

## Project structure

```
warehouse-event-processor/
├── pom.xml
└── src/main/java/com/example/warehouseevents/
    ├── WarehouseEventProcessorApplication.java
    ├── model/
    │   ├── InboundEvent.java
    │   ├── OutboundEvent.java
    │   └── EventProcessingResult.java
    ├── handler/
    │   ├── EventHandler.java
    │   ├── LowStockHandler.java
    │   ├── ShipmentDeliveredHandler.java
    │   └── UnknownEventHandler.java
    ├── publish/
    │   ├── EventPublisher.java
    │   └── LoggingEventPublisher.java
    ├── pipeline/
    │   └── EventProcessingService.java
    ├── web/
    │   └── EventProcessingController.java
    └── WarehouseEventProcessorRunner.java   (CLI demo, mirrors the Python __main__ block)
└── src/main/resources/
    └── application.yml
```

---

## `pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>warehouse-event-processor</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <java.version>25</java.version>
        <spring-ai.version>2.0.0</spring-ai.version>
    </properties>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.0</version>
        <relativePath/>
    </parent>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>${spring-ai.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <!-- Ollama model starter — local llama3.1:8b, same as langchain-ollama -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-model-ollama</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

> **Wiring this to a real bus**: in production, `process_event`/`EventProcessingService.process`
> would be called from a message-queue consumer loop rather than an HTTP controller — a Kafka
> `@KafkaListener`, an SQS poller, or similar. The `spring-kafka` / `spring-cloud-aws-sqs`
> starters are the natural additions to `pom.xml` for that; the REST controller included below is
> a stand-in so the workflow is directly runnable/testable without standing up a broker, exactly
> as the Python demo calls `process_event` directly rather than wiring a real consumer.

## `src/main/resources/application.yml`

```yaml
spring:
  application:
    name: warehouse-event-processor
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: llama3.1:8b
          temperature: 0.3

logging:
  level:
    com.example.warehouseevents: INFO
```

---

## Domain model

### `model/InboundEvent.java`

```java
package com.example.warehouseevents.model;

import java.util.Map;

public record InboundEvent(String eventType, Map<String, Object> payload) {}
```

### `model/OutboundEvent.java`

```java
package com.example.warehouseevents.model;

import java.util.Map;

public record OutboundEvent(String eventType, Map<String, Object> payload) {}
```

### `model/EventProcessingResult.java`

```java
package com.example.warehouseevents.model;

import java.util.List;

public record EventProcessingResult(String processingStatus, List<OutboundEvent> outboundEvents) {}
```

---

## Handlers

### `handler/EventHandler.java`

```java
package com.example.warehouseevents.handler;

import com.example.warehouseevents.model.EventProcessingResult;
import com.example.warehouseevents.model.InboundEvent;

public interface EventHandler {
    EventProcessingResult handle(InboundEvent event);
}
```

Unlike the `sealed` interfaces used for dispatch in earlier ports (`OutcomeBranch`, `QueryAgent`),
this one is **not** `sealed` — new event types this service doesn't yet know about are a normal,
expected occurrence on a shared bus, not a closed set the compiler should enumerate. That's the
same reasoning the Python version's own comment gives for routing unknown types to a no-op
handler instead of raising.

### `handler/LowStockHandler.java`

Applies deterministic reorder policy and **publishes** a new event rather than calling the
procurement system directly — the direct analogue of `handle_low_stock`.

```java
package com.example.warehouseevents.handler;

import com.example.warehouseevents.model.EventProcessingResult;
import com.example.warehouseevents.model.InboundEvent;
import com.example.warehouseevents.model.OutboundEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class LowStockHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(LowStockHandler.class);
    private static final int REORDER_POINT = 20; // units

    @Override
    public EventProcessingResult handle(InboundEvent event) {
        String sku = String.valueOf(event.payload().getOrDefault("sku", "unknown"));
        int quantityOnHand = ((Number) event.payload().getOrDefault("quantity_on_hand", 0)).intValue();
        log.info("EVENT - inventory.low_stock for {} (qty={})", sku, quantityOnHand);

        if (quantityOnHand < REORDER_POINT) {
            var outboundEvent = new OutboundEvent(
                    "purchase_order.requested",
                    Map.of(
                            "sku", sku,
                            "requested_quantity", 100, // simplified: fixed reorder quantity
                            "reason", "Stock (%d) below reorder point (%d).".formatted(quantityOnHand, REORDER_POINT)
                    ));
            return new EventProcessingResult("reorder_requested", List.of(outboundEvent));
        }

        return new EventProcessingResult("no_action_needed", List.of());
    }
}
```

### `handler/ShipmentDeliveredHandler.java`

Drafts a message with the LLM and **publishes** a `customer.notify` event rather than sending the
notification itself — the direct analogue of `handle_shipment_delivered`.

```java
package com.example.warehouseevents.handler;

import com.example.warehouseevents.model.EventProcessingResult;
import com.example.warehouseevents.model.InboundEvent;
import com.example.warehouseevents.model.OutboundEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ShipmentDeliveredHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(ShipmentDeliveredHandler.class);

    private static final String DELIVERY_MESSAGE_PROMPT = """
            Write a short, friendly one-sentence delivery confirmation message for a customer.

            Order ID: {orderId}
            Delivered at: {deliveredAt}
            """;

    private final ChatClient chatClient;

    public ShipmentDeliveredHandler(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public EventProcessingResult handle(InboundEvent event) {
        String orderId = String.valueOf(event.payload().getOrDefault("order_id", "unknown"));
        String deliveredAt = String.valueOf(event.payload().getOrDefault("delivered_at", "just now"));
        log.info("EVENT - shipment.delivered for order {}", orderId);

        String message;
        try {
            message = chatClient.prompt()
                    .user(u -> u.text(DELIVERY_MESSAGE_PROMPT)
                            .param("orderId", orderId)
                            .param("deliveredAt", deliveredAt))
                    .call()
                    .content()
                    .strip();
        } catch (Exception e) {
            log.error("handle_shipment_delivered LLM call failed: {}", e.getMessage());
            message = "Your order %s has been delivered!".formatted(orderId);
        }

        var outboundEvent = new OutboundEvent(
                "customer.notify",
                Map.of("order_id", orderId, "message", message));

        return new EventProcessingResult("notification_queued", List.of(outboundEvent));
    }
}
```

### `handler/UnknownEventHandler.java`

Logs and moves on; does **not** raise — the direct analogue of `handle_unknown_event`.

```java
package com.example.warehouseevents.handler;

import com.example.warehouseevents.model.EventProcessingResult;
import com.example.warehouseevents.model.InboundEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UnknownEventHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(UnknownEventHandler.class);

    @Override
    public EventProcessingResult handle(InboundEvent event) {
        log.warn("EVENT - unrecognized event_type '{}', ignoring", event.eventType());
        return new EventProcessingResult("ignored_unknown_event", List.of());
    }
}
```

---

## Publishing

### `publish/EventPublisher.java`

```java
package com.example.warehouseevents.publish;

import com.example.warehouseevents.model.OutboundEvent;

public interface EventPublisher {
    void publish(OutboundEvent event);
}
```

### `publish/LoggingEventPublisher.java`

The direct analogue of `finalize_and_publish`'s simulated `logger.info(...)` — swap this
implementation for a real Kafka/SQS producer in production, same as the Python comment
(`# In production: message_bus.publish(...)`) implies.

```java
package com.example.warehouseevents.publish;

import com.example.warehouseevents.model.OutboundEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingEventPublisher.class);

    @Override
    public void publish(OutboundEvent event) {
        // In production: kafkaTemplate.send(event.eventType(), event.payload());
        log.info("PUBLISH - {}: {}", event.eventType(), event.payload());
    }
}
```

---

## Dispatch and orchestration

### `pipeline/EventProcessingService.java`

The direct analogue of `dispatch_event` plus the graph's convergence into
`finalize_and_publish`.

```java
package com.example.warehouseevents.pipeline;

import com.example.warehouseevents.handler.EventHandler;
import com.example.warehouseevents.handler.LowStockHandler;
import com.example.warehouseevents.handler.ShipmentDeliveredHandler;
import com.example.warehouseevents.handler.UnknownEventHandler;
import com.example.warehouseevents.model.EventProcessingResult;
import com.example.warehouseevents.model.InboundEvent;
import com.example.warehouseevents.publish.EventPublisher;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class EventProcessingService {

    private final Map<String, EventHandler> handlersByEventType;
    private final EventHandler unknownEventHandler;
    private final EventPublisher eventPublisher;

    public EventProcessingService(LowStockHandler lowStockHandler,
                                   ShipmentDeliveredHandler shipmentDeliveredHandler,
                                   UnknownEventHandler unknownEventHandler,
                                   EventPublisher eventPublisher) {
        // dispatch_event's dict, in Java form — an open-ended string key, so a Map
        // rather than an exhaustive enum switch.
        this.handlersByEventType = Map.of(
                "inventory.low_stock", lowStockHandler,
                "shipment.delivered", shipmentDeliveredHandler
        );
        this.unknownEventHandler = unknownEventHandler;
        this.eventPublisher = eventPublisher;
    }

    public EventProcessingResult process(InboundEvent event) {
        EventHandler handler = handlersByEventType.getOrDefault(event.eventType(), unknownEventHandler);
        EventProcessingResult result = handler.handle(event);

        // All paths converge here, exactly like every handler's edge into finalize_and_publish.
        result.outboundEvents().forEach(eventPublisher::publish);

        return result;
    }
}
```

---

## Entry points

### `web/EventProcessingController.java`

Stands in for a real message-queue consumer loop calling `process_event` for every event it
receives off the bus.

```java
package com.example.warehouseevents.web;

import com.example.warehouseevents.model.EventProcessingResult;
import com.example.warehouseevents.model.InboundEvent;
import com.example.warehouseevents.pipeline.EventProcessingService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EventProcessingController {

    private final EventProcessingService eventProcessingService;

    public EventProcessingController(EventProcessingService eventProcessingService) {
        this.eventProcessingService = eventProcessingService;
    }

    @PostMapping("/api/events")
    public EventProcessingResult process(@RequestBody InboundEvent event) {
        return eventProcessingService.process(event);
    }
}
```

### `WarehouseEventProcessorRunner.java` (CLI demo, mirrors the Python `if __name__ == "__main__"` block)

```java
package com.example.warehouseevents;

import com.example.warehouseevents.model.InboundEvent;
import com.example.warehouseevents.pipeline.EventProcessingService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Profile("demo")
public class WarehouseEventProcessorRunner implements CommandLineRunner {

    private final EventProcessingService eventProcessingService;

    public WarehouseEventProcessorRunner(EventProcessingService eventProcessingService) {
        this.eventProcessingService = eventProcessingService;
    }

    @Override
    public void run(String... args) {
        List<InboundEvent> events = List.of(
                new InboundEvent("inventory.low_stock", Map.of("sku", "SKU-4471", "quantity_on_hand", 12)),
                new InboundEvent("shipment.delivered", Map.of("order_id", "ORD-8820", "delivered_at", "2026-08-14 14:32")),
                new InboundEvent("warehouse.temperature_alert", Map.of("zone", "B4", "temp_c", 31)) // unrecognized type
        );

        for (InboundEvent event : events) {
            var result = eventProcessingService.process(event);
            System.out.printf("[%s] status=%s, published=%d event(s)%n%n",
                    event.eventType(), result.processingStatus(), result.outboundEvents().size());
        }
    }
}
```

### `WarehouseEventProcessorApplication.java`

```java
package com.example.warehouseevents;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WarehouseEventProcessorApplication {
    public static void main(String[] args) {
        SpringApplication.run(WarehouseEventProcessorApplication.class, args);
    }
}
```

---

## Running it

```bash
ollama pull llama3.1:8b
ollama serve   # if not already running

# CLI demo (prints status/published-count for all 3 sample events, like the Python script)
mvn spring-boot:run -Dspring-boot.run.profiles=demo

# Or as a service
mvn spring-boot:run
curl -X POST localhost:8080/api/events \
  -H "Content-Type: application/json" \
  -d '{"eventType":"inventory.low_stock","payload":{"sku":"SKU-4471","quantity_on_hand":12}}'
```

## Notes on the port

- **Map dispatch, not a `switch`**: this is the one dispatch-by-category port in the series that
  does *not* use a `sealed` interface + exhaustive `switch`, and that's deliberate — the event
  types come from an external bus this service doesn't fully own, so "exhaustive" isn't a concept
  that applies. A `Map<String, EventHandler>` with an explicit `unknownEventHandler` fallback is
  the direct, honest equivalent of `dict.get(state.event_type, "handle_unknown_event")`, and
  reads correctly to future maintainers as "open set, graceful fallback" rather than "closed set,
  compiler-checked."
- **React by publishing, not by calling**: both `LowStockHandler` and `ShipmentDeliveredHandler`
  build and return `OutboundEvent`s rather than reaching out to procurement or notification
  systems directly — preserving the architectural point the Python version's own comments make
  twice (`# PUBLISHES a new event rather than calling ... directly`).
- **Unknown events never raise**: `UnknownEventHandler` logs a warning and returns a normal
  result, matching the Python docstring's explicit instruction not to raise on an unrecognized
  type — a consumer on a shared bus has to expect event types it doesn't recognize as routine,
  not exceptional.
- **Publishing is pluggable**: `EventPublisher` is an interface with a logging implementation
  standing in for a real broker client, so swapping in `spring-kafka`'s `KafkaTemplate` (or an
  SQS/SNS client) later touches only `LoggingEventPublisher`, not any handler or the dispatch
  service.
- **Versions**: same Ollama-backed `spring-ai-starter-model-ollama` / `llama3.1:8b` setup as the
  other ports in this series.
