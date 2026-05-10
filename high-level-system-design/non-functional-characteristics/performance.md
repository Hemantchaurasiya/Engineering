# 🚀 5. Performance (Latency & Throughput) — Ultra Deep Dive

## 🔹 1. Definition (Simple + Intuitive)

### ✅ Simple Definition

**Performance** = How fast a system responds (latency) and how much work it can handle (throughput).

### 🧠 Real-World Analogy (Deep Understanding)

Think of a restaurant again, but now focus on speed and capacity:

- **Latency** → Time to serve ONE customer  
- **Throughput** → Number of customers served per hour

### 🔥 Key Insight

A system can be:  
- Fast per request (low latency)  
- But still handle few users (low throughput)

👉 Both matter, but in different ways.

---

## 🔹 2. Why It Matters

### 📉 If Performance is Poor:
- Slow loading apps  
- Timeouts  
- Poor user experience

### 💸 Business Impact
- Users leave slow apps quickly  
- Conversion drops (e.g., slow checkout on Amazon)  
- Engagement drops (e.g., buffering on Netflix)

### 🧠 Deep Insight

> Users perceive performance as “quality” of your system.  
> Even if system is correct: **Slow = feels broken**

---

## 🔹 3. Key Metrics / How to Measure

### 🧩 A. Latency (Response Time)

**📊 Definition**  
Time taken to process a single request

**📊 Percentiles (VERY IMPORTANT)**  
- p50 → median  
- p95 → 95% requests below this  
- p99 → worst-case

**🧠 Why percentiles matter?**

Example:  
Average = 100ms, but p99 = 5 seconds ❌  
👉 Users remember worst experience

### 🧩 B. Throughput

**📊 Definition**  
Number of requests processed per unit time
Throughput = Total Requests / Time


Example: `10,000 requests/sec (RPS)`

### 🧩 C. Concurrency
Number of simultaneous users/requests

### 🧩 D. Resource Utilization
- CPU %  
- Memory usage  
- Disk I/O

### 🧠 Key Insight

> Performance = Latency + Throughput + Resource Efficiency

---

## 🔹 4. How to Achieve High Performance (Deep + Practical)

### 🧩 1. Caching (BIGGEST WIN)

Avoid recomputation.  
Example: Frequently accessed data → store in cache.  
Used heavily by: Google, Amazon.

Types:  
- CDN cache  
- Application cache  
- DB cache

### 🧩 2. Load Balancing
Distributes load → prevents overload

### 🧩 3. Database Optimization

Common bottleneck.  
Techniques:  
- Indexing  
- Query optimization  
- Read replicas

### 🧩 4. Asynchronous Processing

Move heavy work to background:  
`User → API → Queue → Worker`

### 🧩 5. Data Partitioning (Sharding)
Split data → parallel processing

### 🧩 6. Efficient Algorithms
`O(n)` vs `O(n²)` matters massively at scale

### 🧩 7. Connection Pooling
Reuse connections → reduce overhead

### 🧩 8. Content Delivery Network (CDN)
Serve data closer to users

### 🧩 9. Compression
Reduce data size → faster transfer

### 🧩 10. Horizontal Scaling
More servers → more throughput

---

## 🔹 5. Trade-offs (CRITICAL)

### ⚖️ 1. Performance vs Consistency
Strong consistency → slower responses

### ⚖️ 2. Performance vs Durability
Writing to multiple disks → slower

### ⚖️ 3. Performance vs Cost
Faster infra = more expensive

### ⚖️ 4. Performance vs Scalability
Optimization for single machine may not scale

### ⚖️ 5. Performance vs Maintainability
Highly optimized code → harder to maintain

---

## 🔹 6. Real-World Examples

### 🔍 Google
Extremely low latency search (<100ms)  
Uses:  
- Caching  
- Distributed systems

### 🛒 Amazon
Optimizes checkout latency  
Uses caching + DB tuning

### 🎬 Netflix
Optimizes streaming performance  
Uses CDN + adaptive bitrate

---

## 🔹 7. Impact on System Design Decisions

### 🧠 API Design
- Fast endpoints  
- Pagination instead of large responses

### 🧠 Database

| Need           | Decision     |
|----------------|--------------|
| Fast reads     | Indexing     |
| High throughput| Sharding     |

### 🧠 Architecture
- Async systems  
- Microservices

### 🧠 Infra
- CDN  
- Auto-scaling

---

## 🔹 8. Common Mistakes / Pitfalls

- ❌ **1. Optimizing too early** – Premature optimization  
- ❌ **2. Ignoring p99 latency** – Tail latency kills UX  
- ❌ **3. DB as bottleneck** – Poor queries  
- ❌ **4. No caching** – Recomputing everything  
- ❌ **5. Blocking operations** – Synchronous heavy tasks

---

## 🧠 Mini Quiz

1. Why is p99 latency more important than average latency?  
2. How does caching improve both latency and throughput?  
3. Why does asynchronous processing improve performance?

---

## 🎯 Final Mental Model

> **Performance** = Designing systems that are fast, efficient, and responsive under load.