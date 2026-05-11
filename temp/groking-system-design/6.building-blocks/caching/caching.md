### Link: https://www.geeksforgeeks.org/system-design/caching-system-design-concept-for-beginners/

# 📦 Caching - Complete Notes

## 📌 What is Caching?
Caching is a technique of storing frequently accessed data in a fast, temporary storage layer so it can be retrieved quickly instead of fetching it repeatedly from a slower source like a database.

**Goal:** Improve performance, reduce latency, and minimize database load.

---

## ⚡ Why Caching is Important
- Database calls involve **network + I/O operations** → slow
- Cache stores data in **memory (fast access)**
- Reduces repeated computation and database hits

### 🧠 Example
When a tweet goes viral (like on Twitter), millions request the same data.  
Instead of querying the database each time:
- Store tweet in cache
- Serve from cache → **faster + scalable**

---

## ⚙️ How Caching Works

### Step-by-step flow:
1. User sends request
2. System checks cache
   - ❌ Not found → **Cache Miss**
     - Fetch from DB
     - Store in cache
     - Return response
   - ✅ Found → **Cache Hit**
     - Return directly from cache

**Cache Hit = Fast response**  
**Cache Miss = Slower response**

---

## ⚠️ Why Not Store Everything in Cache?
- 💰 Cache memory (RAM) is expensive
- 🔍 Too much data → slower lookup
- 🔄 Cache is often **volatile** (data loss on restart)
- 📉 Not suitable for long-term storage

**Cache should store only relevant & frequently used data**

---

## 🧩 Types of Cache

### 1. 🖥️ Application Server Cache
- Local cache inside each server
- Fast access, simple implementation

**Problem:**
- In multi-server setup:
  - Each server has its own cache
  - Leads to **cache misses**

**Solution:**
- Note: To fix this, there are two main options: Distributed Cache and Global Cache.

---

### 2. 🌐 Distributed Cache
- Cache is divided across multiple nodes
- Uses **consistent hashing** to locate data

**Advantages:**
- Scalable
- Efficient data distribution

---

### 3. 🌍 Global Cache
- Single shared cache for all servers

**Types:**
1. Cache fetches missing data itself
2. App fetches data if cache miss occurs

**Pros:**
- Consistent data across nodes

---

### 4. 🚀 CDN (Content Delivery Network)
- Distributed servers across the globe
- Stores static content (images, JS, CSS, videos)

**How it works:**
1. User requests content
2. CDN checks cache
   - Hit → return
   - Miss → fetch from backend → cache it

Improves latency by serving from nearest server

---

## 🛠️ Applications of Caching

- 🌐 Web Page Caching → Faster page loads (browsers save copies of frequently visited websites.)
- 🗄️ Database Caching → Reduce DB load
- 🌍 CDN → Faster global content delivery (CDNs use caching to keep copies of data (such as pictures and videos) in several places throughout the globe. This enhances website performance by enabling visitors to obtain content more quickly from a nearby server.)
- 👤 Session Caching → Store user sessions (Applications store session data in a cache to remember user information (like login status) between visits, making the experience seamless and personalized without needing to re-login.)
- 🔌 API Response Caching → Faster API responses (Frequently requested API data, like stock prices or weather data, can be cached so responses are faster, reducing the load on the server and delivering data in real-time.)

---

## 🔄 Cache Invalidation

Cache becomes outdated when original data changes.

### Common Strategies:
- ⏱️ Time-based (TTL) → expire after time
- 🔔 Event-based → update when data changes

Critical to avoid stale data

---

## 🧹 Eviction Policies

When cache is full, decide what to remove:

- **LRU (Least Recently Used)**  
  → Remove least recently accessed

- **LFU (Least Frequently Used)**  
  → Remove least used

- **FIFO (First In First Out)**  
  → Remove oldest entry

---

## ✅ Advantages

- 🚀 Faster response time
- 📉 Reduced database load
- 💰 Cost efficiency
- 📈 Better scalability

---

## ❌ Disadvantages

- ⚠️ Data inconsistency risk
- 🧩 Added system complexity
- 🧹 Poor eviction → performance issues
- 🔄 Cache invalidation is hard

---

## 🧠 Key Takeaways

- Cache = **speed optimization layer**
- Always balance:
  - ⚡ Performance
  - 🔄 Consistency
  - 💾 Memory usage
- Use caching **strategically**, not everywhere
