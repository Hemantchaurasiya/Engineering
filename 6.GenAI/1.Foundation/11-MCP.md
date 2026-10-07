# Spring AI MCP (Model Context Protocol) — Effective Notes

> Covers: **MCP Overview**, **MCP Annotations**, **MCP Client Boot Starter**, **MCP Server Boot Starter**, **STDIO & SSE servers**, **Streamable-HTTP servers**, **Stateless servers**, and **MCP Utilities**.
> Simple language notes. Each concept has: **Key Points**, **Use Cases**, **Where to Use**, **Problem Solved**, and **Java Example**.

---

## Table of Contents

**Part A — MCP basics**
1. [What is MCP?](#1-what-is-mcp)
2. [MCP Java SDK architecture (3 layers)](#2-mcp-java-sdk-architecture-3-layers)
3. [MCP Client and MCP Server (what each does)](#3-mcp-client-and-mcp-server-what-each-does)
4. [Spring AI MCP Boot Starters (overview)](#4-spring-ai-mcp-boot-starters-overview)
5. [Upgrading to Spring AI 2.0](#5-upgrading-to-spring-ai-20)

**Part B — MCP Annotations**
6. [MCP Annotations overview](#6-mcp-annotations-overview)
7. [Special parameters](#7-special-parameters)
8. [Annotation configuration and quick example](#8-annotation-configuration-and-quick-example)

**Part C — MCP Client Boot Starter**
9. [Client starters and common properties](#9-client-starters-and-common-properties)
10. [STDIO transport (and Windows)](#10-stdio-transport-and-windows)
11. [Streamable-HTTP and SSE transports](#11-streamable-http-and-sse-transports)
12. [Sync vs Async client](#12-sync-vs-async-client)
13. [Client customizers](#13-client-customizers)
14. [Tool filtering (McpToolFilter)](#14-tool-filtering-mcptoolfilter)
15. [Tool name prefix generation](#15-tool-name-prefix-generation)
16. [ToolContext to MCP meta converter](#16-toolcontext-to-mcp-meta-converter)
17. [Disable MCP ToolCallback auto-config](#17-disable-mcp-toolcallback-auto-config)
18. [Client annotations](#18-client-annotations)
19. [Client usage example](#19-client-usage-example)

**Part D — MCP Server Boot Starter**
20. [Server starters and protocols](#20-server-starters-and-protocols)
21. [Securing the MCP server (important)](#21-securing-the-mcp-server-important)
22. [Server capabilities](#22-server-capabilities)
23. [Sync vs Async server](#23-sync-vs-async-server)
24. [Server annotations and McpTransportContext](#24-server-annotations-and-mcptransportcontext)
25. [Common server properties](#25-common-server-properties)
26. [STDIO MCP server](#26-stdio-mcp-server)
27. [SSE MCP server (deprecated)](#27-sse-mcp-server-deprecated)
28. [Streamable-HTTP MCP server](#28-streamable-http-mcp-server)
29. [Stateless MCP server](#29-stateless-mcp-server)
30. [Server features: tools, resources, prompts, completions](#30-server-features-tools-resources-prompts-completions)
31. [Server features: logging, progress, roots, ping, keep-alive](#31-server-features-logging-progress-roots-ping-keep-alive)
32. [Protocol comparison](#32-protocol-comparison)

**Part E — Utilities**
33. [MCP utilities (ToolCallback, providers, McpToolUtils)](#33-mcp-utilities)
34. [Native image support](#34-native-image-support)

**Part F — Revision**
35. [Cheat sheet](#35-cheat-sheet)
36. [Common mistakes](#36-common-mistakes)
37. [Interview quick Q&A](#37-interview-quick-qa)

---

# Part A — MCP basics

## 1. What is MCP?

**Key Points**
- **MCP (Model Context Protocol)** = a **standard protocol** that lets AI models use **external tools and resources** in a structured way.
- Think of it as a **bridge** between AI and the real world: databases, APIs, file systems, other services, all through **one consistent interface**.
- Supports **multiple transports** (STDIO, HTTP/SSE, Streamable-HTTP).
- **MCP Java SDK** = Java implementation, with **sync and async** styles.
- Spring AI supports **both sides**:
  1. **Consume** MCP servers (build AI apps that use tools from MCP servers).
  2. **Create** MCP servers (expose your Spring services to the wider AI world).
- Spring AI gives **Boot Starters** and **MCP Java Annotations**.

**Use Cases**
- Use ready-made tools (filesystem, web search, GitHub) in your agent.
- Expose your company's order/inventory services as tools for any AI client.

**Where to Use**
- Any app where AI needs standard access to tools and data.

**Problem Solved**
- Without MCP, each AI app writes custom adapters for each tool. MCP = **write once, use from any MCP client**.

**Java Example**
```java
// Consume side: tools from MCP servers become Spring AI tools
@Autowired SyncMcpToolCallbackProvider mcpTools;

String answer = chatClient.prompt("Search the web for Spring AI news")
    .tools(mcpTools)
    .call()
    .content();
```

---

## 2. MCP Java SDK architecture (3 layers)

**Key Points**
| Layer | Classes | Job |
|---|---|---|
| **Client/Server (top)** | `McpClient`, `McpServer` | Main logic and protocol operations; use the session layer below |
| **Session (middle)** | `McpSession`, `McpClientSession`, `McpServerSession` | Communication patterns and connection state |
| **Transport (bottom)** | `McpTransport` | JSON-RPC message **serialization/deserialization**; many transports (STDIO, HTTP/SSE, Streamable-HTTP...) |

```
McpClient / McpServer
        ↓
McpSession (client/server session)
        ↓
McpTransport (STDIO | SSE | Streamable-HTTP ...)
```

**Problem Solved**
- Separation of concerns: change the transport without changing your tool logic.
- For low-level API details see the MCP Java SDK docs. For easy setup use **Boot Starters**.

---

## 3. MCP Client and MCP Server (what each does)

### MCP Client
- Connects to and manages connections with MCP servers. Handles:
  - **Protocol version negotiation** (compatibility).
  - **Capability negotiation** (which features are available).
  - Message transport and **JSON-RPC**.
  - **Tool discovery and execution**.
  - **Resource** access and management.
  - **Prompt** system interaction.
  - Optional: **Roots** management, **Sampling** support, sync and async operations.
- Client transports: **Stdio**, **Java HttpClient SSE**, **WebFlux SSE**.

### MCP Server
- Provides tools, resources, and capabilities to clients. Handles:
  - Server-side protocol operations.
  - **Tool** exposure and discovery.
  - **Resources** with **URI-based access**.
  - **Prompt templates**.
  - Capability negotiation.
  - **Structured logging and notifications**.
  - **Concurrent client** connections.
  - Sync and async APIs.
- Server transports: **Stdio**, **Streamable-HTTP**, **Stateless Streamable-HTTP**, **SSE**.

| Concept | Meaning in simple words |
|---|---|
| **Tool** | A function the AI can call |
| **Resource** | Data the AI can read (by URI) |
| **Prompt** | A reusable prompt template |
| **Completion** | Auto-complete suggestions for prompt/resource arguments |
| **Roots** | Folders the client lets the server work in |
| **Sampling** | Server asks the client's LLM to generate text |
| **Elicitation** | Server asks the user for more info (via the client) |

---

## 4. Spring AI MCP Boot Starters (overview)

**Client starters**
| Starter | What it gives |
|---|---|
| `spring-ai-starter-mcp-client` | Core: STDIO, Servlet-based Streamable-HTTP, Stateless Streamable-HTTP, SSE |
| `spring-ai-starter-mcp-client-webflux` | WebFlux-based Streamable-HTTP, Stateless Streamable-HTTP, SSE |

**Server starters**
| Server type | Dependency | Property |
|---|---|---|
| **STDIO** | `spring-ai-starter-mcp-server` | `spring.ai.mcp.server.stdio=true` |
| SSE WebMVC *(deprecated since 2.0.0)* | `spring-ai-starter-mcp-server-webmvc` | `spring.ai.mcp.server.protocol=SSE` |
| **Streamable-HTTP WebMVC** | `spring-ai-starter-mcp-server-webmvc` | `spring.ai.mcp.server.protocol=STREAMABLE` |
| **Stateless WebMVC** | `spring-ai-starter-mcp-server-webmvc` | `spring.ai.mcp.server.protocol=STATELESS` |
| SSE WebFlux *(deprecated since 2.0.0)* | `spring-ai-starter-mcp-server-webflux` | `spring.ai.mcp.server.protocol=SSE` |
| **Streamable-HTTP WebFlux** | `spring-ai-starter-mcp-server-webflux` | `spring.ai.mcp.server.protocol=STREAMABLE` |
| **Stateless WebFlux** | `spring-ai-starter-mcp-server-webflux` | `spring.ai.mcp.server.protocol=STATELESS` |

> The overview page lists "SSE or empty" as the property for SSE (default). The Server Boot Starter page marks SSE as **deprecated** and recommends **STREAMABLE**.

**Easy choice**
- WebMVC (servlet) app → `...-webmvc`. Reactive app → `...-webflux`.
- If your project has `spring-boot-starter-web`, use **webmvc** (Spring Boot prefers `DispatcherServlet` when both are present).

---

## 5. Upgrading to Spring AI 2.0

**Key Points**
- **Breaking change**: Spring-specific MCP transports (`mcp-spring-webflux`, `mcp-spring-webmvc`) **moved from the MCP Java SDK into Spring AI**.
- **Maven group ID changed**: `io.modelcontextprotocol.sdk` → **`org.springframework.ai`**.
- **Java packages moved** to `org.springframework.ai...`:

| Class | New package |
|---|---|
| `WebFluxSseServerTransportProvider`, `WebFluxStreamableServerTransportProvider`, `WebFluxStatelessServerTransport` | `org.springframework.ai.mcp.server.webflux.transport` |
| `WebMvcSseServerTransportProvider`, `WebMvcStreamableServerTransportProvider`, `WebMvcStatelessServerTransport` | `org.springframework.ai.mcp.server.webmvc.transport` |
| `WebFluxSseClientTransport`, `WebClientStreamableHttpTransport` | `org.springframework.ai.mcp.client.webflux.transport` |

- **SDK version**: Spring AI 2.0 needs **MCP Java SDK 1.0.0 (RC1 or later)** (was 0.18.x).
- **If you only use Boot starters/auto-config**: **no Java code change**. Only update dependency coordinates. With `spring-ai-bom` or starters, **no explicit version** needed.

**Java Example**
```xml
<!-- Before -->
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp-spring-webflux</artifactId>
</dependency>

<!-- After -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>mcp-spring-webflux</artifactId>
</dependency>
```
```java
// After
import org.springframework.ai.mcp.server.webflux.transport.WebFluxSseServerTransportProvider;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcSseServerTransportProvider;
import org.springframework.ai.mcp.client.webflux.transport.WebFluxSseClientTransport;
import org.springframework.ai.mcp.client.webflux.transport.WebClientStreamableHttpTransport;
```

---

# Part B — MCP Annotations

## 6. MCP Annotations overview

**Key Points**
- Module that gives an **annotation-based** (declarative) way to build MCP servers and client handlers. Built on top of the MCP Java SDK.
- Benefits: less boilerplate, **automatic JSON schema** for tool parameters, access to special context parameters, **auto-discovery** by scanning.

| Server annotations | Purpose |
|---|---|
| `@McpTool` | Tools, with automatic JSON schema |
| `@McpResource` | Resources through **URI templates** |
| `@McpPrompt` | Generates prompt messages |
| `@McpComplete` | Auto-completion |

| Client annotations | Purpose |
|---|---|
| `@McpLogging` | Log message notifications |
| `@McpSampling` | Sampling requests |
| `@McpElicitation` | Elicitation requests (ask user for more info) |
| `@McpProgress` | Progress notifications for long operations |
| `@McpToolListChanged` | Tool list change notifications |
| `@McpResourceListChanged` | Resource list change notifications |
| `@McpPromptListChanged` | Prompt list change notifications |

**Dependency** (automatically included with any MCP Boot Starter)
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-mcp-annotations</artifactId>
</dependency>
```

**Problem Solved**
- Replaces long manual specification code with simple annotated methods.

---

## 7. Special parameters

**Key Points**
- Special parameter types are **injected automatically** and **excluded from JSON schema** generation.

| Type | What it gives |
|---|---|
| `McpSyncRequestContext` | Unified access to: original request, server exchange (stateful), transport context (stateless), and helper methods for **logging, progress, sampling, elicitation, roots**. Supported in Complete, Prompt, Resource, Tool methods. |
| `McpAsyncRequestContext` | Same, but **reactive (`Mono`)** return types |
| `McpTransportContext` | Lightweight transport-level context for **stateless** operations |
| `@McpProgressToken` | Marks a parameter to receive the **progress token**. (With request context, use `ctx.request().progressToken()`.) |
| `McpMeta` | Access **metadata** from requests/notifications/results. Excluded from parameter count limits. (With request context, use `ctx.requestMeta()`.) |
| `MetaProvider` | Interface to supply `_meta` data for tool/prompt/resource declarations via `metaProvider` attribute |
| `McpSyncServerExchange` / `McpAsyncServerExchange` | Full server context for advanced operations |
| `CallToolRequest` | Dynamic schema support for flexible tools |

**Use Cases**
- Send progress updates, log from a tool, read request metadata, read HTTP headers.

**Java Example**
```java
@Component
public class ReportTools {

    @McpTool(description = "Generate a long report")
    public String generateReport(String topic, McpSyncRequestContext ctx) {
        // 'ctx' is injected; it is NOT part of the tool's JSON schema
        return "Report on " + topic;
    }
}
```

---

## 8. Annotation configuration and quick example

**Key Points**
- Annotation scanning is **enabled by default** with Boot starters. Turn off with properties.

```yaml
# Client
spring:
  ai:
    mcp:
      client:
        annotation-scanner:
          enabled: true
# Server
spring:
  ai:
    mcp:
      server:
        annotation-scanner:
          enabled: true
```

**Java Example**
```java
@Component
public class CalculatorTools {

    @McpTool(name = "add", description = "Add two numbers together")
    public int add(
            @McpToolParam(description = "First number", required = true) int a,
            @McpToolParam(description = "Second number", required = true) int b) {
        return a + b;
    }

    @McpTool(name = "multiply", description = "Multiply two numbers")
    public double multiply(
            @McpToolParam(description = "First number", required = true) double x,
            @McpToolParam(description = "Second number", required = true) double y) {
        return x * y;
    }
}

@Component
public class LoggingHandler {

    @McpLogging(clients = "my-server")
    public void handleLoggingMessage(LoggingMessageNotification notification) {
        System.out.println("Received log: " + notification.level() + " - " + notification.data());
    }
}
```
> `clients = "my-server"` links a handler to a specific named client connection.

---

# Part C — MCP Client Boot Starter

## 9. Client starters and common properties

**Key Points**
- Gives: **multiple client instances**, auto initialization, **multiple named transports** (STDIO, HTTP/SSE, Streamable HTTP), integration with Spring AI's **tool execution**, **tool filtering**, **tool name prefixing**, lifecycle cleanup, and **customizers**.
- **Standard starter** (`spring-ai-starter-mcp-client`): connects to **one or more** servers over STDIO, SSE, Streamable-HTTP, Stateless Streamable-HTTP. HTTP transports use the **JDK HttpClient**. **Each connection = a new client instance.** Choose SYNC or ASYNC (**cannot mix**).
- **WebFlux starter**: same but WebFlux-based. **Recommended for production** HTTP connections.

| Property (prefix `spring.ai.mcp.client`) | Meaning | Default |
|---|---|---|
| `enabled` | Enable/disable MCP client | `true` |
| `name` | Client instance name | `spring-ai-mcp-client` |
| `version` | Client version | `1.0.0` |
| `initialized` | Initialize clients on creation | `true` |
| `request-timeout` | Request timeout | `20s` |
| `type` | `SYNC` or `ASYNC` (no mixing) | `SYNC` |
| `root-change-notification` | Root change notifications for all clients | `true` |
| `toolcallback.enabled` | MCP tool callback integration with Spring AI | `true` |
| `annotation-scanner.enabled` | Auto-scan client annotations | `true` |

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-client</artifactId>
</dependency>
<!-- or for production HTTP -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-client-webflux</artifactId>
</dependency>
```

---

## 10. STDIO transport (and Windows)

**Key Points**
- STDIO = the client **starts the MCP server as a process** and talks via standard in/out.
- Properties prefix `spring.ai.mcp.client.stdio`:
  - `servers-configuration` — JSON resource (**Claude Desktop format**).
  - `connections.[name].command`, `.args`, `.env`.
- **Windows special case**: `npx`, `npm`, `node`, `python.cmd`, `pip.cmd`, `mvn.cmd`, `gradle.cmd`, custom `.cmd/.bat` are **batch files**. Java `ProcessBuilder` **cannot run batch files directly** → wrap with **`cmd.exe /c`**.
- **Paths**: relative paths are portable (resolved from the app's working directory). Windows absolute paths need `\\` (escaped backslashes) or escaped forward slashes.
- **Cross-platform**: detect OS in code and build the right command. Add `@ConditionalOnMissingBean(McpSyncClient.class)` to avoid conflict with auto-config.

**Use Cases**
- Run local MCP servers (filesystem, git) as sub-processes.

**Problem Solved**
- Windows batch file problem; sharing config with Claude Desktop.

**Config Example**
```yaml
spring:
  ai:
    mcp:
      client:
        stdio:
          root-change-notification: true
          connections:
            server1:
              command: /path/to/server
              args:
                - --port=8080
                - --mode=production
              env:
                API_KEY: your-api-key
                DEBUG: "true"
```
```yaml
# External JSON file
spring:
  ai:
    mcp:
      client:
        stdio:
          servers-configuration: classpath:mcp-servers.json
```
```json
{
  "mcpServers": {
    "filesystem": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-filesystem", "/Users/username/Desktop"]
    }
  }
}
```
```json
{
  "mcpServers": {
    "filesystem": {
      "command": "cmd.exe",
      "args": ["/c", "npx", "-y", "@modelcontextprotocol/server-filesystem", "C:\\Users\\username\\Desktop"]
    }
  }
}
```

**Java Example (cross-platform)**
```java
@Bean(destroyMethod = "close")
@ConditionalOnMissingBean(McpSyncClient.class)
public McpSyncClient mcpClient() {
    ServerParameters stdioParams;

    if (isWindows()) {
        var winArgs = new ArrayList<>(Arrays.asList(
            "/c", "npx", "-y", "@modelcontextprotocol/server-filesystem", "target"));
        stdioParams = ServerParameters.builder("cmd.exe").args(winArgs).build();
    } else {
        stdioParams = ServerParameters.builder("npx")
                .args("-y", "@modelcontextprotocol/server-filesystem", "target")
                .build();
    }

    return McpClient.sync(new StdioClientTransport(stdioParams, McpJsonDefaults.getMapper()))
            .requestTimeout(Duration.ofSeconds(10))
            .build()
            .initialize();
}

private static boolean isWindows() {
    return System.getProperty("os.name").toLowerCase().contains("win");
}
```

---

## 11. Streamable-HTTP and SSE transports

### Streamable-HTTP client
- Prefix `spring.ai.mcp.client.streamable-http`. Used for **Streamable-HTTP and Stateless Streamable-HTTP** servers.
- `connections.[name].url` — base URL.
- `connections.[name].endpoint` — path suffix, default **`/mcp`**.

```yaml
spring:
  ai:
    mcp:
      client:
        streamable-http:
          connections:
            server1:
              url: http://localhost:8080
            server2:
              url: http://otherserver:8081
              endpoint: /custom-mcp
```

### SSE client
- Prefix `spring.ai.mcp.client.sse`.
- `connections.[name].url` — base URL.
- `connections.[name].sse-endpoint` — path suffix, default **`/sse`**.

```yaml
spring:
  ai:
    mcp:
      client:
        sse:
          connections:
            server1:
              url: http://localhost:8080
            server2:
              url: http://otherserver:8081
              sse-endpoint: /custom-sse
            api-server:
              url: https://api.example.com
              sse-endpoint: /v1/mcp/events?token=abc123&format=json
```

### URL splitting rule (very common mistake)
| Full URL | Config |
|---|---|
| `http://localhost:3000/mcp-hub/sse/token123` | `url` = base (scheme + host + port), `sse-endpoint: /mcp-hub/sse/token123` |
| `https://api.service.com/v2/events?key=secret` | `url` = base, `sse-endpoint: /v2/events?key=secret` |
| `http://localhost:8080/sse` | `url` = base, `sse-endpoint: /sse` (or omit for default) |

> The document's table writes `url: localhost:3000`; in your config include the scheme (`http://localhost:3000`) as the examples above do.

### Troubleshooting 404 on SSE
1. Check `url` has **only scheme, host, port**.
2. Check `sse-endpoint` **starts with `/`** and has the **full path and query**.
3. Test the full URL in a browser or `curl`.

---

## 12. Sync vs Async client

**Key Points**
- **SYNC** (default): blocking request-response. Registers **only synchronous** MCP annotated methods; async ones are **ignored**.
- **ASYNC**: reactive, non-blocking. Registers **only asynchronous** methods; sync ones are **ignored**.
- **Cannot mix** sync and async clients.
- Property: `spring.ai.mcp.client.type=SYNC|ASYNC`.

| Type | Injected beans | Annotated method return types |
|---|---|---|
| SYNC | `List<McpSyncClient>`, `SyncMcpToolCallbackProvider` | normal (`void`, `CreateMessageResult`) |
| ASYNC | `List<McpAsyncClient>`, `AsyncMcpToolCallbackProvider` | `Mono<...>` |

**Common mistake**: a `Mono` handler on a SYNC client is **silently ignored**.

---

## 13. Client customizers

**Key Points**
- Customize clients with callback interfaces: **`McpClientCustomizer<McpClient.SyncSpec>`** (sync) or **`McpClientCustomizer<McpClient.AsyncSpec>`** (async). (The doc text says `McpCustomizer` in one sentence but the code uses `McpClientCustomizer`.)
- Auto-detected from the application context. `serverConfigurationName` tells you **which server** the client is for.
- What you can customize:
  - **Request timeout**.
  - **Sampling handler**: server asks the client's LLM to generate (no server API keys needed).
  - **Roots**: folders the server may use.
  - **Elicitation handler**: server asks the user for info.
  - **Progress consumer**.
  - **Change consumers**: tools / resources / prompts changed.
  - **Logging consumer**.

**Java Example**
```java
@Component
public class CustomMcpSyncClientCustomizer implements McpClientCustomizer<McpClient.SyncSpec> {

    @Override
    public void customize(String serverConfigurationName, McpClient.SyncSpec spec) {

        spec.requestTimeout(Duration.ofSeconds(30));

        spec.sampling((CreateMessageRequest messageRequest) -> {
            CreateMessageResult result = ...;   // call your LLM here
            return result;
        });

        spec.elicitation((ElicitRequest request) ->
            new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("message", request.message())));

        spec.progressConsumer((ProgressNotification progress) -> { /* handle */ });

        spec.toolsChangeConsumer((List<McpSchema.Tool> tools) -> { /* handle */ });
        spec.resourcesChangeConsumer((List<McpSchema.Resource> resources) -> { /* handle */ });
        spec.promptsChangeConsumer((List<McpSchema.Prompt> prompts) -> { /* handle */ });

        spec.loggingConsumer((McpSchema.LoggingMessageNotification log) -> { /* handle */ });
    }
}
```

---

## 14. Tool filtering (McpToolFilter)

**Key Points**
- Select which discovered tools are included, via a bean implementing **`McpToolFilter`**.
- `test(McpConnectionInfo connectionInfo, McpSchema.Tool tool)` → `true` = include, `false` = exclude.
- `McpConnectionInfo` gives: `clientCapabilities`, `clientInfo` (name, version), `initializeResult` (server info).
- Applies to **both sync and async** providers. **No filter = include all tools.**
- **Only ONE** `McpToolFilter` bean allowed. Need several rules? Make one **composite** filter.

**Use Cases**
- Block experimental or dangerous tools; allow tools only from trusted servers.

**Problem Solved**
- Limits what an untrusted or noisy MCP server can expose to your model (security + fewer tokens).

**Java Example**
```java
@Component
public class CustomMcpToolFilter implements McpToolFilter {

    @Override
    public boolean test(McpConnectionInfo connectionInfo, McpSchema.Tool tool) {
        if (connectionInfo.clientInfo().name().equals("restricted-client")) {
            return false;
        }
        if (tool.name().startsWith("allowed_")) {
            return true;
        }
        if (tool.description() != null && tool.description().contains("experimental")) {
            return false;
        }
        return true;
    }
}
```

---

## 15. Tool name prefix generation

**Key Points**
- Avoids **name conflicts** between MCP servers by adding prefixes. Interface: **`McpToolNamePrefixGenerator`**.
- **Default**: `DefaultMcpToolNamePrefixGenerator`:
  - Tracks all connections and tool names for **uniqueness**.
  - Replaces non-alphanumeric characters with `_` (`my-tool` → `my_tool`).
  - Duplicates get a **counter prefix** (`alt_1_search`, `alt_2_search`).
  - **Thread-safe** and **idempotent** (same client/server/tool always gets the same name).
  - Max **64 characters** (truncates from the **beginning**).
- Example: first `search` → `search`; second `search` from another connection → `alt_1_search`.
- **`McpToolNamePrefixGenerator.noPrefix()`**: no prefix. With multiple servers and duplicate names → **`IllegalStateException`**. Not recommended.
- Detected automatically through `ObjectProvider`.
- Custom example: use server name and version as prefix.
- Does **not** know about local `@Tool` names (see Tool Calling notes).

**Java Example**
```java
@Component
public class CustomToolNamePrefixGenerator implements McpToolNamePrefixGenerator {
    @Override
    public String prefixedToolName(McpConnectionInfo connectionInfo, Tool tool) {
        String serverName = connectionInfo.initializeResult().serverInfo().name();
        String serverVersion = connectionInfo.initializeResult().serverInfo().version();
        return serverName + "_v" + serverVersion.replace(".", "_") + "_" + tool.name();
    }
}

// Disable prefixing (not recommended with many servers)
@Configuration
public class McpConfiguration {
    @Bean
    public McpToolNamePrefixGenerator mcpToolNamePrefixGenerator() {
        return McpToolNamePrefixGenerator.noPrefix();
    }
}
```

---

## 16. ToolContext to MCP meta converter

**Key Points**
- Converts Spring AI **`ToolContext`** into **MCP tool-call metadata** (`_meta`) through **`ToolContextToMcpMetaConverter`**.
- Lets you pass extra info (user id, token, **progressToken**) with the LLM's call arguments.
- **Default** (`ToolContextToMcpMetaConverter.defaultConverter()`):
  - Removes the MCP exchange key (`McpToolUtils.TOOL_CONTEXT_MCP_EXCHANGE_KEY`).
  - Removes **null** values.
  - Passes the rest as metadata.
- **`noOp()`**: returns an empty map (disables conversion).
- Applies to sync and async tool callbacks automatically.
- **Security note**: anything in the tool context may be sent to the MCP server as metadata. Do not put secrets there unless the server is trusted, or use `noOp()`.

**Java Example**
```java
String response = ChatClient.create(chatModel)
        .prompt("Tell me more about the customer with ID 42")
        .toolContext(Map.of("progressToken", "my-progress-token"))
        .call()
        .content();

@Component
public class CustomToolContextToMcpMetaConverter implements ToolContextToMcpMetaConverter {
    @Override
    public Map<String, Object> convert(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return Map.of();
        }
        Map<String, Object> metadata = new HashMap<>();
        for (Map.Entry<String, Object> entry : toolContext.getContext().entrySet()) {
            if (entry.getValue() != null) {
                metadata.put("app_" + entry.getKey(), entry.getValue());
            }
        }
        metadata.put("timestamp", System.currentTimeMillis());
        metadata.put("source", "spring-ai");
        return metadata;
    }
}

// Disable
@Bean
public ToolContextToMcpMetaConverter toolContextToMcpMetaConverter() {
    return ToolContextToMcpMetaConverter.noOp();
}
```

---

## 17. Disable MCP ToolCallback auto-config

**Key Points**
- Enabled by default. Turn off: `spring.ai.mcp.client.toolcallback.enabled=false`.
- When off, **no `ToolCallbackProvider` bean** is created from MCP tools.
- Remember: the MCP provider is **not auto-attached** to `ChatClient`; you pass it via `.tools()` / `.defaultTools()`.

---

## 18. Client annotations

**Key Points**
- The starter **detects and registers** annotated handler methods:
  - `@McpLogging`, `@McpSampling`, `@McpElicitation`, `@McpProgress`, `@McpToolListChanged`, `@McpResourceListChanged`, `@McpPromptListChanged`.
- Use **`clients = "server1"`** to bind to a specific named connection.
- Sync handlers for SYNC clients, `Mono` handlers for ASYNC clients.

**Java Example**
```java
@Component
public class McpClientHandlers {

    @McpLogging(clients = "server1")
    public void handleLoggingMessage(LoggingMessageNotification notification) {
        System.out.println("Received log: " + notification.level() + " - " + notification.data());
    }

    @McpSampling(clients = "server1")
    public CreateMessageResult handleSamplingRequest(CreateMessageRequest request) {
        String response = generateLLMResponse(request);
        return CreateMessageResult.builder(Role.ASSISTANT, response, "gpt-4").build();
    }

    @McpProgress(clients = "server1")
    public void handleProgressNotification(ProgressNotification notification) {
        double percentage = notification.progress() * 100;
        System.out.println(String.format("Progress: %.2f%% - %s", percentage, notification.message()));
    }

    @McpToolListChanged(clients = "server1")
    public void handleToolListChanged(List<McpSchema.Tool> updatedTools) {
        toolRegistry.updateTools(updatedTools);
    }
}

// Async version (ASYNC client type)
@McpSampling(clients = "server1")
public Mono<CreateMessageResult> handleAsyncSampling(CreateMessageRequest request) {
    return Mono.fromCallable(() -> {
        String response = generateLLMResponse(request);
        return CreateMessageResult.builder(Role.ASSISTANT, response, "gpt-4").build();
    }).subscribeOn(Schedulers.boundedElastic());
}
```

---

## 19. Client usage example

**Config**
```yaml
spring:
  ai:
    mcp:
      client:
        enabled: true
        name: my-mcp-client
        version: 1.0.0
        request-timeout: 30s
        type: SYNC
        sse:
          connections:
            server1:
              url: http://localhost:8080
            server2:
              url: http://otherserver:8081
        streamable-http:
          connections:
            server3:
              url: http://localhost:8083
              endpoint: /mcp
        stdio:
          root-change-notification: false
          connections:
            server1:
              command: /path/to/server
              args:
                - --port=8080
                - --mode=production
              env:
                API_KEY: your-api-key
                DEBUG: "true"
```

**Java Example**
```java
@Autowired
private List<McpSyncClient> mcpSyncClients;           // sync
// or: private List<McpAsyncClient> mcpAsyncClients;  // async

@Autowired
private SyncMcpToolCallbackProvider toolCallbackProvider;
ToolCallback[] toolCallbacks = toolCallbackProvider.getToolCallbacks();
```
Example apps: Brave Web Search Chatbot, Default MCP Client Starter, WebFlux MCP Client Starter.

---

# Part D — MCP Server Boot Starter

## 20. Server starters and protocols

**Key Points**
- Auto-configures **tools, resources, prompts**, supports STDIO / SSE / Streamable-HTTP / Stateless, sync and async, multiple transports, change notifications, and **annotation-based** development with bean scanning.
- Four protocols:

| Protocol | Meaning |
|---|---|
| **STDIO** | In-process; talks over standard in/out. `spring.ai.mcp.server.stdio=true`. Not network-accessible. |
| **SSE** | Server-Sent Events; independent process, many clients. **Deprecated since 2.0.0** → use STREAMABLE. |
| **STREAMABLE** | HTTP POST + GET with optional SSE streaming; independent process, many clients. **Replaces SSE.** `protocol=STREAMABLE` |
| **STATELESS** | No session state between requests. Good for microservices/cloud-native. `protocol=STATELESS` |

---

## 21. Securing the MCP server (important)

**Key Points**
- HTTP-based servers (SSE, Streamable-HTTP, Stateless) expose an **unauthenticated JSON-RPC endpoint by default** (`POST /mcp` by default).
- The starters **do not add any authentication or authorization**. Any client that can reach the endpoint can **list and call every tool, resource, and prompt**.
- **Before exposing beyond localhost, you MUST add a security layer** (e.g., **Spring Security**, **MCP Security** library).
- Registering a tool/resource/prompt = **deciding to expose it**.
- **Does NOT apply to STDIO** (in-process, not network-accessible).

**Use Cases**
- Any MCP server deployed on a network.

**Problem Solved**
- Prevents unauthorized access to your internal tools and data.

**Java Example (idea with Spring Security)**
```java
@Configuration
@EnableWebSecurity
class McpSecurityConfig {

    @Bean
    SecurityFilterChain mcpSecurity(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(a -> a
                .requestMatchers("/mcp/**", "/sse", "/mcp/message").authenticated()
                .anyRequest().denyAll())
            .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))
            .csrf(csrf -> csrf.disable());   // check CSRF needs for your clients
        return http.build();
    }
}
```
> This is a sketch of the idea, not from your document. Check the MCP Security docs for the recommended setup.

---

## 22. Server capabilities

**Key Points**
| Capability | Purpose |
|---|---|
| **Tools** | Functions the model can call |
| **Resources** | Data exposed to clients |
| **Prompts** | Prompt templates |
| **Utility/Completions** | Argument autocomplete for prompts/resource URIs |
| **Utility/Logging** | Structured log messages to clients |
| **Utility/Progress** | Progress tracking for long-running operations |
| **Utility/Ping** | Health check |

- **All enabled by default.** Disabling one means the server **won't register or expose** that feature.
- Toggle with `spring.ai.mcp.server.capabilities.tool|resource|prompt|completion=true|false`.

---

## 23. Sync vs Async server

**Key Points**
- **SYNC** (default, `McpSyncServer`): request-response. Registers **only sync** annotated methods (async ignored). `spring.ai.mcp.server.type=SYNC`.
- **ASYNC** (`McpAsyncServer`): non-blocking, Project Reactor. Registers **only async** annotated methods (sync ignored). `spring.ai.mcp.server.type=ASYNC`. **Recommended for reactive/WebFlux apps.**

---

## 24. Server annotations and McpTransportContext

**Key Points**
- Annotations: `@McpTool`, `@McpResource`, `@McpPrompt`, `@McpComplete`. Special parameters: `McpMeta`, `@McpProgressToken`, `McpSyncServerExchange` / `McpAsyncServerExchange`, `McpTransportContext`, `CallToolRequest`.
- Auto-config: scans beans with MCP annotations → creates specifications → registers them → handles sync/async based on config.
- **`McpTransportContext`** is **empty by default** (`McpTransportContext.EMPTY`) on purpose (keeps the server transport-agnostic).
- Need **HTTP headers, remote host, etc.** in tools? Configure a **`TransportContextExtractor`** (`contextExtractor(...)`) on the **transport provider**, then read via `McpSyncRequestContext.transportContext()`.

**Use Cases**
- Read the `Authorization` header inside a tool to identify the caller.

**Java Example**
```java
@Component
public class CalculatorTools {

    @McpTool(name = "add", description = "Add two numbers together")
    public int add(
            @McpToolParam(description = "First number", required = true) int a,
            @McpToolParam(description = "Second number", required = true) int b) {
        return a + b;
    }

    @McpResource(uri = "config://{key}", name = "Configuration")
    public String getConfig(String key) {
        return configData.get(key);
    }
}

// WebMVC: add headers to the transport context
@Bean
public WebMvcStreamableServerTransportProvider transport() {
    return WebMvcStreamableServerTransportProvider.builder()
        .contextExtractor(serverRequest -> {
            String authorization = serverRequest.headers().firstHeader("Authorization");
            return McpTransportContext.create(Map.of("authorization", authorization));
        })
        .build();
}

// WebFlux version
@Bean
public WebFluxStreamableServerTransportProvider transport() {
    return WebFluxStreamableServerTransportProvider.builder()
        .contextExtractor(serverRequest -> {
            String authorization = serverRequest.headers().firstHeader("Authorization");
            return McpTransportContext.create(Map.of("authorization", authorization));
        })
        .build();
}

// Reading it in a tool
@McpTool
public String accessProtectedResource(McpSyncRequestContext requestContext) {
    McpTransportContext context = requestContext.transportContext();
    String authorization = (String) context.get("authorization");
    return "Successfully accessed protected resource.";
}
```
> `firstHeader` may return `null` if the header is missing. `Map.of` does not accept null values. Handle null safely in real code.

---

## 25. Common server properties

Prefix `spring.ai.mcp.server`:

| Property | Meaning | Default |
|---|---|---|
| `enabled` | Enable/disable server | `true` |
| `protocol` | `SSE` (or empty), `STREAMABLE`, `STATELESS` | SSE if empty (see notes) |
| `stdio` | Enable STDIO transport | `false` |
| `tool-callback-converter` | Convert Spring AI `ToolCallback`s into MCP tool specs | `true` |
| `name` | Server name | `mcp-server` |
| `version` | Server version | `1.0.0` |
| `instructions` | Guidance for clients on how to use this server | `null` |
| `type` | `SYNC` / `ASYNC` | `SYNC` |
| `capabilities.resource/tool/prompt/completion` | Enable/disable capability | `true` |
| `resource-change-notification` | Resource change notifications | `true` |
| `prompt-change-notification` | Prompt change notifications | `true` |
| `tool-change-notification` | Tool change notifications | `true` |
| `expose-mcp-client-tools` | Re-expose **downstream MCP client tools** as tools of this server | `false` |
| `tool-response-mime-type` | MIME type per tool name (e.g., `...tool-response-mime-type.generateImage=image/png`) | — |
| `request-timeout` | Request timeout | `20 seconds` |
| `annotation-scanner.enabled` | Scan MCP annotations | `true` |

---

## 26. STDIO MCP server

**Key Points**
- Full server features over **STDIO** transport. Good for **command-line and desktop tools**.
- **No extra web dependencies.**
- Handles tool, resource, prompt specs; capabilities; change notifications; sync/async.
- Use **STDIO clients** to connect.

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server</artifactId>
</dependency>
```
```yaml
spring:
  ai:
    mcp:
      server:
        stdio: true
        name: stdio-mcp-server
        version: 1.0.0
        type: SYNC
```
> **Important**: with STDIO, the process uses **stdout for protocol messages**. Do not print normal logs or `System.out` text to stdout, or you will corrupt the protocol. Configure logging to a file or stderr. (This is a general STDIO/MCP rule, not stated in your document.)

---

## 27. SSE MCP server (deprecated)

**Key Points**
- SSE transport on **Spring MVC** or **WebFlux**, plus **optional STDIO** (`spring.ai.mcp.server.stdio=true`).
- **WebMVC**: `spring-ai-starter-mcp-server-webmvc` (`WebMvcSseServerTransportProvider`), includes `spring-boot-starter-web` and `mcp-spring-webmvc`.
- **WebFlux**: `spring-ai-starter-mcp-server-webflux` (`WebFluxSseServerTransportProvider`), includes `spring-boot-starter-webflux` and `mcp-spring-webflux`; activates `McpWebFluxServerAutoConfiguration` and `McpServerAutoConfiguration`.
- If both `DispatcherServlet` and `DispatcherHandler` are on the classpath, Boot picks `DispatcherServlet`. With `spring-boot-starter-web` → use the **webmvc** starter.
- **SSE is deprecated since 2.0.0**. Prefer **STREAMABLE**.

**SSE properties** (prefix `spring.ai.mcp.server`, no `.sse` suffix for backward compatibility):

| Property | Meaning | Default |
|---|---|---|
| `sse-message-endpoint` | Endpoint the client uses to **send messages** | `/mcp/message` |
| `sse-endpoint` | SSE endpoint | `/sse` |
| `base-url` | URL prefix (e.g., `/api/v1` → `/api/v1/sse` and `/api/v1/mcp/message`) | — |
| `keep-alive-interval` | Keep-alive interval | `null` (disabled) |

**Config examples**
```yaml
# WebMVC
spring:
  ai:
    mcp:
      server:
        name: webmvc-mcp-server
        version: 1.0.0
        type: SYNC
        instructions: "This server provides weather information tools and resources"
        capabilities:
          tool: true
          resource: true
          prompt: true
          completion: true
        sse-message-endpoint: /mcp/messages
        keep-alive-interval: 30s
```
```yaml
# WebFlux
spring:
  ai:
    mcp:
      server:
        name: webflux-mcp-server
        version: 1.0.0
        type: ASYNC          # recommended for reactive apps
        sse-message-endpoint: /mcp/messages
        keep-alive-interval: 30s
```

---

## 28. Streamable-HTTP MCP server

**Key Points**
- Servers run as **independent processes** handling many clients with **HTTP POST and GET**, with **optional SSE streaming** for multiple server messages. **Replaces SSE.**
- Introduced with MCP **spec version 2025-03-26**.
- Great when you must **notify clients about dynamic changes** to tools/resources/prompts.
- Enable: `spring.ai.mcp.server.protocol=STREAMABLE`. Clients: use **Streamable-HTTP clients**.
- **WebMVC**: `spring-ai-starter-mcp-server-webmvc`. **WebFlux**: `spring-ai-starter-mcp-server-webflux` (non-blocking, persistent connection management).
- Supports tools, resources, prompts, completion, logging, progress, ping, root-changes.

**Streamable-HTTP properties** (prefix `spring.ai.mcp.server.streamable-http`):

| Property | Meaning | Default |
|---|---|---|
| `mcp-endpoint` | MCP endpoint path | `/mcp` |
| `keep-alive-interval` | Keep-alive interval | `null` (disabled) |
| `disallow-delete` | Disallow delete operations | `false` |

- Keep-alive for streamable-http works **only for the "listening for messages from the server" (SSE) connection**.

**Config Example**
```yaml
spring:
  ai:
    mcp:
      server:
        protocol: STREAMABLE
        name: streamable-mcp-server
        version: 1.0.0
        type: SYNC
        instructions: "This streamable server provides real-time notifications"
        resource-change-notification: true
        tool-change-notification: true
        prompt-change-notification: true
        streamable-http:
          mcp-endpoint: /api/mcp
          keep-alive-interval: 30s
```

**Java Example (a complete tiny server)**
```java
@Service
public class WeatherService {

    @Tool(description = "Get weather information by city name")
    public String getWeather(String cityName) {
        return "Sunny in " + cityName;
    }
}

@SpringBootApplication
public class McpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider weatherTools(WeatherService weatherService) {
        return MethodToolCallbackProvider.builder().toolObjects(weatherService).build();
    }
}
```
The auto-configuration registers the tool callbacks as MCP tools. Many beans producing `ToolCallback`s are **merged**.

---

## 29. Stateless MCP server

**Key Points**
- **No session state** between requests. Ideal for **microservices and cloud-native** deployments (simple scaling behind a load balancer).
- Enable: `spring.ai.mcp.server.protocol=STATELESS`. Clients: **Streamable-HTTP clients**.
- **Limitations**: stateless servers **do not support message requests to the MCP client** (e.g., **elicitation, sampling, ping**).
- **Tool context support is NOT applicable** for stateless servers (no `exchange`). Handlers get a **`context`** (`McpTransportContext`) instead of an exchange.
- Use the **stateless** spec classes: `McpStatelessServerFeatures.SyncToolSpecification`, `...SyncResourceSpecification`, `...SyncPromptSpecification`, `...SyncCompletionSpecification`.
- WebMVC: simple, no session management. WebFlux: non-blocking, **high throughput**.
- No change/notification properties in its table (no `resource-change-notification`, etc.), because it keeps no sessions.

**Stateless connection properties** (prefix `spring.ai.mcp.server.stateless`):

| Property | Meaning | Default |
|---|---|---|
| `mcp-endpoint` | MCP endpoint path | `/mcp` |
| `disallow-delete` | Disallow delete operations | `false` |

> Your document's usage example sets `streamable-http.mcp-endpoint` under a STATELESS server, while the property table says `stateless.mcp-endpoint`. Check your version for the correct prefix.

**Config Example**
```yaml
spring:
  ai:
    mcp:
      server:
        protocol: STATELESS
        name: stateless-mcp-server
        version: 1.0.0
        type: ASYNC
        instructions: "This stateless server is optimized for cloud deployments"
```

**Java Example (low-level stateless tool spec bean)**
```java
@Bean
public List<McpStatelessServerFeatures.SyncToolSpecification> myTools() {
    List<McpStatelessServerFeatures.SyncToolSpecification> tools = ...;
    return tools;
}
```

---

## 30. Server features: tools, resources, prompts, completions

### Tools
- Spring AI **tools are auto-converted** to MCP specs (sync/async by server type). Change notifications supported.
- Auto-detected from: individual `ToolCallback` beans, **lists** of `ToolCallback`s, `ToolCallbackProvider` beans.
- **De-duplicated by name** (**first occurrence wins**).
- Disable: `tool-callback-converter=false`.
- **Tool context** (stateful servers): contains an `McpSyncServerExchange` under the `exchange` key, read with `McpToolUtils.getMcpExchange(toolContext)` (to send logging notifications or create messages).

```java
@Bean
public ToolCallbackProvider myTools(...) {
    List<ToolCallback> tools = ...;
    return ToolCallbackProvider.from(tools);
}

// Low-level
@Bean
public List<McpServerFeatures.SyncToolSpecification> myTools(...) {
    List<McpServerFeatures.SyncToolSpecification> tools = ...;
    return tools;
}
```

### Resources
- Static and dynamic resources, **resource templates**, optional change notifications, sync/async conversion.

```java
@Bean
public List<McpServerFeatures.SyncResourceSpecification> myResources(...) {
    var systemInfoResource = McpSchema.Resource.builder(...);
    var resourceSpecification = new McpServerFeatures.SyncResourceSpecification(systemInfoResource, (exchange, request) -> {
        try {
            var systemInfo = Map.of(...);
            String jsonContent = new JsonMapper().writeValueAsString(systemInfo);
            return McpSchema.ReadResourceResult.builder(
                List.of(McpSchema.TextResourceContents.builder(request.uri(), jsonContent)
                    .mimeType("application/json").build())).build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate system info", e);
        }
    });
    return List.of(resourceSpecification);
}
```

### Prompts
- Prompt templates for clients; change notification; template versioning.

```java
@Bean
public List<McpServerFeatures.SyncPromptSpecification> myPrompts() {
    var prompt = McpSchema.Prompt.builder("greeting").description("A friendly greeting prompt")
        .arguments(List.of(McpSchema.PromptArgument.builder("name")
            .description("The name to greet").required(true).build())).build();

    var promptSpecification = new McpServerFeatures.SyncPromptSpecification(prompt, (exchange, getPromptRequest) -> {
        String nameArgument = (String) getPromptRequest.arguments().get("name");
        if (nameArgument == null) { nameArgument = "friend"; }
        var userMessage = PromptMessage.builder(Role.USER,
            TextContent.builder("Hello " + nameArgument + "! How can I assist you today?").build()).build();
        return GetPromptResult.builder(List.of(userMessage))
            .description("A personalized greeting message").build();
    });
    return List.of(promptSpecification);
}
```

### Completions
- Suggestions for prompt/resource arguments (sync and async).

```java
@Bean
public List<McpServerFeatures.SyncCompletionSpecification> myCompletions() {
    var completion = new McpServerFeatures.SyncCompletionSpecification(
        McpSchema.PromptReference.builder("code-completion")
            .title("Provides code completion suggestions").build(),
        (exchange, request) -> new McpSchema.CompleteResult(List.of("python", "pytorch", "pyside"), 10, true)
    );
    return List.of(completion);
}
```

> Small differences between pages (e.g., `.build()` after `ReadResourceResult.builder(...)`, `.description` vs `.title` on `PromptArgument`) are version/doc differences. Check the Javadoc of your SDK version.

---

## 31. Server features: logging, progress, roots, ping, keep-alive

| Feature | How |
|---|---|
| **Logging** | In a handler use `exchange.loggingNotification(LoggingMessageNotification.builder(LoggingLevel.INFO, "msg").logger("test-logger").build())`. Client registers `loggingConsumer`. |
| **Progress** | `exchange.progressNotification(ProgressNotification.builder("token", 0.25).total(1.0).message("in progress").build())`. Client registers `progressConsumer`. |
| **Root list changes** | When roots change, clients that support `listChanged` send a notification. Register a `BiConsumer<McpSyncServerExchange, List<McpSchema.Root>>` bean. |
| **Ping** | `exchange.ping()` checks the client is alive. |
| **Keep-alive** | Server periodically pings clients. **Disabled by default.** Enable with `keep-alive-interval`. |

**Java Example**
```java
// Logging + progress inside a tool handler
(exchange, request) -> {
    exchange.loggingNotification(LoggingMessageNotification.builder(LoggingLevel.INFO, "Starting")
        .logger("test-logger").build());
    exchange.progressNotification(ProgressNotification.builder("test-progress-token", 0.25)
        .total(1.0).message("tool call in progress").build());
    return ...;
}

// Roots change handler
@Bean
public BiConsumer<McpSyncServerExchange, List<McpSchema.Root>> rootsChangeHandler() {
    return (exchange, roots) -> logger.info("Registering root resources: {}", roots);
}
```
```yaml
# Keep-alive (SSE servers)
spring:
  ai:
    mcp:
      server:
        keep-alive-interval: 30s

# Keep-alive (Streamable-HTTP servers)
spring:
  ai:
    mcp:
      server:
        streamable-http:
          keep-alive-interval: 30s
```
> These need an `exchange`, so they work for **stateful** servers (STDIO, SSE, Streamable-HTTP), **not** stateless ones.

---

## 32. Protocol comparison

| Point | STDIO | SSE (deprecated) | Streamable-HTTP | Stateless |
|---|---|---|---|---|
| Network access | No (in-process) | Yes | Yes | Yes |
| Session state | Yes | Yes | Yes | **No** |
| Many clients | No (one host process) | Yes | Yes | Yes |
| Sampling / elicitation / ping to client | Yes | Yes | Yes | **No** |
| Tool context (`exchange`) | Yes | Yes | Yes | **No** |
| Auth needed (add security) | No | **Yes** | **Yes** | **Yes** |
| Best for | CLI / desktop tools | Legacy | New HTTP servers, live notifications | Microservices, cloud-native, scaling |
| Property | `stdio=true` | `protocol=SSE` | `protocol=STREAMABLE` | `protocol=STATELESS` |
| Client connects with | STDIO client | SSE client | Streamable-HTTP client | Streamable-HTTP client |

---

# Part E — Utilities

## 33. MCP utilities

**Key Points**
- Foundation classes for connecting **Spring AI's tool system** and **MCP**, sync and async. Used for **programmatic** client/server config. For simpler setup, use **Boot starters**.

### Tool callback adapter
- Adapts an **MCP tool** to Spring AI's **`ToolCallback`**.
```java
McpSyncClient mcpClient = ...;
Tool mcpTool = ...;
ToolCallback callback = new SyncMcpToolCallback(mcpClient, mcpTool);

ToolDefinition definition = callback.getToolDefinition();
String result = callback.call("{\"param\": \"value\"}");
```

### Tool callback providers
- Discover and provide MCP tools from clients.
```java
McpSyncClient mcpClient = ...;
ToolCallbackProvider provider = new SyncMcpToolCallbackProvider(mcpClient);
ToolCallback[] tools = provider.getToolCallbacks();

// Many clients
List<McpSyncClient> clients = ...;
List<ToolCallback> callbacks = SyncMcpToolCallbackProvider.syncToolCallbacks(clients);

// Dynamic subset of clients (e.g., per tenant/user)
@Autowired
private List<McpSyncClient> mcpSyncClients;

public ToolCallbackProvider buildProvider(Set<String> allowedServerNames) {
    List<McpSyncClient> selected = mcpSyncClients.stream()
        .filter(c -> allowedServerNames.contains(c.getServerInfo().name()))
        .toList();
    return new SyncMcpToolCallbackProvider(selected);
}
```
**Use Case**: different users get tools from different MCP servers only.

### McpToolUtils
- **ToolCallbacks → MCP tool specifications** (to expose Spring AI tools from an MCP server you build by hand):
```java
List<ToolCallback> toolCallbacks = ...;
List<SyncToolSpecifications> syncToolSpecs = McpToolUtils.toSyncToolSpecifications(toolCallbacks);

McpServer.SyncSpecification syncSpec = ...;
syncSpec.tools(syncToolSpecs);
```
- **MCP clients → ToolCallbacks**:
```java
List<McpSyncClient> syncClients = ...;
List<ToolCallback> syncCallbacks = McpToolUtils.getToolCallbacksFromSyncClients(syncClients);
```
- Async versions exist for all of these.

---

## 34. Native image support

**Key Points**
- **`McpHints`** class provides **GraalVM native image hints** for MCP schema classes.
- It **automatically registers** the needed reflection hints when building native images.
- Remember: your own `@McpTool` / `@Tool` classes should be Spring beans (or registered for reflection) for native image.

---

# Part F — Revision

## 35. Cheat sheet

```yaml
# ---- CLIENT ----
spring.ai.mcp.client.type: SYNC
spring.ai.mcp.client.request-timeout: 30s
spring.ai.mcp.client.streamable-http.connections.s1.url: http://localhost:8080
spring.ai.mcp.client.sse.connections.s2.url: http://localhost:8081
spring.ai.mcp.client.stdio.connections.s3.command: npx
spring.ai.mcp.client.toolcallback.enabled: true

# ---- SERVER ----
spring.ai.mcp.server.protocol: STREAMABLE    # or STATELESS (SSE deprecated)
spring.ai.mcp.server.stdio: true             # for STDIO
spring.ai.mcp.server.type: SYNC              # or ASYNC
spring.ai.mcp.server.name: my-server
spring.ai.mcp.server.streamable-http.mcp-endpoint: /mcp
```
```java
// Use MCP tools in ChatClient
@Autowired SyncMcpToolCallbackProvider mcpTools;
chatClient.prompt("...").tools(mcpTools).call().content();

// Expose a tool from a server
@McpTool(description = "Get weather") String getWeather(@McpToolParam(description = "City") String city) { ... }
```

| Need | Use |
|---|---|
| Use remote MCP tools | `spring-ai-starter-mcp-client` (+ `.tools(mcpTools)`) |
| Production HTTP client | `spring-ai-starter-mcp-client-webflux` |
| Expose your tools | `spring-ai-starter-mcp-server(-webmvc / -webflux)` + `@McpTool` |
| CLI/desktop server | STDIO |
| New HTTP server | `protocol=STREAMABLE` |
| Scalable, no sessions | `protocol=STATELESS` |
| Block bad tools | `McpToolFilter` |
| Fix name conflicts | `McpToolNamePrefixGenerator` |
| Secure server | Spring Security / MCP Security |
| HTTP headers in tool | `TransportContextExtractor` + `McpSyncRequestContext` |
| Windows `npx` | `cmd.exe /c npx ...` |
| Pick servers per user | `new SyncMcpToolCallbackProvider(selectedClients)` |

---

## 36. Common mistakes

1. **No security** on an HTTP MCP server → anyone who can reach it can call all tools. Add Spring Security / MCP Security.
2. Using **SSE** for new servers → deprecated since 2.0.0. Use **STREAMABLE**.
3. **Mixing sync and async** clients → not supported.
4. Writing **`Mono` handlers on a SYNC** client/server (or sync handlers on ASYNC) → **silently ignored**.
5. **Windows**: running `npx` directly → fails. Use `cmd.exe /c npx ...`.
6. **Wrong URL split** for SSE (full URL in `url`) → **404**. Keep `url` = scheme + host + port; put path/query in `sse-endpoint`.
7. Expecting **MCP tools to be auto-attached** to `ChatClient` → inject the provider and pass via `.tools(...)` / `.defaultTools(...)`.
8. **Duplicate tool names** across servers with `noPrefix()` → `IllegalStateException`. Keep the default prefix generator.
9. Defining **more than one `McpToolFilter`** bean → only one allowed; combine into a composite.
10. Using **stateless** server and expecting **sampling, elicitation, ping, or tool context exchange** → not supported.
11. Printing logs to **stdout** in a STDIO server → corrupts the protocol.
12. Putting **secrets in `ToolContext`** → default converter passes them as MCP metadata to the server. Use `noOp()` or filter.
13. Forgetting to update **Maven group ID / package names** when upgrading to Spring AI 2.0 (only needed if you reference the transport classes/artifacts directly).
14. Using **MCP SDK < 1.0.0** with Spring AI 2.0 → needs **1.0.0 (RC1+)**.
15. Choosing **webflux** starter while having `spring-boot-starter-web` → Boot uses `DispatcherServlet`; use **webmvc** starter instead.
16. Assuming `McpTransportContext` has HTTP headers by default → it is **empty**; configure a `contextExtractor`.
17. Exposing **destructive tools** on a shared MCP server without filtering/authorization. Treat each registration as exposure.
18. Using **`AlwaysEvict`-style thinking** for sessions on the server: stateless servers keep no sessions, so do not expect per-session memory.

---

## 37. Interview quick Q&A

**Q1. What is MCP?**
A standard protocol for AI models to use external tools, resources, and prompts through one consistent interface. Spring AI supports both consuming and creating MCP servers.

**Q2. What are the three layers of the MCP Java SDK?**
Client/Server layer (`McpClient`, `McpServer`), Session layer (`McpSession` and implementations), Transport layer (`McpTransport`).

**Q3. Name the MCP transports in Spring AI.**
STDIO, SSE (deprecated), Streamable-HTTP, Stateless Streamable-HTTP.

**Q4. SSE vs Streamable-HTTP?**
Streamable-HTTP uses HTTP POST/GET with optional SSE streaming and replaces SSE (introduced with spec 2025-03-26). SSE is deprecated since 2.0.0.

**Q5. What is a stateless MCP server and its limits?**
No session state; good for microservices. It cannot send requests to the client (sampling, elicitation, ping) and has no tool context exchange.

**Q6. Which starters exist for servers?**
`spring-ai-starter-mcp-server` (STDIO), `-webmvc`, `-webflux`. Choose protocol with `spring.ai.mcp.server.protocol`.

**Q7. How do you secure an HTTP MCP server?**
The starters add no auth. Put Spring Security or MCP Security in front. STDIO does not need it.

**Q8. What do MCP annotations do?**
Declare tools/resources/prompts/completions on the server (`@McpTool`, `@McpResource`, `@McpPrompt`, `@McpComplete`) and handlers on the client (`@McpLogging`, `@McpSampling`, `@McpElicitation`, `@McpProgress`, list-changed annotations).

**Q9. What happens to sync/async mismatches?**
SYNC registers only sync annotated methods; ASYNC registers only async ones. The others are ignored.

**Q10. How do you pass HTTP headers to a tool?**
Configure a `contextExtractor` on the transport provider to build an `McpTransportContext`, then read it through `McpSyncRequestContext.transportContext()`.

**Q11. How does the client avoid tool name conflicts?**
`DefaultMcpToolNamePrefixGenerator` adds counter prefixes like `alt_1_search`, sanitizes names, and limits to 64 characters.

**Q12. How do you restrict which MCP tools are used?**
Implement one `McpToolFilter` bean using connection info and tool properties.

**Q13. What does `ToolContextToMcpMetaConverter` do?**
Converts Spring AI `ToolContext` entries into MCP `_meta` (default removes the MCP exchange key and nulls; `noOp()` disables).

**Q14. Why do you need `cmd.exe /c` on Windows?**
`ProcessBuilder` cannot run `.cmd` batch files such as `npx.cmd` directly.

**Q15. How to expose Spring AI `@Tool` methods through MCP?**
Create a `ToolCallbackProvider` bean (e.g., `MethodToolCallbackProvider`); the auto-config converts it to MCP tool specs (de-duplicated by name).

**Q16. How to disable that conversion?**
`spring.ai.mcp.server.tool-callback-converter=false`.

**Q17. What does `expose-mcp-client-tools` do?**
Re-exposes downstream MCP client tools as tools of this server (default false).

**Q18. What changed for MCP in Spring AI 2.0?**
Spring transports (`mcp-spring-webflux`, `mcp-spring-webmvc`) moved from the MCP SDK to Spring AI (new group ID and packages); needs MCP SDK 1.0.0 RC1+. Starter users only change dependencies.

**Q19. What is keep-alive?**
Optional periodic pings from server to clients to check connection health; disabled by default; set `keep-alive-interval`.

**Q20. What are `McpToolUtils` and the callback providers for?**
Programmatic conversion: MCP tools to Spring AI `ToolCallback`s, and Spring AI `ToolCallback`s to MCP tool specifications.

**Q21. How do you give each user a different set of MCP servers?**
Filter `McpSyncClient`s by `getServerInfo().name()` and build a `SyncMcpToolCallbackProvider` from the selected clients.

**Q22. What is `McpHints`?**
GraalVM native-image reflection hints for MCP schema classes, registered automatically.
