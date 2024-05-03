### Introduction to Load Balancing
Load balancing is a crucial component of System Design, as it helps distribute incoming requests and traffic evenly across multiple servers. The main goal of load balancing is to ensure high availability, reliability, and performance by avoiding overloading a single server and avoiding downtime.

Typically a load balancer sits between the client and the server accepting incoming network and application traffic and distributing the traffic across multiple backend servers using various algorithms. By balancing application requests across multiple servers, a load balancer reduces the load on individual servers and prevents any one server from becoming a single point of failure, thus improving overall application availability and responsiveness.

<p>
  <img src="https://github.com/Hemantchaurasiya/Engineering/blob/High_Level_System_Design/images/1_Load_Balencer.webp" />
</p>

To utilize full scalability and redundancy, we can try to balance the load at each layer of the system. We can add LBs at three places:
- Between the user and the web server
- Between web servers and an internal platform layer, like application servers or cache servers
- Between internal platform layer and database.

<p>
  <img src="https://github.com/Hemantchaurasiya/Engineering/blob/High_Level_System_Design/images/2_Load_Balencer.webp" />
</p>

### Key terminology and concepts
Load Balancer: A device or software that distributes network traffic across multiple servers based on predefined rules or algorithms.

Backend Servers: The servers that receive and process requests forwarded by the load balancer. Also referred to as the server pool or server farm.

Load Balancing Algorithm: The method used by the load balancer to determine how to distribute incoming traffic among the backend servers.

Health Checks: Periodic tests performed by the load balancer to determine the availability and performance of backend servers. Unhealthy servers are removed from the server pool until they recover.

Session Persistence: A technique used to ensure that subsequent requests from the same client are directed to the same backend server, maintaining session state and providing a consistent user experience.

SSL/TLS Termination: The process of decrypting SSL/TLS-encrypted traffic at the load balancer level, offloading the decryption burden from backend servers and allowing for centralized SSL/TLS management.

### How Load Balancer works?
Load balancers work by distributing incoming network traffic across multiple servers or resources to ensure efficient utilization of computing resources and prevent overload. Here are the general steps that a load balancer follows to distribute traffic:

- The load balancer receives a request from a client or user.
- The load balancer evaluates the incoming request and determines which server or resource should handle the request. This is done based on a predefined load-balancing algorithm that takes into account factors such as server capacity, server response time, number of active connections, and geographic location.
- The load balancer forwards the incoming traffic to the selected server or resource.
- The server or resource processes the request and sends a response back to the load balancer.
- The load balancer receives the response from the server or resource and sends it to the client or user who made the request.

## Load Balancing Algorithms
A load balancing algorithm is a method used by a load balancer to distribute incoming traffic and requests among multiple servers or resources. The primary purpose of a load balancing algorithm is to ensure efficient utilization of available resources, improve overall system performance, and maintain high availability and reliability.

Load balancing algorithms help to prevent any single server or resource from becoming overwhelmed, which could lead to performance degradation or failure. By distributing the workload, load balancing algorithms can optimize response times, maximize throughput, and enhance user experience. These algorithms can consider factors such as server capacity, active connections, response times, and server health, among others, to make informed decisions on how to best distribute incoming requests.

Here are the most famous load balancing algorithms:

### 1. Round Robin
This algorithm distributes incoming requests to servers in a cyclic order. It assigns a request to the first server, then moves to the second, third, and so on, and after reaching the last server, it starts again at the first.

Pros:

- Ensures an equal distribution of requests among the servers, as each server gets a turn in a fixed order.
- Easy to implement and understand.
- Works well when servers have similar capacities.

Cons:

- May not perform optimally when servers have different capacities or varying workloads.
- No consideration for server health or response time.
- Round Robin is predictable in its request distribution pattern, which could potentially be exploited by attackers who can observe traffic patterns and might find vulnerabilities in specific servers by predicting which server will handle their requests.

Example: A website with three web servers receives requests in the order A, B, C, A, B, C, and so on, distributing the load evenly among the servers.

<p>
  <img src="https://github.com/Hemantchaurasiya/Engineering/blob/High_Level_System_Design/images/1_Round_Robin_Load_Balancing_Algorithm.gif" />
</p>

### 2. Least Connections
The Least Connections algorithm directs incoming requests to the server with the lowest number of active connections. This approach accounts for the varying workloads of servers.

Pros:

- Adapts to differing server capacities and workloads.
- Balances load more effectively when dealing with requests that take a variable amount of time to process.

Cons:

- Requires tracking the number of active connections for each server, which can increase complexity.
- May not factor in server response time or health.

Example: An email service receives requests from users. The load balancer directs new requests to the server with the fewest active connections, ensuring that servers with heavier workloads are not overwhelmed.

<p>
  <img src="https://github.com/Hemantchaurasiya/Engineering/blob/High_Level_System_Design/images/2_Least_Connections_Load_Balancing_Algorithm.gif" />
</p>

### 3. Weighted Round Robin
The Weighted Round Robin algorithm is an extension of the Round Robin algorithm that assigns different weights to servers based on their capacities. The load balancer distributes requests proportionally to these weights.

Pros:

- Accounts for different server capacities, balancing load more effectively.
- Simple to understand and implement.

Cons:

- Weights must be assigned and maintained manually.
- No consideration for server health or response time.

Example: A content delivery network has three servers with varying capacities. The load balancer assigns weights of 3, 2, and 1 to these servers, respectively, distributing requests in a 3:2:1 ratio.

<p>
  <img src="https://github.com/Hemantchaurasiya/Engineering/blob/High_Level_System_Design/images/3_Weighted_Round_Robin_Load_Balancing_Algorithm.gif" />
</p>

### 4. Weighted Least Connections
The Weighted Least Connections algorithm combines the Least Connections and Weighted Round Robin algorithms. It directs incoming requests to the server with the lowest ratio of active connections to assigned weight.

Pros:

- Balances load effectively, accounting for both server capacities and active connections.
- Adapts to varying server workloads and capacities.

Cons:

- Requires tracking active connections and maintaining server weights.
- May not factor in server response time or health.

Example: An e-commerce website uses three servers with different capacities and assigned weights. The load balancer directs new requests to the server with the lowest ratio of active connections to weight, ensuring an efficient distribution of load.

<p>
  <img src="https://github.com/Hemantchaurasiya/Engineering/blob/High_Level_System_Design/images/4_Weighted_Least_Connections_Load_Balancing_Algorithm.gif" />
</p>

### 5. IP Hash
The IP Hash algorithm determines the server to which a request should be sent based on the source and/or destination IP address. This method maintains session persistence, ensuring that requests from a specific user are directed to the same server.

Pros:

- Maintains session persistence, which can be useful for applications requiring a continuous connection with a specific server.
- Can distribute load evenly when using a well-designed hash function.

Cons:

- May not balance load effectively when dealing with a small number of clients with many requests.
- No consideration for server health, response time, or varying capacities.

Example: An online multiplayer game uses the IP Hash algorithm to ensure that all requests from a specific player are directed to the same server, maintaining a continuous connection for a smooth gaming experience.

<p>
  <img src="https://github.com/Hemantchaurasiya/Engineering/blob/High_Level_System_Design/images/5_IP_Hash_Load_Balancing_Algorithm.gif" />
</p>

### 6. Least Response Time
The Least Response Time algorithm directs incoming requests to the server with the lowest response time and the fewest active connections. This method helps to optimize the user experience by prioritizing faster-performing servers.

Pros:

- Accounts for server response times, improving user experience.
- Considers both active connections and response times, providing effective load balancing.

Cons:

- Requires monitoring and tracking server response times and active connections, adding complexity.
- May not factor in server health or varying capacities.

Example: A video streaming service uses the Least Response Time algorithm to direct users to the server with the fastest response time, ensuring that videos start quickly and minimize buffering times.

<p>
  <img src="https://github.com/Hemantchaurasiya/Engineering/blob/High_Level_System_Design/images/6_Least_Response_Time_Load_Balancing_Algorithm.gif" />
</p>

### 7. Random
The Random algorithm directs incoming requests to a randomly selected server from the available pool. This method can be useful when all servers have similar capacities and no session persistence is required.

Pros:

- Simple to implement and understand.
- Can provide effective load distribution when servers have similar capacities.

Cons:

- No consideration for server health, response times, or varying capacities.
- May not be suitable for applications requiring session persistence.
- Security systems that rely on detecting anomalies or implementing rate limiting (e.g., to mitigate DDoS attacks) might find it slightly more challenging to identify malicious patterns if a Random algorithm is used, due to the inherent unpredictability in request distribution. This could potentially dilute the visibility of attack patterns.

Example: A static content delivery network uses the Random algorithm to distribute requests for images, JavaScript files, and CSS stylesheets among multiple servers. This ensures an even distribution of load and reduces the chances of overloading any single server.

<p>
  <img src="https://github.com/Hemantchaurasiya/Engineering/blob/High_Level_System_Design/images/7_Random_Load_Balancing_Algorithm.gif" />
</p>

### 8. Least Bandwidth
The Least Bandwidth algorithm directs incoming requests to the server currently utilizing the least amount of bandwidth. This approach helps to ensure that servers are not overwhelmed by network traffic.

Pros:

- Considers network bandwidth usage, which can be helpful in managing network resources.
- Can provide effective load balancing when servers have varying bandwidth capacities.

Cons:

- Requires monitoring and tracking server bandwidth usage, adding complexity.
- May not factor in server health, response times, or active connections.

Example: A file hosting service uses the Least Bandwidth algorithm to direct users to the server with the lowest bandwidth usage, ensuring that servers with high traffic are not overwhelmed and that file downloads are fast and reliable.

<p>
  <img src="https://github.com/Hemantchaurasiya/Engineering/blob/High_Level_System_Design/images/8_Least_Bandwidth_Load_Balancing_Algorithm.gif" />
</p>

### 9. Custom Load
The Custom Load algorithm allows administrators to create their own load balancing algorithm based on specific requirements or conditions. This can include factors such as server health, location, capacity, and more.

Pros:

- Highly customizable, allowing for tailored load balancing to suit specific use cases.
- Can consider multiple factors, including server health, response times, and capacity.

Cons:

- Requires custom development and maintenance, which can be time-consuming and complex.
- May require extensive testing to ensure optimal performance.

Example: An organization with multiple data centers around the world develops a custom load balancing algorithm that factors in server health, capacity, and geographic location. This ensures that users are directed to the nearest healthy server with sufficient capacity, optimizing user experience and resource utilization.
