## **Java Collections – Mock Interview: Round 8**

### **Theme**: Distributed Thinking + Optimization + Debugging + Real-World Systems

---

### **Q1. [Multi-Tenant Cache Eviction]**

**Scenario**: You manage an in-memory cache for multiple clients (tenants). Each tenant has a fixed quota of max 1000 items. Eviction should happen per-tenant, not globally.

**Question**:

How would you structure your collections to enforce eviction rules per tenant without affecting others?

---

### **Q2. [Duplicate Detection in Distributed Uploads]**

**Scenario**: Your file upload system receives chunks of files from distributed nodes. You must detect duplicates in real-time across all chunks using a rolling time window of 15 minutes.

**Question**:

How would you design this using Java Collections with memory efficiency and concurrency in mind?

---

### **Q3. [Circular Dependency Graph with Rollbacks]**

**Scenario**: You're executing operations that depend on each other. If a cycle is detected mid-execution, you must rollback previous steps in reverse order.

**Question**:

Which Java collections would you use to model dependencies, detect cycles, and manage rollback?

---

### **Q4. [Analytics Snapshot with Immutable Layers]**

**Scenario**: You collect daily analytics and need to take a snapshot of metrics every 24 hours. Snapshots must be immutable and previous snapshots must be quickly retrievable.

**Question**:

What collection design would allow immutability, fast read access, and minimal memory overhead?

---

### **Q5. [Misuse of HashMap in Production]**

**Scenario**: Your team used a `HashMap` with mutable keys (custom objects). Over time, critical data is missing or misrouted.

**Question**:

What mistake might have been made here? How can you prevent such bugs?

---

### **Q6. [Real-Time Keyword Frequency Ranking]**

**Scenario**: In a streaming chat app, users constantly send messages. You must display the **top 10 most used words** every second.

**Question**:

Which collection(s) would you use to update counts and maintain a real-time ranking with high throughput?

---

### **Q7. [Immutable Entity Access with Fallback]**

**Scenario**: You load `UserProfile` objects from cache. If not found, fall back to DB. Once loaded, objects must not be modified.

**Question**:

How do you enforce immutability using Collections? What pitfalls should you avoid with shared references?

---

### **Q8. [Distributed Lock Coordination Map]**

**Scenario**: Multiple nodes in a distributed system need to coordinate locks for critical resources. Each node must know who holds which lock.

**Question**:

How would you model this with Java Collections to support concurrency and real-time visibility?

---

### **Q9. [Stream Collector Debugging]**

**Scenario**: A developer uses `.collect(Collectors.toMap())` and gets `IllegalStateException: Duplicate key`.

**Question**:

Why does this happen? How do you fix it safely without data loss?

---

### **Q10. [Blocking Retry Queue for Failed Events]**

**Scenario**: Events failing to process are sent to a retry queue. A background thread retries them after a delay. You need a **blocking and delay-aware** queue.

**Question**:

What Collection or class from Java would you choose, and how is it different from a standard queue?
