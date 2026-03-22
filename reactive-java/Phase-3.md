# Phase 3: Advanced Operators and Patterns - Deep Dive

## Overview

Phase 3 covers the essential operators that make reactive programming powerful. You'll learn to transform, filter, combine, and handle errors in reactive streams like a pro.

## 3.1 Essential Operators - Transformation

### map() - One-to-One Transformation

**Purpose**: Transform each element in the stream to another element.

**Visual Representation:**
```
Original:  --[1]----[2]----[3]----[4]--|
map(x*2):  --[2]----[4]----[6]----[8]--|
```

**Basic Examples:**
```java
// Simple transformation
Flux<Integer> numbers = Flux.just(1, 2, 3, 4, 5);
Flux<Integer> doubled = numbers.map(n -> n * 2);

doubled.subscribe(System.out::println);
// Output: 2, 4, 6, 8, 10

// String transformation
Flux<String> names = Flux.just("alice", "bob", "charlie");
Flux<String> upperNames = names.map(String::toUpperCase);

upperNames.subscribe(System.out::println);
// Output: ALICE, BOB, CHARLIE

// Object transformation
Flux<User> users = Flux.just(
    new User("1", "Alice"),
    new User("2", "Bob")
);

Flux<UserDto> userDtos = users.map(user -> 
    new UserDto(user.getId(), user.getName().toUpperCase())
);
```

**Advanced map() Usage:**
```java
// Chaining transformations
Flux.range(1, 5)
    .map(n -> n * n)              // Square each number
    .map(n -> "Number: " + n)     // Convert to string
    .map(String::length)          // Get string length
    .subscribe(System.out::println);

// Conditional transformation
Flux<Integer> numbers = Flux.range(1, 10);
Flux<String> categories = numbers.map(n -> {
    if (n % 2 == 0) return "Even: " + n;
    else return "Odd: " + n;
});

// Error handling in map
Flux<String> data = Flux.just("1", "2", "not-a-number", "4");
Flux<Integer> parsed = data.map(s -> {
    try {
        return Integer.parseInt(s);
    } catch (NumberFormatException e) {
        return -1; // Default value for invalid input
    }
});
```

### flatMap() - One-to-Many Transformation

**Purpose**: Transform each element into a Publisher, then flatten all Publishers into a single stream.

**Visual Representation:**
```
Original:        --[1]--------[2]--------[3]--|
                   |          |          |
flatMap(x->      --[a,b]----[c,d]----[e,f]--|
  just(x+'a',x+'b'))
                   
Flattened:       --[a]-[b]--[c]-[d]--[e]-[f]--|
```

**Basic Examples:**
```java
// Transform each number into multiple values
Flux<Integer> numbers = Flux.just(1, 2, 3);

Flux<String> expanded = numbers.flatMap(n -> 
    Flux.just(n + "a", n + "b", n + "c")
);

expanded.subscribe(System.out::println);
// Output: 1a, 1b, 1c, 2a, 2b, 2c, 3a, 3b, 3c

// Async operations with flatMap
Flux<String> urls = Flux.just(
    "http://api.example.com/user/1",
    "http://api.example.com/user/2",
    "http://api.example.com/user/3"
);

Flux<User> users = urls.flatMap(url -> 
    webClient.get()
        .uri(url)
        .retrieve()
        .bodyToMono(User.class)
);
```

**flatMap vs map difference:**
```java
// Using map - creates nested Publishers
Flux<Publisher<String>> nested = Flux.just(1, 2, 3)
    .map(n -> Flux.just(n + "a", n + "b")); // Returns Flux<Flux<String>>

// Using flatMap - flattens the Publishers  
Flux<String> flattened = Flux.just(1, 2, 3)
    .flatMap(n -> Flux.just(n + "a", n + "b")); // Returns Flux<String>
```

**Concurrency Control in flatMap:**
```java
// Control concurrency - limit parallel processing
Flux<String> results = Flux.range(1, 100)
    .flatMap(
        n -> processAsync(n),    // Async operation
        8,                       // Max concurrency = 8
        32                       // Queue size = 32
    );

// Sequential processing with flatMapSequential
Flux<String> sequential = Flux.range(1, 5)
    .flatMapSequential(n -> 
        Mono.delay(Duration.ofMillis(100 - n * 10))
            .map(tick -> "Item " + n)
    );
// Maintains original order despite different delays
```

**Real-world flatMap Example - Processing Files:**
```java
public Flux<ProcessedData> processFiles(Flux<String> filenames) {
    return filenames
        .flatMap(filename -> 
            readFile(filename)                    // Returns Mono<String>
                .flatMapMany(content -> 
                    Flux.fromArray(content.split("\n"))  // Split into lines
                )
                .filter(line -> !line.trim().isEmpty()) // Remove empty lines
                .map(this::parseLine)             // Parse each line
                .onErrorContinue((error, line) -> 
                    log.error("Failed to process line: " + line, error)
                )
        );
}
```

### cast() and ofType() - Type Transformation

```java
// cast() - Unsafe casting
Flux<Object> objects = Flux.just("hello", "world", 123);
Flux<String> strings = objects.cast(String.class); // Will fail on 123

// ofType() - Safe filtering and casting
Flux<String> safeStrings = objects.ofType(String.class);
safeStrings.subscribe(System.out::println); // Output: hello, world

// Practical example
Flux<Object> mixed = Flux.just("text", 42, "more text", 3.14, "final");
mixed.ofType(String.class)
    .map(String::toUpperCase)
    .subscribe(System.out::println); // TEXT, MORE TEXT, FINAL
```

### index() - Add Index Information

```java
// Add index to each element
Flux<String> names = Flux.just("Alice", "Bob", "Charlie");
Flux<Tuple2<Long, String>> indexed = names.index();

indexed.subscribe(tuple -> 
    System.out.println("Index " + tuple.getT1() + ": " + tuple.getT2())
);
// Output:
// Index 0: Alice  
// Index 1: Bob
// Index 2: Charlie

// Custom index starting point
Flux<Tuple2<Long, String>> customIndex = names.index(100L);
// Starts from index 100

// Using index for processing
names.index()
    .filter(tuple -> tuple.getT1() % 2 == 0) // Even indices only
    .map(Tuple2::getT2)                      // Extract value
    .subscribe(System.out::println);         // Alice, Charlie
```

## 3.2 Essential Operators - Filtering

### filter() - Conditional Filtering

**Purpose**: Only emit elements that match a given predicate.

**Visual Representation:**
```
Original:    --[1]--[2]--[3]--[4]--[5]--[6]--|
filter(even): ------[2]------[4]------[6]--|
```

**Examples:**
```java
// Basic filtering
Flux<Integer> numbers = Flux.range(1, 10);
Flux<Integer> evenNumbers = numbers.filter(n -> n % 2 == 0);

evenNumbers.subscribe(System.out::println); // 2, 4, 6, 8, 10

// Complex filtering
Flux<User> users = Flux.just(
    new User("1", "Alice", 25),
    new User("2", "Bob", 30),
    new User("3", "Charlie", 20)
);

Flux<User> adults = users.filter(user -> user.getAge() >= 25);

// Multiple filter conditions
Flux<String> words = Flux.just("apple", "banana", "cherry", "date", "elderberry");
words.filter(word -> word.length() > 5)      // Long words
     .filter(word -> word.startsWith("b"))   // Starting with 'b'  
     .subscribe(System.out::println);        // banana

// Filtering with external state
Set<String> blacklist = Set.of("badword1", "badword2");
Flux<String> messages = Flux.just("hello", "badword1", "world");
messages.filter(msg -> !blacklist.contains(msg))
        .subscribe(System.out::println); // hello, world
```

### take() and takeLast() - Limiting Elements

```java
// Take first N elements
Flux<Integer> infinite = Flux.range(1, 100);
infinite.take(5)
        .subscribe(System.out::println); // 1, 2, 3, 4, 5

// Take last N elements  
Flux.range(1, 10)
    .takeLast(3)
    .subscribe(System.out::println); // 8, 9, 10

// takeUntil - take until condition is true
Flux.range(1, 20)
    .takeUntil(n -> n > 5) // Takes 1,2,3,4,5,6 (includes the matching element)
    .subscribe(System.out::println);

// takeWhile - take while condition is true
Flux.range(1, 20)
    .takeWhile(n -> n <= 5) // Takes 1,2,3,4,5 (stops before condition fails)
    .subscribe(System.out::println);
```

### skip() and skipLast() - Skipping Elements

```java
// Skip first N elements
Flux.range(1, 10)
    .skip(3)
    .subscribe(System.out::println); // 4, 5, 6, 7, 8, 9, 10

// Skip last N elements
Flux.range(1, 10)
    .skipLast(3)
    .subscribe(System.out::println); // 1, 2, 3, 4, 5, 6, 7

// skipUntil - skip until condition is true
Flux.range(1, 10)
    .skipUntil(n -> n > 5) // Skips until n > 5, then emits 6, 7, 8, 9, 10
    .subscribe(System.out::println);

// skipWhile - skip while condition is true
Flux.range(1, 10)
    .skipWhile(n -> n <= 5) // Skips while n <= 5, then emits 6, 7, 8, 9, 10
    .subscribe(System.out::println);
```

### distinct() and distinctUntilChanged()

```java
// Remove duplicates
Flux<String> withDuplicates = Flux.just("a", "b", "a", "c", "b", "d");
withDuplicates.distinct()
              .subscribe(System.out::println); // a, b, c, d

// Remove consecutive duplicates only
Flux<String> consecutive = Flux.just("a", "a", "b", "b", "b", "c", "a");
consecutive.distinctUntilChanged()
           .subscribe(System.out::println); // a, b, c, a

// Distinct by key
Flux<User> users = Flux.just(
    new User("1", "Alice", 25),
    new User("2", "Bob", 25),
    new User("3", "Alice", 30)  // Same name, different age
);

users.distinct(User::getName)  // Distinct by name
     .subscribe(user -> System.out.println(user.getName())); // Alice, Bob

// distinctUntilChanged with key extractor
Flux<User> stream = Flux.just(
    new User("1", "Alice", 25),
    new User("2", "Alice", 25),  // Same name consecutive
    new User("3", "Bob", 30)
);

stream.distinctUntilChanged(User::getName)
      .subscribe(user -> System.out.println(user.getName())); // Alice, Bob
```

## 3.3 Time-Based Operators

### delay() - Add Delays

```java
// Delay entire stream
Flux<Integer> delayed = Flux.just(1, 2, 3)
    .delay(Duration.ofSeconds(2)); // Wait 2 seconds before starting

// Delay each element
Flux<Integer> delayedEach = Flux.just(1, 2, 3)
    .delayElements(Duration.ofSeconds(1)); // 1 second between each element

// Variable delays
Flux<Integer> variableDelay = Flux.just(1, 2, 3)
    .flatMap(n -> Mono.just(n).delay(Duration.ofSeconds(n)));
// Element 1 delayed by 1s, element 2 by 2s, element 3 by 3s

// Practical example - Rate limiting
public Flux<ApiResponse> callApiWithRateLimit(Flux<ApiRequest> requests) {
    return requests
        .delayElements(Duration.ofMillis(100)) // Max 10 requests per second
        .flatMap(this::callApi);
}
```

### timeout() - Handle Slow Operations

```java
// Timeout entire stream
Mono<String> slowOperation = Mono.fromCallable(() -> {
    Thread.sleep(5000); // Simulate slow operation
    return "Result";
});

slowOperation.timeout(Duration.ofSeconds(2))
             .subscribe(
                 result -> System.out.println("Got: " + result),
                 error -> System.err.println("Timeout: " + error.getMessage())
             );

// Timeout with fallback
slowOperation.timeout(Duration.ofSeconds(2))
             .onErrorReturn("Default value")
             .subscribe(System.out::println);

// Per-element timeout
Flux<String> slowElements = Flux.just("fast", "slow", "medium")
    .flatMap(item -> {
        Duration delay = "slow".equals(item) ? 
            Duration.ofSeconds(3) : Duration.ofMillis(500);
        return Mono.just(item).delay(delay);
    });

slowElements.timeout(Duration.ofSeconds(1))
            .onErrorContinue(TimeoutException.class, 
                (error, item) -> System.err.println("Timeout on: " + item))
            .subscribe(System.out::println);
```

### sample() and throttle() - Rate Control

```java
// Sample at intervals - take latest value every interval
Flux<Long> fastStream = Flux.interval(Duration.ofMillis(100));
fastStream.sample(Duration.ofSeconds(1))  // Sample every second
          .take(5)
          .subscribe(System.out::println);

// Throttle - emit only if no new elements arrive within duration
Flux<String> bursty = Flux.just("a", "b", "c")
    .concatWith(Mono.just("d").delay(Duration.ofSeconds(2)))
    .concatWith(Flux.just("e", "f"))
    .concatWith(Mono.just("g").delay(Duration.ofSeconds(2)));

bursty.throttleFirst(Duration.ofSeconds(1)) // First element in each 1-second window
      .subscribe(System.out::println); // a, d, e, g

// throttleLast (debounce) - emit only after silence period
bursty.throttleLast(Duration.ofMillis(500)) // Only after 500ms of silence
      .subscribe(System.out::println);
```

## 3.4 Combining Publishers

### merge() - Interleave Multiple Streams

**Purpose**: Combine multiple publishers by interleaving their emissions as they arrive.

**Visual Representation:**
```
Stream A: --[1]----[3]------[5]--|
Stream B: ----[2]----[4]--[6]----| 
Merged:   --[1][2][3][4][5][6]---|
```

**Examples:**
```java
// Basic merge
Flux<String> stream1 = Flux.just("A", "B", "C")
    .delayElements(Duration.ofMillis(300));
    
Flux<String> stream2 = Flux.just("1", "2", "3")
    .delayElements(Duration.ofMillis(200));

Flux<String> merged = Flux.merge(stream1, stream2);
merged.subscribe(System.out::println);
// Possible output: 1, A, 2, B, 3, C (order depends on timing)

// Merge with different types (using map to common type)
Flux<Integer> numbers = Flux.just(1, 2, 3);
Flux<String> strings = Flux.just("A", "B", "C");

Flux<String> mergedMixed = Flux.merge(
    numbers.map(Object::toString),
    strings
);

// mergeWith - fluent API
Flux<String> result = stream1.mergeWith(stream2);

// Control concurrency in merge
Flux<String> controlled = Flux.merge(
    Arrays.asList(stream1, stream2, stream3),
    2  // Process only 2 streams concurrently
);
```

**Real-world Example - Multiple Data Sources:**
```java
public Flux<Event> getAllEvents() {
    Flux<Event> databaseEvents = eventRepository.findAll();
    Flux<Event> cacheEvents = cacheService.getEvents();
    Flux<Event> externalEvents = externalApiService.getEvents();
    
    return Flux.merge(databaseEvents, cacheEvents, externalEvents)
               .distinct(Event::getId)  // Remove duplicates
               .sort(Comparator.comparing(Event::getTimestamp));
}
```

### mergeSequential() - Maintain Order

```java
// mergeSequential maintains subscriber order
Flux<String> sequential = Flux.mergeSequential(
    Flux.just("A", "B").delay(Duration.ofMillis(200)),
    Flux.just("1", "2").delay(Duration.ofMillis(100)),
    Flux.just("X", "Y").delay(Duration.ofMillis(50))
);
// Output: A, B, 1, 2, X, Y (maintains order of subscribers)

// Compare with regular merge (would be mixed based on timing)
```

### zip() - Combine Corresponding Elements

**Purpose**: Combine elements from multiple streams pairwise.

**Visual Representation:**
```
Stream A: --[1]----[3]----[5]--|
Stream B: ----[2]----[4]----[6]--|
Zipped:   ----[1,2]--[3,4]--[5,6]--|
```

**Examples:**
```java
// Basic zip
Flux<String> names = Flux.just("Alice", "Bob", "Charlie");
Flux<Integer> ages = Flux.just(25, 30, 35);

Flux<String> profiles = Flux.zip(names, ages, 
    (name, age) -> name + " is " + age + " years old"
);
profiles.subscribe(System.out::println);
// Alice is 25 years old
// Bob is 30 years old  
// Charlie is 35 years old

// zipWith - fluent API
Flux<String> result = names.zipWith(ages, 
    (name, age) -> name + ":" + age
);

// Zip with Tuple (no combinator function)
Flux<Tuple2<String, Integer>> tuples = names.zipWith(ages);
tuples.subscribe(tuple -> 
    System.out.println(tuple.getT1() + " - " + tuple.getT2())
);

// Zip multiple streams
Flux<String> first = Flux.just("A", "B", "C");
Flux<String> second = Flux.just("1", "2", "3");  
Flux<String> third = Flux.just("X", "Y", "Z");

Flux<String> combined = Flux.zip(first, second, third)
    .map(tuple -> tuple.getT1() + tuple.getT2() + tuple.getT3());
// A1X, B2Y, C3Z
```

**Important Zip Behavior:**
```java
// Zip waits for ALL streams - terminates when shortest stream completes
Flux<String> short = Flux.just("A", "B");
Flux<String> long = Flux.just("1", "2", "3", "4", "5");

Flux.zip(short, long, (a, b) -> a + b)
    .subscribe(System.out::println); // Only A1, B2 (stops when short completes)
```

### zipWhen() - Dynamic Zipping

```java
// zipWhen - zip with dynamically created publisher
Flux<User> users = Flux.just(
    new User("1", "Alice"),
    new User("2", "Bob")
);

Flux<UserProfile> profiles = users.zipWhen(
    user -> fetchUserDetails(user.getId()), // Returns Mono<UserDetails>
    (user, details) -> new UserProfile(user, details)
);
```

### concat() - Sequential Combination

**Purpose**: Combine publishers sequentially - second starts only after first completes.

**Visual Representation:**
```
Stream A: --[1]--[2]--[3]--|
Stream B:                   --[4]--[5]--|
Concat:   --[1]--[2]--[3]----[4]--[5]--|
```

**Examples:**
```java
// Basic concat
Flux<String> first = Flux.just("A", "B", "C");
Flux<String> second = Flux.just("D", "E", "F");

Flux<String> concatenated = Flux.concat(first, second);
concatenated.subscribe(System.out::println); // A, B, C, D, E, F

// concatWith - fluent API
Flux<String> result = first.concatWith(second);

// Concat with delays - second waits for first
Flux<String> slow = Flux.just("1", "2").delayElements(Duration.ofSeconds(1));
Flux<String> fast = Flux.just("A", "B").delayElements(Duration.ofMillis(100));

Flux.concat(slow, fast)
    .subscribe(System.out::println); // 1, 2 (each 1s apart), then A, B (fast)

// startWith - prepend elements
Flux<String> main = Flux.just("main1", "main2");
main.startWith("prefix1", "prefix2")
    .subscribe(System.out::println); // prefix1, prefix2, main1, main2
```

**Practical Use Case - Sequential API Calls:**
```java
public Flux<ProcessingResult> processInSequence(List<Task> tasks) {
    return Flux.fromIterable(tasks)
               .concatMap(task -> 
                   processTask(task)  // Each task processed after previous completes
                       .doOnNext(result -> logProgress(result))
               );
}
```

### switchOnNext() and switchMap() - Switch to Latest

```java
// switchMap - switch to latest inner publisher, cancel previous
Flux<String> searchTerms = Flux.just("a", "ab", "abc")
    .delayElements(Duration.ofMillis(300));

Flux<String> searchResults = searchTerms
    .switchMap(term -> 
        searchApi(term)  // Each new search cancels previous one
            .delayElements(Duration.ofSeconds(1))
    );

// switchOnNext - switch between publishers
Flux<Publisher<String>> publishers = Flux.just(
    Flux.just("A", "B").delayElements(Duration.ofSeconds(1)),
    Flux.just("1", "2").delayElements(Duration.ofMillis(500))
);

Flux<String> switched = Flux.switchOnNext(publishers);
// Switches to second publisher, canceling first
```

## 3.5 Error Handling Deep Dive

### onErrorReturn() - Provide Fallback Value

```java
// Simple fallback value
Flux<Integer> numbers = Flux.just(1, 2, 0, 4)
    .map(n -> 10 / n)  // Division by zero on third element
    .onErrorReturn(-1); // Return -1 on any error

numbers.subscribe(System.out::println); // 10, 5, -1

// Conditional fallback
Flux<String> data = Flux.just("1", "2", "invalid", "4")
    .map(Integer::parseInt)
    .map(String::valueOf)
    .onErrorReturn(NumberFormatException.class, "invalid_number");

// Predicate-based fallback
Flux<Integer> result = riskyOperation()
    .onErrorReturn(
        error -> error instanceof TimeoutException,
        -999  // Special value for timeouts
    );
```

### onErrorResume() - Switch to Alternative Publisher

```java
// Switch to alternative stream on error
Flux<String> primarySource = Flux.just("data1", "data2")
    .concatWith(Mono.error(new RuntimeException("Primary failed")));

Flux<String> fallbackSource = Flux.just("backup1", "backup2");

Flux<String> resilient = primarySource
    .onErrorResume(error -> {
        System.err.println("Primary failed: " + error.getMessage());
        return fallbackSource;
    });

resilient.subscribe(System.out::println);
// Output: data1, data2, backup1, backup2

// Type-specific error handling
Mono<User> getUser(String id) {
    return userRepository.findById(id)
        .onErrorResume(DatabaseException.class, error -> 
            userCache.get(id)  // Try cache on database error
        )
        .onErrorResume(CacheException.class, error ->
            Mono.just(User.defaultUser())  // Default user on cache error
        );
}

// Chain multiple fallbacks
Flux<Data> withMultipleFallbacks = primaryService.getData()
    .onErrorResume(TimeoutException.class, error -> 
        secondaryService.getData()
    )
    .onErrorResume(ServiceException.class, error ->
        cacheService.getData()
    )
    .onErrorResume(error -> 
        Flux.just(Data.defaultData())  // Final fallback
    );
```

### onErrorMap() - Transform Errors

```java
// Transform generic exceptions to domain exceptions
Flux<User> users = Flux.just("1", "2", "invalid", "4")
    .map(id -> userService.findById(id))
    .onErrorMap(NumberFormatException.class, 
        error -> new InvalidUserIdException("Invalid user ID format", error)
    )
    .onErrorMap(SQLException.class,
        error -> new UserServiceException("Database error", error)
    );

// Add context to errors
Mono<String> processData(String input) {
    return Mono.fromCallable(() -> heavyComputation(input))
        .onErrorMap(error -> 
            new ProcessingException("Failed to process: " + input, error)
        );
}

// Conditional error mapping
flux.onErrorMap(
    error -> error instanceof IllegalArgumentException,
    error -> new ValidationException("Validation failed", error)
);
```

### retry() and retryWhen() - Automatic Retry

```java
// Simple retry - fixed number of attempts
Flux<String> unreliableService = Flux.just("attempt")
    .flatMap(s -> callUnreliableService())
    .retry(3);  // Retry up to 3 times

// Exponential backoff retry
Flux<String> withBackoff = unreliableService
    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
        .maxBackoff(Duration.ofSeconds(10))
        .jitter(0.1)  // Add 10% jitter
    );

// Conditional retry
Flux<Data> selective = dataService.fetchData()
    .retryWhen(Retry.from(retrySignal -> 
        retrySignal.take(5)  // Max 5 attempts
            .filter(signal -> signal.failure() instanceof TimeoutException)
            .delayElements(Duration.ofSeconds(2))
    ));

// Custom retry logic
public class CustomRetrySpec extends Retry {
    @Override
    public Publisher<?> generateCompanion(Flux<RetrySignal> retrySignals) {
        return retrySignals
            .flatMap(signal -> {
                if (signal.totalRetries() < 3 && isRetryableError(signal.failure())) {
                    Duration delay = Duration.ofSeconds(
                        (long) Math.pow(2, signal.totalRetries())
                    );
                    return Mono.delay(delay);
                } else {
                    return Mono.error(signal.failure());
                }
            });
    }
    
    private boolean isRetryableError(Throwable error) {
        return error instanceof TimeoutException || 
               error instanceof ConnectException;
    }
}
```

### doOnError() - Side Effects on Error

```java
// Log errors without handling them
Flux<String> withLogging = dataStream
    .doOnError(error -> {
        log.error("Error in data stream", error);
        metrics.incrementErrorCount();
    });

// Different actions for different error types
Flux<Data> withDetailedLogging = dataStream
    .doOnError(TimeoutException.class, error ->
        log.warn("Service timeout: {}", error.getMessage())
    )
    .doOnError(ValidationException.class, error -> {
        log.error("Validation failed: {}", error.getMessage());
        alertService.sendAlert("Validation Error", error);
    });

// Conditional side effects
Flux<String> conditional = dataStream
    .doOnError(error -> {
        if (error instanceof CriticalException) {
            emergencyShutdown();
        }
    });
```

### Error Handling Patterns

**1. Circuit Breaker Pattern:**
```java
public class CircuitBreakerFlux {
    private final CircuitBreaker circuitBreaker;
    
    public Flux<Data> getData() {
        return Flux.defer(() -> externalService.fetchData())
            .onErrorResume(error -> {
                if (circuitBreaker.isOpen()) {
                    return Flux.just(Data.cached());
                }
                circuitBreaker.recordFailure();
                return Flux.error(error);
            })
            .doOnNext(data -> circuitBreaker.recordSuccess());
    }
}
```

**2. Graceful Degradation:**
```java
public Flux<EnrichedData> getEnrichedData(Flux<RawData> input) {
    return input.flatMap(rawData -> 
        enrichData(rawData)
            .onErrorResume(error -> {
                log.warn("Enrichment failed, using raw data", error);
                return Mono.just(EnrichedData.fromRaw(rawData));
            })
    );
}
```

**3. Bulkhead Pattern - Isolate Failures:**
```java
public Flux<ProcessedData> processWithIsolation(Flux<RawData> input) {
    return input.flatMap(data -> 
        processData(data)
            .subscribeOn(Schedulers.boundedElastic()) // Separate thread pool
            .timeout(Duration.ofSeconds(5))
            .onErrorResume(error -> {
                if (error instanceof TimeoutException) {
                    return Mono.just(ProcessedData.timeout());
                }
                return Mono.just(ProcessedData.error(error));
            })
    , 10); // Limit concurrency to prevent resource exhaustion
}
```

## 3.6 Side Effects and Debugging

### Side Effect Operators (doOn* family)

**These operators allow you to perform side effects without modifying the stream:**

```java
// Complete lifecycle monitoring
Flux<String> monitoredStream = Flux.just("A", "B", "C")
    .doOnSubscribe(subscription -> 
        System.out.println("Subscribed with: " + subscription)
    )
    .doOnRequest(n -> 
        System.out.println("Requested: " + n + " elements")
    )
    .doOnNext(item -> 
        System.out.println("Emitting: " + item)
    )
    .doOnComplete(() -> 
        System.out.println("Stream completed successfully")
    )
    .doOnError(error -> 
        System.err.println("Stream error: " + error.getMessage())
    )
    .doOnCancel(() -> 
        System.out.println("Stream was cancelled")
    )
    .doOnTerminate(() -> 
        System.out.println("Stream terminated (complete or error)")
    )
    .doFinally(signalType -> 
        System.out.println("Finally: " + signalType)
    );

monitoredStream.subscribe();
```

**Practical Side Effect Examples:**

```java
// Logging and Metrics
public Flux<Order> processOrders(Flux<Order> orders) {
    return orders
        .doOnNext(order -> {
            log.info("Processing order: {}", order.getId());
            metrics.incrementOrderCount();
        })
        .flatMap(this::validateOrder)
        .doOnNext(order -> log.debug("Order validated: {}", order.getId()))
        .flatMap(this::processPayment)
        .doOnNext(order -> {
            log.info("Payment processed for order: {}", order.getId());
            metrics.recordPaymentTime(order.getProcessingTime());
        })
        .doOnError(error -> {
            log.error("Order processing failed", error);
            metrics.incrementErrorCount();
        })
        .doOnComplete(() -> {
            log.info("Batch order processing completed");
            metrics.recordBatchCompletion();
        });
}

// Resource Management
public Flux<Data> processWithResources(Flux<String> input) {
    return input
        .doOnSubscribe(sub -> resourceManager.acquireResources())
        .doOnNext(item -> resourceUsageTracker.recordUsage())
        .doOnError(error -> resourceManager.handleError(error))
        .doFinally(signalType -> {
            resourceManager.releaseResources();
            log.info("Resources released on: {}", signalType);
        });
}

// Caching Side Effects
public Flux<User> getUsersWithCaching(Flux<String> userIds) {
    return userIds
        .flatMap(id -> 
            userService.getUser(id)
                .doOnNext(user -> cache.put(id, user))  // Cache successful results
                .onErrorResume(error -> {
                    User cached = cache.get(id);
                    return cached != null ? 
                        Mono.just(cached) : 
                        Mono.error(error);
                })
        );
}
```

### Debugging Operators

**log() - Comprehensive Stream Logging:**
```java
// Basic logging - shows all signals
Flux.just("A", "B", "C")
    .log()  // Logs all signals with default logger
    .subscribe();

// Custom logger and level
Flux.just("A", "B", "C")
    .log("MyStream", Level.DEBUG)  // Custom logger name and level
    .subscribe();

// Selective logging
Flux.range(1, 10)
    .filter(n -> n % 2 == 0)
    .log("EvenNumbers")  // Log only after filtering
    .map(n -> n * 2)
    .log("Doubled")      // Log after transformation
    .subscribe();

// Custom log format
Flux.just("data1", "data2")
    .log("ProcessingStream", Level.INFO, SignalType.ON_NEXT, SignalType.ON_ERROR)
    .subscribe();
```

**checkpoint() - Error Context:**
```java
// Add checkpoint for better error traces
Flux<String> pipeline = Flux.just("1", "2", "invalid", "4")
    .map(Integer::parseInt)
    .checkpoint("after parsing")  // Checkpoint with description
    .map(n -> n * 2)
    .checkpoint("after doubling")
    .map(n -> 100 / n)
    .checkpoint("after division")
    .subscribe();

// Automatic checkpoints (for development)
Hooks.onOperatorDebug();  // Enable automatic checkpoints globally

// Light checkpoint (production friendly)
flux.checkpoint("critical-operation", false)  // false = light checkpoint
```

**hide() - Remove Optimization Hints:**
```java
// Sometimes useful for debugging fusion optimizations
Flux<String> debugFlux = Flux.just("A", "B", "C")
    .hide()  // Prevents operator fusion
    .log()   // Now you'll see all individual operations
    .map(String::toLowerCase);
```

**Debugging Complex Pipelines:**
```java
public Flux<ProcessedResult> debugComplexPipeline(Flux<Input> input) {
    return input
        .checkpoint("1-input-received")
        .doOnNext(i -> log.debug("Input: {}", i))
        
        .filter(this::isValid)
        .checkpoint("2-after-validation")
        .doOnNext(i -> log.debug("Valid input: {}", i))
        
        .flatMap(this::enrichData)
        .checkpoint("3-after-enrichment")
        .doOnNext(data -> log.debug("Enriched: {}", data))
        
        .groupBy(Data::getCategory)
        .checkpoint("4-after-grouping")
        
        .flatMap(group -> 
            group.collectList()
                 .checkpoint("5-group-" + group.key() + "-collected")
                 .flatMapMany(this::processGroup)
                 .checkpoint("6-group-" + group.key() + "-processed")
        )
        
        .checkpoint("7-final-result");
}
```

## 3.7 Practical Exercises - Week 5

### Exercise 1: Data Transformation Pipeline
```java
public class DataTransformationExercises {
    
    // Exercise 1a: Build a user processing pipeline
    public Flux<UserSummary> processUsers(Flux<RawUser> rawUsers) {
        // TODO: Implement the following pipeline:
        // 1. Filter out users under 18
        // 2. Transform to User objects with validation
        // 3. Enrich with profile data (use flatMap)
        // 4. Group by country
        // 5. Create summary statistics per country
        // 6. Handle all errors gracefully
        return null;
    }
    
    // Exercise 1b: Time-based data processing
    public Flux<AggregatedData> processTimeSeriesData(Flux<DataPoint> dataStream) {
        // TODO: 
        // 1. Sample data every 5 seconds
        // 2. Buffer into 1-minute windows
        // 3. Calculate aggregates (min, max, avg) for each window
        // 4. Filter out windows with less than 5 data points
        // 5. Add timeout handling for slow data sources
        return null;
    }
}
```

### Exercise 2: Error Handling Scenarios
```java
public class ErrorHandlingExercises {
    
    // Exercise 2a: Resilient API client
    public Mono<ApiResponse> callResilientApi(String endpoint) {
        // TODO: Create a resilient API call that:
        // 1. Retries up to 3 times with exponential backoff
        // 2. Falls back to cache if all retries fail
        // 3. Returns default response if cache is also empty
        // 4. Logs all failures appropriately
        return null;
    }
    
    // Exercise 2b: Batch processing with error isolation
    public Flux<ProcessingResult> processBatchWithIsolation(Flux<WorkItem> items) {
        // TODO: Process items where:
        // 1. Each item is processed independently
        // 2. Failures in one item don't affect others
        // 3. Failed items are collected for retry
        // 4. Processing stats are tracked
        return null;
    }
}
```

### Exercise 3: Real-world Integration
```java
public class IntegrationExercises {
    
    // Exercise 3a: Multi-source data aggregation
    public Flux<CombinedData> aggregateFromMultipleSources() {
        // TODO: Combine data from:
        // 1. Database (may be slow)
        // 2. External API (may fail)
        // 3. Cache (fast but may be stale)
        // 4. Real-time events
        // Merge intelligently with proper error handling
        return null;
    }
    
    // Exercise 3b: Event-driven order processing
    public Flux<OrderResult> processOrderEvents(Flux<OrderEvent> events) {
        // TODO: Process order events where:
        // 1. Group events by order ID
        // 2. Process each order's events in sequence
        // 3. Handle different event types (created, updated, cancelled)
        // 4. Emit final order status
        // 5. Handle out-of-order events
        return null;
    }
}
```

## 3.8 Practical Exercises - Week 6

### Exercise 4: Stream Composition Patterns
```java
public class CompositionExercises {
    
    // Exercise 4a: Dynamic stream switching
    public Flux<Data> dynamicDataStream(Flux<StreamConfig> configChanges) {
        // TODO: Switch between different data sources based on config
        // 1. Start with default source
        // 2. Switch sources when config changes
        // 3. Ensure smooth transitions (no data loss)
        // 4. Handle source failures gracefully
        return null;
    }
    
    // Exercise 4b: Rate-limited processing
    public Flux<ProcessedItem> processWithRateLimit(
            Flux<Item> items, 
            Duration minInterval) {
        // TODO: Process items with rate limiting:
        // 1. Ensure minimum interval between processing
        // 2. Buffer overflow items
        // 3. Provide backpressure feedback
        // 4. Handle burst scenarios
        return null;
    }
}
```

### Exercise 5: Advanced Error Recovery
```java
public class AdvancedErrorRecoveryExercises {
    
    // Exercise 5a: Circuit breaker implementation
    public Flux<ServiceResponse> serviceCallWithCircuitBreaker(
            Flux<ServiceRequest> requests) {
        // TODO: Implement circuit breaker pattern:
        // 1. Track failure rate
        // 2. Open circuit after threshold failures
        // 3. Allow test requests in half-open state
        // 4. Close circuit when service recovers
        return null;
    }
    
    // Exercise 5b: Cascading failure prevention
    public Flux<ProcessingResult> processWithBulkhead(
            Flux<WorkItem> items) {
        // TODO: Implement bulkhead pattern:
        // 1. Separate thread pools for different work types
        // 2. Isolate failures between work types
        // 3. Provide degraded service when resources are limited
        return null;
    }
}
```

## 3.9 Assessment Questions

**Conceptual Understanding:**
1. Explain the difference between `map()` and `flatMap()` with examples.
2. When would you use `merge()` vs `concat()` vs `zip()`?
3. What's the difference between `onErrorReturn()` and `onErrorResume()`?
4. How does backpressure interact with error handling?
5. What are the trade-offs between different retry strategies?

**Practical Scenarios:**
1. Design a resilient data pipeline that fetches from multiple APIs.
2. How would you implement rate limiting in a reactive stream?
3. Design an error handling strategy for a microservice communication.
4. How would you debug a complex reactive pipeline in production?

## 3.10 Common Patterns and Anti-patterns

### ✅ Good Patterns

**1. Graceful Degradation:**
```java
public Mono<UserProfile> getUserProfile(String userId) {
    return primaryService.getProfile(userId)
        .timeout(Duration.ofSeconds(2))
        .onErrorResume(error -> 
            fallbackService.getProfile(userId)
        )
        .onErrorReturn(UserProfile.anonymous());
}
```

**2. Resource Management:**
```java
public Flux<Data> processWithResources() {
    return Flux.using(
        () -> resourcePool.acquire(),        // Resource acquisition
        resource -> processData(resource),   // Stream creation
        resource -> resourcePool.release(resource)  // Resource cleanup
    );
}
```

**3. Proper Error Context:**
```java
public Flux<Result> processData(Flux<Input> input) {
    return input
        .index()  // Add index for context
        .flatMap(tuple -> 
            processItem(tuple.getT2())
                .onErrorMap(error -> 
                    new ProcessingException(
                        "Failed at index " + tuple.getT1(), error
                    )
                )
        );
}
```

### ❌ Anti-patterns

**1. Blocking in Reactive Chains:**
```java
// BAD - breaks reactive chain
flux.map(item -> {
    String result = blockingService.process(item); // DON'T DO THIS
    return result;
});

// GOOD - use reactive alternative
flux.flatMap(item -> 
    reactiveService.process(item)
);
```

**2. Ignoring Errors:**
```java
// BAD - errors are swallowed
flux.onErrorReturn(null);

// GOOD - handle errors appropriately  
flux.onErrorResume(error -> {
    log.error("Processing failed", error);
    return Mono.just(DefaultValue.create());
});
```

**3. Overusing flatMap:**
```java
// BAD - unnecessary flatMap for simple transformation
flux.flatMap(item -> Mono.just(item.toUpperCase()));

// GOOD - use map for simple transformations
flux.map(String::toUpperCase);
```

## Key Takeaways - Phase 3

### Transformation Operators
✅ **map()**: One-to-one transformations  
✅ **flatMap()**: One-to-many transformations with flattening  
✅ **cast()/ofType()**: Type transformations  
✅ **index()**: Add positional information  

### Filtering Operators  
✅ **filter()**: Conditional element selection  
✅ **take()/skip()**: Positional filtering  
✅ **distinct()**: Remove duplicates  
✅ **sample()/throttle()**: Rate-based filtering  

### Combination Operators
✅ **merge()**: Interleave multiple streams  
✅ **zip()**: Combine corresponding elements  
✅ **concat()**: Sequential combination  
✅ **switchMap()**: Switch to latest  

### Error Handling
✅ **onErrorReturn()**: Simple fallback values  
✅ **onErrorResume()**: Switch to alternative streams  
✅ **retry()**: Automatic retry with strategies  
✅ **doOnError()**: Side effects without handling  

### Debugging & Monitoring
✅ **log()**: Stream signal logging  
✅ **checkpoint()**: Error context markers  
✅ **doOn*()**: Side effects for monitoring  

## Next Phase Preview

**Phase 4** will cover **Schedulers and Threading** - where you'll learn:
- Understanding different scheduler types
- Thread management with `subscribeOn()` and `publishOn()`
- Parallel processing strategies
- Performance optimization techniques

Key Highlights of Phase 3:
🎯 Core Transformation Skills:

map() vs flatMap() - Understanding when to use each is fundamental
Combining streams - merge, zip, concat each serve different purposes
Error handling strategies - From simple fallbacks to complex resilience patterns

⚡ Critical Concepts Mastered:

Operator composition - Building complex pipelines from simple operators
Error boundaries - Where and how errors propagate through streams
Side effects - Monitoring and debugging without breaking reactive chains
Resource management - Proper cleanup and resource isolation

🛠 Real-world Patterns:

Circuit breaker - Prevent cascading failures
Graceful degradation - Provide reduced functionality when systems fail
Bulkhead isolation - Separate failure domains

Learning Approach for Phase 3:
Week 5 Focus:

Master the core operators (map, flatMap, filter, merge, zip)
Work through the transformation and combination examples
Complete Exercises 1-2 (focus on building correct pipelines)

Week 6 Focus:

Deep dive into error handling patterns
Practice debugging techniques with log(), checkpoint()
Complete Exercises 3-5 (focus on real-world scenarios)

Phase 3 Success Checklist:
Before moving to Phase 4, ensure you can:

 Choose the right operator for any transformation need
 Build resilient pipelines with proper error handling
 Debug reactive streams effectively
 Combine multiple data sources intelligently
 Handle timeouts and retries appropriately

Critical Practice Points:

Build the exercises - They simulate real production scenarios
Run every code example - Reactive behavior is best learned by observation
Experiment with variations - Change parameters, add errors, see what happens
Focus on error scenarios - Most production issues are error-handling related

Would you like me to:

Create more specific practice exercises for any operator you want to master?
Move to Phase 4 (Schedulers and Threading)?
Provide detailed solutions to any of the exercises?
Create a mini-project that combines everything from Phases 1-3?