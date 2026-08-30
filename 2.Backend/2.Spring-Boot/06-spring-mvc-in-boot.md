# 06 - Spring MVC (Plain vs Boot)

---

## Your notes (corrected) — Spring MVC basics

Using MVC you can build web applications and distributed applications (REST APIs).

### The request flow (your ASCII diagram, redrawn clearly)

```
                 ①
Client Request ────────▶  DispatcherServlet (Front Controller)
                                   │
                                   │ ②  HandlerMapping
                                   ▼        (which Controller/method handles this URL?)
                              Controller
                                   │
                                   │ ③  Controller executes business logic,
                                   │     populates a Model
                                   ▼
                            ModelAndView  ④
                                   │
                                   ▼
                             ViewResolver  ⑤
                          (logical view name → actual view, e.g. JSP/Thymeleaf template)
                                   │
                                   ▼
                                 View  →  rendered response back to client
```

Controller (your example, cleaned up):

```java
@Controller
public class ProductController {

    @RequestMapping("/products")
    public String getProducts(Model model) {
        model.addAttribute("products", productService.findAll());
        return "product-list"; // logical view name, resolved by ViewResolver
    }

    @RequestMapping("/product-details")
    public String getProductDetails(@RequestParam Long id, Model model) {
        model.addAttribute("product", productService.findById(id));
        return "product-details";
    }
}
```

For REST APIs (the far more common case in real 2020s+ jobs), you use `@RestController` instead of `@Controller` — it's `@Controller` + `@ResponseBody` combined, meaning return values are serialized directly to the response body (usually as JSON via Jackson) instead of being resolved to a view:

```java
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductRestController {

    private final ProductService productService;

    @GetMapping("/{id}")
    public ProductResponse getProduct(@PathVariable Long id) {
        return productService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(@Valid @RequestBody CreateProductRequest request) {
        return productService.create(request);
    }
}
```

---

## Plain Spring MVC setup (`web.xml` — legacy, but still asked about)

**1. Configure the DispatcherServlet in `web.xml`:**
```xml
<servlet>
    <servlet-name>dispatcher</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
</servlet>

<servlet-mapping>
    <servlet-name>dispatcher</servlet-name>
    <url-pattern>/</url-pattern>
</servlet-mapping>
```

**2. `dispatcher-servlet.xml`** — activates handler mapping and controller scanning for that servlet's context.

**3. Write Controller, Service, and DAO layers.**

### The two-container picture (your diagram, corrected & explained)

```
                    Root WebApplicationContext (Spring Container)
                    — Services, DAOs, DataSource, transaction manager
                              ▲
                              │ is parent of
                              │
       Servlet Container      │
       (Tomcat/Jetty)         │
            │                 │
            ▼                 │
     DispatcherServlet ───────┘
     (has its own child ApplicationContext:
      HandlerMapping, Controllers, ViewResolvers)
```

In classic Spring MVC there are actually **two contexts**: a root context (business layer: services/DAOs, shared across the whole webapp) and a servlet-specific child context owned by `DispatcherServlet` (web layer: controllers, handler mappings, view resolvers). This two-context split is a genuinely tricky, often-misunderstood detail — see Q3 below.

**Manual responsibilities in plain Spring MVC (your notes, kept):**

a) Configuration — either XML (`web.xml` + `dispatcher-servlet.xml`) or Java config (`AbstractAnnotationConfigDispatcherServletInitializer` + a config class extending `WebMvcConfigurerAdapter`/implementing `WebMvcConfigurer`)

b) Manual deployment — build a WAR, drop it into an external Tomcat's `webapps/` folder

---

## Spring Boot MVC — what disappears

- No `web.xml`
- No Java `DispatcherServletInitializer` config class
- No manual deployment step
- You just write `@RestController`/`@Controller` classes and, if needed, add MVC-related properties to `application.properties`

**Your Q kept:** How does Boot create `DispatcherServlet`, `HandlerMapping`, `ViewResolver` etc.?
**A:** All created automatically via `@EnableAutoConfiguration` — specifically `DispatcherServletAutoConfiguration` (creates the servlet) and `WebMvcAutoConfiguration` (creates default `HandlerMapping`, `ViewResolver`, message converters, static resource handling, etc.), each gated by `@ConditionalOnWebApplication` + `@ConditionalOnClass` as covered in file 02. Notably, only a **single** root `ApplicationContext` exists in Boot's default setup — the awkward two-context split from plain MVC is collapsed into one, which removes a whole category of "bean visible in one context but not the other" bugs.

### Customizing MVC behavior in Boot without losing auto-configuration

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("https://myapp.com")
                .allowedMethods("GET", "POST", "PUT", "DELETE");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RequestLoggingInterceptor());
    }
}
```

Implementing `WebMvcConfigurer` (instead of using `@EnableWebMvc` + extending it fully) lets you *add to* Boot's auto-configured MVC setup without disabling it. Using `@EnableWebMvc` explicitly turns **off** `WebMvcAutoConfiguration` entirely and puts you back in charge of everything MVC-related — a classic gotcha (see Q4 below).

---

## Tricky interview questions — Section 6

**Q1. What's the actual difference between `@Controller` and `@RestController`?**
`@RestController` = `@Controller` + `@ResponseBody` at the class level. `@Controller` methods return logical view names resolved by a `ViewResolver` (server-rendered HTML); `@RestController` methods return objects that get serialized directly (usually to JSON via `MappingJackson2HttpMessageConverter`) straight into the HTTP response body — no view resolution happens at all.

**Q2. What does `HandlerMapping` actually do, mechanically?**
At startup, it builds a registry mapping URL patterns (+ HTTP method, headers, params) to specific controller handler methods, by scanning `@RequestMapping`-family annotations. At request time, `DispatcherServlet` asks the `HandlerMapping` chain "who handles this request?" and gets back a `HandlerExecutionChain` (the target method plus any applicable interceptors) before invoking it.

**Q3. Why did the old two-`ApplicationContext` (root + servlet) split in plain Spring MVC cause real bugs, and why is Boot's single-context model safer?**
A bean declared only in the DispatcherServlet's child context (e.g. a `@Controller`) was invisible to code running in the root context, and vice versa — a very common real bug was a `@Transactional` AOP proxy or a security filter configured in the root context not being able to see/intercept beans that only existed in the child context, because Spring's context hierarchy only lets a child see its parent's beans, never the other way around. Boot uses a single, flat `ApplicationContext` by default, so this entire class of "works in one context, invisible in the other" bug simply doesn't exist in a standard Boot app.

**Q4. If you add `@EnableWebMvc` to a Boot application, what breaks?**
`@EnableWebMvc` disables `WebMvcAutoConfiguration` entirely — you lose all of Boot's sensible MVC defaults (default message converters, static resource handling, default exception handling, etc.) and become fully responsible for configuring MVC yourself, same as plain Spring MVC. It's a common accidental-regression bug when a developer copies old plain-Spring config into a Boot project without realizing the annotation is redundant *and* actively harmful there.

**Q5. How do you globally handle exceptions across all controllers without repeating try/catch in every method?**
`@RestControllerAdvice` + `@ExceptionHandler`:
```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ProductNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse("Validation failed"));
    }
}
```
This is intercepted centrally via `HandlerExceptionResolver` machinery that `WebMvcAutoConfiguration` wires up — one more thing you'd have to build by hand in plain MVC.

**Q6. Client sends a request, DispatcherServlet can't find any matching handler — what actually happens, and how do you customize it?**
By default it results in a 404. To get a clean, structured 404 JSON response instead of a default whitelabel error page, set `spring.mvc.throw-exception-if-no-handler-found=true` and `spring.web.resources.add-mappings=false`, then handle `NoHandlerFoundException` in your `@RestControllerAdvice`.
