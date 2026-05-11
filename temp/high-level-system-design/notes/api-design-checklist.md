### reference: https://www.designgurus.io/blog/api-design-checklist

Building a robust Application Programming Interface (API) requires more than just knowing how to write endpoints in a specific language.

Many development cycles begin with an immediate focus on tools and implementation details. Teams often rush to generate documentation or set up server frameworks without fully understanding the architectural requirements.

However, the difference between a fragile system and a scalable architecture lies in the planning phase.

Senior engineers understand that the most critical decisions happen before the integrated development environment is even opened. They focus on structural integrity, security standards, and long-term maintenance.

This approach prevents costly rewrites and ensures that the system can handle growth.

1. Start With the Business Constraints
The first step in API design has nothing to do with database schemas or HTTP methods. It involves understanding the specific problem the software intends to solve.

A common mistake is treating every API as a generic data pipeline.

Senior developers ask questions about the usage patterns to determine the load profile. They need to know if the API will be read-heavy or write-heavy.

A system designed for a news feed involves millions of read operations but relatively few writes. This necessitates a distinct architecture focused heavily on aggressive caching strategies.

Conversely, an API for a telemetry system that ingests sensor data is write-heavy, requiring a database optimized for high-throughput ingestion and asynchronous processing queues.

Latency requirements also dictate architectural decisions.

If the API serves a high-frequency trading platform, a delay of 200 milliseconds is a critical failure.

If the API generates monthly PDF invoices, a delay of five seconds is negligible. Establishing these boundaries early prevents the team from over-engineering a simple tool or under-engineering a mission-critical platform.

2. Adopt Contract-First Development
A common dysfunction in engineering teams is the dependency between frontend and backend.

The backend team builds the API, and the frontend team waits.

If the backend team realizes halfway through that the data structure needs to change, the frontend team’s waiting time increases, or their preliminary work is wasted.

Senior teams utilize Contract-First Development.

In this workflow, the API specification is defined and agreed upon before any logic is written. Tools like OpenAPI (formerly Swagger) or Protobuf are used to create a strict document that describes every endpoint, input parameter, and response object.

Once this contract exists, it acts as the single source of truth.

The frontend team can use the spec to generate mock servers that simulate the API, allowing them to build the user interface immediately.

--- image

Simultaneously, the backend team writes code to fulfill the contract.

This parallel workflow drastically reduces the time to market and ensures that integration surprises are eliminated.

3. Plan for Backward Compatibility
Software is never static. New features are added, and data structures evolve. However, once an API is public, changing it is dangerous.

Unlike a website where the server controls what the user sees, a mobile app version 1.0 might live on a user’s phone for years.

If the API changes the format of the “user” object, that old app will crash.

Designing for Backward Compatibility is non-negotiable. Seniors decide on a versioning strategy immediately to ensure older clients continue to function.

URI Versioning
This is the most explicit method. The version number is included in the URL path, such as /v1/products.

If a breaking change is required, a /v2/products endpoint is deployed. The old app continues to talk to v1, while the new app talks to v2. This is easy to implement and debug because the version is visible in the address.

Header-Based Versioning
This strategy keeps the URLs clean.

The client sends a header like Accept-Version: v1. The server routes the request to the correct handler based on this header.

While philosophically cleaner because the resource URL stays the same, it can be harder to test with simple tools. The specific choice matters less than the commitment to having a strategy in place before the first user signs up.

4. Implement Security by Design
Beginners often implement “Basic Authentication” (sending a username and password with every request) and consider the job done. This approach is neither secure nor scalable for modern distributed systems. Senior engineers design for OAuth2 and OpenID Connect (OIDC) from the start.

OAuth2 uses Access Tokens for authorization. When a user logs in, they exchange their credentials for a token. This token, often a JSON Web Token (JWT), allows the client to access resources for a limited time.

This approach is vital for scaling. With Basic Auth, the server might need to check the user database to validate the password on every single request. In a system with thousands of requests per second, the database becomes a bottleneck. With stateless tokens, the server can verify the token’s digital signature mathematically without checking the database. This offloads work from the data layer and improves system throughput.

5. Ensure Observability with Correlation IDs
In a monolithic application, debugging is straightforward because all logs are in one place.

In a microservices architecture, a single user action (like “Checkout”) might trigger a chain of requests across the Inventory Service, the Payment Service, and the Shipping Service. If the request fails, finding the root cause is difficult.

The solution is the Correlation ID. This is a unique identifier (usually a UUID) generated when the request first enters the system (usually at the Load Balancer or API Gateway).

This ID is injected into the HTTP headers (e.g., X-Correlation-ID) and passed along to every downstream service. When an engineer needs to debug an error, they can search the centralized logs for this specific ID. This reveals the entire journey of the request, showing exactly which service failed and why.

Without this mechanism, tracing an error across distributed logs is nearly impossible.

6. Enforce Strong Pagination Standards
When a database table contains only fifty records, returning all of them in a JSON list is acceptable.

When that table grows to five million records, a “Select All” query will exhaust the server’s memory, lock the database, and crash the application.

Pagination must be designed into every list endpoint by default.

While Offset Pagination (e.g., “skip 50, take 10”) is common, it suffers from performance issues on large datasets because the database must still count and discard the skipped rows.

Senior engineers often prefer Cursor Pagination.

This method asks the database for “10 records starting after this specific ID.”

This utilizes database indexes efficiently and ensures that the query remains fast regardless of how much data exists. It also solves the problem of “drifting” results, where items might be skipped or duplicated if data is added while the user is scrolling.

7. Design for Idempotency
Network connections are unreliable.

A client might send a POST request to create an order, but the internet connection drops before the confirmation response is received.

The client, assuming the request failed, sends it again.

If the API is not Idempotent, the server will create two identical orders and charge the customer twice.

Idempotency guarantees that making the same request multiple times produces the same result as making it once.

To achieve this, the client generates a unique 
Idempotency
 Key for the transaction and sends it in the header.

The server checks a cache (like Redis) to see if it has already processed a request with that key.

If it has, it returns the stored response immediately without re-processing the logic. This mechanism ensures data integrity even in the face of aggressive retries and flaky networks.

8. Standardize Error Responses
Inconsistent error handling is a major source of frustration for frontend developers.

One endpoint might return a simple string when it fails, while another returns a JSON object, and a third just throws a generic 500 status code. This forces the frontend code to have complex, brittle logic to handle each case.

A senior engineer defines a Standard Error Schema. Every error response, regardless of the source, follows the same structure.

A robust error object typically includes:

- Code: A machine-readable string (e.g., INSUFFICIENT_FUNDS) that the frontend code can check against.
- Message: A human-readable description for the developer.
- Details: An optional field for validation errors (e.g., highlighting which specific form field was invalid).
- TraceID: The Correlation ID mentioned earlier, allowing support staff to look up the specific logs.

Standardizing this contract allows the client application to have a single, global error handler, simplifying development and maintenance.

9. Define Rate Limiting Strategies
Every system has a breaking point.

Without protection, a single malicious actor or simply a bug in a client script can send thousands of requests per second, overwhelming the database and taking the system offline for all legitimate users.

Rate Limiting controls the traffic flow. It restricts the number of requests a user or IP address can make within a specific time window (e.g., 60 requests per minute).

This behavior should be communicated clearly via HTTP headers:

- X-RateLimit-Limit: The maximum requests allowed.
- X-RateLimit-Remaining: The number of requests left in the current window.
- X-RateLimit-Reset: The timestamp when the window resets.

When a user exceeds the limit, the API returns a 429 Too Many Requests status. This protects the infrastructure and ensures fair usage across the user base.

10. Implement Caching Headers
Retrieving data from a database is an expensive operation in terms of CPU and I/O. Retrieving data from a local cache or a Content Delivery Network (CDN) is virtually instant.

A well-designed API utilizes HTTP Caching to reduce server load. By using the Cache-Control header, the API instructs the client (browser or mobile app) on how long to store the data.

For example, Cache-Control: max-age=3600 tells the client to use the cached version of the data for the next hour.

During this time, the client does not even contact the server. This dramatically improves the user experience by making the app feel faster and significantly reduces the cost of infrastructure by lowering the number of requests the backend must process.

Conclusion
Writing code is the visible part of software engineering, but it is the hidden work of design that determines success or failure.

The transition from junior to senior involves shifting focus from “getting it to work” to “keeping it working at scale.”

- Business Constraints prevent over-engineering.
- Contracts unblock teams and parallelize work.
- Backward Compatibility ensures long-term viability.
- Security and Observability protect the system and the data.
- Reliability mechanisms like Idempotency and Rate Limiting prepare the system for the real world.
Image

By adhering to this checklist, engineers ensure that the code they eventually write is built on a foundation capable of supporting the business for years to come.