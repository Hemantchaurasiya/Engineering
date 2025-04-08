## **Java Collections – Mock Interview: Round 1**

### **Interviewer:** Let's begin with some real-world problem-solving around collections.

---

### **Q1. [Design Thinking]**

**Scenario**: You’re building a **ride-sharing platform**. Each city has multiple drivers, and each driver has a ride history. You need to:

1. Get all ride histories for a city
2. Search rides by driver ID

**Question**:

How would you model this using Java Collections? Which collections would you use and why?

---

### **Q2. [Map Usage + Conflict Resolution]**

**Scenario**: Two separate services are syncing **product inventories**. If a product is present in both with different stock levels, the higher stock should be kept.

**Question**:

How would you merge these two `Map<String, Integer>` efficiently?

---

### **Q3. [TreeSet Use Case]**

**Scenario**: In a **job portal**, you want to show a list of candidates sorted by experience (highest first) and, if tied, by earliest registration.

**Question**:

Which collection would you choose? How would you maintain this ordering?

---

### **Q4. [Concurrency & Collection Choice]**

**Scenario**: You’re working on a **chat server** where each user has a message queue that could be written to and read by different threads.

**Question**:

Which Java collection would you use for the message queue? How would you ensure thread safety?

---

### **Q5. [High-Volume Data + Set]**

**Scenario**: You receive millions of transaction IDs daily and need to **remove duplicates** efficiently.

**Question**:

Which collection would you use and why? What if memory usage becomes a concern?

---

### **Q6. [Nested Mapping]**

**Scenario**: You’re working on an **educational app**. Each course has multiple students, and each student has a score.

**Question**:

How would you represent this using Java Collections?

---

### **Q7. [Deque / LRU Use Case]**

**Scenario**: Implement a **recently viewed products list** that shows the last 10 distinct items a user clicked on.

**Question**:

How would you design this using Java Collections?

---

### **Q8. [Grouping with Streams]**

**Scenario**: You have a list of employees and want to group them by department using Java 8+ features.

**Question**:

How would you do this with the Collections Framework?
