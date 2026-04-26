## **Java Collections – Mock Interview: Round 10**

### **Theme**: Concurrent Systems + Streaming + Fault Tolerance + Hybrid Patterns

---

### **Q1. [Concurrent Session Tracker]**

**Scenario**: In a multiplayer game backend, you’re tracking player sessions across hundreds of servers. Sessions need to expire automatically, and concurrent updates happen often.

**Question**:

What Java collections and concurrent classes would you use to store and expire sessions safely and efficiently?

---

### **Q2. [Streaming Word Count with Sliding Window]**

**Scenario**: You're building a real-time dashboard that counts word occurrences in messages over a sliding 10-minute window.

**Question**:

Which collections and strategies would you use to store time-bound word counts and keep memory usage under control?

---

### **Q3. [Fault-Tolerant Event Deduplication]**

**Scenario**: Events might get delivered multiple times to your microservice due to retries. You need to store processed event IDs and ensure no reprocessing.

**Question**:

Which collection(s) would you use to store IDs with auto-expiration? What are the trade-offs?

---

### **Q4. [Concurrent Resource Allocation Map]**

**Scenario**: In a cloud provisioning system, you assign limited compute resources (e.g., CPU cores) to users. Multiple threads request and release resources.

**Question**:

How would you safely manage the resource allocation map using Java Collections?

---

### **Q5. [Hybrid Read/Write Performance Store]**

**Scenario**: You need a key-value store with **fast reads and thread-safe writes**, but very few deletions. It's used for runtime config.

**Question**:

What Collection or hybrid pattern would you use here? Would `ConcurrentHashMap` alone be enough?

---

### **Q6. [Distributed Token Bucket]**

**Scenario**: You're implementing a distributed token bucket algorithm for rate limiting. Each user has a bucket that refills periodically.

**Question**:

How can Java collections help manage per-user tokens in a concurrent system? What issues should you anticipate?

---

### **Q7. [Graph Traversal with Backtracking]**

**Scenario**: You’re crawling a graph of APIs for dependency resolution. Some services depend on others, and backtracking is needed for failure recovery.

**Question**:

Which Java collections would you use to manage the visited nodes and the backtrack stack?

---

### **Q8. [Time Series Snapshot Store]**

**Scenario**: You take a snapshot of a metric every 5 seconds and need to store only the last 10 minutes.

**Question**:

What collection would you use to efficiently add new entries, discard old ones, and allow time-range queries?

---

### **Q9. [Debugging Memory Leak with Collections]**

**Scenario**: Your JVM heap is blowing up in production. You suspect a `Map` that holds user sessions is leaking.

**Question**:

What collection misuse might be causing this? How would you debug it?

---

### **Q10. [Prioritized Retry Queue with Delays]**

**Scenario**: Failed operations must be retried in priority order but only after a delay (e.g., exponential backoff).

**Question**:

What Java Collections or concurrent classes would help implement this behavior cleanly?
