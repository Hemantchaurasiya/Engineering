# Phase 6: Function Calling and Tool Integration

## Overview
Function calling (also known as tool calling) allows AI models to execute external functions based on natural language requests. Instead of just generating text, the AI can perform actions like database queries, API calls, calculations, and more.

## 6.1 Function Calling Fundamentals

### Understanding Function Calling
When a user asks "What's the weather in New York?", instead of the AI making up an answer, it can call a weather API function to get real data.

### Basic Setup

```xml
<!-- pom.xml dependencies -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

### Configuration

```yaml
# application.yml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4
          temperature: 0.7
```

## 6.2 Creating Your First Function

### Simple Calculator Function

```java
package com.example.functions;

import org.springframework.stereotype.Component;
import java.util.function.Function;

@Component("calculator")
public class CalculatorFunction implements Function<CalculatorFunction.Request, CalculatorFunction.Response> {
    
    public record Request(String operation, double a, double b) {}
    public record Response(double result, String explanation) {}
    
    @Override
    public Response apply(Request request) {
        double result = switch (request.operation().toLowerCase()) {
            case "add", "+" -> request.a() + request.b();
            case "subtract", "-" -> request.a() - request.b();
            case "multiply", "*" -> request.a() * request.b();
            case "divide", "/" -> {
                if (request.b() == 0) throw new IllegalArgumentException("Cannot divide by zero");
                yield request.a() / request.b();
            }
            default -> throw new IllegalArgumentException("Unsupported operation: " + request.operation());
        };
        
        return new Response(result, 
            String.format("Calculated %s %s %s = %s", 
                request.a(), request.operation(), request.b(), result));
    }
}
```

### Function Description for AI

```java
@Component("calculator")
@Description("Performs basic mathematical operations like addition, subtraction, multiplication, and division")
public class CalculatorFunction implements Function<CalculatorFunction.Request, CalculatorFunction.Response> {
    
    public record Request(
        @JsonPropertyDescription("The mathematical operation to perform: add, subtract, multiply, or divide") 
        String operation,
        @JsonPropertyDescription("The first number") 
        double a,
        @JsonPropertyDescription("The second number") 
        double b
    ) {}
    
    // ... rest of implementation
}
```

## 6.3 Weather Service Function - Real World Example

### Weather API Integration

```java
package com.example.functions;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.function.Function;

@Component("weatherService")
@Description("Gets current weather information for any city")
public class WeatherFunction implements Function<WeatherFunction.Request, WeatherFunction.Response> {
    
    @Value("${weather.api.key}")
    private String apiKey;
    
    private final RestTemplate restTemplate = new RestTemplate();
    
    public record Request(
        @JsonPropertyDescription("The name of the city to get weather for") 
        String city,
        @JsonPropertyDescription("Country code (optional, e.g., 'US', 'UK')") 
        String country
    ) {}
    
    public record Response(
        String city,
        String country,
        double temperature,
        String description,
        int humidity,
        double windSpeed,
        String status
    ) {}
    
    @Override
    public Response apply(Request request) {
        try {
            String location = request.country() != null 
                ? request.city() + "," + request.country()
                : request.city();
                
            String url = String.format(
                "https://api.openweathermap.org/data/2.5/weather?q=%s&appid=%s&units=metric",
                location, apiKey
            );
            
            WeatherApiResponse apiResponse = restTemplate.getForObject(url, WeatherApiResponse.class);
            
            if (apiResponse == null) {
                return new Response(request.city(), request.country(), 0, 
                    "Weather data unavailable", 0, 0, "ERROR");
            }
            
            return new Response(
                apiResponse.name(),
                apiResponse.sys().country(),
                apiResponse.main().temp(),
                apiResponse.weather()[0].description(),
                apiResponse.main().humidity(),
                apiResponse.wind().speed(),
                "SUCCESS"
            );
            
        } catch (Exception e) {
            return new Response(request.city(), request.country(), 0, 
                "Failed to fetch weather: " + e.getMessage(), 0, 0, "ERROR");
        }
    }
    
    // Weather API response DTOs
    public record WeatherApiResponse(
        String name,
        Main main,
        Weather[] weather,
        Wind wind,
        Sys sys
    ) {}
    
    public record Main(double temp, int humidity) {}
    public record Weather(String main, String description) {}
    public record Wind(double speed) {}
    public record Sys(String country) {}
}
```

## 6.4 Database Query Function

### Customer Service Function

```java
package com.example.functions;

import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.EmptyResultDataAccessException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Component("customerService")
@Description("Retrieves customer information from the database")
public class CustomerQueryFunction implements Function<CustomerQueryFunction.Request, CustomerQueryFunction.Response> {
    
    private final JdbcTemplate jdbcTemplate;
    
    public CustomerQueryFunction(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    public record Request(
        @JsonPropertyDescription("Type of query: 'find_by_email', 'find_by_id', 'search_by_name'")
        String queryType,
        @JsonPropertyDescription("The search value (email, ID, or name)")
        String searchValue
    ) {}
    
    public record Response(
        boolean found,
        List<Customer> customers,
        String message
    ) {}
    
    public record Customer(
        Long id,
        String name,
        String email,
        String phone,
        String status
    ) {}
    
    @Override
    public Response apply(Request request) {
        try {
            List<Customer> customers = switch (request.queryType().toLowerCase()) {
                case "find_by_email" -> findByEmail(request.searchValue());
                case "find_by_id" -> findById(Long.parseLong(request.searchValue()));
                case "search_by_name" -> searchByName(request.searchValue());
                default -> throw new IllegalArgumentException("Unknown query type: " + request.queryType());
            };
            
            return new Response(
                !customers.isEmpty(),
                customers,
                customers.isEmpty() ? "No customers found" : "Found " + customers.size() + " customer(s)"
            );
            
        } catch (Exception e) {
            return new Response(false, List.of(), "Error: " + e.getMessage());
        }
    }
    
    private List<Customer> findByEmail(String email) {
        try {
            Customer customer = jdbcTemplate.queryForObject(
                "SELECT id, name, email, phone, status FROM customers WHERE email = ?",
                (rs, rowNum) -> new Customer(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("email"),
                    rs.getString("phone"),
                    rs.getString("status")
                ),
                email
            );
            return List.of(customer);
        } catch (EmptyResultDataAccessException e) {
            return List.of();
        }
    }
    
    private List<Customer> findById(Long id) {
        try {
            Customer customer = jdbcTemplate.queryForObject(
                "SELECT id, name, email, phone, status FROM customers WHERE id = ?",
                (rs, rowNum) -> new Customer(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("email"),
                    rs.getString("phone"),
                    rs.getString("status")
                ),
                id
            );
            return List.of(customer);
        } catch (EmptyResultDataAccessException e) {
            return List.of();
        }
    }
    
    private List<Customer> searchByName(String name) {
        return jdbcTemplate.query(
            "SELECT id, name, email, phone, status FROM customers WHERE name ILIKE ?",
            (rs, rowNum) -> new Customer(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getString("status")
            ),
            "%" + name + "%"
        );
    }
}
```

## 6.5 Email Service Function

### Email Notification Function

```java
package com.example.functions;

import org.springframework.stereotype.Component;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import java.util.function.Function;

@Component("emailService")
@Description("Sends email notifications to customers or internal users")
public class EmailFunction implements Function<EmailFunction.Request, EmailFunction.Response> {
    
    private final JavaMailSender mailSender;
    
    public EmailFunction(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }
    
    public record Request(
        @JsonPropertyDescription("Recipient email address")
        String to,
        @JsonPropertyDescription("Email subject")
        String subject,
        @JsonPropertyDescription("Email body content")
        String body,
        @JsonPropertyDescription("Email type: 'notification', 'welcome', 'support', 'marketing'")
        String emailType
    ) {}
    
    public record Response(
        boolean sent,
        String message,
        String emailId
    ) {}
    
    @Override
    public Response apply(Request request) {
        try {
            // Validate email type and apply templates
            String finalSubject = enhanceSubject(request.subject(), request.emailType());
            String finalBody = enhanceBody(request.body(), request.emailType());
            
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(request.to());
            message.setSubject(finalSubject);
            message.setText(finalBody);
            message.setFrom("noreply@yourcompany.com");
            
            mailSender.send(message);
            
            String emailId = generateEmailId();
            return new Response(true, "Email sent successfully", emailId);
            
        } catch (Exception e) {
            return new Response(false, "Failed to send email: " + e.getMessage(), null);
        }
    }
    
    private String enhanceSubject(String subject, String emailType) {
        return switch (emailType.toLowerCase()) {
            case "welcome" -> "[Welcome] " + subject;
            case "support" -> "[Support] " + subject;
            case "marketing" -> "[Offer] " + subject;
            default -> subject;
        };
    }
    
    private String enhanceBody(String body, String emailType) {
        String signature = "\n\nBest regards,\nYour Company Team";
        
        return switch (emailType.toLowerCase()) {
            case "welcome" -> "Welcome to our platform!\n\n" + body + signature;
            case "support" -> "Thank you for contacting support.\n\n" + body + signature;
            case "marketing" -> body + "\n\n" + getUnsubscribeFooter() + signature;
            default -> body + signature;
        };
    }
    
    private String getUnsubscribeFooter() {
        return "To unsubscribe, click here: https://yourcompany.com/unsubscribe";
    }
    
    private String generateEmailId() {
        return "EMAIL-" + System.currentTimeMillis();
    }
}
```

## 6.6 File Operations Function

### File Management Function

```java
package com.example.functions;

import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.file.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component("fileManager")
@Description("Manages file operations like reading, writing, and listing files")
public class FileManagerFunction implements Function<FileManagerFunction.Request, FileManagerFunction.Response> {
    
    private static final String BASE_PATH = "/app/files/";
    
    public record Request(
        @JsonPropertyDescription("Operation type: 'read', 'write', 'list', 'delete', 'exists'")
        String operation,
        @JsonPropertyDescription("File path relative to base directory")
        String filePath,
        @JsonPropertyDescription("Content to write (for write operations)")
        String content
    ) {}
    
    public record Response(
        boolean success,
        String message,
        String content,
        java.util.List<String> files
    ) {}
    
    @Override
    public Response apply(Request request) {
        try {
            Path fullPath = Paths.get(BASE_PATH, request.filePath()).normalize();
            
            // Security check - ensure path is within base directory
            if (!fullPath.startsWith(BASE_PATH)) {
                return new Response(false, "Access denied: Path outside allowed directory", null, null);
            }
            
            return switch (request.operation().toLowerCase()) {
                case "read" -> readFile(fullPath);
                case "write" -> writeFile(fullPath, request.content());
                case "list" -> listFiles(fullPath);
                case "delete" -> deleteFile(fullPath);
                case "exists" -> checkExists(fullPath);
                default -> new Response(false, "Unknown operation: " + request.operation(), null, null);
            };
            
        } catch (Exception e) {
            return new Response(false, "Error: " + e.getMessage(), null, null);
        }
    }
    
    private Response readFile(Path path) throws IOException {
        if (!Files.exists(path)) {
            return new Response(false, "File does not exist", null, null);
        }
        
        String content = Files.readString(path);
        return new Response(true, "File read successfully", content, null);
    }
    
    private Response writeFile(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        return new Response(true, "File written successfully", null, null);
    }
    
    private Response listFiles(Path path) throws IOException {
        if (!Files.exists(path)) {
            return new Response(false, "Directory does not exist", null, null);
        }
        
        if (!Files.isDirectory(path)) {
            return new Response(false, "Path is not a directory", null, null);
        }
        
        var files = Files.list(path)
            .map(p -> p.getFileName().toString())
            .collect(Collectors.toList());
            
        return new Response(true, "Directory listed successfully", null, files);
    }
    
    private Response deleteFile(Path path) throws IOException {
        if (!Files.exists(path)) {
            return new Response(false, "File does not exist", null, null);
        }
        
        Files.delete(path);
        return new Response(true, "File deleted successfully", null, null);
    }
    
    private Response checkExists(Path path) {
        boolean exists = Files.exists(path);
        return new Response(true, exists ? "File exists" : "File does not exist", 
            String.valueOf(exists), null);
    }
}
```

## 6.7 Controller to Use Functions

### AI Chat Controller with Function Calling

```java
package com.example.controller;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/ai")
public class AiFunctionController {
    
    private final ChatClient chatClient;
    
    public AiFunctionController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }
    
    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        
        // Enable specific functions for this request
        OpenAiChatOptions options = OpenAiChatOptions.builder()
            .withFunction("calculator")
            .withFunction("weatherService")
            .withFunction("customerService")
            .withFunction("emailService")
            .withFunction("fileManager")
            .build();
        
        Prompt prompt = new Prompt(List.of(new UserMessage(request.message())), options);
        
        return chatClient.call(prompt);
    }
    
    @PostMapping("/chat/selective")
    public ChatResponse chatWithSelectiveFunctions(@RequestBody SelectiveChatRequest request) {
        
        // Enable only specified functions
        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder();
        request.enabledFunctions().forEach(optionsBuilder::withFunction);
        
        Prompt prompt = new Prompt(List.of(new UserMessage(request.message())), optionsBuilder.build());
        
        return chatClient.call(prompt);
    }
    
    public record ChatRequest(String message) {}
    public record SelectiveChatRequest(String message, Set<String> enabledFunctions) {}
}
```

## 6.8 Advanced Function Patterns

### Multi-Step Order Processing Function

```java
package com.example.functions;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.function.Function;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component("orderProcessor")
@Description("Processes customer orders including validation, inventory check, and payment")
public class OrderProcessorFunction implements Function<OrderProcessorFunction.Request, OrderProcessorFunction.Response> {
    
    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    
    public OrderProcessorFunction(OrderService orderService, 
                                 InventoryService inventoryService,
                                 PaymentService paymentService) {
        this.orderService = orderService;
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
    }
    
    public record Request(
        @JsonPropertyDescription("Customer ID")
        Long customerId,
        @JsonPropertyDescription("Product ID")
        Long productId,
        @JsonPropertyDescription("Quantity to order")
        Integer quantity,
        @JsonPropertyDescription("Payment method: 'credit_card', 'paypal', 'bank_transfer'")
        String paymentMethod
    ) {}
    
    public record Response(
        boolean success,
        String orderId,
        String status,
        String message,
        BigDecimal totalAmount,
        LocalDateTime orderDate,
        java.util.List<String> steps
    ) {}
    
    @Override
    @Transactional
    public Response apply(Request request) {
        java.util.List<String> steps = new java.util.ArrayList<>();
        
        try {
            // Step 1: Validate customer
            steps.add("Validating customer");
            if (!orderService.isValidCustomer(request.customerId())) {
                return createFailureResponse("Invalid customer ID", steps);
            }
            
            // Step 2: Check inventory
            steps.add("Checking inventory");
            if (!inventoryService.isAvailable(request.productId(), request.quantity())) {
                return createFailureResponse("Insufficient inventory", steps);
            }
            
            // Step 3: Calculate total
            steps.add("Calculating order total");
            BigDecimal unitPrice = orderService.getProductPrice(request.productId());
            BigDecimal totalAmount = unitPrice.multiply(BigDecimal.valueOf(request.quantity()));
            
            // Step 4: Reserve inventory
            steps.add("Reserving inventory");
            inventoryService.reserve(request.productId(), request.quantity());
            
            // Step 5: Process payment
            steps.add("Processing payment");
            String paymentId = paymentService.processPayment(
                request.customerId(), totalAmount, request.paymentMethod()
            );
            
            // Step 6: Create order
            steps.add("Creating order record");
            String orderId = orderService.createOrder(
                request.customerId(), request.productId(), 
                request.quantity(), totalAmount, paymentId
            );
            
            steps.add("Order processed successfully");
            
            return new Response(
                true, orderId, "COMPLETED", "Order processed successfully",
                totalAmount, LocalDateTime.now(), steps
            );
            
        } catch (Exception e) {
            steps.add("Error occurred: " + e.getMessage());
            return createFailureResponse(e.getMessage(), steps);
        }
    }
    
    private Response createFailureResponse(String message, java.util.List<String> steps) {
        return new Response(
            false, null, "FAILED", message,
            BigDecimal.ZERO, LocalDateTime.now(), steps
        );
    }
}
```

## 6.9 Function Registration and Discovery

### Custom Function Registry

```java
package com.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackWrapper;

import java.util.List;
import java.util.function.Function;

@Configuration
public class FunctionConfiguration {
    
    @Bean
    public FunctionCallbackWrapper calculatorFunctionWrapper(CalculatorFunction calculatorFunction) {
        return FunctionCallbackWrapper.builder(calculatorFunction)
            .withName("calculator")
            .withDescription("Performs mathematical calculations")
            .build();
    }
    
    @Bean
    public FunctionCallbackWrapper weatherFunctionWrapper(WeatherFunction weatherFunction) {
        return FunctionCallbackWrapper.builder(weatherFunction)
            .withName("weatherService")
            .withDescription("Gets current weather information for cities")
            .build();
    }
    
    // Auto-discover all functions
    @Bean
    public List<FunctionCallback> functionCallbacks(List<Function<?, ?>> functions) {
        return functions.stream()
            .map(function -> {
                String name = function.getClass().getSimpleName().replace("Function", "");
                return FunctionCallbackWrapper.builder(function)
                    .withName(name.toLowerCase())
                    .withDescription("Auto-discovered function: " + name)
                    .build();
            })
            .collect(Collectors.toList());
    }
}
```

## 6.10 Testing Function Calling

### Integration Test

```java
package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.ai.openai.api-key=test-key",
    "weather.api.key=test-weather-key"
})
class FunctionCallingIntegrationTest {
    
    @Autowired
    private ChatClient chatClient;
    
    @Test
    void testCalculatorFunction() {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
            .withFunction("calculator")
            .build();
            
        Prompt prompt = new Prompt(
            List.of(new UserMessage("Calculate 15 + 25")), 
            options
        );
        
        ChatResponse response = chatClient.call(prompt);
        
        assertThat(response.getResult().getOutput().getContent())
            .contains("40");
    }
    
    @Test
    void testWeatherFunction() {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
            .withFunction("weatherService")
            .build();
            
        Prompt prompt = new Prompt(
            List.of(new UserMessage("What's the weather in London?")), 
            options
        );
        
        ChatResponse response = chatClient.call(prompt);
        
        assertThat(response.getResult().getOutput().getContent())
            .isNotEmpty();
    }
}
```

## Real-World Use Cases

### 1. Customer Service Bot
- **Query customer information** using customer service function
- **Send follow-up emails** using email function
- **Calculate refunds** using calculator function
- **Access order history** using database functions

### 2. E-commerce Assistant
- **Process orders** using order processor function
- **Check inventory** using inventory functions
- **Calculate shipping costs** using calculator function
- **Send confirmation emails** using email function

### 3. Content Management System
- **Read/write files** using file manager function
- **Generate reports** using database query functions
- **Send notifications** using email function
- **Calculate metrics** using calculator function

## Best Practices

1. **Security**: Always validate inputs and restrict file access
2. **Error Handling**: Provide meaningful error messages
3. **Documentation**: Use clear descriptions and parameter documentation
4. **Performance**: Implement caching for expensive operations
5. **Testing**: Write comprehensive tests for all functions
6. **Monitoring**: Log function calls and performance metrics

# Test the calculator function
curl -X POST http://localhost:8080/api/ai/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Please calculate 25 * 4 + 10"}'

## Next Steps

In the next phase, we'll cover **Image Generation and Processing**, where you'll learn to integrate DALL-E, Stable Diffusion, and vision models with Spring AI.

Would you like to proceed with Phase 7, or do you have questions about any of the function calling concepts covered here?