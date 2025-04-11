Here’s a **comprehensive list of annotations** commonly used to write test cases in Java, especially when using **JUnit 5 (Jupiter)** and **Mockito**—two of the most popular testing libraries for Java developers.

---

## 🧪 JUnit 5 Annotations

---

### 1. `@Test`

- Marks a method as a test case.

```java
java
CopyEdit
import org.junit.jupiter.api.Test;

class MyTests {
    @Test
    void shouldAddTwoNumbers() {
        int result = 2 + 3;
        assert result == 5;
    }
}

```

---

### 2. `@BeforeEach`

- Runs **before each test method**.
- Commonly used to set up test data or mocks.

```java
java
CopyEdit
import org.junit.jupiter.api.BeforeEach;

class MyTests {
    int total;

    @BeforeEach
    void setup() {
        total = 0;
    }

    @Test
    void testSomething() {
        total += 5;
        assert total == 5;
    }
}

```

---

### 3. `@AfterEach`

- Runs **after each test method**.
- Used for cleanup.

```java
java
CopyEdit
import org.junit.jupiter.api.AfterEach;

@AfterEach
void tearDown() {
    System.out.println("Cleaning up...");
}

```

---

### 4. `@BeforeAll`

- Runs **once before all tests**.
- Method must be `static`.

```java
java
CopyEdit
import org.junit.jupiter.api.BeforeAll;

@BeforeAll
static void init() {
    System.out.println("Run once before all tests");
}

```

---

### 5. `@AfterAll`

- Runs **once after all tests**.
- Method must be `static`.

```java
java
CopyEdit
import org.junit.jupiter.api.AfterAll;

@AfterAll
static void finish() {
    System.out.println("Run once after all tests");
}

```

---

### 6. `@DisplayName`

- Custom display name for test classes or methods.

```java
java
CopyEdit
import org.junit.jupiter.api.DisplayName;

@DisplayName("Math Utility Test Cases")
class MathTests {

    @Test
    @DisplayName("Test Addition Operation")
    void testAdd() {
        assert 2 + 3 == 5;
    }
}

```

---

### 7. `@Disabled`

- Temporarily disables a test method or class.

```java
java
CopyEdit
import org.junit.jupiter.api.Disabled;

@Disabled("Pending bug fix")
@Test
void testDisabled() {
    // This won't run
}

```

---

### 8. `@Nested`

- Group related tests inside a nested class.

```java
java
CopyEdit
import org.junit.jupiter.api.Nested;

class UserServiceTest {

    @Nested
    class WhenUserExists {

        @Test
        void shouldReturnUserDetails() {
            // test logic
        }
    }
}

```

---

### 9. `@ParameterizedTest`

- Allows running the same test with multiple inputs.

```java
java
CopyEdit
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@ParameterizedTest
@ValueSource(ints = {1, 2, 3, 4})
void testIsEven(int num) {
    assert num % 2 != 0; // intentionally fail for even numbers
}

```

---

### 10. `@Tag`

- Assign tags to test cases to group or filter them.

```java
java
CopyEdit
@Tag("integration")
@Test
void testIntegrationFeature() {
    // integration test logic
}

```

---

## 🧪 Mockito Annotations

---

### 11. `@Mock`

- Creates a mock instance.

```java
java
CopyEdit
import org.mockito.Mock;
import static org.mockito.Mockito.*;

class MyServiceTest {

    @Mock
    private UserRepository userRepository;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }
}

```

---

### 12. `@InjectMocks`

- Injects mock fields into the tested object automatically.

```java
java
CopyEdit
@InjectMocks
private UserService userService;

```

---

### 13. `@Spy`

- Creates a spy (partial mock) of a real object.

```java
java
CopyEdit
@Spy
List<String> list = new ArrayList<>();

```

---

### 14. `@Captor`

- Captures method arguments.

```java
java
CopyEdit
@Captor
ArgumentCaptor<String> captor;

```

---

### 15. `@ExtendWith(MockitoExtension.class)`

- Tells JUnit 5 to enable Mockito annotations without needing `MockitoAnnotations.openMocks`.
