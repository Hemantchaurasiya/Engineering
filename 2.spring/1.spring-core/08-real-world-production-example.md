# 08 — Real-World Production Example: Payment Processing Module

Goal: one continuous, realistic example (not toy Dog/Pedigree) that uses **every concept** from files 01-06 together, the way it'd actually look in a Spring Boot microservice.

**Scenario:** An e-commerce `order-service` needs to process payments through **multiple** payment gateways (Credit Card, UPI, Wallet), pick the right one at runtime, generate a per-order **stateful** receipt builder (Prototype), reuse a **stateless** shared retry-policy bean (Singleton), and load different gateway credentials per environment (dev/prod).

## 1) The contract (interface) — program to abstraction

```java
public interface PaymentGateway {
    PaymentResult charge(BigDecimal amount, String orderId);
}
```

## 2) Multiple implementations + `@Qualifier` (Constructor Injection, the recommended style)

```java
@Service("creditCardGateway")
public class CreditCardGateway implements PaymentGateway {

    private final RetryPolicy retryPolicy;          // Singleton, stateless, shared

    @Autowired
    public CreditCardGateway(RetryPolicy retryPolicy) {
        this.retryPolicy = retryPolicy;
    }

    @Override
    public PaymentResult charge(BigDecimal amount, String orderId) {
        return retryPolicy.executeWithRetry(() -> {
            // real call to card processor (Stripe/Razorpay SDK etc.)
            return new PaymentResult(true, "CC-" + orderId);
        });
    }
}

@Service("upiGateway")
public class UpiGateway implements PaymentGateway {
    private final RetryPolicy retryPolicy;

    @Autowired
    public UpiGateway(RetryPolicy retryPolicy) { this.retryPolicy = retryPolicy; }

    @Override
    public PaymentResult charge(BigDecimal amount, String orderId) {
        return retryPolicy.executeWithRetry(() -> new PaymentResult(true, "UPI-" + orderId));
    }
}

@Service("walletGateway")
@Primary                                              // default gateway if caller doesn't specify one
public class WalletGateway implements PaymentGateway {
    private final RetryPolicy retryPolicy;

    @Autowired
    public WalletGateway(RetryPolicy retryPolicy) { this.retryPolicy = retryPolicy; }

    @Override
    public PaymentResult charge(BigDecimal amount, String orderId) {
        return retryPolicy.executeWithRetry(() -> new PaymentResult(true, "WALLET-" + orderId));
    }
}
```

## 3) Singleton, stateless shared bean — `RetryPolicy`

```java
@Component                     // default scope = Singleton — safe here because it holds NO mutable state
public class RetryPolicy {
    private static final int MAX_ATTEMPTS = 3;

    public PaymentResult executeWithRetry(Supplier<PaymentResult> action) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return action.get();
            } catch (TransientGatewayException e) {
                if (attempt == MAX_ATTEMPTS) throw e;
            }
        }
        throw new IllegalStateException("unreachable");
    }
}
```

## 4) Prototype bean — per-order stateful receipt builder

Each order needs its **own** receipt-building state (line items, discounts applied incrementally) — sharing one instance across orders would corrupt data. This is exactly the "class holds state → use Prototype" rule from file 04.

```java
@Component
@Scope(value = "prototype", proxyMode = ScopedProxyMode.TARGET_CLASS)   // scoped-proxy trick from file 07 Q28
public class ReceiptBuilder {
    private final List<String> lines = new ArrayList<>();     // mutable, per-order state

    public ReceiptBuilder addLine(String line) {
        lines.add(line);
        return this;
    }
    public String build() { return String.join("\n", lines); }
}
```

## 5) The orchestrating Singleton service — correctly consuming a Prototype

```java
@Service
public class OrderCheckoutService {

    private final Map<String, PaymentGateway> gatewaysByName;   // Spring auto-collects ALL PaymentGateway beans into a Map keyed by bean name!
    private final ObjectFactory<ReceiptBuilder> receiptBuilderFactory;   // fixes the Prototype-into-Singleton trap (file 04)

    @Autowired
    public OrderCheckoutService(Map<String, PaymentGateway> gatewaysByName,
                                 ObjectFactory<ReceiptBuilder> receiptBuilderFactory) {
        this.gatewaysByName = gatewaysByName;
        this.receiptBuilderFactory = receiptBuilderFactory;
    }

    public String checkout(String orderId, BigDecimal amount, String preferredGateway) {
        PaymentGateway gateway = gatewaysByName.getOrDefault(preferredGateway, gatewaysByName.get("walletGateway"));
        PaymentResult result = gateway.charge(amount, orderId);

        ReceiptBuilder receipt = receiptBuilderFactory.getObject();   // FRESH instance every checkout call
        receipt.addLine("Order: " + orderId)
               .addLine("Amount: " + amount)
               .addLine("Status: " + result.success());

        return receipt.build();
    }
}
```

> **Note:** Spring has a built-in trick used above — if you `@Autowired` a `Map<String, SomeInterface>`, Spring auto-populates it with **every bean of that type**, keyed by bean name. This is a very common real-world pattern for "strategy pattern" style gateway/handler selection (`@Qualifier`-by-string-lookup at runtime, instead of a hardcoded `switch`).

## 6) Environment-specific credentials — `@Profile` + `@ConfigurationProperties`

`application-dev.properties`
```properties
gateway.credit-card.api-key=sandbox-key-123
gateway.credit-card.base-url=https://sandbox.cardprocessor.com
```

`application-prod.properties`
```properties
gateway.credit-card.api-key=${CC_API_KEY}
gateway.credit-card.base-url=https://api.cardprocessor.com
```

```java
@Configuration
@ConfigurationProperties(prefix = "gateway.credit-card")
public class CreditCardGatewayProperties {
    private String apiKey;
    private String baseUrl;
    // getters/setters
}
```

## 7) Wiring it together — what actually happens at startup (tie-back to file 01)

1. `AnnotationConfigApplicationContext` (or Spring Boot's auto-configured one) starts.
2. `@ComponentScan` discovers `CreditCardGateway`, `UpiGateway`, `WalletGateway`, `RetryPolicy`, `ReceiptBuilder`, `OrderCheckoutService`, `CreditCardGatewayProperties` and registers their `BeanDefinition`s.
3. Active profile (`dev`/`prod`) determines which `application-{profile}.properties` gets merged in.
4. Because `ApplicationContext` eagerly creates Singletons: `RetryPolicy` is created first (no deps), then the 3 gateways (each needs `RetryPolicy` — already available), then `OrderCheckoutService` (needs the `Map<String, PaymentGateway>` — all 3 gateways already created, and `ObjectFactory<ReceiptBuilder>` — a lazy handle, not an eager instance, so `ReceiptBuilder` itself is **not** created yet).
5. `ReceiptBuilder` (Prototype) is only actually instantiated the first time `checkout()` is called — and a **new** one every single time, correctly, thanks to `ObjectFactory`.
6. If `RetryPolicy` had accidentally been declared `@Scope("prototype")` and injected via a plain field into the Singleton gateways, they'd all be stuck sharing the **first** `RetryPolicy` instance forever (Case-4 trap from file 04) — harmless here since it's stateless, but would be a serious bug if `RetryPolicy` held mutable counters.

This single module demonstrates: SRP + interface-based design (file 01), `@Component`/`@Service`/constructor injection (files 02-03), `@Primary` + `@Qualifier`-style map injection (file 03), Singleton vs Prototype + the injection trap and its fix (file 04), `@Profile` + `@ConfigurationProperties` for environment-based config (file 05), and the full bean lifecycle ordering (file 06) — exactly the kind of design decisions a 5-10 yr interview expects you to explain, not just define.
