# Spring Cloud with Microservice

---

## 1. Introduction
- a) Introduction about Spring Boot and Rest API
- b) Cloud Characteristics
- c) Horizontal Scaling and Vertical Scaling
- d) Monolithic Application vs Microservices
- e) Microservice Architecture
- f) Splitting monolithic application into microservices

---

## 2. The Evolution of Spring Cloud
- a) Introduction
- b) Spring Cloud BOM and Dependency Management
- c) Netflix Eureka, a discovery server
- d) Netflix Ribbon, a client side load balancer
- e) Netflix Zuul, an edge server
- f) Netflix Hystrix, a circuit breaker

---

## 3. Spring Cloud Config Server & Config Client
- a) Problem Statement
- b) Solution Requirement
- c) Realtime Example

---

## 4. Service Discovery Using Netflix Eureka
- a) The problem with DNS based service discovery
- b) Configuring the Eureka Server
- c) Configuring clients to the Eureka Server
- d) Register microservices with Eureka Server
- e) Register Config Server with Eureka Server
- f) Eureka Configuration parameters
- g) Challenges with Service discovery

---

## 5. Microservices Interservice Communication
- a) Load balancer (cluster) Case Study
- b) Load Balancing Algorithms
- c) Discovery Client (server-side load balancing)
- d) Ribbon Client (client-side load balancing)
- e) Feign Client (Reduce Boilerplate Code)

---

## 6. Building Resilient System
- a) Circuit Breakers
- b) Implementing Resilience4J
- c) Time Limiter
- d) Retry Mechanism

---

## 7. Spring Cloud Gateway
- a) Setup Spring Cloud Gateway
- b) Configuring microservices
- c) Routing Rules
- d) Configure Eureka Server to route microservices

---

## 8. Distributed Logging and Centralized Logging
- a) Sleuth Logging
- b) Zipkin Server
- c) ELK

---

## 9. Security
- a) OAUTH

---

## Microservice Development

**Microservice Development = Java API / libraries + cloud + Third party tools**

**(Rest API's, Spring Boot) + AWS + Netflix, Apache**

---

### Q - What is the necessity of microservices?

10 year back Application was there.
1. disk / pendrive
2. buy the book at amazon.com → The book will be delivered at your door

The traffic (no. of request) would be limited.
i.e. The amount of usage of the application was very limited / fixed

Now the requirements of the application that was there 10 year back is completely different with today requirement.
i.e. The usage of the application will be huge now.

3. earlier amazon no. of request per day = 1000
   today amazon no. of request per day = 10,00,000

- Have you seen your application was down? → No, → up & running
- Have you seen your application performance issue? → No- always it would fast response

---

We should change the architecture?

**Develop → Deploying → Infrastructure**

**Paytm** → Until demonetization, it was very less traffic, after that huge demand. The traffic would be 100x time more.

Develop a product → "taste food" ---- after 3 months within a month product would be Super Success then we have to increase the resource.

1. buy new physical Server
2. Setup infrastructure
3. deploy the application

To do all these things it may take more time meanwhile the application will not perform well then users will stop using the application.

let's say starting only if we will take a big Server that will have higher resources and deploy the application.

a) If our project (product) will not succeed then the initial cost would be more.

Because of problems in vertical Scaling we should go horizontal Scaling.

---

## Vertical Scaling

| 1 CPU / 1 GB RAM | 2 CPU / 2 GB RAM | 4 CPU / 4 GB RAM |
|---|---|---|
| Physical System | Physical System | Physical System |

1. If more number of request come to your application one of the option is to scale your application was to get a bigger machine.

2. The process of increasing the amount request that we can handle through the application is called **Vertical Scaling**.

3. At any point of only Server (physical System).

**NOTE:**
1. Depending on vertical scaling always get the problem either less traffic or more traffic
2. It is very tough to get the no. of Correct resources
3. This concept doesn't work anymore

**Drawbacks:**
1. We Can't expect to handle more request for resources.
2. The cost of vertical scalability is high
3. We Can't get infinite CPU, RAM. There should be limit to hit number of CPU and RAM.

**NOTE:** Because of problems in vertical Scaling, we should go horizontal scaling.

---

## Horizontal Scalability

"We do not run our application in one Server, instead run in multiple servers."

→ Instead application run in big Server, run in smaller Server.

```
        Server1
req1 →  Application  | private ip
        
        Server2
req2 →  BALANCER → Application  | private ip

        Server3
req3 →  Application  | private ip
```

Q- when huge traffic will come? → Server4 / Application

→ Immediately add one more Server

→ In Horizontal Scaling, we can scale the application Compared to vertical scalability but another problems still exists.

1. how many servers would you keep in your cluster
2. what will be the resource of those servers
3. these servers will be physical server, so when we get a physical server we need to decide how many servers do you need to get

Here also same resource planning problems like had in vertical scaling.

---

## VM - Virtualization

Take a physical Server and create multiple virtual machines.

```
| VM1       | VM2        | VM3      |
| Linux OS  | Windows OS | Linux OS |
|           Hypervisor (virtualization software)          |
|                    OS (HOST)                            |
```

→ Create 3 virtual machine (vm)
→ each VM is treated as one physical Server as it is having Separate CPU, RAM, OS.

1. whenever we came to know more no. of the request will come to the application we can create one more vm.

2. we don't need to someplace to buy the physical machine, instead i have physical machine, hypervisor then i will create one more VM and add to cluster.

These things application developers never think but as a Company owner, they should think.

**On prime servers:**
→ Need more people to manage setup and new infrastructure add or remove the Servers

→ Need to worry about data loss preventions, if one data centre should down and get the data from another datacentre.

→ Data should be backup in same data Centres

---

## Characteristics that demand to develop application today

1. Performance
2. High Availability
3. Auto Scaling
4. Dynamic Resource provisioning
5. Zero Down time
6. Distributed

These are basic requirement of any application like banking, ecommerce ... etc.

Implementing and achieving these features with our own infrastructure using monolithic application is very difficult.

---

1. **Performance:** Application should respond in millisecond irrespective of high or low load.

2. **High Availability:** 24x7 your application should be up and running.

3. **Auto Scaling:** If more number of request will come then automatically create a new virtual machine and added to load balancer, based on CPU utilization.

   - ex: CPU is 60% used → add one VM
   - CPU is 70% used → add two VM
   - CPU is 80% used → add more vm

   This process is Called **Scale out**.

   **Scale in:** If the number of request will be less then it will remove the VM's automatically

   **Auto scaling:** Virtual machine's Can add and remove automatically based on CPU utilization.

4. **Dynamic Resource Providing:** To work with autoscaling need a dynamic resource provision. If we Can't dynamically request a resource then Can't do the autoscaling.

5. **Zero Downtime:** During application deployment or any servers migrations application should not down.

6. **Distributed:** Distributed the data across Geographical data Centre.

To implement all these 6 features cloud came into the picture, when we are using then Can't use monolithic application.

We Can use monolithic application but we will have lot of problems, that is where microservices will help.

---

## Monolithic Application

→ Most of the application developed earlier using monolithic.

→ Monolithic applications are applications where all the features of application is present in one Single deployable artifact. (Jar file one)

```
          E-Commerce Application
| Product Catalog | Shopping Cart |
| Order History   | Customer feature |
```

**Drawback:**

1. If we want to enable autoscale then it should enable the whole application not a specific feature. Because of everything bundled into a single war file if we want to scale one part of the feature we Can't do. If we want Scale one feature then it will Scale whole features. Because of this resource utilization is not up to the mark.

2. **Tight Coupling:**
   a) If we are trying to modify one of features functionality then, it might impact for other feature also.
   
   ex: If we will add product feature development then other features like SC, oh rcs would impact
   
   b) we need to more regression testing on other features and functional testing.
   c) development time and testing time would be increase.

3. **Slow feedback loop:**
   Company amazon → every 6 month new feature is released into market.

4. **Difficult to enable DevOps:**
   → Once developer develop and commit the code into git repo then it will perform CICD.
   → It will take more time to build and deploy the application.

5. **Single point of failure (SPOC):**
   → If one of the feature will stop working then all the other features also will stop working.
   
   ie: If one feature will be down then it will break whole application as down.

To overcome all these problem we should use microservices architecture.

---

## Microservices

→ Microservices are distributed, loosely coupled software objects that Carryout small no. of well defined tasks.

→ Microservices are small autonomous services which are responsible for one kind of functionality or feature.

→ Microservice is a methodology or architectural style of building software application it has provided guidelines, design patterns, and recommendations so that people adopt them in building large Scale complex business System.

→ Decompose the large Scale business Systems into multiple Smaller services which has the below characteristics.

1. **Loosely Coupled:** every service has a separate code base and own repository.
2. **Independently deployable:** every service will build and deploy Separately.
3. **Highly Scalable:** Scale the particular required feature than whole application.
4. **Resilient**
5. **Collaboratives**

---

### Q- How do you split monolithic services into microservices?

**Sol:**
1. SRP: Single Responsibility Principle
2. Bounded Context: it will split monolithic into microservices based on business functions.

**Advantages:**

1. **Loose Coupling and High Cohesion:**
   a) Change in one Service should not break other Service ie. no need to test other services.
   b) all the related features in a Single Service is Called high cohesion.

2. **Database:**
   → every microservice should have its own database
   → We Can Communicate cross-cutting database via Service

3. **Breaking Change:**
   → If any changes will do services that should have backward Compatibility.
   → Every microservices should maintain versioning. i.e. API versioning

4. Every service Single source code. i.e. it own git repo project so that build and deployment would be easy.

5. Developer Can develop the development parallel.
   a) Each service Can build-out of their own source code, without having any dependency of others.
   b) Can be independent in planning and releasing the modules without bothering about other modules.
   c) Each service Can be Scaled up independently of others.
   d) failures Can be isolated due to independent deployable.

**Advantages:**
1. Each microservice is relatively small and has its own source code, independent of other services.
   a) A developer Can easily understand the source code of the application.
   b) IDE will be faster in developing the application.
   c) Server runtimes will quickly deploy the microservices, So that the development team Can quickly test their application.

2. Services Can be deployed independently.
3. parallel development is promoted.
4. testability of the application becomes very easy.
5. Independently Scalable, here we Can achieve horizontal Scaling.

---

```
         → product service ────────→ product DB
response
Call to Rest client
         → order Service ────────→ order DB
write rest client
code to Call product
Service
```

To build the microservices not only required API's/Programming language with we need support for tools to deploy, discover, monitor and manage the microservices.

1. Eureka Server :- Service discovery
2. Fign client :- declarative client api inter service Communication
3. Hystrix :- Circuit breaker for resilience.
4. Ribbon :- Client Side load balancer
5. Zuul :- API Gateway

Even though Netflix has provided enough tools in discovering and integrating microservices, using these tools is quite complex and has to write a lot of boilerplate code.

**Spring Cloud** = Uses all the Netflix provide third-party tools to build Spring-based microservices.

Later Spring framework slowly started developing their own third-party tools rather dependent on netflix.

Spring cloud + Spring microservices (latest) has provided below set of tools.

---

1. Eureka Server Integration
2. Spring cloud config Server and Config client
3. Spring circuit breaker (Replacing Hystrix)
4. Spring API gateway (Replacing Zuul)
5. Spring load Balancer (Replacing Ribbon)
6. feign client API

**Microservices Application = Development + Deploy, manage, Integrate, discover + Infrastructure (cloud).**

**Spring Boot** = is used to develop the microservices and uses features like auto-configuration, embedded server.

**Spring Cloud** = is used to provide the integration tools like Spring cloud Config Server, eureka server, ..etc.
ie. all the required third-party tools provided.

---

```
Server1        Server2        Server3
| Configuration | | Configuration | | Configuration |
```

**Configuration:**
1. database URL, DB name, DB Password
2. actuator endpoints enabled / disabled
3. Redis Server URL, port number
4. Server.port
5. Custom properties

---

## 1. Spring Cloud Config Server

**Problem Statement:**

1. When we will's working on microservices-based application development, we should package the application Configuration within the application by placing an application.properties or YAML file.

2. will deploy the application into the Server.

3. If any changes are required in the Configuration then we need to modify it, rebuild / repackage of the application and then deploy the application into the Server.

4. If we are running the microservice applications in a Containerized environment, then we need to build the image and deployed into artifact (docker hub..etc).

**Q-** Can we inject the updated configuration into the application without rebuilding and restarting the Server?

**Sol:** Yes, we need to externalize the configuration. ie. move the configuration.

```
Externalise the Configuration
↓
Service1.properties    → Combine all three
Service2.properties      server configuration
Service3.properties      in one separate
                         server is called
                         Config Server
```

**NOTE:** Don't keep any application related Configuration inside of the project, move to an external location → Git Repository (external Configuration)

```
service-one ──→ 1.git repo details     → <application-name>.properties
                  → url                   service-one.properties
                  → username              service-one.dev.properties
                  → password              service-one.prod.properties
                ↕
                itp: application           service.two.properties
                name                       service.two.dev.properties
                service-one
                ↓
            Config Server               Git Repository
```

**input:** application-name = service-one
**output:** service-one.properties
            application.properties

**NOTE:** Config server will expose the rest API to retrieve the Configuration for the microservices.

**Microservice --> Config Server --→ external Configuration (git)**

**@RefreshScope** → it is used whenever there is a change in the configuration it will automatically refresh the configuration.

`http://localhost:9090/actuator/refresh` - it is used to refresh the consumer (service-one) to load the latest changes from the spring config server.

---

```
microservice    Spring Cloud Config Server    git repo
    ──────────────→────────────────────────→  password = 123
    ←──────────────────────────────────────
```

a) If will get plain text then → 1) decrypt here and send plain text to microservice

b) If we will get encrypted text then decrypt here. and get the plain text. → 2) send encrypted value as it is to microservices.

**Q-** How to read the configuration details from the git repo.

**External Configuration with encrypted values:**
1. Keep encrypted.key = value in the application.properties or bootstrap.properties file.

2. `http://localhost:9091/encrypt` use this endpoint to get the encrypted value, it is post endpoint send the plain text in the body it will give encrypted text.

3. get the encrypted text and update it into a git repo.

4. By default Spring.cloud.Config.server.enabled = true this property will decrypt the encrypted value.

In Configuration file not recommended to keep plain text data, we have to encrypt the data.

---

**Steps to implement without restart microservice how to get the updated Configuration details?**

1. update the values in Configuration file
2. use /actuator/refresh endpoint to refresh updated Configuration at microservice.
3. @RefreshScope is holding the properties through @value usage.

Every microservice application during the bootup has to invoke Config Server and load the microservice Configuration in a parent IoC Container, which seems to be boilerplate. Spring cloud has provided component Called:

1. Spring-cloud-bootstrap
2. Spring-cloud Config client.

**Spring cloud bootstrap:** to load the bootstrap.properties file and inject values into environment object.

1. If we want some properties to be available during the time of bootstrapping the application then place them in bootstrap.properties or yml file.

2. If we have some properties which are application-specific and will not change over the time then place them in the application.properties file.

3. If we have some application-specific properties file but those will change over time, then place in cloud-Config Server (external Configuration).

---

## Service Discovery

```
application.properties
application-dev.     order Service ──────→ product Service ← 10.0.0.250
properties                                                  ← 10.0.0.260
                     http://10.0.0.250:8080/product/oid     ← 10.0.0.280
prod-service-url=http//    RestTemplate template = new RestTemplate();     ← 10.0.0.290
prod-application.properties    template.getForObject(url, Object);
```

During autoscaling, Services would be restart. while restarting the application, ip address would be change.

**Problem Statement:**

1. IP address doesn't remain Constant in the cloud then a chance of IP address will be the same or it will be different during autoscale or restart the Service.

2. If IP address keeps on changing then it is difficult to call the service.

3. If Order Service should call to product-service then, order Service should know the location of product service. whenever product Service instances/URL would change then it may be impact to order Service.

4. we Can't predict no. of request, in horizontal scaling the IP address will be changes frequently during scale out and scale in.

**NOTE:** In microservices architecture never hardcode IP address or port of any other Service.

**Q:** If we don't hardcode the IP address/URL, how do I find the location of other microservices.

**Sol:** Eureka Server will provide the updated/current running ip address of the microservice.

Eureka Server will maintain the list of IP address instances of the microservices.

During microservice startup, it will store the instance details into Eureka Server.

**Eureka Server = Service discovery** = It is used to store all the instance/IP address of all the microservices.

---

```
(Table of microservice ip address)
App name    | instance details
Config Server  9.00.8888
               9.00.9050
product Service 6.0.0.8800
                6.00.9911
order Service   7.0.0.8002
                7.0.0.9022
```

**Q-** How to Communicate two microservices?

**Sol:** Use Case1: Using Rest Template
In the monolithic application, we can use RestTemplate to Call another microservice because here domain name/IP address always Same.
"always IP address/hostname/domain always hardcoded"

**Use Case 2:** Using Discovery, Ribbon, Feignclient
In a microservice architecture, we should not hardcode the hostname/IP address/domain name because IP address Should not remain constant, it would change during Service restart or autoscaling.

If we don't know that what is the location of the Service, then how can we call the Service.

Here Eureka Server/Service Discovery will provide the location of the Service.
ie. it will provide the latest ipaddress of that microservices.

→ If anyone service want communicate to another service first get the ip address from Eureka of that service then call to another Service.

**Implementation Steps:**
1. Setup Eureka Server
2. Develop productservice, orderservice
3. Register productservice and Orderservice in Eureka Server.
4. OrderService should call to productservice
   a) orderservice will connect Eurekaserver and asking instance/IP list of product service
   b) write the rest-client code in Orderservice
   c) invoke/call the productservice

Eureka Server internally will use a Server-side load balancer, but this loadbalancer couldn't hold the state of the request, due to this always traffic route to only one instance.

Eurekaserver will use Discoveryclient which will take care to get the instances from Eureka and invoke/call to other microservice.

---

## Server Side Load Balancers

**Problem Statement:**
1. The load was not distributed properly, even though multiple instances are running and always get the same instance.

2. If we have 100 microservices then 100 load balancers are required, it would more cost to the Company.

3. To overcome the above problems we should choose client Side load balancing.

---

## Client Side Load Balancing

1. The client should aware of how many instances are running.
2. make sure it doesn't Send a request to the Same instance always.
3. Ribbon is used to handle the client side loadbalancing.
4. If we are running multiple instances without autoscaling then go for Ribbon loadbalancer.
5. If there are multiple instances are running without load balancers then go for client-side load balancing.

**Implementation:**
1. Create a RestTemplate Object using @LoadBalanced annotation.
2. In URL instead of given IP address, port and give the application name.

---

## Open Feign

1. Spring cloud OpenFiegn is a declarative client library that Can be used to access the microservice or rest endpoint without writing the code.

2. we Can Create declarative client API by using @feignclient annotation.

**NOTE:** feign client is Same as Ribbon client but using feign client we can remove boilerplate code. ie developer Just declare the interface which will Configure with @feignclient annotation.

→ Load balancers internally will use Round Robin and LRU algorithms to route the requests.

**Q-** How to inter-service communication in microservice architecture?

**Ans:**
1. DiscoveryClient
2. Ribbon Client
3. Feignclient

---

## Spring Cloud Gateway

There are few common requirement we want to enforce the microservice of our application like:

1. **Security:** Apply all the endpoints Security at gateway level, it will act as entry point.

2. **Routing:** based on Conditions route the request different version of microservices.

3. **Transformation:** we Can modify the both request and response body during the time of request response by attaching to filters, to the gateway asking him apply.

4. **Data aggregation:** call multiple microservices and aggregate the data and return the response client.

```
I-way:
                                → product Service
Client → API Gateway → order Service
                                → payment Service

http://10.0.150/products
http://10.0.150/orders
http://10.0.150/payment
```

```
II-way:
                        4 ────────→ product Service
                                              ↓ 2.9 Register
                    2          Register →
                         Register → Eureka service
Client → /product → product service ← 3.b
              1     ← 3.a
                              ↓ Register 2.b
                         order service
```

---

```
order service → product Service → payment service → payment gateway like paypal
```

**Problem Statement:** If the remote Service will not provide the expected behaviour, due to this resource utilization would be impact.

1. When some requests got failed, then instead of Calling remote Services Continuously, give our own response Send default response to Consumer.

---

## What is Circuit Breaker?

1. Circuit breaker is one of the integration tier design pattern which is used for protecting the client applications while access the remote Services.

2. Circuit breaker is not an Spring framework feature and it is an independent design pattern of its own and Can be applied anywhere at the client side when we are accessing remote Services.

3. Due to Several remote Services might become an unresponsive.
   a) heavy load
   b) Limitations at their end

4. If our client application is allowed to access the remote Service, it creates several problems:
   a) longer waiting time
   b) unresponsive due to blocking Threads
   c) because of Threads under blocked state and client application might crashes.

→ Instead of Implement circuit breaker design pattern manually, Spring cloud has provided third party libraries:
1. netflix hystrix
2. resilience4j
3. Spring retry
4. Sentinal