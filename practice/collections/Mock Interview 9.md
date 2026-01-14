## **Java Collections – Mock Interview: Round 9**

### **Theme**: Microservices + Cloud Patterns + Resilience + High-Throughput Collection Handling

---

### **Q1. [Failover Routing Table]**

**Scenario**: You have a microservice that dynamically updates its failover routing table to redirect requests to secondary instances during outages.

**Question**:

How would you maintain a consistent, thread-safe routing table using Java collections that supports real-time updates and fast lookups?

---

### **Q2. [Distributed Cache Invalidation Tracker]**

**Scenario**: Your service caches product details but must track and invalidate product IDs updated in other nodes within the last 5 minutes.

**Question**:

Which Java collection would you use to store and expire invalidation keys efficiently? How would you handle memory leaks?

---

### **Q3. [Graceful Degradation Based on Load]**

**Scenario**: Your service logs incoming request timestamps and wants to **deny access if requests exceed X per second**, but allow after cooldown.

**Question**:

How can Java Collections help track request timestamps with sliding window logic to enforce throttling?

---

### **Q4. [Request Idempotency Token Storage]**

**Scenario**: You need to store incoming request IDs (UUIDs) for 10 minutes to prevent reprocessing of duplicate requests.

**Question**:

What collection would be ideal for expiring these entries and allowing constant time lookup?

---

### **Q5. [Feature Toggle Registry per Tenant]**

**Scenario**: In a SaaS platform, different tenants can have different feature toggles enabled.

**Question**:

How would you design a Collection-backed structure to efficiently store, fetch, and update toggles per tenant?

---

### **Q6. [Dynamic Circuit Breaker Metrics]**

**Scenario**: You maintain response time metrics for each downstream service to trigger circuit breakers.

**Question**:

How can Java collections be used to collect and aggregate these metrics in a performant and thread-safe way?

---

### **Q7. [Hierarchical Access Control List]**

**Scenario**: Permissions are assigned at Org → Project → Resource level. You need to resolve effective permissions quickly.

**Question**:

How would you model this hierarchy with nested Java collections? What collection types would you pick and why?

---

### **Q8. [Real-Time Order Book (Trading System)]**

**Scenario**: In a trading engine, you have buy/sell orders at various price points. You need to match them in real-time by price priority.

**Question**:

How would you use Java Collections to efficiently manage, insert, and match orders?

---

### **Q9. [Bulk Configuration Loader]**

**Scenario**: Your microservice loads configs from multiple sources: env vars, DB, and remote config server. Precedence order matters.

**Question**:

How can Java Collections be used to model this precedence chain? How would you structure the lookup logic?

---

### **Q10. [Correlation Id Chain Tracking]**

**Scenario**: Across microservices, you want to track correlation IDs with their full path of services visited.

**Question**:

Which collection(s) would you use to model the path of service hops per request, ensuring thread isolation and propagation?
