## **Java Collections – Mock Interview: Round 7**

### **Theme:** Modern Java + Complex Systems + Edge Behavior

---

### **Q1. [Rate-Limited Queue Processing]**

**Scenario**: You're building a rate-limited email notification system. You must only send 100 emails per minute, even if 10,000 are queued.

**Question**:

Which Java collections and scheduling approach would you use? How would you structure the rate-limiting?

---

### **Q2. [Most Frequently Accessed Files Tracker]**

**Scenario**: You're designing a local file system watcher that tracks and displays the **top 5 most accessed files**.

**Question**:

How would you model this using Collections? How would you keep the structure updated in real-time?

---

### **Q3. [Stream Collector with Custom Result Type]**

**Scenario**: You have a stream of `Order` objects and want to **collect them into a `TreeMap<CustomerId, List<Order>>`**, sorted by `CustomerId`.

**Question**:

How would you write a custom collector for this?

---

### **Q4. [API Throttling with Sliding Window]**

**Scenario**: You need to implement **sliding window rate limiting** (not fixed window) using in-memory collections.

**Question**:

Which Collection structure would best fit, and how would you efficiently purge expired entries?

---

### **Q5. [Fault-Tolerant Service Registry]**

**Scenario**: You're building a service registry where services register with a `timestamp` and `health`. Unhealthy services should be removed after a timeout.

**Question**:

How would you design this using Java collections with minimal synchronization overhead?

---

### **Q6. [Asynchronous Task Batching]**

**Scenario**: Tasks are submitted asynchronously and should be grouped into batches of 100 for processing.

**Question**:

How would you implement this batching mechanism using Collections while ensuring thread safety?

---

### **Q7. [Immutable Time-Bound Event History]**

**Scenario**: You need to maintain a history of **the last 1000 events**, in the exact order received, and expose it **read-only** to external callers.

**Question**:

Which Java Collection(s) would you use to ensure both **immutability** and **order preservation**?

---

### **Q8. [Combining Multiple Stream Pipelines Efficiently]**

**Scenario**: You have 3 separate lists of transactions: failed, successful, and pending. You want to flatten and group all by customer ID in a single pass.

**Question**:

How would you achieve this with Java Streams and collections?

---

### **Q9. [Versioned Key-Value Store]**

**Scenario**: Implement a versioned key-value store like:

```java
java
CopyEdit
put("x", "v1");
put("x", "v2");
get("x", 1); // v1

```

**Question**:

What collection structure would you use to store keys and their versioned values?

---

### **Q10. [Permission Hierarchy Resolver]**

**Scenario**: You have a user permission hierarchy:

- `Admin` inherits from `Editor`
- `Editor` inherits from `Viewer`

You must resolve **all effective permissions** for any given role.

**Question**:

How would you represent this using Collections, and how would you resolve permissions efficiently?
