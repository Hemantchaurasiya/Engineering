## **Java Collections – Mock Interview: Round 6**

### **Interviewer:** These problems simulate real-world systems with tricky constraints. Expect to reason about time, memory, and concurrency.

---

### **Q1. [Concurrent Job Scheduler]**

**Scenario**: You're building a **job scheduler** that runs jobs at specific timestamps. Multiple threads can add or remove jobs concurrently.

**Question**:

How would you model this scheduler using Java Collections? Which concurrency-safe collection would you choose and why?

---

### **Q2. [Stream-Based Grouping and Aggregation]**

**Scenario**: You have a list of `Transaction` objects. You need to **group them by customer ID** and calculate the **total amount per customer**.

**Question**:

Which Java Collections and Stream APIs would you use to achieve this? Can you do it in one line?

---

### **Q3. [Memory Leak in Large HashMap]**

**Scenario**: You store millions of entries in a `HashMap`, and memory keeps growing. Eventually, the app crashes with an `OutOfMemoryError`.

**Question**:

What are potential pitfalls when using large maps in memory-heavy applications? How can you prevent memory leaks?

---

### **Q4. [Longest Unique Substring]**

**Scenario**: You’re asked to write a function that returns the **longest substring without repeating characters** from a given string.

**Question**:

Which Java Collection would you use, and what would be the time complexity of your approach?

---

### **Q5. [Analytics: Time-Partitioned User Events]**

**Scenario**: You need to track **user activity per hour**, for the past 24 hours. You want constant-time lookup and efficient memory usage.

**Question**:

Which Collection would you use for partitioning data hourly? How would you rotate old entries?

---

### **Q6. [Top K Trending Hashtags in Real-Time]**

**Scenario**: You're tracking Twitter hashtags. New hashtags stream in constantly, and you need to return the **top K trending** ones every minute.

**Question**:

What collection(s) would you use to handle real-time ranking and frequency counting efficiently?

---

### **Q7. [Immutable Graph Snapshot]**

**Scenario**: You have a mutable graph represented using `Map<Node, List<Node>>`. You need to take **snapshots** of the graph at various times without affecting the original.

**Question**:

How would you use Java Collections to create a deep, immutable snapshot of the graph?

---

### **Q8. [Duplicate API Calls Detection]**

**Scenario**: You receive thousands of API requests per second. You need to **detect and ignore duplicate calls** with the same request ID for the last 10 minutes.

**Question**:

How would you store and clean up old request IDs efficiently?

---

### **Q9. [Priority-Based Retry Queue]**

**Scenario**: Your system retries failed API calls. Each call has a retry count (higher means more urgent). The retry queue must process higher-priority retries first.

**Question**:

How would you design this retry queue using Java Collections?

---

### **Q10. [Dependency Graph with Cycle Detection]**

**Scenario**: You’re building a module loader that must ensure **no circular dependencies** exist among modules.

**Question**:

How would you represent the modules and their dependencies using Collections? How would you detect a cycle?
