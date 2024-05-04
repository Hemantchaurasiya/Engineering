## Introduction to Caching

### What is Caching?
The cache is a high-speed storage layer that sits between the application and the original source of the data, such as a database, a file system, or a remote web service. When data is requested by the application, it is first checked in the cache. If the data is found in the cache, it is returned to the application. If the data is not found in the cache, it is retrieved from its original source, stored in the cache for future use, and returned to the application.

Caching can be used for various types of data, such as web pages, database queries, API responses, images, and videos. The goal of caching is to reduce the number of times data needs to be fetched from its original source, which can result in faster processing and reduced latency.

Caching can be implemented in various ways, including in-memory caching, disk caching, database caching, and CDN caching. In-memory caching stores data in the main memory of the computer, which is faster to access than disk storage. Disk caching stores data on the hard disk, which is slower than main memory but faster than retrieving data from a remote source. Database caching stores frequently accessed data in the database itself, reducing the need to access external storage. CDN caching stores data on a distributed network of servers, reducing the latency of accessing data from remote locations.

### Key terminology and concepts
1. Cache: A temporary storage location for data or computation results, typically designed for fast access and retrieval.

2. Cache hit: When a requested data item or computation result is found in the cache.

3. Cache miss: When a requested data item or computation result is not found in the cache and needs to be fetched from the original data source or recalculated.

4. Cache eviction: The process of removing data from the cache, typically to make room for new data or based on a predefined cache eviction policy.

5. Cache staleness: When the data in the cache is outdated compared to the original data source.