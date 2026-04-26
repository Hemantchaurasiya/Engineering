## **Java Collections – Mock Interview: Round 5**

### **Interviewer:** These are complex scenarios. Expect follow-up questions, performance discussions, and real-time trade-off decisions.

---

### **Q1. [Concurrent Cache Eviction]**

**Scenario**: You’re building a multi-threaded in-memory cache where data should expire based on LRU (Least Recently Used) policy. It must support concurrent reads/writes.

**Question**:

Which collections or concurrent utilities in Java would you use? How would you enforce thread-safe eviction?

---

### **Q2. [Real-Time Leaderboard with Constant Updates]**

**Scenario**: You need to implement a **real-time updating leaderboard** where player scores change frequently, and top-N players are queried every few seconds.

**Question**:

What collection(s) would you use to maintain performance for both updates and reads?

---

### **Q3. [Distributed System – Duplicate Detection]**

**Scenario**: In a distributed microservices setup, each service logs user IDs. You need to detect if a user ID has been processed **before** globally, across all nodes.

**Question**:

How would you design this in memory using Java Collections for a single node, and what would you change to make it distributed?

---

### **Q4. [Efficient Time-Series Storage]**

**Scenario**: You're tracking **stock prices** every second and want to support range queries like: "Get average price from 10:00 to 10:05".

**Question**:

Which collection(s) would you use to store and query this data efficiently?

---

### **Q5. [Priority Event Throttling]**

**Scenario**: You receive events from various systems with a `priority` value. You must process **high-priority events first**, but ensure that **no low-priority event starves**.

**Question**:

How would you implement this queue? What collection(s) would you use?

---

### **Q6. [High-Frequency Trading Orders]**

**Scenario**: In a trading platform, you receive **buy/sell orders** that must be matched by price. You must efficiently match and remove best offers.

**Question**:

Which Java collections would you use to model this system?

---

### **Q7. [Type-Safe Heterogeneous Data Store]**

**Scenario**: You’re building a context store that can hold different types of values (e.g., `String`, `Integer`, `CustomObject`) but must remain type-safe.

**Question**:

How would you use generics and collections to implement this?

---

### **Q8. [Batch Job Deduplication by Time Window]**

**Scenario**: A batch job runs every hour and reads events from a queue. You want to ensure **no event is processed more than once per day**, even if it's repeated in multiple runs.

**Question**:

Which collection(s) would you use, and how would you implement cleanup of old data efficiently?

---

### **Q9. [Deep Copy of Nested Collections]**

**Scenario**: You have a `Map<String, List<CustomObject>>` and need to deep copy it to prevent mutation issues in multithreaded code.

**Question**:

How would you perform a safe deep copy of this structure?

---

### **Q10. [Streaming Rolling Average]**

**Scenario**: You’re processing a **real-time stream of temperature readings** and must show the **rolling average over the last 60 readings**.

**Question**:

Which Java Collection(s) would you use to manage the rolling window, and how would you maintain performance?
