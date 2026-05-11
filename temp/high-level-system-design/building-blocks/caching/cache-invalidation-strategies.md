### Cache invalidation is the process of removing stale or outdated data from the cache to ensure users get fresh and accurate data.

## 1. Write Through Cache:
- Data is simultaneously written to both the cache and the database.
- Ensures data consistency but adds write latency.
- Best for read-heavy workloads.
---
Under this scheme, data is written into the cache and the corresponding database simultaneously. The cached data allows for fast retrieval and, since the same data gets written in the permanent storage, we will have complete data consistency between the cache and the storage. Also, this scheme ensures that nothing will get lost in case of a crash, power failure, or other system disruptions. Although, write-through minimizes the risk of data loss, since every write operation must be done twice before returning success to the client, this scheme has the disadvantage of higher latency for write operations.
Example: An e-commerce website updates its product inventory in real-time. Whenever a product's stock changes, the cache is also updated to reflect the new inventory count.
---
1. Simultaneous Write: Data is written to both the cache and the database at the same time.
2. Strong Consistency: Ensures the cache and permanent storage always have the same data.
3. Fast Reads: Cached data enables quicker data retrieval.
4. High Reliability: No data loss during crashes or failures since data is already stored in the database.
5. Write Safety: Guarantees durability because data is persisted immediately.
6. Higher Write Latency: Write operations are slower because they must update two places before confirming - success.
7. Use Case Fit: Ideal where data accuracy and consistency are more important than write speed.
8. Example Scenario: In an e-commerce system, updating product inventory updates both cache and database instantly to maintain real-time accuracy.

## 2. Write Around Cache:
- Data is directly written to the database, bypassing the cache.
- Cache is only updated when data is read.
- Reduces cache pollution but increases cache misses.
---
This technique is similar to write-through cache, but data is written directly to permanent storage, bypassing the cache. This can reduce the cache being flooded with write operations that will not subsequently be re-read, but has the disadvantage that a read request for recently written data will create a “cache miss” and must be read from slower back-end storage and experience higher latency.

Example: An application updates user profile information, which is infrequently accessed. The application writes the new data directly to the data store, avoiding unnecessary cache updates.
---
1. Direct Write to Storage: Data is written straight to the database, bypassing the cache.
2. Cache Not Immediately Updated: The cache is only updated when the data is read later.
3. Avoids Cache Pollution: Prevents the cache from being filled with rarely accessed data.
4. Improved Write Performance: Faster writes compared to write-through since only one write operation occurs.
5. Read Penalty (Cache Miss): Recently written data is not in cache, so the first read is slower.
6. Higher Read Latency Initially: Data must be fetched from backend storage on first access.
7. Best for Low Read Frequency Data: Suitable when written data is not frequently accessed soon after.
8. Example Scenario: Updating user profile info that is rarely accessed—data is stored directly in the database without updating the cache.

## 3. Write Back Cache (or lazy-write):
- Data is written to cache first, then asynchronously written to the database.
- Improves write performance , but risk of lose of data ( if cache goes down before updating to source).
- Best for write-heavy workloads.
---
Under this scheme, data is written to cache alone, and completion is immediately confirmed to the client. The write to the permanent storage is done based on certain conditions, for example, when the system needs some free space. This results in low-latency and high-throughput for write-intensive applications; however, this speed comes with the risk of data loss in case of a crash or other adverse event because the only copy of the written data is in the cache.

Example: Imagine a collaborative document editing application that allows multiple users to make changes to a document simultaneously. When users make changes, those changes are first saved to the cache, allowing the application to respond quickly and provide a smooth editing experience. When certain conditions are met (e.g., the number of changes reaches a certain threshold), the application writes the cached changes back to the data store, updating the document with the latest changes from all users. This approach minimizes the number of write operations to the data store and reduces the load on the storage system, improving the overall performance of the application.
---
1. Write to Cache First: Data is written only to the cache initially.
2. Delayed Database Write: Data is written to permanent storage later based on certain conditions (e.g., cache eviction, thresholds).
3. Low Write Latency: Immediate acknowledgment to the client makes writes very fast.
4. High Throughput: Suitable for write-heavy applications due to reduced direct database operations.
5. Efficient Resource Usage: Minimizes the number of writes to the database, reducing load.
6. Risk of Data Loss: If a crash occurs before data is persisted, the data in cache may be lost.
7. Eventual Consistency: Cache and database may not always be in sync immediately.
8. Best for Performance-Critical Systems: Ideal where speed is prioritized over strict consistency.
9. Example Scenario: In collaborative document editing, changes are first stored in cache and later written to the database in batches to improve performance.

## 4. Write-behind cache:
It is quite similar to write-back cache. In this scheme, data is written to the cache and acknowledged to the application immediately, but it is not immediately written to the permanent storage. Instead, the write operation is deferred, and the data is eventually written to the permanent storage at a later time. The main difference between write-back cache and write-behind cache is when the data is written to the permanent storage. In write-back caching, data is only written to the permanent storage when it is necessary for the cache to free up space or when an event happens, while in write-behind caching, data is written to the permanent storage at specified intervals.

Example: A document editor application temporarily saves changes to the cache while the user is editing. Periodically, the changes are written back to the data store to minimize the number of write operations.
---
1. Write to Cache First: Data is written to the cache and acknowledged immediately.
2. Deferred Persistence: Data is written to permanent storage later, not instantly.
3. Scheduled Writes: Writes to the database happen at fixed intervals or periodic batches.
4. Low Latency: Fast response to the client since writes don’t wait for the database.
5. High Throughput: Efficient for handling a large number of write operations.
6. Batch Processing Advantage: Multiple updates can be combined into fewer database writes.
7. Risk of Data Loss: If the system crashes before scheduled writes, data in cache may be lost.
8. Eventual Consistency: Cache and database may temporarily differ until sync occurs.
9. Difference from Write-Back: Write-back triggers storage writes on events (like eviction), while write-behind uses time-based intervals.
10. Example Scenario: A document editor stores changes in cache and periodically saves them to the database to reduce write load.

## Time To Live:
- Cache entries expire after a set time period. Ensures data is not stale and manages memory.

## Event Based Cache:
- Cache is updated or invalidated based on specific events or triggers in the application.

# Cache Invalidations Methods
## Purge method:
The purge method removes cached content for a specific object, URL, or a set of URLs. It’s typically used when there is an update or change to the content and the cached version is no longer valid. When a purge request is received, the cached content is immediately removed, and the next request for the content will be served directly from the origin server.

Example: A news website purges a specific article from its cache after significant updates have been made, ensuring that users receive the latest version.
---
1. Selective Removal: Deletes cached data for a specific object, URL, or group of URLs.
2. Immediate Invalidation: Cached content is removed instantly upon request.
3. Ensures Fresh Data: Guarantees users receive the most updated version of content.
4. Forces Cache Miss: The next request fetches data from the origin server instead of cache.
5. Manual or Triggered Action: Usually initiated when content is updated or modified.
6. Improves Data Accuracy: Prevents stale or outdated data from being served.
7. Higher Load on Origin (Temporarily): After purge, requests hit the main server until cache is rebuilt.
8. Use Case Fit: Ideal for frequently updated or critical content.
9. Example Scenario: A news website purges an article after updates so users always see the latest version.

## Refresh method:
The refresh method retrieves requested content from the origin server, even if a cached version is available. When a refresh request is received, the cache updates the content with the latest version from the origin server, ensuring up-to-date information. Unlike a purge, a refresh request does not remove the existing cached content but updates it with the most recent version.

Example: An e-commerce website refreshes the cache of a product page when a new sale is launched to display the updated pricing information.
---
1. Fetch Latest Data: Retrieves updated content from the origin server even if cache exists.
2. Cache Update (Not Removal): Replaces old cached data with the latest version instead of deleting it.
3. Ensures Freshness: Keeps cached content up to date without causing a cache miss.
4. No Empty Cache State: Unlike purge, the cache always holds some version of the data.
5. Immediate Update Trigger: Usually triggered when content changes (e.g., price updates).
6. Balanced Approach: Maintains performance benefits of caching while ensuring accuracy.
7. Lower Origin Load Spike: Less sudden load compared to purge since cache isn’t fully cleared first.
8. Use Case Fit: Ideal when you want updated data without temporarily losing cached content.
9. Example Scenario: An e-commerce site refreshes product pages to show updated prices during a sale.

## Ban method:
The ban method invalidates cached content based on specific criteria, such as a URL pattern or header. Upon receiving a ban request, any cached content matching the specified criteria is immediately removed. Subsequent requests for the content will be served directly from the origin server, ensuring that users receive the most recent and relevant information.

Example: A content management system bans all cached content with a specific tag when that tag is modified, ensuring that users only see the updated content.
---
1. Pattern-Based Invalidation: Removes cached data based on rules like URL patterns, tags, or headers.
2. Bulk Removal: Can invalidate multiple cache entries at once using defined criteria.
3. Immediate Effect: Matching cached content is removed as soon as the ban is applied.
4. Forces Cache Miss: Future requests fetch fresh data from the origin server.
5. Flexible Targeting: More dynamic than purge, as it doesn’t require exact URLs.
6. Efficient for Large Systems: Useful when many related cache entries need to be invalidated together.
7. Ensures Data Freshness: Prevents outdated grouped content from being served.
8. Temporary Origin Load Increase: After banning, requests hit the backend until cache is rebuilt.
9. Use Case Fit: Ideal for systems using tags, categories, or grouped content.
10. Example Scenario: A CMS bans all cached content with a specific tag after it’s updated, ensuring users see the latest data.

## Time-to-Live (TTL) expiration:
This method involves setting a time-to-live value for cached content, after which the content is considered stale and must be refreshed. When a request is received for the content, the cache checks the time-to-live value and serves the cached content only if the value hasn’t expired. If the value has expired, the cache fetches the latest version of the content from the origin server and caches it.

Example: A weather website sets a 1-hour TTL for its weather forecast data, ensuring that users receive relatively up-to-date weather information without overloading the origin server.
---
1. Time-Based Expiration: Each cached item has a predefined lifespan (TTL).
2. Automatic Invalidation: Data is considered stale once the TTL expires.
3. Cache Validation on Request: Cache checks TTL before serving data.
4. Fresh Data Fetch: If expired, data is retrieved from the origin server and updated in cache.
5. No Manual Intervention Needed: Works automatically without explicit purge/refresh actions.
6. Balances Performance & Freshness: Reduces server load while keeping data reasonably up to date.
7. Possible Stale Data Window: Data may be slightly outdated until TTL expires.
8. Configurable Duration: TTL can be short or long depending on use case.
9. Use Case Fit: Ideal for data that changes at predictable intervals.
10. Example Scenario: A weather site uses a 1-hour TTL to keep forecasts fresh without frequent server hits.

## stale-while-revalidate:
This method is used in web browsers and CDNs to serve stale content from the cache while the content is being updated in the background. When a request is received for a piece of content, the cached version is immediately served to the user, and an asynchronous request is made to the origin server to fetch the latest version of the content. Once the latest version is available, the cached version is updated. This method ensures that the user is always served content quickly, even if the cached version is slightly outdated.

Example: A media streaming platform uses stale-while-revalidate to serve video thumbnails, ensuring that users can quickly browse the catalog while the platform updates thumbnail images in the background.
---
1. Serve Stale Data Immediately: Returns cached (possibly outdated) content instantly to the user.
2. Background Refresh: Simultaneously fetches the latest data from the origin server asynchronously.
3. Non-Blocking Updates: Users don’t wait for fresh data, improving response time.
4. Cache Update After Fetch: Once new data arrives, the cache is updated for future requests.
5. Low Latency Experience: Ensures fast responses even when data needs refreshing.
6. Eventual Freshness: Data becomes up to date shortly after the background update completes.
7. Improved User Experience: Minimizes delays while still keeping data reasonably current.
8. Slight Staleness Trade-off: Users may briefly see outdated content.
9. Efficient for High-Traffic Systems: Reduces load spikes on the origin server.
10. Use Case Fit: Ideal where speed is more critical than perfectly real-time data.
11. Example Scenario: A streaming platform serves cached thumbnails instantly while updating them in the background.

## Summary points
Cache invalidation strategy should be chosen carefully to balance the trade-off between performance and data accuracy. By understanding different cache invalidation strategies, software engineers can select the appropriate strategy to optimize cache performance and reduce latency while ensuring that the data stored in the cache is accurate and up-to-date.
---
1. Careful Strategy Selection: Cache invalidation methods must be chosen thoughtfully.
2. Performance vs Accuracy Trade-off: There is always a balance between speed (performance) and data freshness (accuracy).
3. Impact on Latency: The right strategy helps reduce response time.
4. Ensures Data Consistency: Proper invalidation keeps cached data correct and up to date.
5. Optimization Goal: Aim to maximize cache efficiency while minimizing stale data.
6. Context-Driven Choice: Different applications require different invalidation strategies.
7. Engineering Responsibility: Software engineers must understand trade-offs to make the best decision.