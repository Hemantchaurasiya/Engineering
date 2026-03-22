## **Java Collections – Mock Interview: Round 11**

### **Theme**: Streams + Parallel Processing + Immutable Collections + Memory Optimization

---

### **Q1. [Real-Time Analytics Dashboard]**

**Scenario**: You receive a stream of user activity logs (e.g., click, scroll, purchase). You need to calculate user actions per category in real-time.

**Question**:

How would you use Java Streams + Collections to aggregate these stats efficiently? How would you handle concurrency?

---

### **Q2. [Immutable Product Catalog Snapshots]**

**Scenario**: You generate read-only product catalog snapshots every hour that are shared across threads.

**Question**:

Which collections would you use to ensure immutability and thread-safety without expensive locking?

---

### **Q3. [Large File Word Frequency Counter]**

**Scenario**: You want to read a large file (1GB+) line by line and count word frequency using Java Streams.

**Question**:

How would you design the collection usage to balance memory and performance? Would parallel streams help?

---

### **Q4. [Lambda-Driven Filtering System]**

**Scenario**: You have a dynamic filter builder that accepts predicates from user input and applies them on product objects.

**Question**:

How can you use Java Collection streams and functional interfaces to compose and apply these filters efficiently?

---

### **Q5. [Parallel Data Aggregator]**

**Scenario**: You're aggregating large datasets by categories (e.g., sales by region) using multi-threaded processing.

**Question**:

Which Java Collections would you use with parallel streams to avoid contention and ensure accurate aggregation?

---

### **Q6. [Stream-Based Deduplication in Pipeline]**

**Scenario**: Your data pipeline ingests IDs that must be deduplicated in-stream before processing.

**Question**:

How can Java Collection streams be used for this? How would you do it in a memory-efficient way?

---

### **Q7. [Custom Collector for Hierarchical Grouping]**

**Scenario**: You want to group employees by department and then by role using `Collectors`.

**Question**:

How would you implement this using nested `Collectors.groupingBy()` and which collection types would you choose?

---

### **Q8. [Lazy Evaluation for Large Dataset Filtering]**

**Scenario**: You’re filtering millions of records for a dashboard, and you want to avoid loading everything into memory.

**Question**:

How would you use Streams + Collections to implement lazy evaluation? What pitfalls should you avoid?

---

### **Q9. [Efficient Top-K Search using Streams]**

**Scenario**: From a large product list, return the top 5 most expensive items efficiently using Java Streams.

**Question**:

What collection(s) would you use internally, and how would you structure the stream logic?

---

### **Q10. [Stateless Stream Reprocessing]**

**Scenario**: A pipeline step failed and you want to replay a stream of events stored in memory, but the process must be stateless.

**Question**:

How can you leverage Java Collections to store just enough to allow reprocessing without retaining unnecessary state?

### **Theme**: Internals of Map, Set, Queue + Performance + Real-World Design Choices

---

### **Q1. [User Role Lookup with Optimal Retrieval]**

**Scenario**: You're building a role-based access control system where you need to retrieve user roles by username frequently.

**Question**:

Which `Map` implementation would you use and why? How would your answer change if the data set were extremely large and loaded from a file?

---

### **Q2. [Custom Object in HashSet Failing to Detect Duplicates]**

**Scenario**: You stored custom `Employee` objects in a `HashSet`, but duplicates are not being detected.

**Question**:

What could be wrong in the `Employee` class? How would you fix it?

---

### **Q3. [Least Recently Used (LRU) Cache]**

**Scenario**: You need to design an in-memory cache that evicts the least recently used items when full.

**Question**:

How would you implement it using core Java Collections? Which collection types would you use and why?

---

### **Q4. [Priority Task Scheduling Queue]**

**Scenario**: You're implementing a task scheduler where tasks have priority. Tasks with the same priority should follow insertion order.

**Question**:

Which Queue or Collection would you use? How would you maintain both priority and insertion order?

---

### **Q5. [Massive Dataset - Unique IP Tracker]**

**Scenario**: You’re analyzing traffic and want to track unique IPs hitting your server. You get 100M+ hits per day.

**Question**:

Would you use a `HashSet`? What are the memory implications, and what alternatives can you consider?

---

### **Q6. [Concurrent Access to TreeMap]**

**Scenario**: You have a `TreeMap` that stores timestamped data, accessed and updated by multiple threads.

**Question**:

What risks do you foresee with this? How would you make the access thread-safe?

---

### **Q7. [Multi-Value Map Implementation]**

**Scenario**: You need a structure like `Map<String, List<String>>` to store tags by category.

**Question**:

How would you design utility methods to simplify adding new entries? What potential issues arise?

---

### **Q8. [Custom Comparator in TreeSet]**

**Scenario**: You use a `TreeSet` with a custom comparator to sort orders by amount. However, some orders are missing.

**Question**:

Why might this happen? How do TreeSet and TreeMap treat duplicates under custom comparators?

---

### **Q9. [Distributed Logging Queue]**

**Scenario**: You’re buffering logs in a queue before flushing to disk or Kafka. It needs to be thread-safe and ordered.

**Question**:

Which Queue collection would you choose? Would it differ in a single-threaded scenario?

---

### **Q10. [HashMap Resize Behavior]**

**Scenario**: You experience latency spikes during high-traffic load when using a `HashMap`.

**Question**:

What internal behavior might be causing this? How can you prevent or mitigate it?
