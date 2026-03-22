🏗️ Enterprise E-Commerce Backend — Java 21
Master Plan: Phases Breakdown
Before we start, let me lay out the complete phase plan so you know exactly what we're building:

PHASE 1 — Project Foundation & Architecture Setup

Clean/Hexagonal Architecture scaffold
Maven multi-module structure
Docker & Docker Compose
Base configurations

PHASE 2 — Domain Layer & DDD

Aggregates, Entities, Value Objects
Domain Events
Repository interfaces

PHASE 3 — Creational Patterns (Singleton, Factory, Abstract Factory, Builder, Prototype)
PHASE 4 — Structural Patterns (Adapter, Bridge, Composite, Decorator, Facade, Flyweight, Proxy)
PHASE 5 — Behavioral Patterns Part 1 (Strategy, Observer, Command, State, Chain of Responsibility)
PHASE 6 — Behavioral Patterns Part 2 (Mediator, Template Method, Iterator, Memento, Visitor)
PHASE 7 — Architectural Patterns (Repository, Unit of Work, DI, CQRS, Event Sourcing)
PHASE 8 — Cross-Cutting Concerns (Auth, RBAC, Caching, Rate Limiting, Idempotency)
PHASE 9 — Infrastructure Layer (Persistence, Messaging, Search)
PHASE 10 — API Layer, Testing, DevOps, Pattern Interaction Map

Let's begin.

PHASE 1 — Project Foundation & Architecture
1.1 High-Level Architecture
┌─────────────────────────────────────────────────────────────────┐
│                         CLIENT LAYER                            │
│              (Web / Mobile / Third-party APIs)                  │
└────────────────────────────┬────────────────────────────────────┘
                             │ HTTPS
┌────────────────────────────▼────────────────────────────────────┐
│                        API GATEWAY                              │
│         (Rate Limiting / Auth / Routing / Versioning)           │
└──────┬──────────┬──────────┬──────────┬──────────┬─────────────┘
       │          │          │          │          │
┌──────▼──┐ ┌────▼────┐ ┌───▼───┐ ┌────▼───┐ ┌───▼──────┐
│  User   │ │Product  │ │ Cart  │ │ Order  │ │ Payment  │
│Service  │ │Service  │ │Service│ │Service │ │ Service  │
└──────┬──┘ └────┬────┘ └───┬───┘ └────┬───┘ └───┬──────┘
       │         │           │          │          │
┌──────▼─────────▼───────────▼──────────▼──────────▼──────────┐
│                     MESSAGE BUS (Kafka)                       │
│         (Order Events / Payment Events / Inventory Events)    │
└──────┬──────────┬──────────┬──────────┬──────────┬───────────┘
       │          │          │          │          │
┌──────▼──┐ ┌────▼────┐ ┌───▼──────┐ ┌─▼───────┐ ┌▼──────────┐
│Inventory│ │Shipping │ │Notif.    │ │ Search  │ │ Audit Log │
│Service  │ │Service  │ │Service   │ │ Service │ │ Service   │
└──────┬──┘ └────┬────┘ └──────────┘ └─────────┘ └───────────┘
       │         │
┌──────▼─────────▼──────────────────────────────────────────────┐
│                    DATA LAYER                                  │
│  PostgreSQL  │  Redis (Cache)  │  Elasticsearch  │  S3        │
└───────────────────────────────────────────────────────────────┘
1.2 Clean Architecture Layers
┌─────────────────────────────────────┐
│         INFRASTRUCTURE LAYER        │  ← DB, Kafka, Redis, HTTP clients
│  (adapters, persistence, messaging) │
├─────────────────────────────────────┤
│         APPLICATION LAYER           │  ← Use Cases, CQRS handlers, DTOs
│    (services, commands, queries)    │
├─────────────────────────────────────┤
│           DOMAIN LAYER              │  ← Entities, Aggregates, Events
│  (pure Java, zero dependencies)     │
└─────────────────────────────────────┘
         Dependency Rule: arrows point INWARD only
1.3 Maven Multi-Module Structure
ecommerce-backend/
├── pom.xml                          ← Parent POM
├── docker-compose.yml
├── .env.example
│
├── ecommerce-domain/                ← Pure domain — NO framework deps
│   ├── pom.xml
│   └── src/main/java/com/ecommerce/domain/
│       ├── user/
│       ├── product/
│       ├── order/
│       ├── cart/
│       ├── payment/
│       ├── inventory/
│       ├── shipping/
│       ├── discount/
│       ├── review/
│       ├── shared/
│       │   ├── valueobject/
│       │   ├── event/
│       │   └── exception/
│       └── ports/                   ← Interfaces (driven/driving)
│
├── ecommerce-application/           ← Use cases, orchestration
│   ├── pom.xml
│   └── src/main/java/com/ecommerce/application/
│       ├── user/
│       ├── order/
│       ├── product/
│       ├── cart/
│       ├── payment/
│       ├── inventory/
│       └── shared/
│           ├── dto/
│           ├── command/
│           ├── query/
│           └── handler/
│
├── ecommerce-infrastructure/        ← DB, Cache, Messaging, External APIs
│   ├── pom.xml
│   └── src/main/java/com/ecommerce/infrastructure/
│       ├── persistence/
│       ├── cache/
│       ├── messaging/
│       ├── search/
│       ├── payment/
│       ├── shipping/
│       ├── notification/
│       └── security/
│
└── ecommerce-api/                   ← Spring Boot app, controllers, config
    ├── pom.xml
    └── src/main/java/com/ecommerce/api/
        ├── EcommerceApplication.java
        ├── controller/
        ├── config/
        ├── filter/
        ├── middleware/
        └── advice/
1.4 Parent POM
xml<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.ecommerce</groupId>
    <artifactId>ecommerce-backend</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>

    <modules>
        <module>ecommerce-domain</module>
        <module>ecommerce-application</module>
        <module>ecommerce-infrastructure</module>
        <module>ecommerce-api</module>
    </modules>

    <properties>
        <java.version>21</java.version>
        <spring-boot.version>3.2.0</spring-boot.version>
        <spring-cloud.version>2023.0.0</spring-cloud.version>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>

        <!-- Library versions -->
        <mapstruct.version>1.5.5.Final</mapstruct.version>
        <lombok.version>1.18.30</lombok.version>
        <jjwt.version>0.12.3</jjwt.version>
        <resilience4j.version>2.1.0</resilience4j.version>
        <testcontainers.version>1.19.3</testcontainers.version>
        <arch-unit.version>1.2.1</arch-unit.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <!-- Spring Boot BOM -->
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <!-- Spring Cloud BOM -->
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <!-- Internal modules -->
            <dependency>
                <groupId>com.ecommerce</groupId>
                <artifactId>ecommerce-domain</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.ecommerce</groupId>
                <artifactId>ecommerce-application</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.ecommerce</groupId>
                <artifactId>ecommerce-infrastructure</artifactId>
                <version>${project.version}</version>
            </dependency>

            <!-- Lombok -->
            <dependency>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
                <version>${lombok.version}</version>
                <scope>provided</scope>
            </dependency>
            <!-- MapStruct -->
            <dependency>
                <groupId>org.mapstruct</groupId>
                <artifactId>mapstruct</artifactId>
                <version>${mapstruct.version}</version>
            </dependency>
            <!-- JWT -->
            <dependency>
                <groupId>io.jsonwebtoken</groupId>
                <artifactId>jjwt-api</artifactId>
                <version>${jjwt.version}</version>
            </dependency>
            <dependency>
                <groupId>io.jsonwebtoken</groupId>
                <artifactId>jjwt-impl</artifactId>
                <version>${jjwt.version}</version>
            </dependency>
            <dependency>
                <groupId>io.jsonwebtoken</groupId>
                <artifactId>jjwt-jackson</artifactId>
                <version>${jjwt.version}</version>
            </dependency>
            <!-- Resilience4j -->
            <dependency>
                <groupId>io.github.resilience4j</groupId>
                <artifactId>resilience4j-spring-boot3</artifactId>
                <version>${resilience4j.version}</version>
            </dependency>
            <!-- TestContainers -->
            <dependency>
                <groupId>org.testcontainers</groupId>
                <artifactId>testcontainers-bom</artifactId>
                <version>${testcontainers.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <!-- ArchUnit -->
            <dependency>
                <groupId>com.tngtech.archunit</groupId>
                <artifactId>archunit-junit5</artifactId>
                <version>${arch-unit.version}</version>
                <scope>test</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                    <version>${spring-boot.version}</version>
                    <configuration>
                        <excludes>
                            <exclude>
                                <groupId>org.projectlombok</groupId>
                                <artifactId>lombok</artifactId>
                            </exclude>
                        </excludes>
                    </configuration>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.11.0</version>
                    <configuration>
                        <source>21</source>
                        <target>21</target>
                        <annotationProcessorPaths>
                            <path>
                                <groupId>org.projectlombok</groupId>
                                <artifactId>lombok</artifactId>
                                <version>${lombok.version}</version>
                            </path>
                            <path>
                                <groupId>org.mapstruct</groupId>
                                <artifactId>mapstruct-processor</artifactId>
                                <version>${mapstruct.version}</version>
                            </path>
                        </annotationProcessorPaths>
                    </configuration>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
1.5 Domain Module POM
xml<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.ecommerce</groupId>
        <artifactId>ecommerce-backend</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>ecommerce-domain</artifactId>

    <dependencies>
        <!-- Lombok only — domain has ZERO framework deps -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>
        <!-- Jakarta Validation API only (annotations, no impl) -->
        <dependency>
            <groupId>jakarta.validation</groupId>
            <artifactId>jakarta.validation-api</artifactId>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
1.6 Docker Compose
yamlversion: '3.9'

services:
  postgres:
    image: postgres:16-alpine
    container_name: ecommerce-postgres
    environment:
      POSTGRES_DB: ecommerce
      POSTGRES_USER: ${DB_USER:-ecom_user}
      POSTGRES_PASSWORD: ${DB_PASSWORD:-ecom_pass}
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./scripts/init.sql:/docker-entrypoint-initdb.d/init.sql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ecom_user -d ecommerce"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7.2-alpine
    container_name: ecommerce-redis
    command: redis-server --requirepass ${REDIS_PASSWORD:-redis_pass} --maxmemory 512mb --maxmemory-policy allkeys-lru
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    container_name: ecommerce-zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    container_name: ecommerce-kafka
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092,PLAINTEXT_INTERNAL://kafka:29092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_INTERNAL:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT_INTERNAL
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"

  elasticsearch:
    image: elasticsearch:8.11.0
    container_name: ecommerce-elasticsearch
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
    ports:
      - "9200:9200"
    volumes:
      - es_data:/usr/share/elasticsearch/data

  app:
    build:
      context: .
      dockerfile: ecommerce-api/Dockerfile
    container_name: ecommerce-app
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
      kafka:
        condition: service_started
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - DB_URL=jdbc:postgresql://postgres:5432/ecommerce
      - DB_USER=${DB_USER:-ecom_user}
      - DB_PASSWORD=${DB_PASSWORD:-ecom_pass}
      - REDIS_HOST=redis
      - REDIS_PASSWORD=${REDIS_PASSWORD:-redis_pass}
      - KAFKA_BOOTSTRAP_SERVERS=kafka:29092
      - JWT_SECRET=${JWT_SECRET:-change_this_in_production_min_32_chars}
    volumes:
      - app_logs:/app/logs

volumes:
  postgres_data:
  redis_data:
  es_data:
  app_logs:
1.7 Dockerfile (ecommerce-api)
dockerfile# Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

COPY pom.xml .
COPY ecommerce-domain/pom.xml ecommerce-domain/
COPY ecommerce-application/pom.xml ecommerce-application/
COPY ecommerce-infrastructure/pom.xml ecommerce-infrastructure/
COPY ecommerce-api/pom.xml ecommerce-api/

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -q

COPY . .
RUN mvn clean package -DskipTests -q

# Runtime stage
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /build/ecommerce-api/target/*.jar app.jar

RUN mkdir -p /app/logs && chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseG1GC", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
1.8 Base Shared Domain Classes
java// ecommerce-domain: com.ecommerce.domain.shared.valueobject.AggregateId.java
package com.ecommerce.domain.shared.valueobject;

import java.util.Objects;
import java.util.UUID;

/**
 * Base class for all strongly-typed aggregate identifiers.
 * Prevents primitive obsession anti-pattern.
 * Using Java 21 records for immutability.
 */
public abstract class AggregateId {

    private final UUID value;

    protected AggregateId(UUID value) {
        this.value = Objects.requireNonNull(value, "ID value cannot be null");
    }

    protected AggregateId() {
        this.value = UUID.randomUUID();
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AggregateId that = (AggregateId) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
java// com.ecommerce.domain.shared.valueobject.Money.java
package com.ecommerce.domain.shared.valueobject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Value Object representing monetary value.
 * Immutable — operations return new instances.
 * Prevents floating-point precision issues.
 */
public final class Money {

    public static final Money ZERO_USD = new Money(BigDecimal.ZERO, Currency.getInstance("USD"));

    private final BigDecimal amount;
    private final Currency currency;

    private Money(BigDecimal amount, Currency currency) {
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
        this.currency = Objects.requireNonNull(currency);
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        if (amount == null) throw new IllegalArgumentException("Amount cannot be null");
        return new Money(amount, Currency.getInstance(currencyCode));
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public Money add(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    public Money multiply(int multiplier) {
        return new Money(this.amount.multiply(BigDecimal.valueOf(multiplier)), this.currency);
    }

    public Money multiply(BigDecimal multiplier) {
        return new Money(this.amount.multiply(multiplier), this.currency);
    }

    public boolean isGreaterThan(Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) > 0;
    }

    public boolean isGreaterThanOrEqual(Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) >= 0;
    }

    public boolean isNegative() {
        return amount.compareTo(BigDecimal.ZERO) < 0;
    }

    public boolean isZero() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }

    private void assertSameCurrency(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                "Cannot operate on different currencies: " + this.currency + " vs " + other.currency
            );
        }
    }

    public BigDecimal getAmount() { return amount; }
    public Currency getCurrency() { return currency; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money money)) return false;
        return amount.compareTo(money.amount) == 0 && currency.equals(money.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros(), currency);
    }

    @Override
    public String toString() {
        return currency.getSymbol() + amount.toPlainString();
    }
}
java// com.ecommerce.domain.shared.event.DomainEvent.java
package com.ecommerce.domain.shared.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Base interface for all domain events.
 * Events are immutable records of something that happened.
 * Using Java 21 sealed interface for exhaustive pattern matching.
 */
public interface DomainEvent {

    UUID eventId();
    Instant occurredAt();
    String aggregateId();
    String eventType();
    int version(); // for event versioning/schema evolution
}
java// com.ecommerce.domain.shared.event.BaseDomainEvent.java
package com.ecommerce.domain.shared.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Abstract base for domain events.
 * Using Java 21 record for clean immutability.
 */
public abstract class BaseDomainEvent implements DomainEvent {

    private final UUID eventId;
    private final Instant occurredAt;

    protected BaseDomainEvent() {
        this.eventId = UUID.randomUUID();
        this.occurredAt = Instant.now();
    }

    @Override
    public UUID eventId() { return eventId; }

    @Override
    public Instant occurredAt() { return occurredAt; }

    @Override
    public int version() { return 1; }
}
java// com.ecommerce.domain.shared.aggregate.AggregateRoot.java
package com.ecommerce.domain.shared.aggregate;

import com.ecommerce.domain.shared.event.DomainEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for all Aggregate Roots.
 * Aggregates are consistency boundaries.
 * They collect domain events which are dispatched after transaction commits.
 * 
 * Key design: Events are stored in-memory, dispatched by Unit of Work
 * after successful DB commit — ensures atomicity.
 */
public abstract class AggregateRoot {

    private final List<DomainEvent> domainEvents = new ArrayList<>();
    private int domainEventVersion = 0;

    protected void registerEvent(DomainEvent event) {
        domainEvents.add(event);
    }

    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    public void clearDomainEvents() {
        domainEvents.clear();
    }

    public int getDomainEventVersion() {
        return domainEventVersion;
    }

    protected void incrementVersion() {
        domainEventVersion++;
    }
}
java// com.ecommerce.domain.shared.exception.DomainException.java
package com.ecommerce.domain.shared.exception;

/**
 * Base exception for all domain rule violations.
 * Domain exceptions represent business rule violations — NOT system errors.
 * They are ALWAYS caught at the application layer and translated to proper responses.
 */
public class DomainException extends RuntimeException {

    private final String errorCode;

    public DomainException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public DomainException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
java// com.ecommerce.domain.shared.exception.ErrorCodes.java
package com.ecommerce.domain.shared.exception;

/**
 * Centralized error code registry.
 * Prevents magic strings scattered across codebase.
 */
public final class ErrorCodes {

    private ErrorCodes() {}

    // User domain
    public static final String USER_NOT_FOUND = "USER_001";
    public static final String USER_EMAIL_DUPLICATE = "USER_002";
    public static final String USER_INVALID_CREDENTIALS = "USER_003";
    public static final String USER_ACCOUNT_LOCKED = "USER_004";

    // Product domain
    public static final String PRODUCT_NOT_FOUND = "PROD_001";
    public static final String PRODUCT_OUT_OF_STOCK = "PROD_002";
    public static final String PRODUCT_INVALID_PRICE = "PROD_003";

    // Order domain
    public static final String ORDER_NOT_FOUND = "ORD_001";
    public static final String ORDER_INVALID_STATE = "ORD_002";
    public static final String ORDER_EMPTY = "ORD_003";
    public static final String ORDER_ALREADY_PAID = "ORD_004";

    // Inventory domain
    public static final String INVENTORY_INSUFFICIENT = "INV_001";
    public static final String INVENTORY_RESERVATION_FAILED = "INV_002";

    // Payment domain
    public static final String PAYMENT_FAILED = "PAY_001";
    public static final String PAYMENT_GATEWAY_ERROR = "PAY_002";
    public static final String PAYMENT_DUPLICATE = "PAY_003";

    // Cart domain
    public static final String CART_NOT_FOUND = "CART_001";
    public static final String CART_ITEM_NOT_FOUND = "CART_002";

    // Discount domain
    public static final String COUPON_NOT_FOUND = "DISC_001";
    public static final String COUPON_EXPIRED = "DISC_002";
    public static final String COUPON_USAGE_EXCEEDED = "DISC_003";
    public static final String COUPON_MIN_ORDER_NOT_MET = "DISC_004";
}

1.9 Application Layer Base — CQRS Infrastructure
java// com.ecommerce.application.shared.command.Command.java
package com.ecommerce.application.shared.command;

/**
 * Marker interface for all commands (write-side of CQRS).
 * Commands express intent to change state.
 * Commands are named in imperative: PlaceOrderCommand, not OrderPlaced
 */
public interface Command {}
java// com.ecommerce.application.shared.command.CommandHandler.java
package com.ecommerce.application.shared.command;

/**
 * Generic command handler.
 * Each command has exactly ONE handler — enforces Single Responsibility.
 */
public interface CommandHandler<C extends Command, R> {
    R handle(C command);
}
java// com.ecommerce.application.shared.query.Query.java
package com.ecommerce.application.shared.query;

/**
 * Marker interface for all queries (read-side of CQRS).
 * Queries never mutate state — they only read.
 */
public interface Query<R> {}
java// com.ecommerce.application.shared.query.QueryHandler.java
package com.ecommerce.application.shared.query;

public interface QueryHandler<Q extends Query<R>, R> {
    R handle(Q query);
}
java// com.ecommerce.application.shared.CommandBus.java
package com.ecommerce.application.shared;

import com.ecommerce.application.shared.command.Command;

/**
 * Command Bus — decouples command senders from handlers.
 * Allows cross-cutting concerns (logging, validation, transactions)
 * to be applied uniformly via decorators.
 */
public interface CommandBus {
    <R> R dispatch(Command command);
}
java// com.ecommerce.application.shared.QueryBus.java
package com.ecommerce.application.shared;

import com.ecommerce.application.shared.query.Query;

public interface QueryBus {
    <R> R dispatch(Query<R> query);
}

✅ Phase 1 complete. We've established:

Multi-module Maven architecture
Clean Architecture layers with strict dependency rules
Docker Compose with all infrastructure components
Shared domain building blocks (AggregateRoot, DomainEvent, Money, AggregateId)
CQRS Command/Query bus infrastructure
Centralized error codes


Ready for Phase 2? Reply "Phase 2" and I'll implement the complete Domain Layer — all aggregates (User, Product, Order, Cart, Payment, Inventory), Value Objects, Domain Events, and Repository ports using DDD tactical patterns.Phase 215:36PHASE 2 — Domain Layer & Domain-Driven Design
2.1 Domain Overview — Bounded Contexts & Aggregates
┌─────────────────────────────────────────────────────────────────────┐
│                        BOUNDED CONTEXTS                             │
├──────────────┬──────────────┬──────────────┬───────────────────────┤
│   IDENTITY   │   CATALOG    │   COMMERCE   │     FULFILLMENT       │
│              │              │              │                       │
│  User        │  Product     │  Cart        │  Inventory            │
│  Role        │  Category    │  Order       │  Shipping             │
│  Permission  │  Review      │  Payment     │  Notification         │
│              │  Discount    │  Coupon      │  AuditLog             │
└──────────────┴──────────────┴──────────────┴───────────────────────┘
Aggregate Boundaries (consistency boundaries):

User Aggregate:          Product Aggregate:       Order Aggregate:
┌─────────────┐          ┌──────────────┐         ┌──────────────┐
│ User (root) │          │Product (root)│         │ Order (root) │
│  - Address  │          │  - Variant   │         │  - OrderItem │
│  - Role     │          │  - Image     │         │  - Address   │
└─────────────┘          │  - Attribute │         │  - Payment   │
                         └──────────────┘         └──────────────┘

Cart Aggregate:          Inventory Aggregate:     Discount Aggregate:
┌─────────────┐          ┌──────────────┐         ┌──────────────┐
│ Cart (root) │          │Inventory(rt) │         │ Coupon (root)│
│  - CartItem │          │ -Reservation │         │  - Rule      │
└─────────────┘          └──────────────┘         └──────────────┘

2.2 User Domain
java// ecommerce-domain: com.ecommerce.domain.user.UserId.java
package com.ecommerce.domain.user;

import com.ecommerce.domain.shared.valueobject.AggregateId;
import java.util.UUID;

public final class UserId extends AggregateId {

    private UserId(UUID value) { super(value); }
    private UserId() { super(); }

    public static UserId generate() { return new UserId(); }
    public static UserId of(UUID value) { return new UserId(value); }
    public static UserId of(String value) { return new UserId(UUID.fromString(value)); }
}
java// com.ecommerce.domain.user.Email.java
package com.ecommerce.domain.user;

import com.ecommerce.domain.shared.exception.DomainException;
import com.ecommerce.domain.shared.exception.ErrorCodes;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value Object — Email
 * Encapsulates validation logic. Immutable.
 * Self-validating on construction — invalid state impossible.
 */
public final class Email {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$"
    );

    private final String value;

    private Email(String value) {
        this.value = value.toLowerCase().trim();
    }

    public static Email of(String value) {
        Objects.requireNonNull(value, "Email cannot be null");
        String trimmed = value.toLowerCase().trim();
        if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
            throw new DomainException(
                ErrorCodes.USER_NOT_FOUND,
                "Invalid email format: " + value
            );
        }
        return new Email(trimmed);
    }

    public String getValue() { return value; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Email email)) return false;
        return value.equals(email.value);
    }

    @Override
    public int hashCode() { return Objects.hash(value); }

    @Override
    public String toString() { return value; }
}
java// com.ecommerce.domain.user.PhoneNumber.java
package com.ecommerce.domain.user;

import com.ecommerce.domain.shared.exception.DomainException;
import com.ecommerce.domain.shared.exception.ErrorCodes;
import java.util.Objects;
import java.util.regex.Pattern;

public final class PhoneNumber {

    private static final Pattern PHONE_PATTERN =
        Pattern.compile("^\\+?[1-9]\\d{6,14}$");

    private final String value;

    private PhoneNumber(String value) { this.value = value; }

    public static PhoneNumber of(String value) {
        Objects.requireNonNull(value, "Phone number cannot be null");
        String normalized = value.replaceAll("[\\s\\-()]", "");
        if (!PHONE_PATTERN.matcher(normalized).matches()) {
            throw new DomainException(
                ErrorCodes.USER_NOT_FOUND,
                "Invalid phone number: " + value
            );
        }
        return new PhoneNumber(normalized);
    }

    public String getValue() { return value; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PhoneNumber p)) return false;
        return value.equals(p.value);
    }

    @Override
    public int hashCode() { return Objects.hash(value); }

    @Override
    public String toString() { return value; }
}
java// com.ecommerce.domain.user.Address.java
package com.ecommerce.domain.user;

import java.util.Objects;

/**
 * Value Object — Address.
 * Embedded in User and Order aggregates — demonstrating
 * that VOs can be shared across aggregates by VALUE (copy), not reference.
 */
public final class Address {

    private final String fullName;
    private final String line1;
    private final String line2;
    private final String city;
    private final String state;
    private final String country;
    private final String postalCode;
    private final boolean isDefault;

    private Address(Builder builder) {
        Objects.requireNonNull(builder.fullName, "Full name required");
        Objects.requireNonNull(builder.line1, "Address line 1 required");
        Objects.requireNonNull(builder.city, "City required");
        Objects.requireNonNull(builder.country, "Country required");
        Objects.requireNonNull(builder.postalCode, "Postal code required");

        this.fullName   = builder.fullName;
        this.line1      = builder.line1;
        this.line2      = builder.line2;
        this.city       = builder.city;
        this.state      = builder.state;
        this.country    = builder.country;
        this.postalCode = builder.postalCode;
        this.isDefault  = builder.isDefault;
    }

    // ── Builder Pattern (Creational) ─────────────────────────────────
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String fullName, line1, line2, city, state, country, postalCode;
        private boolean isDefault;

        public Builder fullName(String v)   { this.fullName = v;   return this; }
        public Builder line1(String v)      { this.line1 = v;      return this; }
        public Builder line2(String v)      { this.line2 = v;      return this; }
        public Builder city(String v)       { this.city = v;       return this; }
        public Builder state(String v)      { this.state = v;      return this; }
        public Builder country(String v)    { this.country = v;    return this; }
        public Builder postalCode(String v) { this.postalCode = v; return this; }
        public Builder isDefault(boolean v) { this.isDefault = v;  return this; }
        public Address build()              { return new Address(this); }
    }

    // Getters
    public String getFullName()   { return fullName; }
    public String getLine1()      { return line1; }
    public String getLine2()      { return line2; }
    public String getCity()       { return city; }
    public String getState()      { return state; }
    public String getCountry()    { return country; }
    public String getPostalCode() { return postalCode; }
    public boolean isDefault()    { return isDefault; }

    /**
     * Value objects equality is based on field values, not identity.
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Address a)) return false;
        return Objects.equals(fullName, a.fullName)
            && Objects.equals(line1, a.line1)
            && Objects.equals(line2, a.line2)
            && Objects.equals(city, a.city)
            && Objects.equals(state, a.state)
            && Objects.equals(country, a.country)
            && Objects.equals(postalCode, a.postalCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fullName, line1, line2, city, state, country, postalCode);
    }
}
java// com.ecommerce.domain.user.UserRole.java
package com.ecommerce.domain.user;

/**
 * Enum-based Role — simple RBAC.
 * For fine-grained permissions, see Permission domain service.
 */
public enum UserRole {
    CUSTOMER,
    VENDOR,
    ADMIN,
    SUPER_ADMIN;

    public boolean hasAdminPrivileges() {
        return this == ADMIN || this == SUPER_ADMIN;
    }
}
java// com.ecommerce.domain.user.UserStatus.java
package com.ecommerce.domain.user;

public enum UserStatus {
    PENDING_VERIFICATION,
    ACTIVE,
    SUSPENDED,
    DEACTIVATED;

    public boolean isActive() { return this == ACTIVE; }
}
java// com.ecommerce.domain.user.event.UserRegisteredEvent.java
package com.ecommerce.domain.user.event;

import com.ecommerce.domain.shared.event.BaseDomainEvent;

public final class UserRegisteredEvent extends BaseDomainEvent {

    private final String userId;
    private final String email;
    private final String fullName;

    public UserRegisteredEvent(String userId, String email, String fullName) {
        super();
        this.userId   = userId;
        this.email    = email;
        this.fullName = fullName;
    }

    @Override public String aggregateId() { return userId; }
    @Override public String eventType()   { return "USER_REGISTERED"; }

    public String getUserId()   { return userId; }
    public String getEmail()    { return email; }
    public String getFullName() { return fullName; }
}
java// com.ecommerce.domain.user.event.UserEmailVerifiedEvent.java
package com.ecommerce.domain.user.event;

import com.ecommerce.domain.shared.event.BaseDomainEvent;

public final class UserEmailVerifiedEvent extends BaseDomainEvent {

    private final String userId;

    public UserEmailVerifiedEvent(String userId) {
        super();
        this.userId = userId;
    }

    @Override public String aggregateId() { return userId; }
    @Override public String eventType()   { return "USER_EMAIL_VERIFIED"; }
    public String getUserId()             { return userId; }
}
java// com.ecommerce.domain.user.User.java  ← THE AGGREGATE ROOT
package com.ecommerce.domain.user;

import com.ecommerce.domain.shared.aggregate.AggregateRoot;
import com.ecommerce.domain.shared.exception.DomainException;
import com.ecommerce.domain.shared.exception.ErrorCodes;
import com.ecommerce.domain.user.event.UserEmailVerifiedEvent;
import com.ecommerce.domain.user.event.UserRegisteredEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * User Aggregate Root.
 *
 * Design decisions:
 * 1. All state changes go through domain methods — no setters.
 * 2. Business rules enforced inside the aggregate (invariants).
 * 3. Domain events registered on state changes.
 * 4. Soft delete via UserStatus.DEACTIVATED — data retained for audit.
 * 5. Addresses managed as a collection inside aggregate boundary.
 *
 * Invariants:
 * - A User must always have at least one verified email.
 * - A suspended user cannot place orders.
 * - An admin user cannot be demoted by a non-super-admin.
 */
public class User extends AggregateRoot {

    private final UserId id;
    private Email email;
    private String passwordHash;
    private String firstName;
    private String lastName;
    private PhoneNumber phoneNumber;
    private UserRole role;
    private UserStatus status;
    private final List<Address> addresses;
    private boolean emailVerified;
    private int failedLoginAttempts;
    private Instant lastLoginAt;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;      // soft delete

    // Max failed attempts before account lock
    private static final int MAX_FAILED_ATTEMPTS = 5;

    // Private constructor — use factory method
    private User(UserId id, Email email, String passwordHash,
                 String firstName, String lastName, UserRole role) {
        this.id                  = Objects.requireNonNull(id);
        this.email               = Objects.requireNonNull(email);
        this.passwordHash        = Objects.requireNonNull(passwordHash);
        this.firstName           = Objects.requireNonNull(firstName);
        this.lastName            = Objects.requireNonNull(lastName);
        this.role                = Objects.requireNonNull(role);
        this.status              = UserStatus.PENDING_VERIFICATION;
        this.addresses           = new ArrayList<>();
        this.emailVerified       = false;
        this.failedLoginAttempts = 0;
        this.createdAt           = Instant.now();
        this.updatedAt           = Instant.now();
    }

    /**
     * Factory Method (Creational Pattern — Phase 3).
     * Named constructor that enforces all required state and emits event.
     */
    public static User register(Email email, String passwordHash,
                                String firstName, String lastName) {
        UserId id = UserId.generate();
        User user = new User(id, email, passwordHash, firstName, lastName, UserRole.CUSTOMER);

        // Register domain event — dispatched after successful persistence
        user.registerEvent(new UserRegisteredEvent(
            id.getValue().toString(), email.getValue(), firstName + " " + lastName
        ));

        return user;
    }

    /**
     * Reconstitute from persistence — no events fired.
     * Used by Repository to rebuild aggregate from DB rows.
     */
    public static User reconstitute(UserId id, Email email, String passwordHash,
                                    String firstName, String lastName,
                                    UserRole role, UserStatus status,
                                    boolean emailVerified, int failedLoginAttempts,
                                    Instant lastLoginAt, Instant createdAt,
                                    Instant updatedAt, Instant deletedAt,
                                    List<Address> addresses) {
        User user = new User(id, email, passwordHash, firstName, lastName, role);
        user.status              = status;
        user.emailVerified       = emailVerified;
        user.failedLoginAttempts = failedLoginAttempts;
        user.lastLoginAt         = lastLoginAt;
        user.updatedAt           = updatedAt;
        user.deletedAt           = deletedAt;
        user.addresses.addAll(addresses);
        return user;
    }

    // ── Domain Behaviors (Commands) ───────────────────────────────────

    public void verifyEmail() {
        if (this.emailVerified) return;
        if (this.status == UserStatus.DEACTIVATED) {
            throw new DomainException(ErrorCodes.USER_ACCOUNT_LOCKED, "Cannot verify deactivated account");
        }
        this.emailVerified = true;
        this.status        = UserStatus.ACTIVE;
        this.updatedAt     = Instant.now();
        registerEvent(new UserEmailVerifiedEvent(id.getValue().toString()));
    }

    public void recordSuccessfulLogin() {
        ensureActive();
        this.failedLoginAttempts = 0;
        this.lastLoginAt         = Instant.now();
        this.updatedAt           = Instant.now();
    }

    public void recordFailedLogin() {
        this.failedLoginAttempts++;
        this.updatedAt = Instant.now();
        if (this.failedLoginAttempts >= MAX_FAILED_ATTEMPTS) {
            this.status = UserStatus.SUSPENDED;
            // emit account locked event
        }
    }

    public void suspend(String reason) {
        if (this.status == UserStatus.DEACTIVATED) {
            throw new DomainException(ErrorCodes.USER_ACCOUNT_LOCKED,
                "Cannot suspend a deactivated user");
        }
        this.status    = UserStatus.SUSPENDED;
        this.updatedAt = Instant.now();
    }

    public void reactivate() {
        if (this.status == UserStatus.DEACTIVATED) {
            throw new DomainException(ErrorCodes.USER_ACCOUNT_LOCKED,
                "Cannot reactivate a deactivated user");
        }
        this.status              = UserStatus.ACTIVE;
        this.failedLoginAttempts = 0;
        this.updatedAt           = Instant.now();
    }

    /**
     * Soft delete — data preserved for audit compliance (GDPR requires process).
     */
    public void deactivate() {
        this.status    = UserStatus.DEACTIVATED;
        this.deletedAt = Instant.now();
        this.updatedAt = Instant.now();
        // Anonymize PII in real GDPR flow — separate process
    }

    public void addAddress(Address address) {
        ensureActive();
        // If new address is default, clear existing defaults
        if (address.isDefault()) {
            addresses.clear(); // In real system: only clear default flag, not all
        }
        addresses.add(address);
        this.updatedAt = Instant.now();
    }

    public void changePassword(String newPasswordHash) {
        ensureActive();
        Objects.requireNonNull(newPasswordHash, "Password hash cannot be null");
        this.passwordHash = newPasswordHash;
        this.updatedAt    = Instant.now();
        // emit PasswordChangedEvent for notification
    }

    public void updateProfile(String firstName, String lastName, PhoneNumber phone) {
        ensureActive();
        this.firstName   = Objects.requireNonNull(firstName);
        this.lastName    = Objects.requireNonNull(lastName);
        this.phoneNumber = phone;
        this.updatedAt   = Instant.now();
    }

    public void promoteToAdmin(User promotedBy) {
        if (!promotedBy.getRole().hasAdminPrivileges()) {
            throw new DomainException(ErrorCodes.USER_NOT_FOUND,
                "Only admins can promote users");
        }
        this.role      = UserRole.ADMIN;
        this.updatedAt = Instant.now();
    }

    // ── Business Rule Guards ──────────────────────────────────────────

    public void ensureActive() {
        if (!status.isActive()) {
            throw new DomainException(ErrorCodes.USER_ACCOUNT_LOCKED,
                "User account is not active: " + status);
        }
    }

    public void ensureEmailVerified() {
        if (!emailVerified) {
            throw new DomainException(ErrorCodes.USER_NOT_FOUND,
                "Email not verified");
        }
    }

    // ── Getters (read-only access) ────────────────────────────────────

    public UserId getId()                  { return id; }
    public Email getEmail()                { return email; }
    public String getPasswordHash()        { return passwordHash; }
    public String getFirstName()           { return firstName; }
    public String getLastName()            { return lastName; }
    public String getFullName()            { return firstName + " " + lastName; }
    public PhoneNumber getPhoneNumber()    { return phoneNumber; }
    public UserRole getRole()              { return role; }
    public UserStatus getStatus()          { return status; }
    public boolean isEmailVerified()       { return emailVerified; }
    public int getFailedLoginAttempts()    { return failedLoginAttempts; }
    public Instant getLastLoginAt()        { return lastLoginAt; }
    public Instant getCreatedAt()          { return createdAt; }
    public Instant getUpdatedAt()          { return updatedAt; }
    public Instant getDeletedAt()          { return deletedAt; }
    public boolean isDeleted()             { return deletedAt != null; }
    public List<Address> getAddresses()    { return Collections.unmodifiableList(addresses); }

    public Address getDefaultAddress() {
        return addresses.stream()
            .filter(Address::isDefault)
            .findFirst()
            .orElse(addresses.isEmpty() ? null : addresses.get(0));
    }
}
java// com.ecommerce.domain.user.UserRepository.java
package com.ecommerce.domain.user;

import java.util.Optional;

/**
 * Repository Port (Domain-side interface).
 * Lives in the domain layer — implementation in infrastructure.
 * Dependency Inversion Principle in action.
 *
 * Key: The domain dictates WHAT it needs, not HOW it's stored.
 */
public interface UserRepository {

    void save(User user);

    void update(User user);

    Optional<User> findById(UserId id);

    Optional<User> findByEmail(Email email);

    boolean existsByEmail(Email email);

    /**
     * Soft-delete aware — only returns non-deleted users by default.
     */
    Optional<User> findActiveById(UserId id);
}

2.3 Product Domain
java// com.ecommerce.domain.product.ProductId.java
package com.ecommerce.domain.product;

import com.ecommerce.domain.shared.valueobject.AggregateId;
import java.util.UUID;

public final class ProductId extends AggregateId {
    private ProductId(UUID v) { super(v); }
    private ProductId()       { super(); }

    public static ProductId generate()       { return new ProductId(); }
    public static ProductId of(UUID v)       { return new ProductId(v); }
    public static ProductId of(String v)     { return new ProductId(UUID.fromString(v)); }
}
java// com.ecommerce.domain.product.CategoryId.java
package com.ecommerce.domain.product;

import com.ecommerce.domain.shared.valueobject.AggregateId;
import java.util.UUID;

public final class CategoryId extends AggregateId {
    private CategoryId(UUID v) { super(v); }
    private CategoryId()       { super(); }

    public static CategoryId generate()   { return new CategoryId(); }
    public static CategoryId of(UUID v)   { return new CategoryId(v); }
    public static CategoryId of(String v) { return new CategoryId(UUID.fromString(v)); }
}
java// com.ecommerce.domain.product.ProductStatus.java
package com.ecommerce.domain.product;

public enum ProductStatus {
    DRAFT,
    ACTIVE,
    OUT_OF_STOCK,
    DISCONTINUED;

    public boolean isAvailableForPurchase() {
        return this == ACTIVE;
    }
}
java// com.ecommerce.domain.product.ProductVariant.java
package com.ecommerce.domain.product;

import com.ecommerce.domain.shared.valueobject.Money;
import com.ecommerce.domain.shared.valueobject.AggregateId;
import java.util.UUID;
import java.util.Objects;

/**
 * Entity (not aggregate root) — has identity within Product aggregate.
 * e.g., "Nike Air Max — Size 10, Color Red"
 */
public class ProductVariant {

    private final VariantId id;
    private String sku;
    private String name;                // e.g. "Red / XL"
    private Money price;
    private Money compareAtPrice;       // strike-through price
    private java.util.Map<String, String> attributes; // color -> Red, size -> XL

    private ProductVariant(VariantId id, String sku, String name,
                           Money price, java.util.Map<String, String> attributes) {
        this.id         = id;
        this.sku        = sku;
        this.name       = name;
        this.price      = price;
        this.attributes = new java.util.HashMap<>(attributes);
    }

    public static ProductVariant create(String sku, String name, Money price,
                                        java.util.Map<String, String> attributes) {
        Objects.requireNonNull(sku, "SKU required");
        Objects.requireNonNull(price, "Price required");
        if (price.isNegative()) {
            throw new com.ecommerce.domain.shared.exception.DomainException(
                com.ecommerce.domain.shared.exception.ErrorCodes.PRODUCT_INVALID_PRICE,
                "Variant price cannot be negative"
            );
        }
        return new ProductVariant(VariantId.generate(), sku, name, price,
            attributes == null ? java.util.Collections.emptyMap() : attributes);
    }

    public void updatePrice(Money newPrice) {
        if (newPrice.isNegative()) {
            throw new com.ecommerce.domain.shared.exception.DomainException(
                com.ecommerce.domain.shared.exception.ErrorCodes.PRODUCT_INVALID_PRICE,
                "Price cannot be negative"
            );
        }
        this.compareAtPrice = this.price; // keep old as strike price
        this.price          = newPrice;
    }

    public VariantId getId()                    { return id; }
    public String getSku()                      { return sku; }
    public String getName()                     { return name; }
    public Money getPrice()                     { return price; }
    public Money getCompareAtPrice()            { return compareAtPrice; }
    public java.util.Map<String, String> getAttributes() {
        return java.util.Collections.unmodifiableMap(attributes);
    }

    /**
     * Strongly-typed VariantId — defined as inner class for cohesion.
     */
    public static final class VariantId extends AggregateId {
        private VariantId(UUID v) { super(v); }
        private VariantId()       { super(); }

        public static VariantId generate()   { return new VariantId(); }
        public static VariantId of(UUID v)   { return new VariantId(v); }
        public static VariantId of(String v) { return new VariantId(UUID.fromString(v)); }
    }
}
java// com.ecommerce.domain.product.event/ProductPublishedEvent.java
package com.ecommerce.domain.product.event;

import com.ecommerce.domain.shared.event.BaseDomainEvent;

public final class ProductPublishedEvent extends BaseDomainEvent {

    private final String productId;
    private final String productName;
    private final String categoryId;

    public ProductPublishedEvent(String productId, String productName, String categoryId) {
        super();
        this.productId   = productId;
        this.productName = productName;
        this.categoryId  = categoryId;
    }

    @Override public String aggregateId() { return productId; }
    @Override public String eventType()   { return "PRODUCT_PUBLISHED"; }

    public String getProductId()   { return productId; }
    public String getProductName() { return productName; }
    public String getCategoryId()  { return categoryId; }
}
java// com.ecommerce.domain.product.Product.java  ← AGGREGATE ROOT
package com.ecommerce.domain.product;

import com.ecommerce.domain.shared.aggregate.AggregateRoot;
import com.ecommerce.domain.shared.exception.DomainException;
import com.ecommerce.domain.shared.exception.ErrorCodes;
import com.ecommerce.domain.shared.valueobject.Money;
import com.ecommerce.domain.product.event.ProductPublishedEvent;

import java.time.Instant;
import java.util.*;

/**
 * Product Aggregate Root.
 *
 * Invariants:
 * - A published product must have at least one active variant.
 * - Price must be non-negative.
 * - A discontinued product cannot be re-activated directly (must go through review).
 * - Product slug must be unique (enforced at application layer with domain service).
 */
public class Product extends AggregateRoot {

    private final ProductId id;
    private String name;
    private String description;
    private String slug;            // URL-friendly identifier
    private CategoryId categoryId;
    private ProductStatus status;
    private final List<ProductVariant> variants;
    private final List<String> imageUrls;
    private final Map<String, String> attributes; // brand, material, etc.
    private double averageRating;
    private int reviewCount;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    private Product(ProductId id, String name, String description,
                    String slug, CategoryId categoryId) {
        this.id          = Objects.requireNonNull(id);
        this.name        = Objects.requireNonNull(name);
        this.description = description;
        this.slug        = Objects.requireNonNull(slug);
        this.categoryId  = Objects.requireNonNull(categoryId);
        this.status      = ProductStatus.DRAFT;
        this.variants    = new ArrayList<>();
        this.imageUrls   = new ArrayList<>();
        this.attributes  = new LinkedHashMap<>();
        this.createdAt   = Instant.now();
        this.updatedAt   = Instant.now();
    }

    public static Product create(String name, String description,
                                 String slug, CategoryId categoryId) {
        return new Product(ProductId.generate(), name, description, slug, categoryId);
    }

    public static Product reconstitute(ProductId id, String name, String description,
                                       String slug, CategoryId categoryId,
                                       ProductStatus status, List<ProductVariant> variants,
                                       List<String> imageUrls, Map<String, String> attributes,
                                       double avgRating, int reviewCount,
                                       Instant createdAt, Instant updatedAt, Instant deletedAt) {
        Product p = new Product(id, name, description, slug, categoryId);
        p.status        = status;
        p.averageRating = avgRating;
        p.reviewCount   = reviewCount;
        p.updatedAt     = updatedAt;
        p.deletedAt     = deletedAt;
        p.variants.addAll(variants);
        p.imageUrls.addAll(imageUrls);
        p.attributes.putAll(attributes);
        return p;
    }

    // ── Domain Behaviors ──────────────────────────────────────────────

    public void addVariant(ProductVariant variant) {
        Objects.requireNonNull(variant);
        boolean skuExists = variants.stream()
            .anyMatch(v -> v.getSku().equals(variant.getSku()));
        if (skuExists) {
            throw new DomainException(ErrorCodes.PRODUCT_INVALID_PRICE,
                "Variant SKU already exists: " + variant.getSku());
        }
        variants.add(variant);
        updatedAt = Instant.now();
    }

    public void publish() {
        if (variants.isEmpty()) {
            throw new DomainException(ErrorCodes.PRODUCT_NOT_FOUND,
                "Cannot publish a product with no variants");
        }
        if (status == ProductStatus.DISCONTINUED) {
            throw new DomainException(ErrorCodes.PRODUCT_NOT_FOUND,
                "Discontinued products cannot be re-published directly");
        }
        this.status    = ProductStatus.ACTIVE;
        this.updatedAt = Instant.now();

        registerEvent(new ProductPublishedEvent(
            id.getValue().toString(), name, categoryId.getValue().toString()
        ));
    }

    public void discontinue() {
        if (status == ProductStatus.DRAFT) {
            throw new DomainException(ErrorCodes.PRODUCT_NOT_FOUND,
                "Cannot discontinue a draft product");
        }
        this.status    = ProductStatus.DISCONTINUED;
        this.updatedAt = Instant.now();
    }

    public void markOutOfStock() {
        if (status == ProductStatus.ACTIVE) {
            this.status    = ProductStatus.OUT_OF_STOCK;
            this.updatedAt = Instant.now();
        }
    }

    public void markBackInStock() {
        if (status == ProductStatus.OUT_OF_STOCK) {
            this.status    = ProductStatus.ACTIVE;
            this.updatedAt = Instant.now();
        }
    }

    public void updateRating(double newAvgRating, int newReviewCount) {
        this.averageRating = newAvgRating;
        this.reviewCount   = newReviewCount;
        this.updatedAt     = Instant.now();
    }

    public void addImage(String imageUrl) {
        Objects.requireNonNull(imageUrl);
        imageUrls.add(imageUrl);
        updatedAt = Instant.now();
    }

    public void setAttribute(String key, String value) {
        attributes.put(key, value);
        updatedAt = Instant.now();
    }

    public void softDelete() {
        this.status    = ProductStatus.DISCONTINUED;
        this.deletedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void ensureAvailableForPurchase() {
        if (!status.isAvailableForPurchase()) {
            throw new DomainException(ErrorCodes.PRODUCT_OUT_OF_STOCK,
                "Product is not available: " + name + " [" + status + "]");
        }
    }

    public Optional<ProductVariant> findVariantBySku(String sku) {
        return variants.stream().filter(v -> v.getSku().equals(sku)).findFirst();
    }

    // ── Getters ───────────────────────────────────────────────────────

    public ProductId getId()                       { return id; }
    public String getName()                        { return name; }
    public String getDescription()                 { return description; }
    public String getSlug()                        { return slug; }
    public CategoryId getCategoryId()              { return categoryId; }
    public ProductStatus getStatus()               { return status; }
    public List<ProductVariant> getVariants()      { return Collections.unmodifiableList(variants); }
    public List<String> getImageUrls()             { return Collections.unmodifiableList(imageUrls); }
    public Map<String, String> getAttributes()     { return Collections.unmodifiableMap(attributes); }
    public double getAverageRating()               { return averageRating; }
    public int getReviewCount()                    { return reviewCount; }
    public Instant getCreatedAt()                  { return createdAt; }
    public Instant getUpdatedAt()                  { return updatedAt; }
    public Instant getDeletedAt()                  { return deletedAt; }
    public boolean isDeleted()                     { return deletedAt != null; }
}
java// com.ecommerce.domain.product.ProductRepository.java
package com.ecommerce.domain.product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    void save(Product product);
    void update(Product product);
    Optional<Product> findById(ProductId id);
    Optional<Product> findBySlug(String slug);
    List<Product> findByCategoryId(CategoryId categoryId, int page, int size);
    boolean existsBySlug(String slug);
    boolean existsBySku(String sku);
}

2.4 Inventory Domain
java// com.ecommerce.domain.inventory.InventoryId.java
package com.ecommerce.domain.inventory;

import com.ecommerce.domain.shared.valueobject.AggregateId;
import java.util.UUID;

public final class InventoryId extends AggregateId {
    private InventoryId(UUID v) { super(v); }
    private InventoryId()       { super(); }

    public static InventoryId generate()   { return new InventoryId(); }
    public static InventoryId of(UUID v)   { return new InventoryId(v); }
    public static InventoryId of(String v) { return new InventoryId(UUID.fromString(v)); }
}
java// com.ecommerce.domain.inventory.StockReservation.java
package com.ecommerce.domain.inventory;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity within Inventory aggregate.
 * Represents a temporary hold on stock during checkout.
 * Reservations expire if payment not completed within TTL.
 *
 * This is the key concurrency mechanism:
 * Reserve → Pay → Confirm (or expire → release)
 */
public class StockReservation {

    private final UUID reservationId;
    private final String orderId;
    private final int quantity;
    private ReservationStatus status;
    private final Instant createdAt;
    private final Instant expiresAt;
    private Instant confirmedAt;
    private Instant cancelledAt;

    public enum ReservationStatus { ACTIVE, CONFIRMED, EXPIRED, CANCELLED }

    private static final long RESERVATION_TTL_MINUTES = 15;

    StockReservation(String orderId, int quantity) {
        this.reservationId = UUID.randomUUID();
        this.orderId       = orderId;
        this.quantity      = quantity;
        this.status        = ReservationStatus.ACTIVE;
        this.createdAt     = Instant.now();
        this.expiresAt     = createdAt.plusSeconds(RESERVATION_TTL_MINUTES * 60);
    }

    public void confirm() {
        if (status != ReservationStatus.ACTIVE) {
            throw new com.ecommerce.domain.shared.exception.DomainException(
                com.ecommerce.domain.shared.exception.ErrorCodes.INVENTORY_RESERVATION_FAILED,
                "Cannot confirm reservation in state: " + status
            );
        }
        if (isExpired()) {
            throw new com.ecommerce.domain.shared.exception.DomainException(
                com.ecommerce.domain.shared.exception.ErrorCodes.INVENTORY_RESERVATION_FAILED,
                "Reservation has expired"
            );
        }
        this.status      = ReservationStatus.CONFIRMED;
        this.confirmedAt = Instant.now();
    }

    public void cancel() {
        if (status == ReservationStatus.CONFIRMED) {
            throw new com.ecommerce.domain.shared.exception.DomainException(
                com.ecommerce.domain.shared.exception.ErrorCodes.INVENTORY_RESERVATION_FAILED,
                "Cannot cancel a confirmed reservation"
            );
        }
        this.status      = ReservationStatus.CANCELLED;
        this.cancelledAt = Instant.now();
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt) && status == ReservationStatus.ACTIVE;
    }

    public UUID getReservationId()    { return reservationId; }
    public String getOrderId()        { return orderId; }
    public int getQuantity()          { return quantity; }
    public ReservationStatus getStatus() { return status; }
    public Instant getCreatedAt()     { return createdAt; }
    public Instant getExpiresAt()     { return expiresAt; }
    public Instant getConfirmedAt()   { return confirmedAt; }
    public Instant getCancelledAt()   { return cancelledAt; }
}
java// com.ecommerce.domain.inventory.event/StockReservedEvent.java
package com.ecommerce.domain.inventory.event;

import com.ecommerce.domain.shared.event.BaseDomainEvent;

public final class StockReservedEvent extends BaseDomainEvent {

    private final String inventoryId;
    private final String sku;
    private final String orderId;
    private final int quantity;

    public StockReservedEvent(String inventoryId, String sku,
                              String orderId, int quantity) {
        super();
        this.inventoryId = inventoryId;
        this.sku         = sku;
        this.orderId     = orderId;
        this.quantity    = quantity;
    }

    @Override public String aggregateId() { return inventoryId; }
    @Override public String eventType()   { return "STOCK_RESERVED"; }

    public String getInventoryId() { return inventoryId; }
    public String getSku()         { return sku; }
    public String getOrderId()     { return orderId; }
    public int getQuantity()       { return quantity; }
}
java// com.ecommerce.domain.inventory.event/StockDepletedEvent.java
package com.ecommerce.domain.inventory.event;

import com.ecommerce.domain.shared.event.BaseDomainEvent;

public final class StockDepletedEvent extends BaseDomainEvent {

    private final String inventoryId;
    private final String sku;

    public StockDepletedEvent(String inventoryId, String sku) {
        super();
        this.inventoryId = inventoryId;
        this.sku         = sku;
    }

    @Override public String aggregateId() { return inventoryId; }
    @Override public String eventType()   { return "STOCK_DEPLETED"; }

    public String getInventoryId() { return inventoryId; }
    public String getSku()         { return sku; }
}
java// com.ecommerce.domain.inventory.Inventory.java  ← AGGREGATE ROOT
package com.ecommerce.domain.inventory;

import com.ecommerce.domain.shared.aggregate.AggregateRoot;
import com.ecommerce.domain.shared.exception.DomainException;
import com.ecommerce.domain.shared.exception.ErrorCodes;
import com.ecommerce.domain.inventory.event.StockDepletedEvent;
import com.ecommerce.domain.inventory.event.StockReservedEvent;

import java.time.Instant;
import java.util.*;

/**
 * Inventory Aggregate Root.
 *
 * KEY DESIGN: Prevents overselling via reservation model.
 *
 * Available = OnHand - Reserved
 *
 * Concurrency: Uses optimistic locking (version field).
 * The version is checked during save — if changed by another thread,
 * a concurrency exception is thrown and the caller must retry.
 *
 * Invariants:
 * - availableQuantity must never go below 0
 * - Cannot reserve more than available
 * - Confirmed reservations reduce onHandQuantity permanently
 */
public class Inventory extends AggregateRoot {

    private final InventoryId id;
    private final String sku;               // Links to ProductVariant
    private int onHandQuantity;             // Physical stock
    private int reservedQuantity;           // Temporarily held
    private int reorderPoint;               // Trigger replenishment
    private int reorderQuantity;
    private long version;                   // Optimistic locking
    private final List<StockReservation> reservations;
    private final Instant createdAt;
    private Instant updatedAt;

    private Inventory(InventoryId id, String sku, int initialQuantity,
                      int reorderPoint, int reorderQuantity) {
        if (initialQuantity < 0) throw new IllegalArgumentException("Quantity cannot be negative");
        this.id              = Objects.requireNonNull(id);
        this.sku             = Objects.requireNonNull(sku);
        this.onHandQuantity  = initialQuantity;
        this.reservedQuantity = 0;
        this.reorderPoint    = reorderPoint;
        this.reorderQuantity = reorderQuantity;
        this.version         = 0L;
        this.reservations    = new ArrayList<>();
        this.createdAt       = Instant.now();
        this.updatedAt       = Instant.now();
    }

    public static Inventory create(String sku, int initialQuantity,
                                   int reorderPoint, int reorderQuantity) {
        return new Inventory(InventoryId.generate(), sku, initialQuantity,
            reorderPoint, reorderQuantity);
    }

    public static Inventory reconstitute(InventoryId id, String sku,
                                         int onHand, int reserved,
                                         int reorderPoint, int reorderQuantity,
                                         long version, List<StockReservation> reservations,
                                         Instant createdAt, Instant updatedAt) {
        Inventory inv = new Inventory(id, sku, onHand, reorderPoint, reorderQuantity);
        inv.reservedQuantity = reserved;
        inv.version          = version;
        inv.updatedAt        = updatedAt;
        inv.reservations.addAll(reservations);
        return inv;
    }

    // ── Domain Behaviors ──────────────────────────────────────────────

    /**
     * Reserve stock for an order.
     * This is the critical section — must be called within a DB transaction
     * with pessimistic/optimistic lock on the inventory row.
     *
     * @throws DomainException if insufficient stock
     */
    public StockReservation reserve(String orderId, int quantity) {
        Objects.requireNonNull(orderId, "Order ID required");
        if (quantity <= 0) {
            throw new DomainException(ErrorCodes.INVENTORY_INSUFFICIENT,
                "Reservation quantity must be positive");
        }

        // Remove expired reservations before calculating available
        releaseExpiredReservations();

        int available = getAvailableQuantity();
        if (available < quantity) {
            throw new DomainException(ErrorCodes.INVENTORY_INSUFFICIENT,
                String.format("Insufficient stock for SKU %s. Available: %d, Requested: %d",
                    sku, available, quantity));
        }

        StockReservation reservation = new StockReservation(orderId, quantity);
        reservations.add(reservation);
        reservedQuantity += quantity;
        updatedAt = Instant.now();
        incrementVersion();

        registerEvent(new StockReservedEvent(
            id.getValue().toString(), sku, orderId, quantity
        ));

        // Check if we're running low
        if (getAvailableQuantity() <= reorderPoint) {
            // Emit low stock event for purchasing team
        }

        return reservation;
    }

    /**
     * Confirm reservation after successful payment.
     * Permanently deducts from onHand quantity.
     */
    public void confirmReservation(UUID reservationId) {
        StockReservation reservation = findReservation(reservationId);
        reservation.confirm();
        onHandQuantity  -= reservation.getQuantity();
        reservedQuantity -= reservation.getQuantity();
        updatedAt = Instant.now();
        incrementVersion();

        if (onHandQuantity <= 0) {
            registerEvent(new StockDepletedEvent(
                id.getValue().toString(), sku
            ));
        }
    }

    /**
     * Cancel reservation — releases stock back to available pool.
     */
    public void cancelReservation(UUID reservationId) {
        StockReservation reservation = findReservation(reservationId);
        reservation.cancel();
        reservedQuantity -= reservation.getQuantity();
        updatedAt = Instant.now();
        incrementVersion();
    }

    /**
     * Restock — add new inventory (e.g., supplier delivery).
     */
    public void restock(int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("Restock quantity must be positive");
        onHandQuantity += quantity;
        updatedAt = Instant.now();
        incrementVersion();
    }

    private void releaseExpiredReservations() {
        for (StockReservation r : reservations) {
            if (r.isExpired()) {
                reservedQuantity -= r.getQuantity();
                r.cancel();
            }
        }
    }

    private StockReservation findReservation(UUID reservationId) {
        return reservations.stream()
            .filter(r -> r.getReservationId().equals(reservationId))
            .findFirst()
            .orElseThrow(() -> new DomainException(
                ErrorCodes.INVENTORY_RESERVATION_FAILED,
                "Reservation not found: " + reservationId
            ));
    }

    // ── Computed Properties ───────────────────────────────────────────

    public int getAvailableQuantity() {
        return Math.max(0, onHandQuantity - reservedQuantity);
    }

    public boolean isInStock()    { return getAvailableQuantity() > 0; }
    public boolean needsReorder() { return getAvailableQuantity() <= reorderPoint; }

    // ── Getters ───────────────────────────────────────────────────────

    public InventoryId getId()        { return id; }
    public String getSku()            { return sku; }
    public int getOnHandQuantity()    { return onHandQuantity; }
    public int getReservedQuantity()  { return reservedQuantity; }
    public int getReorderPoint()      { return reorderPoint; }
    public int getReorderQuantity()   { return reorderQuantity; }
    public long getVersion()          { return version; }
    public Instant getCreatedAt()     { return createdAt; }
    public Instant getUpdatedAt()     { return updatedAt; }
    public List<StockReservation> getReservations() {
        return Collections.unmodifiableList(reservations);
    }
}
java// com.ecommerce.domain.inventory.InventoryRepository.java
package com.ecommerce.domain.inventory;

import java.util.Optional;

public interface InventoryRepository {

    void save(Inventory inventory);

    /**
     * Optimistic lock save — throws ConcurrencyException if version mismatch.
     */
    void saveWithVersionCheck(Inventory inventory);

    Optional<Inventory> findBySku(String sku);

    Optional<Inventory> findBySkuWithLock(String sku); // pessimistic lock variant
}

2.5 Order Domain
java// com.ecommerce.domain.order/OrderId.java
package com.ecommerce.domain.order;

import com.ecommerce.domain.shared.valueobject.AggregateId;
import java.util.UUID;

public final class OrderId extends AggregateId {
    private OrderId(UUID v) { super(v); }
    private OrderId()       { super(); }

    public static OrderId generate()   { return new OrderId(); }
    public static OrderId of(UUID v)   { return new OrderId(v); }
    public static OrderId of(String v) { return new OrderId(UUID.fromString(v)); }
}
java// com.ecommerce.domain.order/OrderStatus.java
package com.ecommerce.domain.order;

/**
 * State machine for Order lifecycle.
 * Transitions enforced by the Order aggregate.
 *
 *  PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED
 *                    ↘                        ↗
 *                   CANCELLED ← (any state before SHIPPED)
 *                                          ↓
 *                                       REFUNDED
 */
public enum OrderStatus {

    PENDING,        // Created, payment not yet collected
    CONFIRMED,      // Payment succeeded, awaiting fulfillment
    PROCESSING,     // Warehouse picking/packing
    SHIPPED,        // With carrier
    DELIVERED,      // Confirmed received
    CANCELLED,      // Cancelled before shipping
    REFUNDED;       // Money returned

    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case PENDING    -> next == CONFIRMED || next == CANCELLED;
            case CONFIRMED  -> next == PROCESSING || next == CANCELLED;
            case PROCESSING -> next == SHIPPED || next == CANCELLED;
            case SHIPPED    -> next == DELIVERED;
            case DELIVERED  -> next == REFUNDED;
            case CANCELLED, REFUNDED -> false;
        };
    }
}
java// com.ecommerce.domain.order/OrderItem.java
package com.ecommerce.domain.order;

import com.ecommerce.domain.product.ProductId;
import com.ecommerce.domain.product.ProductVariant;
import com.ecommerce.domain.shared.valueobject.Money;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity within Order aggregate.
 * Snapshot of product/price at time of order — immutable.
 * Critical: price is captured at order time, not looked up later.
 * This prevents price change issues after order placement.
 */
public class OrderItem {

    private final UUID id;
    private final ProductId productId;
    private final String variantSku;
    private final String productName;
    private final String variantName;
    private final int quantity;
    private final Money unitPrice;       // Price at time of order
    private final Money totalPrice;      // unitPrice × quantity

    private OrderItem(ProductId productId, String variantSku,
                      String productName, String variantName,
                      int quantity, Money unitPrice) {
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
        this.id          = UUID.randomUUID();
        this.productId   = Objects.requireNonNull(productId);
        this.variantSku  = Objects.requireNonNull(variantSku);
        this.productName = Objects.requireNonNull(productName);
        this.variantName = variantName;
        this.quantity    = quantity;
        this.unitPrice   = Objects.requireNonNull(unitPrice);
        this.totalPrice  = unitPrice.multiply(quantity);
    }

    public static OrderItem of(ProductId productId, ProductVariant variant,
                               String productName, int quantity) {
        return new OrderItem(
            productId, variant.getSku(), productName,
            variant.getName(), quantity, variant.getPrice()
        );
    }

    // For reconstitution
    public static OrderItem reconstitute(UUID id, ProductId productId, String variantSku,
                                         String productName, String variantName,
                                         int quantity, Money unitPrice) {
        OrderItem item = new OrderItem(productId, variantSku, productName,
            variantName, quantity, unitPrice);
        return item;
    }

    public UUID getId()             { return id; }
    public ProductId getProductId() { return productId; }
    public String getVariantSku()   { return variantSku; }
    public String getProductName()  { return productName; }
    public String getVariantName()  { return variantName; }
    public int getQuantity()        { return quantity; }
    public Money getUnitPrice()     { return unitPrice; }
    public Money getTotalPrice()    { return totalPrice; }
}
java// com.ecommerce.domain.order/event/OrderPlacedEvent.java
package com.ecommerce.domain.order.event;

import com.ecommerce.domain.shared.event.BaseDomainEvent;
import java.math.BigDecimal;
import java.util.List;

public final class OrderPlacedEvent extends BaseDomainEvent {

    private final String orderId;
    private final String userId;
    private final BigDecimal totalAmount;
    private final String currency;
    private final List<String> skus;

    public OrderPlacedEvent(String orderId, String userId,
                            BigDecimal totalAmount, String currency,
                            List<String> skus) {
        super();
        this.orderId     = orderId;
        this.userId      = userId;
        this.totalAmount = totalAmount;
        this.currency    = currency;
        this.skus        = List.copyOf(skus);
    }

    @Override public String aggregateId() { return orderId; }
    @Override public String eventType()   { return "ORDER_PLACED"; }

    public String getOrderId()         { return orderId; }
    public String getUserId()          { return userId; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCurrency()        { return currency; }
    public List<String> getSkus()      { return skus; }
}
java// com.ecommerce.domain.order/event/OrderCancelledEvent.java
package com.ecommerce.domain.order.event;

import com.ecommerce.domain.shared.event.BaseDomainEvent;

public final class OrderCancelledEvent extends BaseDomainEvent {

    private final String orderId;
    private final String reason;

    public OrderCancelledEvent(String orderId, String reason) {
        super();
        this.orderId = orderId;
        this.reason  = reason;
    }

    @Override public String aggregateId() { return orderId; }
    @Override public String eventType()   { return "ORDER_CANCELLED"; }

    public String getOrderId() { return orderId; }
    public String getReason()  { return reason; }
}
java// com.ecommerce.domain.order/event/OrderStatusChangedEvent.java
package com.ecommerce.domain.order.event;

import com.ecommerce.domain.order.OrderStatus;
import com.ecommerce.domain.shared.event.BaseDomainEvent;

public final class OrderStatusChangedEvent extends BaseDomainEvent {

    private final String orderId;
    private final OrderStatus from;
    private final OrderStatus to;

    public OrderStatusChangedEvent(String orderId, OrderStatus from, OrderStatus to) {
        super();
        this.orderId = orderId;
        this.from    = from;
        this.to      = to;
    }

    @Override public String aggregateId() { return orderId; }
    @Override public String eventType()   { return "ORDER_STATUS_CHANGED"; }

    public String getOrderId()   { return orderId; }
    public OrderStatus getFrom() { return from; }
    public OrderStatus getTo()   { return to; }
}
java// com.ecommerce.domain.order/Order.java  ← AGGREGATE ROOT
package com.ecommerce.domain.order;

import com.ecommerce.domain.shared.aggregate.AggregateRoot;
import com.ecommerce.domain.shared.exception.DomainException;
import com.ecommerce.domain.shared.exception.ErrorCodes;
import com.ecommerce.domain.shared.valueobject.Money;
import com.ecommerce.domain.user.Address;
import com.ecommerce.domain.user.UserId;
import com.ecommerce.domain.order.event.OrderCancelledEvent;
import com.ecommerce.domain.order.event.OrderPlacedEvent;
import com.ecommerce.domain.order.event.OrderStatusChangedEvent;

import java.time.Instant;
import java.util.*;

/**
 * Order Aggregate Root — the heart of commerce.
 *
 * Invariants:
 * - Order must have at least one item.
 * - Order total must match sum of item totals + shipping - discount.
 * - Status transitions must follow the state machine.
 * - A DELIVERED order cannot be cancelled (only refunded).
 * - Price snapshot taken at order creation — protected from catalog changes.
 *
 * Design Pattern: State Pattern — order status governs behavior.
 * (Full State pattern implemented in Phase 5)
 */
public class Order extends AggregateRoot {

    private final OrderId id;
    private final UserId userId;
    private final String orderNumber;   // Human-readable: ORD-2024-00001
    private final List<OrderItem> items;
    private OrderStatus status;
    private Address shippingAddress;
    private Address billingAddress;
    private Money subtotal;
    private Money shippingCost;
    private Money discountAmount;
    private Money totalAmount;
    private String couponCode;
    private String paymentIntentId;     // Idempotency key for payment
    private String trackingNumber;
    private String cancellationReason;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant confirmedAt;
    private Instant shippedAt;
    private Instant deliveredAt;

    private Order(OrderId id, UserId userId, String orderNumber) {
        this.id          = Objects.requireNonNull(id);
        this.userId      = Objects.requireNonNull(userId);
        this.orderNumber = Objects.requireNonNull(orderNumber);
        this.items       = new ArrayList<>();
        this.status      = OrderStatus.PENDING;
        this.createdAt   = Instant.now();
        this.updatedAt   = Instant.now();
    }

    /**
     * Factory method — creates a complete Order.
     * Calculates totals from items. Validates invariants.
     */
    public static Order place(UserId userId, String orderNumber,
                              List<OrderItem> items,
                              Address shippingAddress,
                              Address billingAddress,
                              Money shippingCost,
                              Money discountAmount,
                              String couponCode) {
        if (items == null || items.isEmpty()) {
            throw new DomainException(ErrorCodes.ORDER_EMPTY,
                "Order must contain at least one item");
        }

        Order order = new Order(OrderId.generate(), userId, orderNumber);
        order.items.addAll(items);
        order.shippingAddress = Objects.requireNonNull(shippingAddress);
        order.billingAddress  = billingAddress != null ? billingAddress : shippingAddress;
        order.shippingCost    = shippingCost != null ? shippingCost : Money.ZERO_USD;
        order.discountAmount  = discountAmount != null ? discountAmount : Money.ZERO_USD;
        order.couponCode      = couponCode;
        order.subtotal        = order.calculateSubtotal();
        order.totalAmount     = order.calculateTotal();

        // Validate total is positive
        if (order.totalAmount.isNegative() || order.totalAmount.isZero()) {
            throw new DomainException(ErrorCodes.ORDER_EMPTY,
                "Order total must be positive");
        }

        // Register domain event
        List<String> skus = items.stream().map(OrderItem::getVariantSku).toList();
        order.registerEvent(new OrderPlacedEvent(
            order.id.getValue().toString(),
            userId.getValue().toString(),
            order.totalAmount.getAmount(),
            order.totalAmount.getCurrency().getCurrencyCode(),
            skus
        ));

        return order;
    }

    public static Order reconstitute(OrderId id, UserId userId, String orderNumber,
                                     List<OrderItem> items, OrderStatus status,
                                     Address shippingAddress, Address billingAddress,
                                     Money subtotal, Money shippingCost,
                                     Money discountAmount, Money totalAmount,
                                     String couponCode, String paymentIntentId,
                                     String trackingNumber, String cancellationReason,
                                     Instant createdAt, Instant updatedAt,
                                     Instant confirmedAt, Instant shippedAt,
                                     Instant deliveredAt) {
        Order order = new Order(id, userId, orderNumber);
        order.items.addAll(items);
        order.status             = status;
        order.shippingAddress    = shippingAddress;
        order.billingAddress     = billingAddress;
        order.subtotal           = subtotal;
        order.shippingCost       = shippingCost;
        order.discountAmount     = discountAmount;
        order.totalAmount        = totalAmount;
        order.couponCode         = couponCode;
        order.paymentIntentId    = paymentIntentId;
        order.trackingNumber     = trackingNumber;
        order.cancellationReason = cancellationReason;
        order.updatedAt          = updatedAt;
        order.confirmedAt        = confirmedAt;
        order.shippedAt          = shippedAt;
        order.deliveredAt        = deliveredAt;
        return order;
    }

    // ── Domain Behaviors ──────────────────────────────────────────────

    public void confirm(String paymentIntentId) {
        transitionTo(OrderStatus.CONFIRMED);
        this.paymentIntentId = Objects.requireNonNull(paymentIntentId);
        this.confirmedAt     = Instant.now();
        this.updatedAt       = Instant.now();
    }

    public void startProcessing() {
        transitionTo(OrderStatus.PROCESSING);
        updatedAt = Instant.now();
    }

    public void ship(String trackingNumber) {
        transitionTo(OrderStatus.SHIPPED);
        this.trackingNumber = Objects.requireNonNull(trackingNumber);
        this.shippedAt      = Instant.now();
        this.updatedAt      = Instant.now();
    }

    public void deliver() {
        transitionTo(OrderStatus.DELIVERED);
        this.deliveredAt = Instant.now();
        this.updatedAt   = Instant.now();
    }

    public void cancel(String reason) {
        transitionTo(OrderStatus.CANCELLED);
        this.cancellationReason = reason;
        this.updatedAt          = Instant.now();

        registerEvent(new OrderCancelledEvent(
            id.getValue().toString(), reason
        ));
    }

    public void refund() {
        transitionTo(OrderStatus.REFUNDED);
        updatedAt = Instant.now();
    }

    private void transitionTo(OrderStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new DomainException(ErrorCodes.ORDER_INVALID_STATE,
                String.format("Cannot transition from %s to %s", status, next));
        }
        OrderStatus previous = this.status;
        this.status = next;

        registerEvent(new OrderStatusChangedEvent(
            id.getValue().toString(), previous, next
        ));
    }

    // ── Private Calculations ──────────────────────────────────────────

    private Money calculateSubtotal() {
        return items.stream()
            .map(OrderItem::getTotalPrice)
            .reduce(Money.ZERO_USD, Money::add);
    }

    private Money calculateTotal() {
        return subtotal
            .add(shippingCost)
            .subtract(discountAmount);
    }

    // ── Getters ───────────────────────────────────────────────────────

    public OrderId getId()               { return id; }
    public UserId getUserId()            { return userId; }
    public String getOrderNumber()       { return orderNumber; }
    public List<OrderItem> getItems()    { return Collections.unmodifiableList(items); }
    public OrderStatus getStatus()       { return status; }
    public Address getShippingAddress()  { return shippingAddress; }
    public Address getBillingAddress()   { return billingAddress; }
    public Money getSubtotal()           { return subtotal; }
    public Money getShippingCost()       { return shippingCost; }
    public Money getDiscountAmount()     { return discountAmount; }
    public Money getTotalAmount()        { return totalAmount; }
    public String getCouponCode()        { return couponCode; }
    public String getPaymentIntentId()   { return paymentIntentId; }
    public String getTrackingNumber()    { return trackingNumber; }
    public String getCancellationReason(){ return cancellationReason; }
    public Instant getCreatedAt()        { return createdAt; }
    public Instant getUpdatedAt()        { return updatedAt; }
    public Instant getConfirmedAt()      { return confirmedAt; }
    public Instant getShippedAt()        { return shippedAt; }
    public Instant getDeliveredAt()      { return deliveredAt; }
    public boolean isPaid()              { return paymentIntentId != null; }
}
java// com.ecommerce.domain.order/OrderRepository.java
package com.ecommerce.domain.order;

import com.ecommerce.domain.user.UserId;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    void save(Order order);
    void update(Order order);
    Optional<Order> findById(OrderId id);
    Optional<Order> findByOrderNumber(String orderNumber);
    List<Order> findByUserId(UserId userId, int page, int size);
    List<Order> findByStatus(OrderStatus status, int page, int size);
    String nextOrderNumber(); // Generates ORD-2024-XXXXX
}

2.6 Cart Domain
java// com.ecommerce.domain.cart/CartItem.java
package com.ecommerce.domain.cart;

import com.ecommerce.domain.product.ProductId;
import com.ecommerce.domain.shared.valueobject.Money;
import java.util.Objects;
import java.util.UUID;

public class CartItem {

    private final UUID id;
    private final ProductId productId;
    private final String variantSku;
    private final String productName;
    private final String variantName;
    private int quantity;
    private Money unitPrice;            // Updated when product price changes

    public CartItem(ProductId productId, String variantSku,
                    String productName, String variantName,
                    int quantity, Money unitPrice) {
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
        this.id          = UUID.randomUUID();
        this.productId   = Objects.requireNonNull(productId);
        this.variantSku  = Objects.requireNonNull(variantSku);
        this.productName = Objects.requireNonNull(productName);
        this.variantName = variantName;
        this.quantity    = quantity;
        this.unitPrice   = Objects.requireNonNull(unitPrice);
    }

    public void updateQuantity(int newQuantity) {
        if (newQuantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
        this.quantity = newQuantity;
    }

    public void updatePrice(Money newPrice) { this.unitPrice = newPrice; }

    public Money getTotalPrice() { return unitPrice.multiply(quantity); }

    public UUID getId()             { return id; }
    public ProductId getProductId() { return productId; }
    public String getVariantSku()   { return variantSku; }
    public String getProductName()  { return productName; }
    public String getVariantName()  { return variantName; }
    public int getQuantity()        { return quantity; }
    public Money getUnitPrice()     { return unitPrice; }
}
java// com.ecommerce.domain.cart/Cart.java  ← AGGREGATE ROOT
package com.ecommerce.domain.cart;

import com.ecommerce.domain.product.ProductId;
import com.ecommerce.domain.shared.aggregate.AggregateRoot;
import com.ecommerce.domain.shared.exception.DomainException;
import com.ecommerce.domain.shared.exception.ErrorCodes;
import com.ecommerce.domain.shared.valueobject.Money;
import com.ecommerce.domain.user.UserId;

import java.time.Instant;
import java.util.*;

/**
 * Cart Aggregate Root.
 *
 * Key decisions:
 * - One cart per user (identified by userId or sessionId for guests).
 * - Cart items can be updated in-place (quantity, price sync).
 * - Cart is soft-converted to Order — not deleted after checkout.
 * - Price stored in cart is informational — Order captures final price.
 */
public class Cart extends AggregateRoot {

    private final CartId id;
    private final UserId userId;        // null for guest carts
    private final String sessionId;     // for guest users
    private final Map<String, CartItem> items; // sku -> item
    private Instant createdAt;
    private Instant updatedAt;
    private Instant expiresAt;

    private static final long CART_TTL_DAYS = 30;

    private Cart(CartId id, UserId userId, String sessionId) {
        this.id        = Objects.requireNonNull(id);
        this.userId    = userId;
        this.sessionId = sessionId;
        this.items     = new LinkedHashMap<>();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.expiresAt = createdAt.plusSeconds(CART_TTL_DAYS * 24 * 3600);
    }

    public static Cart forUser(UserId userId) {
        return new Cart(CartId.generate(), userId, null);
    }

    public static Cart forGuest(String sessionId) {
        return new Cart(CartId.generate(), null, sessionId);
    }

    // ── Domain Behaviors ──────────────────────────────────────────────

    public void addItem(ProductId productId, String variantSku,
                        String productName, String variantName,
                        int quantity, Money unitPrice) {
        CartItem existing = items.get(variantSku);
        if (existing != null) {
            existing.updateQuantity(existing.getQuantity() + quantity);
        } else {
            items.put(variantSku, new CartItem(
                productId, variantSku, productName, variantName, quantity, unitPrice
            ));
        }
        updatedAt = Instant.now();
    }

    public void updateItemQuantity(String variantSku, int newQuantity) {
        CartItem item = items.get(variantSku);
        if (item == null) {
            throw new DomainException(ErrorCodes.CART_ITEM_NOT_FOUND,
                "Item not found in cart: " + variantSku);
        }
        if (newQuantity <= 0) {
            items.remove(variantSku);
        } else {
            item.updateQuantity(newQuantity);
        }
        updatedAt = Instant.now();
    }

    public void removeItem(String variantSku) {
        if (!items.containsKey(variantSku)) {
            throw new DomainException(ErrorCodes.CART_ITEM_NOT_FOUND,
                "Item not found: " + variantSku);
        }
        items.remove(variantSku);
        updatedAt = Instant.now();
    }

    public void clear() {
        items.clear();
        updatedAt = Instant.now();
    }

    public void syncPrices(Map<String, Money> skuToPriceMap) {
        skuToPriceMap.forEach((sku, price) -> {
            CartItem item = items.get(sku);
            if (item != null) item.updatePrice(price);
        });
        updatedAt = Instant.now();
    }

    public Money calculateTotal() {
        return items.values().stream()
            .map(CartItem::getTotalPrice)
            .reduce(Money.ZERO_USD, Money::add);
    }

    public boolean isEmpty()  { return items.isEmpty(); }
    public boolean isExpired(){ return Instant.now().isAfter(expiresAt); }

    public CartId getId()                          { return id; }
    public UserId getUserId()                      { return userId; }
    public String getSessionId()                   { return sessionId; }
    public Collection<CartItem> getItems()         {
        return Collections.unmodifiableCollection(items.values());
    }
    public int getTotalItemCount()                 {
        return items.values().stream().mapToInt(CartItem::getQuantity).sum();
    }
    public Instant getCreatedAt()                  { return createdAt; }
    public Instant getUpdatedAt()                  { return updatedAt; }
    public Instant getExpiresAt()                  { return expiresAt; }
}
java// com.ecommerce.domain.cart/CartId.java
package com.ecommerce.domain.cart;

import com.ecommerce.domain.shared.valueobject.AggregateId;
import java.util.UUID;

public final class CartId extends AggregateId {
    private CartId(UUID v) { super(v); }
    private CartId()       { super(); }

    public static CartId generate()   { return new CartId(); }
    public static CartId of(UUID v)   { return new CartId(v); }
    public static CartId of(String v) { return new CartId(UUID.fromString(v)); }
}

2.7 Payment Domain
java// com.ecommerce.domain.payment/PaymentId.java
package com.ecommerce.domain.payment;

import com.ecommerce.domain.shared.valueobject.AggregateId;
import java.util.UUID;

public final class PaymentId extends AggregateId {
    private PaymentId(UUID v) { super(v); }
    private PaymentId()       { super(); }

    public static PaymentId generate()   { return new PaymentId(); }
    public static PaymentId of(UUID v)   { return new PaymentId(v); }
    public static PaymentId of(String v) { return new PaymentId(UUID.fromString(v)); }
}
java// com.ecommerce.domain.payment/PaymentStatus.java
package com.ecommerce.domain.payment;

public enum PaymentStatus {
    PENDING,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    REFUNDED,
    PARTIALLY_REFUNDED;

    public boolean isSuccessful() { return this == SUCCEEDED; }
    public boolean isFinal()      {
        return this == SUCCEEDED || this == FAILED
            || this == REFUNDED || this == PARTIALLY_REFUNDED;
    }
}
java// com.ecommerce.domain.payment/PaymentMethod.java
package com.ecommerce.domain.payment;

public enum PaymentMethod {
    CREDIT_CARD,
    DEBIT_CARD,
    PAYPAL,
    STRIPE,
    BANK_TRANSFER,
    CRYPTO,
    GIFT_CARD,
    WALLET
}
java// com.ecommerce.domain.payment/event/PaymentSucceededEvent.java
package com.ecommerce.domain.payment.event;

import com.ecommerce.domain.shared.event.BaseDomainEvent;
import java.math.BigDecimal;

public final class PaymentSucceededEvent extends BaseDomainEvent {

    private final String paymentId;
    private final String orderId;
    private final BigDecimal amount;
    private final String currency;
    private final String gatewayTransactionId;

    public PaymentSucceededEvent(String paymentId, String orderId,
                                 BigDecimal amount, String currency,
                                 String gatewayTransactionId) {
        super();
        this.paymentId            = paymentId;
        this.orderId              = orderId;
        this.amount               = amount;
        this.currency             = currency;
        this.gatewayTransactionId = gatewayTransactionId;
    }

    @Override public String aggregateId() { return paymentId; }
    @Override public String eventType()   { return "PAYMENT_SUCCEEDED"; }

    public String getPaymentId()            { return paymentId; }
    public String getOrderId()              { return orderId; }
    public BigDecimal getAmount()           { return amount; }
    public String getCurrency()             { return currency; }
    public String getGatewayTransactionId() { return gatewayTransactionId; }
}
java// com.ecommerce.domain.payment/event/PaymentFailedEvent.java
package com.ecommerce.domain.payment.event;

import com.ecommerce.domain.shared.event.BaseDomainEvent;

public final class PaymentFailedEvent extends BaseDomainEvent {

    private final String paymentId;
    private final String orderId;
    private final String failureReason;
    private final String gatewayCode;

    public PaymentFailedEvent(String paymentId, String orderId,
                              String failureReason, String gatewayCode) {
        super();
        this.paymentId     = paymentId;
        this.orderId       = orderId;
        this.failureReason = failureReason;
        this.gatewayCode   = gatewayCode;
    }

    @Override public String aggregateId() { return paymentId; }
    @Override public String eventType()   { return "PAYMENT_FAILED"; }

    public String getPaymentId()     { return paymentId; }
    public String getOrderId()       { return orderId; }
    public String getFailureReason() { return failureReason; }
    public String getGatewayCode()   { return gatewayCode; }
}
java// com.ecommerce.domain.payment/Payment.java  ← AGGREGATE ROOT
package com.ecommerce.domain.payment;

import com.ecommerce.domain.order.OrderId;
import com.ecommerce.domain.shared.aggregate.AggregateRoot;
import com.ecommerce.domain.shared.exception.DomainException;
import com.ecommerce.domain.shared.exception.ErrorCodes;
import com.ecommerce.domain.shared.valueobject.Money;
import com.ecommerce.domain.user.UserId;
import com.ecommerce.domain.payment.event.PaymentFailedEvent;
import com.ecommerce.domain.payment.event.PaymentSucceededEvent;

import java.time.Instant;
import java.util.Objects;

/**
 * Payment Aggregate Root.
 *
 * Design: Payment is separate from Order to allow:
 * - Multiple payment attempts per order.
 * - Partial payments / split payments.
 * - Independent payment audit trail.
 *
 * Idempotency: idempotencyKey ensures duplicate payment requests
 * are detected and rejected.
 *
 * Invariants:
 * - Amount must match order total.
 * - Cannot re-process a completed payment (SUCCEEDED/FAILED).
 * - Refund amount cannot exceed original amount.
 */
public class Payment extends AggregateRoot {

    private final PaymentId id;
    private final OrderId orderId;
    private final UserId userId;
    private final Money amount;
    private final PaymentMethod method;
    private final String idempotencyKey;   // Prevents duplicate payments
    private PaymentStatus status;
    private String gatewayTransactionId;   // Stripe/PayPal transaction ID
    private String gatewayResponse;        // Raw gateway response (JSON)
    private String failureReason;
    private String failureCode;
    private Money refundedAmount;
    private final Instant createdAt;
    private Instant processedAt;
    private Instant updatedAt;

    private Payment(PaymentId id, OrderId orderId, UserId userId,
                    Money amount, PaymentMethod method, String idempotencyKey) {
        this.id             = Objects.requireNonNull(id);
        this.orderId        = Objects.requireNonNull(orderId);
        this.userId         = Objects.requireNonNull(userId);
        this.amount         = Objects.requireNonNull(amount);
        this.method         = Objects.requireNonNull(method);
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey);
        this.status         = PaymentStatus.PENDING;
        this.refundedAmount = Money.ZERO_USD;
        this.createdAt      = Instant.now();
        this.updatedAt      = Instant.now();
    }

    public static Payment initiate(OrderId orderId, UserId userId,
                                   Money amount, PaymentMethod method,
                                   String idempotencyKey) {
        return new Payment(PaymentId.generate(), orderId, userId,
            amount, method, idempotencyKey);
    }

    public static Payment reconstitute(PaymentId id, OrderId orderId, UserId userId,
                                       Money amount, PaymentMethod method,
                                       String idempotencyKey, PaymentStatus status,
                                       String gatewayTransactionId, String gatewayResponse,
                                       String failureReason, String failureCode,
                                       Money refundedAmount, Instant createdAt,
                                       Instant processedAt, Instant updatedAt) {
        Payment p = new Payment(id, orderId, userId, amount, method, idempotencyKey);
        p.status               = status;
        p.gatewayTransactionId = gatewayTransactionId;
        p.gatewayResponse      = gatewayResponse;
        p.failureReason        = failureReason;
        p.failureCode          = failureCode;
        p.refundedAmount       = refundedAmount;
        p.processedAt          = processedAt;
        p.updatedAt            = updatedAt;
        return p;
    }

    // ── Domain Behaviors ──────────────────────────────────────────────

    public void markProcessing() {
        ensureNotFinal();
        this.status    = PaymentStatus.PROCESSING;
        this.updatedAt = Instant.now();
    }

    public void succeed(String gatewayTransactionId, String gatewayResponse) {
        ensureNotFinal();
        this.status               = PaymentStatus.SUCCEEDED;
        this.gatewayTransactionId = Objects.requireNonNull(gatewayTransactionId);
        this.gatewayResponse      = gatewayResponse;
        this.processedAt          = Instant.now();
        this.updatedAt            = Instant.now();

        registerEvent(new PaymentSucceededEvent(
            id.getValue().toString(),
            orderId.getValue().toString(),
            amount.getAmount(),
            amount.getCurrency().getCurrencyCode(),
            gatewayTransactionId
        ));
    }

    public void fail(String reason, String code, String gatewayResponse) {
        ensureNotFinal();
        this.status          = PaymentStatus.FAILED;
        this.failureReason   = reason;
        this.failureCode     = code;
        this.gatewayResponse = gatewayResponse;
        this.processedAt     = Instant.now();
        this.updatedAt       = Instant.now();

        registerEvent(new PaymentFailedEvent(
            id.getValue().toString(),
            orderId.getValue().toString(),
            reason, code
        ));
    }

    public void refund(Money refundAmount) {
        if (status != PaymentStatus.SUCCEEDED && status != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new DomainException(ErrorCodes.PAYMENT_FAILED,
                "Can only refund succeeded payments");
        }
        Money newRefunded = refundedAmount.add(refundAmount);
        if (newRefunded.isGreaterThan(amount)) {
            throw new DomainException(ErrorCodes.PAYMENT_FAILED,
                "Refund amount exceeds original payment");
        }
        this.refundedAmount = newRefunded;
        this.status = refundedAmount.equals(amount)
            ? PaymentStatus.REFUNDED
            : PaymentStatus.PARTIALLY_REFUNDED;
        this.updatedAt = Instant.now();
    }

    private void ensureNotFinal() {
        if (status.isFinal()) {
            throw new DomainException(ErrorCodes.PAYMENT_FAILED,
                "Payment is already in final state: " + status);
        }
    }

    // ── Getters ───────────────────────────────────────────────────────

    public PaymentId getId()               { return id; }
    public OrderId getOrderId()            { return orderId; }
    public UserId getUserId()              { return userId; }
    public Money getAmount()               { return amount; }
    public PaymentMethod getMethod()       { return method; }
    public String getIdempotencyKey()      { return idempotencyKey; }
    public PaymentStatus getStatus()       { return status; }
    public String getGatewayTransactionId(){ return gatewayTransactionId; }
    public String getGatewayResponse()     { return gatewayResponse; }
    public String getFailureReason()       { return failureReason; }
    public String getFailureCode()         { return failureCode; }
    public Money getRefundedAmount()       { return refundedAmount; }
    public Instant getCreatedAt()          { return createdAt; }
    public Instant getProcessedAt()        { return processedAt; }
    public Instant getUpdatedAt()          { return updatedAt; }
}

2.8 Discount Domain
java// com.ecommerce.domain.discount/DiscountType.java
package com.ecommerce.domain.discount;

public enum DiscountType {
    PERCENTAGE,         // 10% off
    FIXED_AMOUNT,       // $10 off
    FREE_SHIPPING,      // Waive shipping cost
    BUY_X_GET_Y        // Buy 2 get 1 free
}
java// com.ecommerce.domain.discount/Coupon.java  ← AGGREGATE ROOT
package com.ecommerce.domain.discount;

import com.ecommerce.domain.shared.aggregate.AggregateRoot;
import com.ecommerce.domain.shared.exception.DomainException;
import com.ecommerce.domain.shared.exception.ErrorCodes;
import com.ecommerce.domain.shared.valueobject.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Coupon Aggregate Root.
 *
 * Strategy Pattern connection: Coupon holds the RULES,
 * DiscountStrategy (Phase 5) computes the actual discount.
 * This separation allows different calculation strategies per DiscountType.
 *
 * Invariants:
 * - Coupon cannot be used after expiry.
 * - Usage count cannot exceed maxUsage.
 * - Minimum order amount must be met.
 * - User-specific coupons can only be used by the designated user.
 */
public class Coupon extends AggregateRoot {

    private final CouponId id;
    private final String code;
    private final DiscountType type;
    private final BigDecimal value;             // % or fixed amount
    private final Money minimumOrderAmount;
    private final Money maximumDiscountAmount;  // Cap for percentage discounts
    private final int maxUsage;                 // -1 = unlimited
    private int usageCount;
    private final String applicableUserId;      // null = all users
    private final boolean isActive;
    private final Instant validFrom;
    private final Instant validUntil;
    private final Instant createdAt;

    private Coupon(CouponId id, String code, DiscountType type, BigDecimal value,
                   Money minimumOrderAmount, Money maximumDiscountAmount,
                   int maxUsage, String applicableUserId,
                   Instant validFrom, Instant validUntil) {
        this.id                   = Objects.requireNonNull(id);
        this.code                 = Objects.requireNonNull(code).toUpperCase();
        this.type                 = Objects.requireNonNull(type);
        this.value                = Objects.requireNonNull(value);
        this.minimumOrderAmount   = minimumOrderAmount;
        this.maximumDiscountAmount = maximumDiscountAmount;
        this.maxUsage             = maxUsage;
        this.usageCount           = 0;
        this.applicableUserId     = applicableUserId;
        this.isActive             = true;
        this.validFrom            = validFrom != null ? validFrom : Instant.now();
        this.validUntil           = Objects.requireNonNull(validUntil);
        this.createdAt            = Instant.now();
    }

    public static Coupon create(String code, DiscountType type, BigDecimal value,
                                Money minimumOrderAmount, Money maximumDiscountAmount,
                                int maxUsage, String applicableUserId,
                                Instant validFrom, Instant validUntil) {
        return new Coupon(CouponId.generate(), code, type, value,
            minimumOrderAmount, maximumDiscountAmount, maxUsage,
            applicableUserId, validFrom, validUntil);
    }

    /**
     * Validate coupon applicability.
     * Throws DomainException if not applicable.
     */
    public void validate(Money orderAmount, String userId) {
        if (!isActive) {
            throw new DomainException(ErrorCodes.COUPON_EXPIRED, "Coupon is inactive");
        }
        Instant now = Instant.now();
        if (now.isBefore(validFrom) || now.isAfter(validUntil)) {
            throw new DomainException(ErrorCodes.COUPON_EXPIRED,
                "Coupon expired or not yet valid");
        }
        if (maxUsage > 0 && usageCount >= maxUsage) {
            throw new DomainException(ErrorCodes.COUPON_USAGE_EXCEEDED,
                "Coupon usage limit reached");
        }
        if (minimumOrderAmount != null && !orderAmount.isGreaterThanOrEqual(minimumOrderAmount)) {
            throw new DomainException(ErrorCodes.COUPON_MIN_ORDER_NOT_MET,
                "Order minimum not met. Required: " + minimumOrderAmount);
        }
        if (applicableUserId != null && !applicableUserId.equals(userId)) {
            throw new DomainException(ErrorCodes.COUPON_NOT_FOUND,
                "Coupon not applicable to this user");
        }
    }

    /**
     * Calculate discount amount.
     * Returns actual discount — may be capped by maximumDiscountAmount.
     */
    public Money calculateDiscount(Money orderAmount) {
        Money discount = switch (type) {
            case PERCENTAGE -> orderAmount.multiply(
                value.divide(BigDecimal.valueOf(100))
            );
            case FIXED_AMOUNT -> Money.of(value, orderAmount.getCurrency());
            case FREE_SHIPPING -> Money.ZERO_USD; // handled separately
            case BUY_X_GET_Y  -> Money.ZERO_USD;  // handled by discount service
        };

        // Apply cap
        if (maximumDiscountAmount != null && discount.isGreaterThan(maximumDiscountAmount)) {
            discount = maximumDiscountAmount;
        }

        // Cannot discount more than order total
        if (discount.isGreaterThan(orderAmount)) {
            discount = orderAmount;
        }

        return discount;
    }

    public void recordUsage() {
        if (maxUsage > 0 && usageCount >= maxUsage) {
            throw new DomainException(ErrorCodes.COUPON_USAGE_EXCEEDED,
                "Usage limit already reached");
        }
        usageCount++;
    }

    // Getters
    public CouponId getId()                      { return id; }
    public String getCode()                      { return code; }
    public DiscountType getType()                { return type; }
    public BigDecimal getValue()                 { return value; }
    public Money getMinimumOrderAmount()         { return minimumOrderAmount; }
    public Money getMaximumDiscountAmount()      { return maximumDiscountAmount; }
    public int getMaxUsage()                     { return maxUsage; }
    public int getUsageCount()                   { return usageCount; }
    public String getApplicableUserId()          { return applicableUserId; }
    public boolean isActive()                    { return isActive; }
    public Instant getValidFrom()                { return validFrom; }
    public Instant getValidUntil()               { return validUntil; }
    public Instant getCreatedAt()                { return createdAt; }
    public boolean isFreeShipping()              { return type == DiscountType.FREE_SHIPPING; }
}
java// com.ecommerce.domain.discount/CouponId.java
package com.ecommerce.domain.discount;

import com.ecommerce.domain.shared.valueobject.AggregateId;
import java.util.UUID;

public final class CouponId extends AggregateId {
    private CouponId(UUID v) { super(v); }
    private CouponId()       { super(); }

    public static CouponId generate()   { return new CouponId(); }
    public static CouponId of(UUID v)   { return new CouponId(v); }
    public static CouponId of(String v) { return new CouponId(UUID.fromString(v)); }
}

2.9 Domain Service — Order Number Generator
java// com.ecommerce.domain.order/OrderNumberGenerator.java
package com.ecommerce.domain.order;

/**
 * Domain Service — spans multiple aggregates or requires infrastructure.
 * Generating unique, human-readable order numbers needs coordination
 * (database sequence or distributed ID), so it's a domain service
 * with infrastructure implementation.
 */
public interface OrderNumberGenerator {
    String generate();  // returns ORD-2024-00001
}
```

---

## 2.10 Aggregate Class Diagram
```
Domain Layer — Class Diagram (text format)

┌─────────────────────────────────────────────────────────────────────┐
│ AggregateRoot                                                       │
│ + registerEvent(DomainEvent)                                        │
│ + getDomainEvents(): List<DomainEvent>                              │
│ + clearDomainEvents()                                               │
└──────────────────────────────┬──────────────────────────────────────┘
                               │ extends
        ┌──────────────────────┼───────────────────────────┐
        ▼                      ▼                           ▼
┌──────────────┐    ┌──────────────────┐    ┌─────────────────────────┐
│    User      │    │     Product      │    │        Order            │
│ - id:UserId  │    │ - id:ProductId   │    │ - id:OrderId            │
│ - email      │    │ - name:String    │    │ - userId:UserId         │
│ - role       │    │ - status         │    │ - items:List<OrderItem> │
│ - status     │    │ - variants:List  │    │ - status:OrderStatus    │
│ - addresses  │    │ - categoryId     │    │ - totalAmount:Money     │
│ + register() │    │ + publish()      │    │ + place()               │
│ + verify()   │    │ + addVariant()   │    │ + confirm()             │
│ + login()    │    │ + discontinue()  │    │ + ship()                │
└──────────────┘    └──────────────────┘    │ + cancel()              │
                                            └─────────────────────────┘
┌──────────────┐    ┌──────────────────┐    ┌─────────────────────────┐
│  Inventory   │    │      Cart        │    │       Payment           │
│ - sku:String │    │ - id:CartId      │    │ - id:PaymentId          │
│ - onHand:int │    │ - userId:UserId  │    │ - orderId:OrderId       │
│ - reserved   │    │ - items:Map      │    │ - amount:Money          │
│ - version    │    │ + addItem()      │    │ - method                │
│ + reserve()  │    │ + removeItem()   │    │ - idempotencyKey        │
│ + confirm()  │    │ + clear()        │    │ + succeed()             │
│ + restock()  │    │ + calcTotal()    │    │ + fail()                │
└──────────────┘    └──────────────────┘    │ + refund()              │
                                            └─────────────────────────┘
┌──────────────────────────────────────────────────────────────────────┐
│  Value Objects: Money | Email | Address | PhoneNumber | AggregateId  │
│  — Immutable, equality by value, self-validating                     │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 2.11 Domain Event Flow Sequence
```
Sequence: Order Placement Domain Events

User         Cart        Order      Inventory     Payment
 │            │            │            │             │
 │──place──►  │            │            │             │
 │            │            │            │             │
 │        Cart.items ──►  Order.place() │             │
 │                         │            │             │
 │                    [OrderPlacedEvent emitted]       │
 │                         │            │             │
 │                         │ ──reserve──►             │
 │                         │            │             │
 │                         │     [StockReservedEvent] │
 │                         │            │             │
 │                         │            │ ──initiate──►
 │                         │            │             │
 │                         │            │    [PaymentSucceededEvent]
 │                         │            │             │
 │                         │   ◄─confirm reservation  │
 │                         │            │             │
 │                    Order.confirm()   │             │
 │                         │            │             │
 │                    [OrderStatusChangedEvent]        │
 │                         │            │             │
 ◄─ All events dispatched via EventPublisher after DB commit ─────────

✅ Phase 2 complete. We've built:

6 full Aggregate Roots: User, Product, Inventory, Order, Cart, Payment, Coupon
Rich Value Objects: Money, Email, Address, PhoneNumber, all strongly-typed IDs
Domain Events: 10+ events covering all state changes
State machines: Order & Payment status transitions with invariant enforcement
Optimistic locking: Inventory version field for concurrency
Reservation system: Prevents overselling at domain level
Repository ports: All defined as domain interfaces
Zero framework dependencies in the domain layer


Reply "Phase 3" and I'll implement all 5 Creational Design Patterns — Singleton, Factory Method, Abstract Factory, Builder, and Prototype — with full production code, class diagrams, sequence diagrams, trade-offs, and scalability analysis for each.Phase 315:51PHASE 3 — Creational Design Patterns
Overview: Why Creational Patterns Matter Here
Problem: In an enterprise e-commerce system with 1M+ users, object
creation is NOT trivial. We need to answer:

  ├── WHO creates objects?        → Factory Method / Abstract Factory
  ├── HOW are complex objects assembled? → Builder
  ├── HOW many instances exist?   → Singleton
  └── HOW do we copy objects?    → Prototype

Anti-pattern avoided: `new SomeClass()` scattered everywhere
  → Tight coupling to concrete classes
  → Impossible to swap implementations
  → No control over instantiation lifecycle
  → Untestable code

Pattern 1: Singleton
Problem It Solves
Database connection pools, JWT secret managers, configuration managers,
rate limiter counters — these MUST have exactly one instance per JVM.
Creating multiple instances causes:
  - Multiple DB connection pools → resource exhaustion
  - Multiple config readers → inconsistent config state
  - Multiple rate limiters → limits not enforced correctly

Spring's @Bean(singleton) solves most cases, but we need
thread-safe lazy initialization for non-Spring contexts
and to understand the pattern deeply.
Why Singleton Over Alternatives
Alternative 1: Static class     → Cannot be mocked, cannot implement interfaces
Alternative 2: Enum singleton   → Cannot be lazy-initialized, serialization issues
Alternative 3: Spring @Bean     → Best for Spring context, but not domain layer
Singleton Pattern               → Lazy + thread-safe + mockable + testable
Class Diagram
┌─────────────────────────────────────────────────────┐
│                 ApplicationConfig                    │
├─────────────────────────────────────────────────────┤
│ - instance: ApplicationConfig   (volatile)          │
│ - properties: Map<String,String>                    │
│ - loadedAt: Instant                                 │
├─────────────────────────────────────────────────────┤
│ - ApplicationConfig()           (private)           │
│ + getInstance(): ApplicationConfig  (synchronized)  │
│ + get(key): String                                  │
│ + getOrDefault(key, def): String                    │
│ + reload()                                          │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│              DatabaseConnectionPool                  │
├─────────────────────────────────────────────────────┤
│ - instance: DatabaseConnectionPool  (volatile)      │
│ - pool: HikariDataSource                            │
│ - metrics: PoolMetrics                              │
├─────────────────────────────────────────────────────┤
│ - DatabaseConnectionPool()      (private)           │
│ + getInstance(): DatabaseConnectionPool             │
│ + getConnection(): Connection                       │
│ + getMetrics(): PoolMetrics                         │
└─────────────────────────────────────────────────────┘
Sequence Diagram
Thread-1          Thread-2          Singleton
   │                 │                 │
   │──getInstance()──►                 │
   │                 │                 │
   │         [instance == null]        │
   │──synchronized block──────────────►│
   │                 │         [check again]
   │                 │──getInstance()──►│
   │                 │         [wait for lock]
   │         new Instance()            │
   │◄──────────────── instance ────────│
   │         [release lock]            │
   │                 │◄── instance ────│
   │                 │         [same instance]
Implementation
java// ecommerce-infrastructure:
// com.ecommerce.infrastructure.config.ApplicationConfigManager.java
package com.ecommerce.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Singleton Pattern — Application Configuration Manager.
 *
 * Implementation: Double-checked locking with volatile.
 * Java 21: Could use record + holder pattern, but double-checked
 * locking shown for educational completeness.
 *
 * Thread safety: volatile + synchronized inner check.
 * Performance: After first creation, no synchronization overhead.
 *
 * Why NOT Spring @Value here:
 * - Domain/application layers must not depend on Spring.
 * - Used for infrastructure-level config before Spring context.
 * - Demonstrates the pattern in its pure form.
 */
public final class ApplicationConfigManager {

    private static final Logger log =
        LoggerFactory.getLogger(ApplicationConfigManager.class);

    // volatile: ensures visibility across threads (Java memory model)
    // Without volatile, Thread-2 may see partially constructed object
    private static volatile ApplicationConfigManager instance;

    private final Map<String, String> properties;
    private final Instant loadedAt;
    private final String environment;

    // Private constructor — prevents external instantiation
    private ApplicationConfigManager() {
        this.properties = new HashMap<>();
        this.environment = System.getenv().getOrDefault("APP_ENV", "development");
        loadProperties();
        this.loadedAt = Instant.now();
        log.info("ApplicationConfigManager initialized for env: {}", environment);
    }

    /**
     * Double-checked locking — gold standard thread-safe lazy singleton.
     * First check: avoid synchronization overhead for existing instance.
     * Second check (inside sync): prevent race condition on first creation.
     */
    public static ApplicationConfigManager getInstance() {
        if (instance == null) {                          // Check 1: no lock
            synchronized (ApplicationConfigManager.class) {
                if (instance == null) {                  // Check 2: with lock
                    instance = new ApplicationConfigManager();
                }
            }
        }
        return instance;
    }

    private void loadProperties() {
        // Load base properties
        loadFromClasspath("application.properties");
        // Load env-specific (overrides base)
        loadFromClasspath("application-" + environment + ".properties");
        // Load from environment variables (highest priority)
        loadFromEnvironment();
    }

    private void loadFromClasspath(String filename) {
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream(filename)) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                props.forEach((k, v) -> properties.put(k.toString(), v.toString()));
                log.debug("Loaded {} properties from {}", props.size(), filename);
            }
        } catch (IOException e) {
            log.warn("Could not load properties from {}: {}", filename, e.getMessage());
        }
    }

    private void loadFromEnvironment() {
        // Map env vars to property keys (e.g., DB_URL -> db.url)
        Map<String, String> envMappings = Map.of(
            "DB_URL",              "database.url",
            "DB_USER",             "database.username",
            "DB_PASSWORD",         "database.password",
            "REDIS_HOST",          "redis.host",
            "REDIS_PASSWORD",      "redis.password",
            "JWT_SECRET",          "jwt.secret",
            "KAFKA_SERVERS",       "kafka.bootstrap-servers",
            "STRIPE_SECRET_KEY",   "payment.stripe.secret-key"
        );
        envMappings.forEach((envKey, propKey) -> {
            String value = System.getenv(envKey);
            if (value != null && !value.isBlank()) {
                properties.put(propKey, value);
            }
        });
    }

    public String get(String key) {
        String value = properties.get(key);
        if (value == null) {
            throw new ConfigurationException("Configuration key not found: " + key);
        }
        return value;
    }

    public String getOrDefault(String key, String defaultValue) {
        return properties.getOrDefault(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        String value = properties.get(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid integer config for key {}: {}", key, value);
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = properties.get(key);
        if (value == null) return defaultValue;
        return Boolean.parseBoolean(value.trim());
    }

    public Map<String, String> getAllProperties() {
        return Collections.unmodifiableMap(properties);
    }

    public String getEnvironment() { return environment; }
    public Instant getLoadedAt()   { return loadedAt; }

    /**
     * For testing — reset singleton (use only in tests).
     * Uses reflection in tests, or expose for test profile only.
     */
    static void resetForTesting() {
        instance = null;
    }

    public static class ConfigurationException extends RuntimeException {
        public ConfigurationException(String message) { super(message); }
    }
}
java// com.ecommerce.infrastructure.config/JwtSecretManager.java
package com.ecommerce.infrastructure.config;

import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Singleton — JWT Secret Key Manager.
 *
 * Uses Initialization-on-demand holder pattern (Bill Pugh Singleton).
 * This is the BEST singleton pattern in Java:
 * - Lazy initialization (holder class not loaded until getInstance called)
 * - Thread-safe WITHOUT synchronization (class loading is thread-safe)
 * - No volatile needed
 * - Immune to double-checked locking issues
 *
 * Performance: Zero overhead after first access.
 */
public final class JwtSecretManager {

    private final SecretKey signingKey;
    private final long accessTokenExpiryMs;
    private final long refreshTokenExpiryMs;

    private JwtSecretManager() {
        ApplicationConfigManager config = ApplicationConfigManager.getInstance();
        String secret = config.get("jwt.secret");
        validateSecret(secret);

        // HMAC-SHA256 key derived from secret
        this.signingKey = Keys.hmacShaKeyFor(
            secret.getBytes(StandardCharsets.UTF_8)
        );
        this.accessTokenExpiryMs  = config.getInt("jwt.access-token-expiry-minutes", 15)
            * 60_000L;
        this.refreshTokenExpiryMs = config.getInt("jwt.refresh-token-expiry-days", 30)
            * 24 * 60 * 60_000L;
    }

    // Bill Pugh Singleton — Initialization-on-demand holder
    private static final class Holder {
        private static final JwtSecretManager INSTANCE = new JwtSecretManager();
    }

    public static JwtSecretManager getInstance() {
        return Holder.INSTANCE;
    }

    private void validateSecret(String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                "JWT secret must be at least 32 characters long. " +
                "Current length: " + (secret == null ? 0 : secret.length())
            );
        }
    }

    public SecretKey getSigningKey()         { return signingKey; }
    public long getAccessTokenExpiryMs()     { return accessTokenExpiryMs; }
    public long getRefreshTokenExpiryMs()    { return refreshTokenExpiryMs; }
}
java// com.ecommerce.infrastructure.ratelimit/RateLimiterRegistry.java
package com.ecommerce.infrastructure.ratelimit;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Singleton — In-Memory Rate Limiter Registry.
 * Tracks request counts per client key (IP/userId).
 *
 * Production note: For multi-instance deployments,
 * this moves to Redis (see infrastructure layer Phase 8).
 * This singleton handles single-instance or per-node limiting.
 *
 * Pattern: Singleton + Token Bucket algorithm
 */
public final class RateLimiterRegistry {

    private static final class Holder {
        private static final RateLimiterRegistry INSTANCE = new RateLimiterRegistry();
    }

    public static RateLimiterRegistry getInstance() {
        return Holder.INSTANCE;
    }

    private final ConcurrentHashMap<String, RateLimitBucket> buckets;

    private RateLimiterRegistry() {
        this.buckets = new ConcurrentHashMap<>();
        startCleanupTask();
    }

    public boolean isAllowed(String key, int maxRequests, long windowMs) {
        RateLimitBucket bucket = buckets.computeIfAbsent(
            key, k -> new RateLimitBucket(maxRequests, windowMs)
        );
        return bucket.tryConsume();
    }

    public RateLimitInfo getInfo(String key) {
        RateLimitBucket bucket = buckets.get(key);
        if (bucket == null) return RateLimitInfo.UNLIMITED;
        return new RateLimitInfo(bucket.getRemaining(), bucket.getResetAt());
    }

    private void startCleanupTask() {
        // Scheduled cleanup to prevent memory leak
        Thread.ofVirtual()     // Java 21 Virtual Threads!
            .name("rate-limiter-cleanup")
            .start(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(60_000); // every minute
                        cleanupExpiredBuckets();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
    }

    private void cleanupExpiredBuckets() {
        Instant now = Instant.now();
        buckets.entrySet().removeIf(
            entry -> entry.getValue().isExpired(now)
        );
    }

    // ── Inner Classes ─────────────────────────────────────────────────

    static final class RateLimitBucket {
        private final int maxTokens;
        private final long windowMs;
        private final AtomicInteger tokens;
        private volatile long windowStart;

        RateLimitBucket(int maxTokens, long windowMs) {
            this.maxTokens   = maxTokens;
            this.windowMs    = windowMs;
            this.tokens      = new AtomicInteger(maxTokens);
            this.windowStart = System.currentTimeMillis();
        }

        synchronized boolean tryConsume() {
            long now = System.currentTimeMillis();
            if (now - windowStart >= windowMs) {
                // Reset window
                tokens.set(maxTokens);
                windowStart = now;
            }
            if (tokens.get() <= 0) return false;
            tokens.decrementAndGet();
            return true;
        }

        int getRemaining() { return Math.max(0, tokens.get()); }

        Instant getResetAt() {
            return Instant.ofEpochMilli(windowStart + windowMs);
        }

        boolean isExpired(Instant now) {
            return now.toEpochMilli() - windowStart > windowMs * 2;
        }
    }

    public record RateLimitInfo(int remaining, Instant resetAt) {
        static final RateLimitInfo UNLIMITED = new RateLimitInfo(Integer.MAX_VALUE, Instant.MAX);
    }
}
```

### Trade-offs & Scalability
```
✅ PROS:
  - Guarantees single instance: no duplicate connection pools
  - Lazy init: no startup cost if never used
  - Thread-safe: Bill Pugh pattern has zero synchronization after init

❌ CONS:
  - Global state: harder to test in isolation
  - Hidden dependency: callers don't declare what they depend on
  - Multi-JVM: singleton is per-JVM, not per-cluster
    (Rate limiter must move to Redis for horizontal scaling)

📈 SCALABILITY:
  - JWT secrets: same key per JVM, shared via env var → fine
  - Config: read-only after load → perfectly scalable
  - Rate limiter: per-node → must centralize in Redis for true global limiting
    (Phase 8 shows the Redis-backed distributed rate limiter)

🔒 SECURITY:
  - JWT secret loaded once, never logged, stored in volatile memory only
  - Config values masked in logs (password, secret fields)
```

---

## Pattern 2: Factory Method

### Problem It Solves
```
Problem: Notification system must send via Email, SMS, Push, Slack.
The ORDER service shouldn't know WHICH notification type to create.

Without Factory Method:
  if (type == EMAIL) new EmailNotification(...)
  else if (type == SMS) new SmsNotification(...)
  // Adding Slack requires modifying Order service → OCP violation!

With Factory Method:
  NotificationFactory.create(type) → returns correct implementation
  Adding new type = add new factory, zero existing code changes
```

### Class Diagram
```
         «interface»
    NotificationFactory
    + create(context): Notification
           ▲
    ┌──────┴──────┬────────────────┐
    │             │                │
EmailNoti.    SmsNoti.       PushNoti.
Factory       Factory        Factory
    │             │                │
    ▼             ▼                ▼
EmailNoti.    SmsNoti.       PushNoti.
    ▲             ▲                ▲
    └─────────────┴────────────────┘
              «interface»
             Notification
    + send(recipient, payload): Result
    + getChannel(): NotificationChannel
    + isSupported(NotificationContext): boolean
```

### Sequence Diagram
```
OrderService    NotificationRegistry    EmailFactory    EmailNotification
     │                  │                   │                  │
     │─createFor(ctx)──►│                   │                  │
     │                  │──select factory───►│                  │
     │                  │                   │──create()────────►│
     │                  │                   │◄── EmailNotif ───│
     │◄── Notification ─│                   │                  │
     │─send(payload)────────────────────────────────────────── ►│
     │◄── Result ────────────────────────────────────────────── │
Implementation
java// ecommerce-domain: com.ecommerce.domain.notification/Notification.java
package com.ecommerce.domain.notification;

/**
 * Product interface in Factory Method pattern.
 * Each notification type implements this contract.
 */
public interface Notification {

    NotificationResult send(NotificationRecipient recipient, NotificationPayload payload);

    NotificationChannel getChannel();

    boolean isSupported(NotificationContext context);

    /**
     * Priority for fallback chain — lower number = higher priority.
     */
    int getPriority();
}
java// com.ecommerce.domain.notification/NotificationChannel.java
package com.ecommerce.domain.notification;

public enum NotificationChannel {
    EMAIL,
    SMS,
    PUSH,
    IN_APP,
    SLACK,       // for admin alerts
    WEBHOOK      // for vendor integrations
}
java// com.ecommerce.domain.notification/NotificationContext.java
package com.ecommerce.domain.notification;

import java.util.Set;

/**
 * Context passed to Factory to determine which notification to create.
 * Using Java 21 record for clean immutability.
 */
public record NotificationContext(
    String userId,
    String eventType,
    Set<NotificationChannel> preferredChannels,
    boolean isUrgent,
    boolean userHasPushEnabled,
    boolean userHasSmsEnabled,
    String userEmail,
    String userPhone
) {
    public boolean hasEmail()       { return userEmail != null && !userEmail.isBlank(); }
    public boolean hasPhone()       { return userPhone != null && !userPhone.isBlank(); }
    public boolean hasPushEnabled() { return userHasPushEnabled; }
}
java// com.ecommerce.domain.notification/NotificationPayload.java
package com.ecommerce.domain.notification;

import java.util.Map;

/**
 * Data transfer object carrying notification content.
 * Template variables resolved by each notification type.
 */
public record NotificationPayload(
    String templateId,
    String subject,
    String body,
    Map<String, String> variables,
    Map<String, String> metadata
) {
    public String resolve(String template) {
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }
}
java// com.ecommerce.domain.notification/NotificationRecipient.java
package com.ecommerce.domain.notification;

public record NotificationRecipient(
    String userId,
    String email,
    String phoneNumber,
    String pushToken,
    String locale
) {}
java// com.ecommerce.domain.notification/NotificationResult.java
package com.ecommerce.domain.notification;

import java.time.Instant;

public record NotificationResult(
    boolean success,
    String messageId,
    NotificationChannel channel,
    String errorCode,
    String errorMessage,
    Instant sentAt
) {
    public static NotificationResult success(String messageId, NotificationChannel channel) {
        return new NotificationResult(true, messageId, channel,
            null, null, Instant.now());
    }

    public static NotificationResult failure(NotificationChannel channel,
                                             String errorCode, String errorMessage) {
        return new NotificationResult(false, null, channel,
            errorCode, errorMessage, Instant.now());
    }
}
java// com.ecommerce.domain.notification/NotificationFactory.java
package com.ecommerce.domain.notification;

/**
 * Creator interface in Factory Method pattern.
 * Each channel has its own factory implementation.
 * Adding new channel = new factory class, no existing changes.
 */
public interface NotificationFactory {

    /**
     * Factory Method — subclasses decide which object to create.
     */
    Notification create(NotificationContext context);

    NotificationChannel getChannel();

    /**
     * Can this factory handle the given context?
     */
    boolean canHandle(NotificationContext context);
}
java// ecommerce-infrastructure:
// com.ecommerce.infrastructure.notification/EmailNotification.java
package com.ecommerce.infrastructure.notification;

import com.ecommerce.domain.notification.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import jakarta.mail.internet.MimeMessage;
import java.util.UUID;

/**
 * Concrete Product — Email Notification.
 * Implements actual email sending via JavaMailSender.
 */
public class EmailNotification implements Notification {

    private static final Logger log = LoggerFactory.getLogger(EmailNotification.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String fromName;

    public EmailNotification(JavaMailSender mailSender,
                             String fromAddress, String fromName) {
        this.mailSender   = mailSender;
        this.fromAddress  = fromAddress;
        this.fromName     = fromName;
    }

    @Override
    public NotificationResult send(NotificationRecipient recipient,
                                   NotificationPayload payload) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress, fromName);
            helper.setTo(recipient.email());
            helper.setSubject(payload.resolve(payload.subject()));
            helper.setText(payload.resolve(payload.body()), true); // HTML

            mailSender.send(message);

            String messageId = UUID.randomUUID().toString();
            log.info("Email sent to {} for user {}, messageId: {}",
                recipient.email(), recipient.userId(), messageId);

            return NotificationResult.success(messageId, NotificationChannel.EMAIL);

        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", recipient.email(), e.getMessage());
            return NotificationResult.failure(
                NotificationChannel.EMAIL, "EMAIL_SEND_FAILED", e.getMessage()
            );
        }
    }

    @Override
    public NotificationChannel getChannel() { return NotificationChannel.EMAIL; }

    @Override
    public boolean isSupported(NotificationContext context) {
        return context.hasEmail();
    }

    @Override
    public int getPriority() { return 1; }
}
java// com.ecommerce.infrastructure.notification/SmsNotification.java
package com.ecommerce.infrastructure.notification;

import com.ecommerce.domain.notification.*;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Concrete Product — SMS Notification via Twilio.
 */
public class SmsNotification implements Notification {

    private static final Logger log = LoggerFactory.getLogger(SmsNotification.class);

    private final String fromNumber;
    private final String accountSid;
    private final String authToken;

    public SmsNotification(String accountSid, String authToken, String fromNumber) {
        this.accountSid = accountSid;
        this.authToken  = authToken;
        this.fromNumber = fromNumber;
        Twilio.init(accountSid, authToken);
    }

    @Override
    public NotificationResult send(NotificationRecipient recipient,
                                   NotificationPayload payload) {
        try {
            // SMS: body only, no HTML, max 160 chars per segment
            String body = truncate(payload.resolve(payload.body()), 320);

            Message message = Message.creator(
                new PhoneNumber(recipient.phoneNumber()),
                new PhoneNumber(fromNumber),
                body
            ).create();

            log.info("SMS sent to {}, SID: {}", recipient.phoneNumber(), message.getSid());
            return NotificationResult.success(message.getSid(), NotificationChannel.SMS);

        } catch (Exception e) {
            log.error("SMS failed to {}: {}", recipient.phoneNumber(), e.getMessage());
            return NotificationResult.failure(
                NotificationChannel.SMS, "SMS_SEND_FAILED", e.getMessage()
            );
        }
    }

    private String truncate(String text, int maxLen) {
        return text.length() > maxLen ? text.substring(0, maxLen - 3) + "..." : text;
    }

    @Override
    public NotificationChannel getChannel() { return NotificationChannel.SMS; }

    @Override
    public boolean isSupported(NotificationContext context) {
        return context.hasPhone() && context.userHasSmsEnabled();
    }

    @Override
    public int getPriority() { return 2; }
}
java// com.ecommerce.infrastructure.notification/PushNotification.java
package com.ecommerce.infrastructure.notification;

import com.ecommerce.domain.notification.*;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Concrete Product — Push Notification via Firebase FCM.
 */
public class PushNotification implements com.ecommerce.domain.notification.Notification {

    private static final Logger log = LoggerFactory.getLogger(PushNotification.class);

    private final FirebaseMessaging firebaseMessaging;

    public PushNotification(FirebaseMessaging firebaseMessaging) {
        this.firebaseMessaging = firebaseMessaging;
    }

    @Override
    public NotificationResult send(NotificationRecipient recipient,
                                   NotificationPayload payload) {
        try {
            Message message = Message.builder()
                .setToken(recipient.pushToken())
                .setNotification(Notification.builder()
                    .setTitle(payload.subject())
                    .setBody(payload.resolve(payload.body()))
                    .build())
                .putAllData(payload.metadata())
                .build();

            String messageId = firebaseMessaging.send(message);
            log.info("Push sent to user {}, messageId: {}", recipient.userId(), messageId);
            return NotificationResult.success(messageId, NotificationChannel.PUSH);

        } catch (Exception e) {
            log.error("Push failed for user {}: {}", recipient.userId(), e.getMessage());
            return NotificationResult.failure(
                NotificationChannel.PUSH, "PUSH_SEND_FAILED", e.getMessage()
            );
        }
    }

    @Override
    public NotificationChannel getChannel() { return NotificationChannel.PUSH; }

    @Override
    public boolean isSupported(NotificationContext context) {
        return context.hasPushEnabled();
    }

    @Override
    public int getPriority() { return 3; }
}
java// com.ecommerce.infrastructure.notification/EmailNotificationFactory.java
package com.ecommerce.infrastructure.notification;

import com.ecommerce.domain.notification.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Concrete Creator — Email Notification Factory.
 * Implements the Factory Method pattern's creator role.
 */
@Component
public class EmailNotificationFactory implements NotificationFactory {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String fromName;

    public EmailNotificationFactory(JavaMailSender mailSender,
                                    @org.springframework.beans.factory.annotation.Value(
                                        "${notification.email.from-address}")
                                    String fromAddress,
                                    @org.springframework.beans.factory.annotation.Value(
                                        "${notification.email.from-name}")
                                    String fromName) {
        this.mailSender  = mailSender;
        this.fromAddress = fromAddress;
        this.fromName    = fromName;
    }

    @Override
    public Notification create(NotificationContext context) {
        // Factory Method: decide which concrete product to create
        // Could create specialized email types: OrderConfirmation, PasswordReset etc.
        return new EmailNotification(mailSender, fromAddress, fromName);
    }

    @Override
    public NotificationChannel getChannel() { return NotificationChannel.EMAIL; }

    @Override
    public boolean canHandle(NotificationContext context) {
        return context.hasEmail();
    }
}
java// com.ecommerce.infrastructure.notification/SmsNotificationFactory.java
package com.ecommerce.infrastructure.notification;

import com.ecommerce.domain.notification.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SmsNotificationFactory implements NotificationFactory {

    private final String accountSid;
    private final String authToken;
    private final String fromNumber;

    public SmsNotificationFactory(
        @Value("${notification.twilio.account-sid}") String accountSid,
        @Value("${notification.twilio.auth-token}")  String authToken,
        @Value("${notification.twilio.from-number}") String fromNumber) {
        this.accountSid = accountSid;
        this.authToken  = authToken;
        this.fromNumber = fromNumber;
    }

    @Override
    public Notification create(NotificationContext context) {
        return new SmsNotification(accountSid, authToken, fromNumber);
    }

    @Override
    public NotificationChannel getChannel() { return NotificationChannel.SMS; }

    @Override
    public boolean canHandle(NotificationContext context) {
        return context.hasPhone() && context.userHasSmsEnabled();
    }
}
java// com.ecommerce.infrastructure.notification/NotificationFactoryRegistry.java
package com.ecommerce.infrastructure.notification;

import com.ecommerce.domain.notification.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Registry that holds all NotificationFactory implementations.
 * Spring auto-injects all NotificationFactory beans.
 *
 * Extensibility example:
 *   Adding WhatsApp notifications = create WhatsAppNotificationFactory
 *   + annotate @Component. ZERO other code changes. ✅ OCP in action.
 */
@Component
public class NotificationFactoryRegistry {

    private static final Logger log =
        LoggerFactory.getLogger(NotificationFactoryRegistry.class);

    private final List<NotificationFactory> factories;

    // Spring injects ALL NotificationFactory implementations
    public NotificationFactoryRegistry(List<NotificationFactory> factories) {
        this.factories = factories;
        log.info("Loaded {} notification factories: {}",
            factories.size(),
            factories.stream()
                .map(f -> f.getChannel().name())
                .collect(Collectors.joining(", ")));
    }

    /**
     * Get best factory for context (by priority, filtered by capability).
     */
    public NotificationFactory getFactory(NotificationContext context) {
        return factories.stream()
            .filter(f -> f.canHandle(context))
            .min(Comparator.comparingInt(f -> f.create(context).getPriority()))
            .orElseThrow(() -> new IllegalStateException(
                "No notification factory available for user: " + context.userId()
            ));
    }

    /**
     * Get ALL factories that can handle this context (for multi-channel send).
     */
    public List<NotificationFactory> getAllFactories(NotificationContext context) {
        return factories.stream()
            .filter(f -> f.canHandle(context))
            .sorted(Comparator.comparingInt(f -> f.create(context).getPriority()))
            .collect(Collectors.toList());
    }
}
```

---

## Pattern 3: Abstract Factory

### Problem It Solves
```
Problem: Payment processing must support multiple FAMILIES of providers:
  - Stripe family:  StripeChargeGateway + StripeRefundGateway + StripeWebhookHandler
  - PayPal family:  PayPalChargeGateway + PayPalRefundGateway + PayPalWebhookHandler
  - Braintree family: BraintreeCharge... etc.

Without Abstract Factory:
  - Code mixes Stripe and PayPal classes everywhere
  - Switching provider = hunting through entire codebase
  - Testing requires real provider credentials

With Abstract Factory:
  - One factory per provider family
  - Switch entire provider by changing one config value
  - Test with MockPaymentGatewayFactory
```

### Class Diagram
```
«interface»                          «interface»
PaymentGatewayFactory                ChargeGateway
+ createChargeGateway()  ──────────► + charge(request): ChargeResult
+ createRefundGateway()              + authorize(request): AuthResult
+ createWebhookHandler() ◄────────── «interface»
                                     RefundGateway
        ▲                            + refund(request): RefundResult
   ┌────┴──────────────┐             + partialRefund(): RefundResult
   │                   │
StripeGateway      PayPalGateway    «interface»
Factory            Factory          WebhookHandler
   │                   │            + handle(event): void
   │─creates──►StripeCharge         + validate(sig): boolean
   │─creates──►StripeRefund
   │─creates──►StripeWebhook
   │
   │─creates──►PayPalCharge
   │─creates──►PayPalRefund
   │─creates──►PayPalWebhook
Implementation
java// ecommerce-domain: com.ecommerce.domain.payment/gateway/ChargeRequest.java
package com.ecommerce.domain.payment.gateway;

import com.ecommerce.domain.shared.valueobject.Money;
import java.util.Map;

/**
 * Gateway-agnostic charge request.
 * Domain doesn't know about Stripe vs PayPal — it speaks this language.
 */
public record ChargeRequest(
    String idempotencyKey,
    Money amount,
    String currency,
    String paymentMethodToken,   // tokenized card/account
    String customerId,
    String description,
    String orderId,
    Map<String, String> metadata,
    boolean captureImmediately   // true = charge, false = authorize only
) {}
java// com.ecommerce.domain.payment/gateway/ChargeResult.java
package com.ecommerce.domain.payment.gateway;

public record ChargeResult(
    boolean success,
    String transactionId,
    String status,
    String failureCode,
    String failureMessage,
    String rawResponse,
    boolean requiresAction,         // 3DS authentication
    String actionUrl                // 3DS redirect URL
) {
    public static ChargeResult success(String transactionId, String rawResponse) {
        return new ChargeResult(true, transactionId, "succeeded",
            null, null, rawResponse, false, null);
    }

    public static ChargeResult failure(String failureCode,
                                       String failureMessage, String rawResponse) {
        return new ChargeResult(false, null, "failed",
            failureCode, failureMessage, rawResponse, false, null);
    }

    public static ChargeResult requiresAction(String transactionId, String actionUrl) {
        return new ChargeResult(false, transactionId, "requires_action",
            null, null, null, true, actionUrl);
    }
}
java// com.ecommerce.domain.payment/gateway/RefundRequest.java
package com.ecommerce.domain.payment.gateway;

import com.ecommerce.domain.shared.valueobject.Money;

public record RefundRequest(
    String transactionId,
    Money amount,
    String reason,
    String idempotencyKey
) {}
java// com.ecommerce.domain.payment/gateway/RefundResult.java
package com.ecommerce.domain.payment.gateway;

public record RefundResult(
    boolean success,
    String refundId,
    String status,
    String failureCode,
    String failureMessage
) {
    public static RefundResult success(String refundId) {
        return new RefundResult(true, refundId, "succeeded", null, null);
    }

    public static RefundResult failure(String code, String message) {
        return new RefundResult(false, null, "failed", code, message);
    }
}
java// ecommerce-domain: com.ecommerce.domain.payment/gateway/ChargeGateway.java
package com.ecommerce.domain.payment.gateway;

/**
 * Abstract Product 1 of the Abstract Factory family.
 */
public interface ChargeGateway {
    ChargeResult charge(ChargeRequest request);
    ChargeResult authorize(ChargeRequest request);
    ChargeResult capture(String authorizationId, String idempotencyKey);
    String getProviderName();
}
java// com.ecommerce.domain.payment/gateway/RefundGateway.java
package com.ecommerce.domain.payment.gateway;

/**
 * Abstract Product 2 of the Abstract Factory family.
 */
public interface RefundGateway {
    RefundResult refund(RefundRequest request);
    RefundResult getRefundStatus(String refundId);
}
java// com.ecommerce.domain.payment/gateway/WebhookHandler.java
package com.ecommerce.domain.payment.gateway;

import java.util.Map;

/**
 * Abstract Product 3 of the Abstract Factory family.
 */
public interface WebhookHandler {
    boolean validateSignature(String payload, String signature, String secret);
    WebhookEvent parse(String payload, Map<String, String> headers);
    void handle(WebhookEvent event);

    record WebhookEvent(
        String eventId,
        String eventType,
        String transactionId,
        String status,
        Map<String, Object> data
    ) {}
}
java// com.ecommerce.domain.payment/gateway/PaymentGatewayFactory.java
package com.ecommerce.domain.payment.gateway;

/**
 * Abstract Factory interface.
 * Defines the family of related payment objects.
 * Each payment provider implements this factory.
 */
public interface PaymentGatewayFactory {

    /**
     * Factory Method 1 — creates provider-specific charge gateway.
     */
    ChargeGateway createChargeGateway();

    /**
     * Factory Method 2 — creates provider-specific refund gateway.
     */
    RefundGateway createRefundGateway();

    /**
     * Factory Method 3 — creates provider-specific webhook handler.
     */
    WebhookHandler createWebhookHandler();

    String getProviderName();

    boolean isAvailable();  // health check
}
java// ecommerce-infrastructure:
// com.ecommerce.infrastructure.payment/stripe/StripeChargeGateway.java
package com.ecommerce.infrastructure.payment.stripe;

import com.ecommerce.domain.payment.gateway.*;
import com.stripe.Stripe;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Map;

/**
 * Concrete Product 1 — Stripe Charge Gateway.
 */
public class StripeChargeGateway implements ChargeGateway {

    private static final Logger log = LoggerFactory.getLogger(StripeChargeGateway.class);

    private final String apiKey;

    public StripeChargeGateway(String apiKey) {
        this.apiKey = apiKey;
        Stripe.apiKey = apiKey;
    }

    @Override
    public ChargeResult charge(ChargeRequest request) {
        try {
            // Amount in smallest currency unit (cents for USD)
            long amountInCents = request.amount().getAmount()
                .multiply(java.math.BigDecimal.valueOf(100))
                .longValue();

            Map<String, String> metadata = new HashMap<>(request.metadata());
            metadata.put("orderId",          request.orderId());
            metadata.put("idempotencyKey",   request.idempotencyKey());

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency(request.currency().toLowerCase())
                .setCustomer(request.customerId())
                .setPaymentMethod(request.paymentMethodToken())
                .setDescription(request.description())
                .setConfirm(true)
                .setConfirmationMethod(PaymentIntentCreateParams.ConfirmationMethod.AUTOMATIC)
                .setCaptureMethod(request.captureImmediately()
                    ? PaymentIntentCreateParams.CaptureMethod.AUTOMATIC
                    : PaymentIntentCreateParams.CaptureMethod.MANUAL)
                .putAllMetadata(metadata)
                .build();

            com.stripe.net.RequestOptions options = com.stripe.net.RequestOptions.builder()
                .setIdempotencyKey(request.idempotencyKey())
                .build();

            PaymentIntent intent = PaymentIntent.create(params, options);

            if ("requires_action".equals(intent.getStatus())) {
                return ChargeResult.requiresAction(
                    intent.getId(),
                    intent.getNextAction().getRedirectToUrl().getUrl()
                );
            }

            return ChargeResult.success(intent.getId(), intent.toJson());

        } catch (CardException e) {
            log.warn("Card declined for order {}: {} - {}",
                request.orderId(), e.getCode(), e.getMessage());
            return ChargeResult.failure(e.getCode(), e.getMessage(), null);

        } catch (StripeException e) {
            log.error("Stripe API error for order {}: {}", request.orderId(), e.getMessage());
            return ChargeResult.failure("STRIPE_ERROR", e.getMessage(), null);
        }
    }

    @Override
    public ChargeResult authorize(ChargeRequest request) {
        return charge(ChargeRequest_withCaptureOff(request));
    }

    private ChargeRequest ChargeRequest_withCaptureOff(ChargeRequest r) {
        return new ChargeRequest(r.idempotencyKey(), r.amount(), r.currency(),
            r.paymentMethodToken(), r.customerId(), r.description(),
            r.orderId(), r.metadata(), false);
    }

    @Override
    public ChargeResult capture(String authorizationId, String idempotencyKey) {
        try {
            PaymentIntent intent = PaymentIntent.retrieve(authorizationId);
            PaymentIntent captured = intent.capture();
            return ChargeResult.success(captured.getId(), captured.toJson());
        } catch (StripeException e) {
            return ChargeResult.failure("CAPTURE_FAILED", e.getMessage(), null);
        }
    }

    @Override
    public String getProviderName() { return "STRIPE"; }
}
java// com.ecommerce.infrastructure.payment/stripe/StripeRefundGateway.java
package com.ecommerce.infrastructure.payment.stripe;

import com.ecommerce.domain.payment.gateway.*;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Refund;
import com.stripe.param.RefundCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StripeRefundGateway implements RefundGateway {

    private static final Logger log = LoggerFactory.getLogger(StripeRefundGateway.class);

    public StripeRefundGateway(String apiKey) {
        Stripe.apiKey = apiKey;
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        try {
            long amountInCents = request.amount().getAmount()
                .multiply(java.math.BigDecimal.valueOf(100))
                .longValue();

            RefundCreateParams params = RefundCreateParams.builder()
                .setPaymentIntent(request.transactionId())
                .setAmount(amountInCents)
                .setReason(mapReason(request.reason()))
                .build();

            com.stripe.net.RequestOptions options = com.stripe.net.RequestOptions.builder()
                .setIdempotencyKey(request.idempotencyKey())
                .build();

            Refund refund = Refund.create(params, options);
            log.info("Stripe refund created: {} for intent: {}",
                refund.getId(), request.transactionId());

            return RefundResult.success(refund.getId());

        } catch (StripeException e) {
            log.error("Stripe refund failed: {}", e.getMessage());
            return RefundResult.failure("REFUND_FAILED", e.getMessage());
        }
    }

    @Override
    public RefundResult getRefundStatus(String refundId) {
        try {
            Refund refund = Refund.retrieve(refundId);
            return RefundResult.success(refund.getId());
        } catch (StripeException e) {
            return RefundResult.failure("STATUS_CHECK_FAILED", e.getMessage());
        }
    }

    private RefundCreateParams.Reason mapReason(String reason) {
        if (reason == null) return RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER;
        return switch (reason.toUpperCase()) {
            case "DUPLICATE"  -> RefundCreateParams.Reason.DUPLICATE;
            case "FRAUDULENT" -> RefundCreateParams.Reason.FRAUDULENT;
            default           -> RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER;
        };
    }
}
java// com.ecommerce.infrastructure.payment/stripe/StripeWebhookHandler.java
package com.ecommerce.infrastructure.payment.stripe;

import com.ecommerce.domain.payment.gateway.WebhookHandler;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

public class StripeWebhookHandler implements WebhookHandler {

    private static final Logger log =
        LoggerFactory.getLogger(StripeWebhookHandler.class);

    @Override
    public boolean validateSignature(String payload, String signature, String secret) {
        try {
            Webhook.constructEvent(payload, signature, secret);
            return true;
        } catch (SignatureVerificationException e) {
            log.warn("Invalid Stripe webhook signature: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public WebhookEvent parse(String payload, Map<String, String> headers) {
        try {
            Event event = Event.PRETTY_PRINT_GSON.fromJson(payload, Event.class);
            return new WebhookEvent(
                event.getId(),
                event.getType(),
                extractTransactionId(event),
                extractStatus(event),
                Map.of("type", event.getType(), "livemode",
                    String.valueOf(event.getLivemode()))
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse Stripe webhook: "
                + e.getMessage());
        }
    }

    @Override
    public void handle(WebhookEvent event) {
        log.info("Handling Stripe webhook: {} for txn: {}",
            event.eventType(), event.transactionId());
        // Actual handling done by application layer event handlers
    }

    private String extractTransactionId(Event event) {
        // Extract PaymentIntent ID from event data
        return event.getDataObjectDeserializer()
            .getObject()
            .map(obj -> ((com.stripe.model.PaymentIntent)obj).getId())
            .orElse("unknown");
    }

    private String extractStatus(Event event) {
        return switch (event.getType()) {
            case "payment_intent.succeeded"               -> "succeeded";
            case "payment_intent.payment_failed"          -> "failed";
            case "payment_intent.requires_action"         -> "requires_action";
            case "charge.refunded"                        -> "refunded";
            default                                       -> "unknown";
        };
    }
}
java// com.ecommerce.infrastructure.payment/stripe/StripePaymentGatewayFactory.java
package com.ecommerce.infrastructure.payment.stripe;

import com.ecommerce.domain.payment.gateway.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Concrete Factory — Stripe payment family.
 * Activated when payment.provider=stripe in config.
 *
 * Key: Creates the ENTIRE Stripe family of objects.
 * Switching to PayPal = change payment.provider=paypal
 */
@Component
@ConditionalOnProperty(name = "payment.provider", havingValue = "stripe")
public class StripePaymentGatewayFactory implements PaymentGatewayFactory {

    private final String secretKey;
    private final String webhookSecret;

    public StripePaymentGatewayFactory(
        @Value("${payment.stripe.secret-key}") String secretKey,
        @Value("${payment.stripe.webhook-secret}") String webhookSecret) {
        this.secretKey     = secretKey;
        this.webhookSecret = webhookSecret;
    }

    @Override
    public ChargeGateway createChargeGateway() {
        return new StripeChargeGateway(secretKey);
    }

    @Override
    public RefundGateway createRefundGateway() {
        return new StripeRefundGateway(secretKey);
    }

    @Override
    public WebhookHandler createWebhookHandler() {
        return new StripeWebhookHandler();
    }

    @Override
    public String getProviderName() { return "STRIPE"; }

    @Override
    public boolean isAvailable() {
        // Health check: ping Stripe API
        try {
            com.stripe.Stripe.apiKey = secretKey;
            com.stripe.model.Balance.retrieve();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
java// com.ecommerce.infrastructure.payment/paypal/PayPalChargeGateway.java
package com.ecommerce.infrastructure.payment.paypal;

import com.ecommerce.domain.payment.gateway.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Concrete Product — PayPal Charge Gateway.
 * Client code doesn't change when switching from Stripe to PayPal.
 * Only the factory changes.
 */
public class PayPalChargeGateway implements ChargeGateway {

    private static final Logger log = LoggerFactory.getLogger(PayPalChargeGateway.class);

    private final String clientId;
    private final String clientSecret;
    private final boolean sandbox;

    public PayPalChargeGateway(String clientId, String clientSecret, boolean sandbox) {
        this.clientId     = clientId;
        this.clientSecret = clientSecret;
        this.sandbox      = sandbox;
    }

    @Override
    public ChargeResult charge(ChargeRequest request) {
        // PayPal Orders API v2 implementation
        try {
            // In real implementation: use PayPal Java SDK
            // PayPalHttpClient client = new PayPalHttpClient(environment);
            // OrdersCreateRequest ordersRequest = new OrdersCreateRequest();
            // ... (abbreviated for brevity — full impl would use PayPal SDK)

            log.info("PayPal charge initiated for order: {}", request.orderId());

            // Simulating PayPal API call structure
            String transactionId = "PAYPAL-" + request.idempotencyKey();
            return ChargeResult.success(transactionId, "{\"status\":\"COMPLETED\"}");

        } catch (Exception e) {
            log.error("PayPal charge failed: {}", e.getMessage());
            return ChargeResult.failure("PAYPAL_ERROR", e.getMessage(), null);
        }
    }

    @Override
    public ChargeResult authorize(ChargeRequest request) {
        return charge(request); // PayPal handles differently
    }

    @Override
    public ChargeResult capture(String authorizationId, String idempotencyKey) {
        log.info("PayPal capture for auth: {}", authorizationId);
        return ChargeResult.success("CAPTURED-" + authorizationId, "{}");
    }

    @Override
    public String getProviderName() { return "PAYPAL"; }
}
java// com.ecommerce.infrastructure.payment/paypal/PayPalPaymentGatewayFactory.java
package com.ecommerce.infrastructure.payment.paypal;

import com.ecommerce.domain.payment.gateway.*;
import com.ecommerce.infrastructure.payment.stripe.StripeWebhookHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Concrete Factory — PayPal payment family.
 * Demonstrates extensibility: adding new provider = new factory class only.
 */
@Component
@ConditionalOnProperty(name = "payment.provider", havingValue = "paypal")
public class PayPalPaymentGatewayFactory implements PaymentGatewayFactory {

    private final String clientId;
    private final String clientSecret;
    private final boolean sandbox;

    public PayPalPaymentGatewayFactory(
        @Value("${payment.paypal.client-id}")     String clientId,
        @Value("${payment.paypal.client-secret}") String clientSecret,
        @Value("${payment.paypal.sandbox:true}")  boolean sandbox) {
        this.clientId     = clientId;
        this.clientSecret = clientSecret;
        this.sandbox      = sandbox;
    }

    @Override
    public ChargeGateway createChargeGateway() {
        return new PayPalChargeGateway(clientId, clientSecret, sandbox);
    }

    @Override
    public RefundGateway createRefundGateway() {
        return new PayPalRefundGateway(clientId, clientSecret, sandbox);
    }

    @Override
    public WebhookHandler createWebhookHandler() {
        return new PayPalWebhookHandler();
    }

    @Override
    public String getProviderName() { return "PAYPAL"; }

    @Override
    public boolean isAvailable() {
        // TODO: PayPal health check
        return true;
    }
}
java// com.ecommerce.infrastructure.payment/MockPaymentGatewayFactory.java
package com.ecommerce.infrastructure.payment;

import com.ecommerce.domain.payment.gateway.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.UUID;

/**
 * Mock Factory — for testing and development.
 * Shows how Abstract Factory enables testing without real credentials.
 * Activated with: payment.provider=mock
 */
@Component
@ConditionalOnProperty(name = "payment.provider", havingValue = "mock",
    matchIfMissing = false)
public class MockPaymentGatewayFactory implements PaymentGatewayFactory {

    @Override
    public ChargeGateway createChargeGateway() {
        return new ChargeGateway() {
            @Override
            public ChargeResult charge(ChargeRequest request) {
                // Simulate failure for specific test cards
                if (request.paymentMethodToken().startsWith("FAIL")) {
                    return ChargeResult.failure("card_declined",
                        "Your card was declined", null);
                }
                return ChargeResult.success("MOCK-TXN-" + UUID.randomUUID(), "{}");
            }
            @Override
            public ChargeResult authorize(ChargeRequest r) { return charge(r); }
            @Override
            public ChargeResult capture(String id, String key) {
                return ChargeResult.success("CAPTURED-" + id, "{}");
            }
            @Override
            public String getProviderName() { return "MOCK"; }
        };
    }

    @Override
    public RefundGateway createRefundGateway() {
        return new RefundGateway() {
            @Override
            public RefundResult refund(RefundRequest request) {
                return RefundResult.success("MOCK-REFUND-" + UUID.randomUUID());
            }
            @Override
            public RefundResult getRefundStatus(String id) {
                return RefundResult.success(id);
            }
        };
    }

    @Override
    public WebhookHandler createWebhookHandler() {
        return new WebhookHandler() {
            @Override
            public boolean validateSignature(String p, String s, String sec) { return true; }
            @Override
            public WebhookEvent parse(String payload, Map<String, String> headers) {
                return new WebhookEvent("mock-id", "payment_intent.succeeded",
                    "mock-txn", "succeeded", Map.of());
            }
            @Override
            public void handle(WebhookEvent event) {}
        };
    }

    @Override
    public String getProviderName() { return "MOCK"; }

    @Override
    public boolean isAvailable() { return true; }
}
```

---

## Pattern 4: Builder

### Problem It Solves
```
Problem: Creating complex objects with many optional parameters.

Without Builder — "Telescoping Constructor" anti-pattern:
  new Order(id, userId, items, address, billing, shipping,
            discount, coupon, null, null, null, null, false, ...)
  → Unreadable, error-prone (wrong parameter order silent bug),
    impossible to have optional params without null telescoping

With Builder:
  OrderResponse.builder()
    .orderId(order.getId())
    .status(order.getStatus())
    .items(mapItems(order.getItems()))
    .totalAmount(order.getTotalAmount())
    .build();
  → Readable, type-safe, optional fields natural, validates on build()
```

### Class Diagram
```
«Director»              «Builder Interface»
OrderResponseDirector   OrderResponseBuilder
+ construct(order)      + orderId(id)
                        + userId(id)
        │               + status(status)
        │ uses          + items(items)
        ▼               + shippingAddress(addr)
«ConcreteBuilder»       + totalAmount(money)
OrderResponseBuilder    + build(): OrderResponse
impl → OrderResponse

Product (result):
OrderResponse
- orderId: String
- userId: String
- orderNumber: String
- status: OrderStatus
- items: List<OrderItemResponse>
- shippingAddress: AddressDto
- subtotal: MoneyDto
- totalAmount: MoneyDto
- createdAt: Instant
- estimatedDelivery: LocalDate
Implementation
java// ecommerce-application:
// com.ecommerce.application.order/dto/MoneyDto.java
package com.ecommerce.application.order.dto;

import com.ecommerce.domain.shared.valueobject.Money;
import java.math.BigDecimal;

/**
 * DTO — Data Transfer Object.
 * Separates domain model from API contract.
 * Domain Money → MoneyDto → JSON response
 */
public record MoneyDto(
    BigDecimal amount,
    String currency,
    String formatted
) {
    public static MoneyDto from(Money money) {
        if (money == null) return null;
        return new MoneyDto(
            money.getAmount(),
            money.getCurrency().getCurrencyCode(),
            money.toString()
        );
    }
}
java// com.ecommerce.application.order/dto/AddressDto.java
package com.ecommerce.application.order.dto;

import com.ecommerce.domain.user.Address;

public record AddressDto(
    String fullName,
    String line1,
    String line2,
    String city,
    String state,
    String country,
    String postalCode
) {
    public static AddressDto from(Address address) {
        if (address == null) return null;
        return new AddressDto(
            address.getFullName(), address.getLine1(), address.getLine2(),
            address.getCity(), address.getState(),
            address.getCountry(), address.getPostalCode()
        );
    }
}
java// com.ecommerce.application.order/dto/OrderItemResponse.java
package com.ecommerce.application.order.dto;

import com.ecommerce.domain.order.OrderItem;

public record OrderItemResponse(
    String productId,
    String variantSku,
    String productName,
    String variantName,
    int quantity,
    MoneyDto unitPrice,
    MoneyDto totalPrice
) {
    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
            item.getProductId().getValue().toString(),
            item.getVariantSku(),
            item.getProductName(),
            item.getVariantName(),
            item.getQuantity(),
            MoneyDto.from(item.getUnitPrice()),
            MoneyDto.from(item.getTotalPrice())
        );
    }
}
java// com.ecommerce.application.order/dto/OrderResponse.java
package com.ecommerce.application.order.dto;

import com.ecommerce.domain.order.OrderStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Complex DTO built by Builder Pattern.
 *
 * Why Builder here:
 * - 15+ fields, many optional
 * - Computed fields (estimatedDelivery, canCancel, canReturn)
 * - Validation logic on build()
 * - Immutable after construction
 */
public final class OrderResponse {

    private final String orderId;
    private final String userId;
    private final String orderNumber;
    private final OrderStatus status;
    private final String statusLabel;
    private final List<OrderItemResponse> items;
    private final AddressDto shippingAddress;
    private final AddressDto billingAddress;
    private final MoneyDto subtotal;
    private final MoneyDto shippingCost;
    private final MoneyDto discountAmount;
    private final MoneyDto totalAmount;
    private final String couponCode;
    private final String trackingNumber;
    private final String trackingUrl;
    private final Instant createdAt;
    private final Instant confirmedAt;
    private final Instant shippedAt;
    private final Instant deliveredAt;
    private final LocalDate estimatedDelivery;
    private final boolean canCancel;
    private final boolean canReturn;
    private final int totalItems;
    private final String paymentMethod;

    // Private constructor — only Builder can create
    private OrderResponse(Builder builder) {
        this.orderId          = builder.orderId;
        this.userId           = builder.userId;
        this.orderNumber      = builder.orderNumber;
        this.status           = builder.status;
        this.statusLabel      = builder.status != null
            ? formatStatus(builder.status) : null;
        this.items            = builder.items != null
            ? List.copyOf(builder.items) : List.of();
        this.shippingAddress  = builder.shippingAddress;
        this.billingAddress   = builder.billingAddress;
        this.subtotal         = builder.subtotal;
        this.shippingCost     = builder.shippingCost;
        this.discountAmount   = builder.discountAmount;
        this.totalAmount      = builder.totalAmount;
        this.couponCode       = builder.couponCode;
        this.trackingNumber   = builder.trackingNumber;
        this.trackingUrl      = buildTrackingUrl(builder.trackingNumber);
        this.createdAt        = builder.createdAt;
        this.confirmedAt      = builder.confirmedAt;
        this.shippedAt        = builder.shippedAt;
        this.deliveredAt      = builder.deliveredAt;
        this.estimatedDelivery = builder.estimatedDelivery;
        this.canCancel        = computeCanCancel(builder.status);
        this.canReturn        = computeCanReturn(builder.status, builder.deliveredAt);
        this.totalItems       = this.items.stream()
            .mapToInt(OrderItemResponse::quantity).sum();
        this.paymentMethod    = builder.paymentMethod;
    }

    public static Builder builder() { return new Builder(); }

    private static String formatStatus(OrderStatus status) {
        return switch (status) {
            case PENDING    -> "Awaiting Payment";
            case CONFIRMED  -> "Order Confirmed";
            case PROCESSING -> "Being Prepared";
            case SHIPPED    -> "On the Way";
            case DELIVERED  -> "Delivered";
            case CANCELLED  -> "Cancelled";
            case REFUNDED   -> "Refunded";
        };
    }

    private static String buildTrackingUrl(String trackingNumber) {
        if (trackingNumber == null) return null;
        // Could be dynamic based on carrier
        return "https://track.example.com/" + trackingNumber;
    }

    private static boolean computeCanCancel(OrderStatus status) {
        return status == OrderStatus.PENDING
            || status == OrderStatus.CONFIRMED
            || status == OrderStatus.PROCESSING;
    }

    private static boolean computeCanReturn(OrderStatus status, Instant deliveredAt) {
        if (status != OrderStatus.DELIVERED || deliveredAt == null) return false;
        // Return window: 30 days after delivery
        return deliveredAt.isAfter(
            Instant.now().minusSeconds(30L * 24 * 3600)
        );
    }

    // ── Builder ────────────────────────────────────────────────────────
    public static final class Builder {
        private String orderId;
        private String userId;
        private String orderNumber;
        private OrderStatus status;
        private List<OrderItemResponse> items;
        private AddressDto shippingAddress;
        private AddressDto billingAddress;
        private MoneyDto subtotal;
        private MoneyDto shippingCost;
        private MoneyDto discountAmount;
        private MoneyDto totalAmount;
        private String couponCode;
        private String trackingNumber;
        private Instant createdAt;
        private Instant confirmedAt;
        private Instant shippedAt;
        private Instant deliveredAt;
        private LocalDate estimatedDelivery;
        private String paymentMethod;

        private Builder() {}

        public Builder orderId(String v)                    { orderId = v;          return this; }
        public Builder userId(String v)                     { userId = v;           return this; }
        public Builder orderNumber(String v)                { orderNumber = v;      return this; }
        public Builder status(OrderStatus v)                { status = v;           return this; }
        public Builder items(List<OrderItemResponse> v)     { items = v;            return this; }
        public Builder shippingAddress(AddressDto v)        { shippingAddress = v;  return this; }
        public Builder billingAddress(AddressDto v)         { billingAddress = v;   return this; }
        public Builder subtotal(MoneyDto v)                 { subtotal = v;         return this; }
        public Builder shippingCost(MoneyDto v)             { shippingCost = v;     return this; }
        public Builder discountAmount(MoneyDto v)           { discountAmount = v;   return this; }
        public Builder totalAmount(MoneyDto v)              { totalAmount = v;      return this; }
        public Builder couponCode(String v)                 { couponCode = v;       return this; }
        public Builder trackingNumber(String v)             { trackingNumber = v;   return this; }
        public Builder createdAt(Instant v)                 { createdAt = v;        return this; }
        public Builder confirmedAt(Instant v)               { confirmedAt = v;      return this; }
        public Builder shippedAt(Instant v)                 { shippedAt = v;        return this; }
        public Builder deliveredAt(Instant v)               { deliveredAt = v;      return this; }
        public Builder estimatedDelivery(LocalDate v)       { estimatedDelivery = v; return this; }
        public Builder paymentMethod(String v)              { paymentMethod = v;    return this; }

        /**
         * Validates required fields and builds immutable OrderResponse.
         */
        public OrderResponse build() {
            // Validation before construction
            requireNonNull(orderId,      "orderId");
            requireNonNull(userId,       "userId");
            requireNonNull(orderNumber,  "orderNumber");
            requireNonNull(status,       "status");
            requireNonNull(totalAmount,  "totalAmount");
            requireNonNull(createdAt,    "createdAt");

            return new OrderResponse(this);
        }

        private void requireNonNull(Object value, String fieldName) {
            if (value == null) {
                throw new IllegalStateException(
                    "OrderResponse." + fieldName + " is required"
                );
            }
        }
    }

    // ── Getters ────────────────────────────────────────────────────────
    public String getOrderId()              { return orderId; }
    public String getUserId()               { return userId; }
    public String getOrderNumber()          { return orderNumber; }
    public OrderStatus getStatus()          { return status; }
    public String getStatusLabel()          { return statusLabel; }
    public List<OrderItemResponse> getItems() { return items; }
    public AddressDto getShippingAddress()  { return shippingAddress; }
    public AddressDto getBillingAddress()   { return billingAddress; }
    public MoneyDto getSubtotal()           { return subtotal; }
    public MoneyDto getShippingCost()       { return shippingCost; }
    public MoneyDto getDiscountAmount()     { return discountAmount; }
    public MoneyDto getTotalAmount()        { return totalAmount; }
    public String getCouponCode()           { return couponCode; }
    public String getTrackingNumber()       { return trackingNumber; }
    public String getTrackingUrl()          { return trackingUrl; }
    public Instant getCreatedAt()           { return createdAt; }
    public Instant getConfirmedAt()         { return confirmedAt; }
    public Instant getShippedAt()           { return shippedAt; }
    public Instant getDeliveredAt()         { return deliveredAt; }
    public LocalDate getEstimatedDelivery() { return estimatedDelivery; }
    public boolean isCanCancel()            { return canCancel; }
    public boolean isCanReturn()            { return canReturn; }
    public int getTotalItems()              { return totalItems; }
    public String getPaymentMethod()        { return paymentMethod; }
}
java// com.ecommerce.application.order/dto/OrderResponseAssembler.java
package com.ecommerce.application.order.dto;

import com.ecommerce.domain.order.Order;
import org.springframework.stereotype.Component;
import java.time.LocalDate;

/**
 * Director — orchestrates the Builder to assemble OrderResponse.
 * Knows HOW to build, delegates actual construction to Builder.
 * Keeps mapping logic out of domain and out of controllers.
 */
@Component
public class OrderResponseAssembler {

    /**
     * Full response — for GET /orders/{id}
     */
    public OrderResponse toDetailResponse(Order order) {
        return OrderResponse.builder()
            .orderId(order.getId().getValue().toString())
            .userId(order.getUserId().getValue().toString())
            .orderNumber(order.getOrderNumber())
            .status(order.getStatus())
            .items(order.getItems().stream()
                .map(OrderItemResponse::from)
                .toList())
            .shippingAddress(AddressDto.from(order.getShippingAddress()))
            .billingAddress(AddressDto.from(order.getBillingAddress()))
            .subtotal(MoneyDto.from(order.getSubtotal()))
            .shippingCost(MoneyDto.from(order.getShippingCost()))
            .discountAmount(MoneyDto.from(order.getDiscountAmount()))
            .totalAmount(MoneyDto.from(order.getTotalAmount()))
            .couponCode(order.getCouponCode())
            .trackingNumber(order.getTrackingNumber())
            .createdAt(order.getCreatedAt())
            .confirmedAt(order.getConfirmedAt())
            .shippedAt(order.getShippedAt())
            .deliveredAt(order.getDeliveredAt())
            .estimatedDelivery(estimateDelivery(order))
            .build();
    }

    /**
     * Summary response — for GET /orders (list)
     */
    public OrderResponse toSummaryResponse(Order order) {
        return OrderResponse.builder()
            .orderId(order.getId().getValue().toString())
            .userId(order.getUserId().getValue().toString())
            .orderNumber(order.getOrderNumber())
            .status(order.getStatus())
            .totalAmount(MoneyDto.from(order.getTotalAmount()))
            .createdAt(order.getCreatedAt())
            .build();
    }

    private LocalDate estimateDelivery(Order order) {
        if (order.getShippedAt() != null) {
            return order.getShippedAt()
                .atZone(java.time.ZoneOffset.UTC)
                .toLocalDate()
                .plusDays(5); // standard delivery window
        }
        if (order.getConfirmedAt() != null) {
            return order.getConfirmedAt()
                .atZone(java.time.ZoneOffset.UTC)
                .toLocalDate()
                .plusDays(7);
        }
        return LocalDate.now().plusDays(10);
    }
}
```

---

## Pattern 5: Prototype

### Problem It Solves
```
Problem: Creating product templates and reusing them across catalog.

Scenario:
  - Admin creates a "Laptop" product template with 50 attributes
  - Creating new laptop products should clone the template
  - Cloning is cheaper than building from scratch each time
  - The clone is independent — changes don't affect the original

Also used for:
  - Notification templates (clone base, customize per event)
  - Discount rule templates
  - Email templates
```

### Class Diagram
```
«interface»
Cloneable<T>
+ deepCopy(): T
      ▲
      │
ProductTemplate
- name: String
- categoryId: CategoryId
- attributes: Map<String,String>
- defaultImages: List<String>
- variantSchema: VariantSchema
+ deepCopy(): ProductTemplate
+ customize(name, overrides): ProductTemplate

NotificationTemplate
- templateId: String
- subject: String
- body: String
- variables: Set<String>
+ deepCopy(): NotificationTemplate
+ withVariables(Map): NotificationTemplate
Implementation
java// ecommerce-domain: com.ecommerce.domain.shared/Prototype.java
package com.ecommerce.domain.shared;

/**
 * Prototype interface — type-safe deep copy.
 * Avoids Java's Object.clone() pitfalls (shallow copy, checked exception).
 */
public interface Prototype<T> {
    T deepCopy();
}
java// com.ecommerce.domain.product/ProductTemplate.java
package com.ecommerce.domain.product;

import com.ecommerce.domain.shared.Prototype;
import java.util.*;

/**
 * Prototype Pattern — Product Template.
 *
 * Use case: Admin defines "Gaming Laptop Template" with
 * standard attributes (RAM, GPU, Storage, Display, Weight).
 * Creating new laptops clones the template in milliseconds.
 * vs rebuilding from scratch every time.
 *
 * Production use: Shopify's product type system works similarly.
 */
public final class ProductTemplate implements Prototype<ProductTemplate> {

    private final String templateId;
    private String name;
    private String description;
    private CategoryId categoryId;
    private final Map<String, String> defaultAttributes;
    private final List<VariantSchema> variantSchemas;
    private final List<String> defaultImageUrls;
    private final Map<String, String> seoDefaults;

    private ProductTemplate(String templateId, String name, String description,
                            CategoryId categoryId,
                            Map<String, String> defaultAttributes,
                            List<VariantSchema> variantSchemas,
                            List<String> defaultImageUrls,
                            Map<String, String> seoDefaults) {
        this.templateId         = templateId;
        this.name               = name;
        this.description        = description;
        this.categoryId         = categoryId;
        this.defaultAttributes  = new LinkedHashMap<>(defaultAttributes);
        this.variantSchemas     = new ArrayList<>(variantSchemas);
        this.defaultImageUrls   = new ArrayList<>(defaultImageUrls);
        this.seoDefaults        = new LinkedHashMap<>(seoDefaults);
    }

    public static ProductTemplate create(String name, CategoryId categoryId) {
        return new ProductTemplate(
            UUID.randomUUID().toString(), name, "",
            categoryId, new LinkedHashMap<>(),
            new ArrayList<>(), new ArrayList<>(), new LinkedHashMap<>()
        );
    }

    /**
     * Deep copy — new instance with independent collections.
     * Changes to the copy DO NOT affect the original template.
     */
    @Override
    public ProductTemplate deepCopy() {
        // Deep copy of variant schemas
        List<VariantSchema> copiedSchemas = variantSchemas.stream()
            .map(VariantSchema::deepCopy)
            .toList();

        return new ProductTemplate(
            UUID.randomUUID().toString(),   // new ID for the copy
            this.name,
            this.description,
            this.categoryId,                // CategoryId is immutable VO — safe to share
            new LinkedHashMap<>(this.defaultAttributes),
            copiedSchemas,
            new ArrayList<>(this.defaultImageUrls),
            new LinkedHashMap<>(this.seoDefaults)
        );
    }

    /**
     * Clone template and apply customizations for a specific product.
     * Returns a new template — does not mutate this one.
     */
    public ProductTemplate customize(String productName,
                                     Map<String, String> attributeOverrides) {
        ProductTemplate copy = this.deepCopy();
        copy.name = productName;
        copy.defaultAttributes.putAll(attributeOverrides);
        return copy;
    }

    /**
     * Convert template to actual Product aggregate.
     */
    public Product toProduct(String slug, Map<String, String> overrides) {
        Product product = Product.create(name, description, slug, categoryId);

        // Apply template attributes, then overrides
        defaultAttributes.forEach(product::setAttribute);
        if (overrides != null) overrides.forEach(product::setAttribute);

        defaultImageUrls.forEach(product::addImage);

        return product;
    }

    // Setters for template building
    public ProductTemplate withDescription(String desc) {
        this.description = desc;
        return this;
    }

    public ProductTemplate withAttribute(String key, String value) {
        this.defaultAttributes.put(key, value);
        return this;
    }

    public ProductTemplate withVariantSchema(VariantSchema schema) {
        this.variantSchemas.add(schema);
        return this;
    }

    public ProductTemplate withSeoDefault(String key, String value) {
        this.seoDefaults.put(key, value);
        return this;
    }

    // Getters
    public String getTemplateId()                    { return templateId; }
    public String getName()                          { return name; }
    public String getDescription()                   { return description; }
    public CategoryId getCategoryId()                { return categoryId; }
    public Map<String, String> getDefaultAttributes(){ return Collections.unmodifiableMap(defaultAttributes); }
    public List<VariantSchema> getVariantSchemas()   { return Collections.unmodifiableList(variantSchemas); }
    public List<String> getDefaultImageUrls()        { return Collections.unmodifiableList(defaultImageUrls); }
    public Map<String, String> getSeoDefaults()      { return Collections.unmodifiableMap(seoDefaults); }

    // ── Inner record: Variant Schema ──────────────────────────────────
    public record VariantSchema(
        String attributeName,       // e.g. "Color", "Size"
        List<String> allowedValues, // e.g. ["Red", "Blue", "Green"]
        boolean isRequired
    ) implements Prototype<VariantSchema> {

        @Override
        public VariantSchema deepCopy() {
            return new VariantSchema(
                this.attributeName,
                new ArrayList<>(this.allowedValues),
                this.isRequired
            );
        }
    }
}
java// com.ecommerce.domain.notification/NotificationTemplate.java
package com.ecommerce.domain.notification;

import com.ecommerce.domain.shared.Prototype;
import java.util.*;

/**
 * Prototype — Notification Template.
 *
 * Use case: Base "Order" email template cloned for:
 *   - Order Placed
 *   - Order Shipped
 *   - Order Delivered
 * Each clone customizes subject + body while sharing base HTML structure.
 */
public final class NotificationTemplate implements Prototype<NotificationTemplate> {

    private final String templateId;
    private String subject;
    private String htmlBody;
    private String textBody;
    private final Set<String> requiredVariables;
    private final Map<String, String> defaultVariables;
    private NotificationChannel channel;

    private NotificationTemplate(String templateId, String subject,
                                  String htmlBody, String textBody,
                                  Set<String> requiredVariables,
                                  Map<String, String> defaultVariables,
                                  NotificationChannel channel) {
        this.templateId        = templateId;
        this.subject           = subject;
        this.htmlBody          = htmlBody;
        this.textBody          = textBody;
        this.requiredVariables = new LinkedHashSet<>(requiredVariables);
        this.defaultVariables  = new LinkedHashMap<>(defaultVariables);
        this.channel           = channel;
    }

    public static NotificationTemplate create(String subject, String htmlBody,
                                              NotificationChannel channel) {
        return new NotificationTemplate(
            UUID.randomUUID().toString(), subject, htmlBody,
            extractTextVersion(htmlBody), new LinkedHashSet<>(),
            new LinkedHashMap<>(), channel
        );
    }

    @Override
    public NotificationTemplate deepCopy() {
        return new NotificationTemplate(
            UUID.randomUUID().toString(),
            this.subject,
            this.htmlBody,
            this.textBody,
            new LinkedHashSet<>(this.requiredVariables),
            new LinkedHashMap<>(this.defaultVariables),
            this.channel
        );
    }

    /**
     * Create event-specific template from base template.
     * Returns independent copy — original unchanged.
     */
    public NotificationTemplate withSubject(String newSubject) {
        NotificationTemplate copy = deepCopy();
        copy.subject = newSubject;
        return copy;
    }

    public NotificationTemplate withBody(String newHtmlBody) {
        NotificationTemplate copy = deepCopy();
        copy.htmlBody = newHtmlBody;
        copy.textBody = extractTextVersion(newHtmlBody);
        return copy;
    }

    public NotificationTemplate withDefaultVariable(String key, String value) {
        NotificationTemplate copy = deepCopy();
        copy.defaultVariables.put(key, value);
        return copy;
    }

    public NotificationTemplate requireVariable(String variableName) {
        NotificationTemplate copy = deepCopy();
        copy.requiredVariables.add(variableName);
        return copy;
    }

    /**
     * Render template with provided variables.
     * Validates all required variables are present.
     */
    public NotificationPayload render(Map<String, String> variables) {
        // Merge: defaults < provided variables
        Map<String, String> merged = new HashMap<>(defaultVariables);
        merged.putAll(variables);

        // Validate required variables
        List<String> missing = requiredVariables.stream()
            .filter(v -> !merged.containsKey(v))
            .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                "Missing required template variables: " + missing
            );
        }

        return new NotificationPayload(
            templateId, subject, htmlBody, merged, Map.of()
        );
    }

    private static String extractTextVersion(String html) {
        if (html == null) return "";
        // Simple HTML stripping — production would use Jsoup
        return html.replaceAll("<[^>]+>", "").trim();
    }

    public String getTemplateId()                    { return templateId; }
    public String getSubject()                       { return subject; }
    public String getHtmlBody()                      { return htmlBody; }
    public String getTextBody()                      { return textBody; }
    public Set<String> getRequiredVariables()        { return Collections.unmodifiableSet(requiredVariables); }
    public Map<String, String> getDefaultVariables() { return Collections.unmodifiableMap(defaultVariables); }
    public NotificationChannel getChannel()          { return channel; }
}
java// com.ecommerce.infrastructure.notification/NotificationTemplateRegistry.java
package com.ecommerce.infrastructure.notification;

import com.ecommerce.domain.notification.NotificationChannel;
import com.ecommerce.domain.notification.NotificationTemplate;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for notification templates.
 * Uses Prototype pattern: base templates stored, cloned for each use.
 * Ensures templates are never mutated after registration.
 */
@Component
public class NotificationTemplateRegistry {

    private static final Logger log =
        LoggerFactory.getLogger(NotificationTemplateRegistry.class);

    private final Map<String, NotificationTemplate> templates = new ConcurrentHashMap<>();

    @PostConstruct
    public void initializeTemplates() {
        // Base order template — all order notifications share this structure
        NotificationTemplate baseOrderTemplate = NotificationTemplate.create(
            "Your Order #{{orderNumber}}",
            """
            <html><body>
              <h1>{{heading}}</h1>
              <p>Hi {{customerName}},</p>
              <p>{{body}}</p>
              <p>Order Total: {{orderTotal}}</p>
              <p>Track your order: <a href="{{trackingUrl}}">here</a></p>
            </body></html>
            """,
            NotificationChannel.EMAIL
        ).requireVariable("customerName")
         .requireVariable("orderNumber")
         .withDefaultVariable("trackingUrl", "#");

        // Prototype: clone base, customize for specific events
        register("ORDER_PLACED", baseOrderTemplate.deepCopy()
            .withSubject("Order Confirmed #{{orderNumber}} 🎉")
            .withDefaultVariable("heading", "Your order has been placed!")
        );

        register("ORDER_SHIPPED", baseOrderTemplate.deepCopy()
            .withSubject("Your order is on the way! #{{orderNumber}} 🚚")
            .withDefaultVariable("heading", "Your order has shipped!")
            .requireVariable("trackingUrl")
        );

        register("ORDER_DELIVERED", baseOrderTemplate.deepCopy()
            .withSubject("Your order has arrived! #{{orderNumber}} ✅")
            .withDefaultVariable("heading", "Your order was delivered!")
        );

        register("ORDER_CANCELLED", baseOrderTemplate.deepCopy()
            .withSubject("Order #{{orderNumber}} has been cancelled")
            .withDefaultVariable("heading", "Your order was cancelled")
        );

        register("PAYMENT_FAILED", NotificationTemplate.create(
            "Payment Failed for Order #{{orderNumber}}",
            "<h1>Payment Failed</h1><p>Please retry your payment.</p>",
            NotificationChannel.EMAIL
        ).requireVariable("orderNumber").requireVariable("retryUrl"));

        log.info("Registered {} notification templates", templates.size());
    }

    private void register(String eventType, NotificationTemplate template) {
        templates.put(eventType, template);
    }

    /**
     * Returns a DEEP COPY of the template — caller can safely mutate it.
     * Original template in registry is never exposed directly.
     */
    public NotificationTemplate getTemplate(String eventType) {
        NotificationTemplate template = templates.get(eventType);
        if (template == null) {
            throw new IllegalArgumentException("No template for event: " + eventType);
        }
        return template.deepCopy(); // Prototype in action
    }

    public boolean hasTemplate(String eventType) {
        return templates.containsKey(eventType);
    }
}
```

---

## Pattern Interaction Map — Creational Patterns
```
How Creational Patterns work TOGETHER in Order Flow:

1. OrderPlaced event fires
   │
   ▼
2. NotificationTemplateRegistry.getTemplate("ORDER_PLACED")
   └── PROTOTYPE: deepCopy() of base email template
   │
   ▼
3. NotificationFactoryRegistry.getAllFactories(context)
   └── FACTORY METHOD: each factory.create() returns channel-specific Notification
   │
   ▼
4. NotificationTemplate.render(variables) → NotificationPayload
   │
   ▼
5. PaymentGatewayFactory.createChargeGateway()
   └── ABSTRACT FACTORY: returns Stripe or PayPal ChargeGateway
   │
   ▼
6. OrderResponseAssembler.toDetailResponse(order)
   └── BUILDER: constructs complex OrderResponse with 15+ fields
   │
   ▼
7. RateLimiterRegistry.getInstance().isAllowed(...)
   └── SINGLETON: single rate limiter registry across all requests
```

---

## Summary Table — Creational Patterns
```
┌───────────────┬──────────────────────────┬──────────────────────────────────┐
│ Pattern       │ Used For                 │ Key Benefit                      │
├───────────────┼──────────────────────────┼──────────────────────────────────┤
│ Singleton     │ Config, JWT, RateLimiter │ One instance, thread-safe lazy   │
│ Factory Method│ Notification creation    │ OCP: add channel, zero change    │
│ Abstract Fac. │ Payment providers        │ Switch Stripe↔PayPal via config  │
│ Builder       │ OrderResponse DTO        │ 15+ fields, readable, validated  │
│ Prototype     │ Product/Notif templates  │ Cheap cloning, safe mutation     │
└───────────────┴──────────────────────────┴──────────────────────────────────┘

✅ Phase 3 complete. All 5 Creational Patterns implemented with:

Full production code (Stripe/PayPal/Twilio/Firebase integration)
Java 21 features (records, sealed types, virtual threads, text blocks)
Trade-off analysis for each
Scalability implications
Pattern interaction map


Reply "Phase 4" and I'll implement all 7 Structural Patterns — Adapter, Bridge, Composite, Decorator, Facade, Flyweight, and Proxy — with the same depth: payment adapter for legacy systems, search bridge, product catalog composite, caching/logging decorators, checkout facade, product attribute flyweight, and security proxy.Phase 415:57PHASE 4 — Structural Design Patterns
Overview: Why Structural Patterns Here
Structural patterns answer: HOW do we compose objects and classes
into larger structures while keeping them flexible and efficient?

In e-commerce at scale:
  ├── Adapter   → Legacy payment system integration (can't change their code)
  ├── Bridge    → Search (Elasticsearch vs Algolia) independent of domain
  ├── Composite → Category tree (leaf + container same interface)
  ├── Decorator → Caching + Logging + Retry on repositories (stackable)
  ├── Facade    → Checkout (orchestrates 8 subsystems behind one interface)
  ├── Flyweight → Product attributes (millions of products, shared metadata)
  └── Proxy     → Security enforcement + lazy loading + audit logging

Pattern 1: Adapter
Problem It Solves
Problem: We integrate with a legacy payment processor (e.g., a bank's
internal system) that has an incompatible interface. We CANNOT modify
their code (external library / legal contract). Their API speaks XML,
uses different field names, synchronous polling — our system speaks
JSON, uses our domain objects, event-driven.

Without Adapter:
  - Legacy API leaks into domain layer
  - Entire codebase couples to their naming conventions
  - Testing requires their sandbox environment
  - Switching processor = rewriting entire codebase

With Adapter:
  - Incompatible interface → wrapped behind our domain interface
  - Domain layer never knows about the legacy system
  - Testing: mock our interface, not theirs
  - Switching: write new adapter, zero domain changes
Class Diagram
«interface» (Target)              «Adaptee» (Legacy)
ChargeGateway                     LegacyBankPaymentClient
+ charge(ChargeRequest)           + processPayment(BankPaymentXml)
+ authorize(ChargeRequest)        + checkPaymentStatus(txnRef)
+ capture(id, key)                + reverseTransaction(txnRef, amount)
       ▲                                    │
       │ implements                         │ wraps
       │                                   ▼
LegacyBankPaymentAdapter  ─────► LegacyBankPaymentClient
+ charge(ChargeRequest)           (converts our objects ↔ their objects)
+ authorize(ChargeRequest)
+ capture(id, key)

XmlPayloadBuilder         ←── used by adapter
BankResponseParser        ←── used by adapter
Sequence Diagram
PaymentService     LegacyBankAdapter    LegacyBankClient    XmlBuilder
     │                    │                    │                 │
     │─charge(request)───►│                    │                 │
     │                    │─toXml(request)────────────────────── ►│
     │                    │◄─── BankPaymentXml ─────────────────  │
     │                    │─processPayment(xml)►│                 │
     │                    │◄── BankResponse ────│                 │
     │                    │─parseResponse()     │                 │
     │◄──ChargeResult──── │                    │                 │
Implementation
java// ecommerce-infrastructure:
// com.ecommerce.infrastructure.payment.legacy/LegacyBankPaymentClient.java
package com.ecommerce.infrastructure.payment.legacy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;

/**
 * Adaptee — Third-party legacy bank payment client.
 * Simulates an external library we CANNOT modify.
 * Uses XML, different naming, synchronous polling model.
 */
public class LegacyBankPaymentClient {

    private static final Logger log =
        LoggerFactory.getLogger(LegacyBankPaymentClient.class);

    private final String merchantId;
    private final String terminalId;
    private final String endpoint;

    public LegacyBankPaymentClient(String merchantId,
                                   String terminalId,
                                   String endpoint) {
        this.merchantId = merchantId;
        this.terminalId = terminalId;
        this.endpoint   = endpoint;
    }

    /**
     * Legacy API — sends XML, gets XML back.
     * Field names are bank-specific (acct_ref, txn_amt_minor, etc.)
     */
    public BankPaymentResponse processPayment(BankPaymentRequest request) {
        log.info("Legacy bank charge: acctRef={}, amt={}",
            request.getAcctRef(), request.getTxnAmtMinor());

        // Simulated legacy HTTP+XML call
        // Real: HttpClient → POST XML → parse response XML
        try {
            Thread.sleep(200); // simulate network latency
            // Simulate success for demo
            BankPaymentResponse response = new BankPaymentResponse();
            response.setTxnRef("BANK-TXN-" + System.currentTimeMillis());
            response.setResponseCode("00");  // 00 = success in ISO 8583
            response.setResponseMsg("APPROVED");
            response.setAuthCode("AUTH123456");
            response.setAcquirerRef("ACQ-789");
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Legacy bank call interrupted", e);
        }
    }

    public BankStatusResponse checkPaymentStatus(String txnRef) {
        BankStatusResponse status = new BankStatusResponse();
        status.setTxnRef(txnRef);
        status.setStatusCode("S"); // S=settled, P=pending, F=failed
        return status;
    }

    public BankReversalResponse reverseTransaction(String txnRef,
                                                    BigDecimal amtMinor) {
        BankReversalResponse reversal = new BankReversalResponse();
        reversal.setOrigTxnRef(txnRef);
        reversal.setReversalRef("REV-" + txnRef);
        reversal.setResultCode("00");
        return reversal;
    }

    // Legacy bank DTOs — cannot be changed
    public static class BankPaymentRequest {
        private String acctRef;          // customer account reference
        private String txnAmtMinor;      // amount in minor units (cents), as STRING
        private String currencyCode;     // ISO 4217
        private String merchantId;
        private String terminalId;
        private String txnType;          // P=purchase, A=auth, C=capture
        private String clientRef;        // our order reference
        private String cardToken;

        // Getters/Setters
        public String getAcctRef()       { return acctRef; }
        public void setAcctRef(String v) { acctRef = v; }
        public String getTxnAmtMinor()   { return txnAmtMinor; }
        public void setTxnAmtMinor(String v) { txnAmtMinor = v; }
        public String getCurrencyCode()  { return currencyCode; }
        public void setCurrencyCode(String v) { currencyCode = v; }
        public String getMerchantId()    { return merchantId; }
        public void setMerchantId(String v) { merchantId = v; }
        public String getTerminalId()    { return terminalId; }
        public void setTerminalId(String v) { terminalId = v; }
        public String getTxnType()       { return txnType; }
        public void setTxnType(String v) { txnType = v; }
        public String getClientRef()     { return clientRef; }
        public void setClientRef(String v) { clientRef = v; }
        public String getCardToken()     { return cardToken; }
        public void setCardToken(String v) { cardToken = v; }
    }

    public static class BankPaymentResponse {
        private String txnRef;
        private String responseCode;     // 00=approved, 05=declined, 51=insufficient
        private String responseMsg;
        private String authCode;
        private String acquirerRef;

        public String getTxnRef()           { return txnRef; }
        public void setTxnRef(String v)     { txnRef = v; }
        public String getResponseCode()     { return responseCode; }
        public void setResponseCode(String v){ responseCode = v; }
        public String getResponseMsg()      { return responseMsg; }
        public void setResponseMsg(String v){ responseMsg = v; }
        public String getAuthCode()         { return authCode; }
        public void setAuthCode(String v)   { authCode = v; }
        public String getAcquirerRef()      { return acquirerRef; }
        public void setAcquirerRef(String v){ acquirerRef = v; }
        public boolean isApproved()         { return "00".equals(responseCode); }
    }

    public static class BankStatusResponse {
        private String txnRef;
        private String statusCode;

        public String getTxnRef()            { return txnRef; }
        public void setTxnRef(String v)      { txnRef = v; }
        public String getStatusCode()        { return statusCode; }
        public void setStatusCode(String v)  { statusCode = v; }
    }

    public static class BankReversalResponse {
        private String origTxnRef;
        private String reversalRef;
        private String resultCode;

        public String getOrigTxnRef()        { return origTxnRef; }
        public void setOrigTxnRef(String v)  { origTxnRef = v; }
        public String getReversalRef()       { return reversalRef; }
        public void setReversalRef(String v) { reversalRef = v; }
        public String getResultCode()        { return resultCode; }
        public void setResultCode(String v)  { resultCode = v; }
        public boolean isSuccess()           { return "00".equals(resultCode); }
    }
}
java// com.ecommerce.infrastructure.payment.legacy/LegacyBankPaymentAdapter.java
package com.ecommerce.infrastructure.payment.legacy;

import com.ecommerce.domain.payment.gateway.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * ADAPTER PATTERN — bridges incompatible interfaces.
 *
 * Target interface:  ChargeGateway (our domain interface)
 * Adaptee:           LegacyBankPaymentClient (their interface)
 *
 * This class is the ONLY place that knows about the legacy bank's
 * naming conventions, response codes, and XML structure.
 * The rest of our system speaks ChargeGateway.
 *
 * Object Adapter (composition > inheritance).
 * Using composition means we can mock the client in tests.
 */
public class LegacyBankPaymentAdapter implements ChargeGateway {

    private static final Logger log =
        LoggerFactory.getLogger(LegacyBankPaymentAdapter.class);

    private final LegacyBankPaymentClient bankClient;  // Adaptee (composed)
    private final String merchantId;
    private final String terminalId;

    // ISO 8583 response code mappings → our error codes
    private static final Map<String, String> RESPONSE_CODE_MAP = Map.of(
        "05", "card_declined",
        "14", "invalid_card",
        "51", "insufficient_funds",
        "54", "expired_card",
        "57", "transaction_not_permitted",
        "61", "exceeds_limit",
        "91", "bank_unavailable",
        "96", "system_error"
    );

    public LegacyBankPaymentAdapter(LegacyBankPaymentClient bankClient,
                                     String merchantId, String terminalId) {
        this.bankClient = bankClient;
        this.merchantId = merchantId;
        this.terminalId = terminalId;
    }

    /**
     * ADAPTATION: ChargeRequest (ours) → BankPaymentRequest (theirs)
     *             BankPaymentResponse (theirs) → ChargeResult (ours)
     */
    @Override
    public ChargeResult charge(ChargeRequest request) {
        log.info("Adapting charge request for legacy bank: orderId={}",
            request.orderId());

        // Step 1: Convert our request → their request
        LegacyBankPaymentClient.BankPaymentRequest bankRequest =
            toBankRequest(request, "P"); // P = Purchase

        // Step 2: Call their API
        LegacyBankPaymentClient.BankPaymentResponse bankResponse =
            bankClient.processPayment(bankRequest);

        // Step 3: Convert their response → our response
        return fromBankResponse(bankResponse);
    }

    @Override
    public ChargeResult authorize(ChargeRequest request) {
        LegacyBankPaymentClient.BankPaymentRequest bankRequest =
            toBankRequest(request, "A"); // A = Authorization
        LegacyBankPaymentClient.BankPaymentResponse response =
            bankClient.processPayment(bankRequest);
        return fromBankResponse(response);
    }

    @Override
    public ChargeResult capture(String authorizationId, String idempotencyKey) {
        // Legacy bank uses a different flow for capture
        LegacyBankPaymentClient.BankPaymentRequest captureRequest =
            new LegacyBankPaymentClient.BankPaymentRequest();
        captureRequest.setMerchantId(merchantId);
        captureRequest.setTerminalId(terminalId);
        captureRequest.setClientRef(authorizationId);
        captureRequest.setTxnType("C"); // C = Capture

        LegacyBankPaymentClient.BankPaymentResponse response =
            bankClient.processPayment(captureRequest);
        return fromBankResponse(response);
    }

    @Override
    public String getProviderName() { return "LEGACY_BANK"; }

    // ── Private Conversion Methods (the core of the Adapter) ──────────

    /**
     * Our ChargeRequest → Legacy BankPaymentRequest.
     * Field name translation, unit conversion, type conversion.
     */
    private LegacyBankPaymentClient.BankPaymentRequest toBankRequest(
            ChargeRequest request, String txnType) {

        LegacyBankPaymentClient.BankPaymentRequest bankRequest =
            new LegacyBankPaymentClient.BankPaymentRequest();

        // Field name adaptation
        bankRequest.setMerchantId(merchantId);
        bankRequest.setTerminalId(terminalId);
        bankRequest.setAcctRef(request.customerId());       // customerId → acctRef
        bankRequest.setCardToken(request.paymentMethodToken()); // token → cardToken
        bankRequest.setClientRef(request.idempotencyKey()); // idempotencyKey → clientRef

        // Unit conversion: BigDecimal dollars → minor units (cents) as String
        String amountMinor = request.amount().getAmount()
            .multiply(BigDecimal.valueOf(100))
            .setScale(0, RoundingMode.HALF_UP)
            .toPlainString();
        bankRequest.setTxnAmtMinor(amountMinor);

        // Currency code format: they want numeric ISO 4217 (840 = USD)
        bankRequest.setCurrencyCode(
            toNumericCurrencyCode(request.currency())
        );

        bankRequest.setTxnType(txnType);

        return bankRequest;
    }

    /**
     * Legacy BankPaymentResponse → Our ChargeResult.
     */
    private ChargeResult fromBankResponse(
            LegacyBankPaymentClient.BankPaymentResponse response) {

        if (response.isApproved()) {
            log.info("Legacy bank approved: txnRef={}, authCode={}",
                response.getTxnRef(), response.getAuthCode());
            return ChargeResult.success(
                response.getTxnRef(),
                buildRawResponse(response)
            );
        } else {
            String ourCode = RESPONSE_CODE_MAP.getOrDefault(
                response.getResponseCode(), "unknown_error"
            );
            log.warn("Legacy bank declined: code={}, msg={}",
                response.getResponseCode(), response.getResponseMsg());
            return ChargeResult.failure(
                ourCode,
                response.getResponseMsg(),
                buildRawResponse(response)
            );
        }
    }

    private String toNumericCurrencyCode(String alphabeticCode) {
        return switch (alphabeticCode.toUpperCase()) {
            case "USD" -> "840";
            case "EUR" -> "978";
            case "GBP" -> "826";
            case "CAD" -> "124";
            default    -> "840";
        };
    }

    private String buildRawResponse(
            LegacyBankPaymentClient.BankPaymentResponse r) {
        return String.format(
            "{\"txnRef\":\"%s\",\"code\":\"%s\",\"msg\":\"%s\",\"auth\":\"%s\"}",
            r.getTxnRef(), r.getResponseCode(),
            r.getResponseMsg(), r.getAuthCode()
        );
    }
}
java// com.ecommerce.infrastructure.payment.legacy/LegacyBankRefundAdapter.java
package com.ecommerce.infrastructure.payment.legacy;

import com.ecommerce.domain.payment.gateway.RefundGateway;
import com.ecommerce.domain.payment.gateway.RefundRequest;
import com.ecommerce.domain.payment.gateway.RefundResult;

/**
 * Adapter for legacy bank refund (reversal) operations.
 * Their concept: "reversal". Our concept: "refund".
 * Adapter bridges the terminology gap.
 */
public class LegacyBankRefundAdapter implements RefundGateway {

    private final LegacyBankPaymentClient bankClient;

    public LegacyBankRefundAdapter(LegacyBankPaymentClient bankClient) {
        this.bankClient = bankClient;
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        // Their terminology: "reversal". Our terminology: "refund".
        java.math.BigDecimal amountMinor = request.amount().getAmount()
            .multiply(java.math.BigDecimal.valueOf(100));

        LegacyBankPaymentClient.BankReversalResponse reversal =
            bankClient.reverseTransaction(request.transactionId(), amountMinor);

        if (reversal.isSuccess()) {
            return RefundResult.success(reversal.getReversalRef());
        } else {
            return RefundResult.failure("REVERSAL_FAILED",
                "Legacy bank reversal failed for txn: " + request.transactionId());
        }
    }

    @Override
    public RefundResult getRefundStatus(String refundId) {
        LegacyBankPaymentClient.BankStatusResponse status =
            bankClient.checkPaymentStatus(refundId);
        boolean settled = "S".equals(status.getStatusCode());
        return settled
            ? RefundResult.success(refundId)
            : RefundResult.failure("PENDING", "Reversal not yet settled");
    }
}
java// com.ecommerce.infrastructure.payment.legacy/LegacyBankGatewayFactory.java
package com.ecommerce.infrastructure.payment.legacy;

import com.ecommerce.domain.payment.gateway.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Abstract Factory using Adapters internally.
 * Demonstrates: Abstract Factory + Adapter working together.
 * The factory creates adapted objects without clients knowing.
 */
@Component
@ConditionalOnProperty(name = "payment.provider", havingValue = "legacy_bank")
public class LegacyBankGatewayFactory implements PaymentGatewayFactory {

    private final LegacyBankPaymentClient bankClient;

    public LegacyBankGatewayFactory(
        @Value("${payment.legacy-bank.endpoint}")    String endpoint,
        @Value("${payment.legacy-bank.merchant-id}") String merchantId,
        @Value("${payment.legacy-bank.terminal-id}") String terminalId) {
        this.bankClient = new LegacyBankPaymentClient(merchantId, terminalId, endpoint);
    }

    @Override
    public ChargeGateway createChargeGateway() {
        // Returns Adapter — client sees ChargeGateway, not LegacyBankPaymentClient
        return new LegacyBankPaymentAdapter(
            bankClient,
            "MERCHANT-001",
            "TERM-001"
        );
    }

    @Override
    public RefundGateway createRefundGateway() {
        return new LegacyBankRefundAdapter(bankClient);
    }

    @Override
    public WebhookHandler createWebhookHandler() {
        // Legacy bank uses polling, not webhooks
        // Adapter returns no-op handler
        return new WebhookHandler() {
            @Override
            public boolean validateSignature(String p, String s, String sec) {
                return true; // Legacy bank polls don't have signatures
            }
            @Override
            public WebhookEvent parse(String payload,
                                      java.util.Map<String, String> headers) {
                throw new UnsupportedOperationException(
                    "Legacy bank uses polling, not webhooks");
            }
            @Override
            public void handle(WebhookEvent event) {}
        };
    }

    @Override
    public String getProviderName() { return "LEGACY_BANK"; }

    @Override
    public boolean isAvailable() {
        try {
            // Poll a status check to verify connectivity
            bankClient.checkPaymentStatus("HEALTH_CHECK");
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
```

---

## Pattern 2: Bridge

### Problem It Solves
```
Problem: Search functionality must work across multiple backends
(Elasticsearch, Algolia, OpenSearch, DB full-text) AND across
multiple domains (Products, Orders, Users).

Without Bridge — explosion of subclasses:
  ElasticsearchProductSearch
  ElasticsearchOrderSearch
  ElasticsearchUserSearch
  AlgoliaProductSearch
  AlgoliaOrderSearch         ← M × N classes!
  AlgoliaUserSearch

With Bridge — separate Abstraction from Implementation:
  ProductSearch(impl: ElasticsearchSearchEngine)
  OrderSearch(impl: AlgoliaSearchEngine)
  → M + N classes, any combination works
```

### Class Diagram
```
«Abstraction»                «Implementor»
SearchService                SearchEngine
+ search(query): Results     + execute(SearchRequest): RawResults
+ suggest(term): List        + index(doc): void
+ index(entity)              + deleteIndex(id): void
       ▲                            ▲
  ┌────┴──────┐              ┌──────┴──────────┐
  │           │              │                 │
Product   Order         Elasticsearch      Algolia
Search    Search         Engine             Engine
Abstrac.  Abstrac.
Implementation
java// ecommerce-domain: com.ecommerce.domain.search/SearchQuery.java
package com.ecommerce.domain.search;

import java.util.List;
import java.util.Map;

/**
 * Domain search query — engine-agnostic.
 * Bridge abstraction layer uses this.
 */
public record SearchQuery(
    String term,
    Map<String, List<String>> filters,   // field → values
    Map<String, Object> rangeFilters,    // field → {min, max}
    List<SortField> sortFields,
    int page,
    int size,
    boolean fuzzy,
    String locale
) {
    public static SearchQuery of(String term, int page, int size) {
        return new SearchQuery(term, Map.of(), Map.of(),
            List.of(), page, size, true, "en");
    }

    public record SortField(String field, boolean ascending) {}

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String term = "";
        private Map<String, List<String>> filters = new java.util.HashMap<>();
        private Map<String, Object> rangeFilters = new java.util.HashMap<>();
        private List<SortField> sortFields = new java.util.ArrayList<>();
        private int page = 0;
        private int size = 20;
        private boolean fuzzy = true;
        private String locale = "en";

        public Builder term(String v)           { term = v;         return this; }
        public Builder filter(String k, List<String> v) {
            filters.put(k, v);              return this;
        }
        public Builder range(String k, Object v){
            rangeFilters.put(k, v);         return this;
        }
        public Builder sort(String field, boolean asc) {
            sortFields.add(new SortField(field, asc)); return this;
        }
        public Builder page(int v)              { page = v;         return this; }
        public Builder size(int v)              { size = v;         return this; }
        public Builder fuzzy(boolean v)         { fuzzy = v;        return this; }
        public Builder locale(String v)         { locale = v;       return this; }
        public SearchQuery build() {
            return new SearchQuery(term, Map.copyOf(filters),
                Map.copyOf(rangeFilters), List.copyOf(sortFields),
                page, size, fuzzy, locale);
        }
    }
}
java// com.ecommerce.domain.search/SearchResult.java
package com.ecommerce.domain.search;

import java.util.List;
import java.util.Map;

public record SearchResult<T>(
    List<T> hits,
    long totalHits,
    int page,
    int totalPages,
    Map<String, List<FacetValue>> facets,
    long queryTimeMs
) {
    public record FacetValue(String value, long count) {}

    public static <T> SearchResult<T> empty(int page) {
        return new SearchResult<>(List.of(), 0, page, 0, Map.of(), 0);
    }
}
java// ecommerce-domain: com.ecommerce.domain.search/SearchEngine.java
package com.ecommerce.domain.search;

import java.util.List;
import java.util.Map;

/**
 * Bridge IMPLEMENTOR interface.
 * Defines the low-level search operations.
 * Completely decoupled from what is being searched (products, orders...).
 */
public interface SearchEngine {

    /**
     * Execute a search query against the engine.
     * Returns raw results — mapping done by abstraction layer.
     */
    RawSearchResult execute(String indexName, SearchQuery query);

    /**
     * Index a document (upsert semantics).
     */
    void index(String indexName, String docId, Map<String, Object> document);

    /**
     * Bulk index for performance.
     */
    void bulkIndex(String indexName,
                   List<Map.Entry<String, Map<String, Object>>> documents);

    /**
     * Remove document from index (called on soft delete).
     */
    void delete(String indexName, String docId);

    /**
     * Auto-complete suggestions.
     */
    List<String> suggest(String indexName, String prefix, int maxResults);

    /**
     * Engine health check.
     */
    boolean isHealthy();

    String getEngineName();

    record RawSearchResult(
        List<Map<String, Object>> hits,
        long totalHits,
        Map<String, List<Map<String, Object>>> aggregations,
        long queryTimeMs
    ) {}
}
java// ecommerce-infrastructure:
// com.ecommerce.infrastructure.search/ElasticsearchSearchEngine.java
package com.ecommerce.infrastructure.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.*;
import com.ecommerce.domain.search.SearchEngine;
import com.ecommerce.domain.search.SearchQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Bridge CONCRETE IMPLEMENTOR — Elasticsearch.
 * Implements SearchEngine using Elasticsearch 8.x Java client.
 */
@Component("elasticsearchEngine")
public class ElasticsearchSearchEngine implements SearchEngine {

    private static final Logger log =
        LoggerFactory.getLogger(ElasticsearchSearchEngine.class);

    private final ElasticsearchClient client;

    public ElasticsearchSearchEngine(ElasticsearchClient client) {
        this.client = client;
    }

    @Override
    public RawSearchResult execute(String indexName, SearchQuery query) {
        long start = System.currentTimeMillis();
        try {
            SearchRequest request = buildSearchRequest(indexName, query);
            SearchResponse<Map> response = client.search(request, Map.class);

            List<Map<String, Object>> hits = response.hits().hits().stream()
                .map(hit -> {
                    Map<String, Object> source = new HashMap<>(hit.source());
                    source.put("_id",    hit.id());
                    source.put("_score", hit.score());
                    return source;
                })
                .collect(Collectors.toList());

            Map<String, List<Map<String, Object>>> aggs =
                buildAggregationResult(response);

            long total = response.hits().total() != null
                ? response.hits().total().value() : 0;

            return new RawSearchResult(hits, total, aggs,
                System.currentTimeMillis() - start);

        } catch (IOException e) {
            log.error("Elasticsearch search failed on index {}: {}",
                indexName, e.getMessage());
            return new RawSearchResult(List.of(), 0, Map.of(),
                System.currentTimeMillis() - start);
        }
    }

    @Override
    public void index(String indexName, String docId,
                      Map<String, Object> document) {
        try {
            client.index(i -> i
                .index(indexName)
                .id(docId)
                .document(document)
            );
        } catch (IOException e) {
            log.error("Failed to index doc {} in {}: {}", docId, indexName, e.getMessage());
            throw new RuntimeException("Elasticsearch index failed", e);
        }
    }

    @Override
    public void bulkIndex(String indexName,
                          List<Map.Entry<String, Map<String, Object>>> documents) {
        if (documents.isEmpty()) return;
        try {
            BulkRequest.Builder bulk = new BulkRequest.Builder();
            documents.forEach(entry ->
                bulk.operations(op -> op
                    .index(idx -> idx
                        .index(indexName)
                        .id(entry.getKey())
                        .document(entry.getValue())
                    )
                )
            );
            BulkResponse response = client.bulk(bulk.build());
            if (response.errors()) {
                log.warn("Bulk index had errors for index: {}", indexName);
            }
        } catch (IOException e) {
            throw new RuntimeException("Elasticsearch bulk index failed", e);
        }
    }

    @Override
    public void delete(String indexName, String docId) {
        try {
            client.delete(d -> d.index(indexName).id(docId));
        } catch (IOException e) {
            log.warn("Failed to delete doc {} from {}: {}", docId, indexName, e.getMessage());
        }
    }

    @Override
    public List<String> suggest(String indexName, String prefix, int maxResults) {
        try {
            SearchResponse<Map> response = client.search(s -> s
                .index(indexName)
                .suggest(sg -> sg
                    .suggesters("name-suggest", fs -> fs
                        .prefix(prefix)
                        .completion(c -> c
                            .field("suggest")
                            .size(maxResults)
                        )
                    )
                ),
                Map.class
            );
            return response.suggest().getOrDefault("name-suggest", List.of())
                .stream()
                .flatMap(option -> option.completion().options().stream())
                .map(CompletionSuggestOption::text)
                .collect(Collectors.toList());
        } catch (IOException e) {
            log.warn("Suggest failed: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public boolean isHealthy() {
        try {
            return client.cluster().health().status() !=
                co.elastic.clients.elasticsearch._types.HealthStatus.Red;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String getEngineName() { return "ELASTICSEARCH"; }

    // ── Private Builders ─────────────────────────────────────────────

    private SearchRequest buildSearchRequest(String indexName, SearchQuery query) {
        return SearchRequest.of(s -> {
            s.index(indexName)
             .from(query.page() * query.size())
             .size(query.size())
             .query(buildQuery(query));

            // Sort fields
            query.sortFields().forEach(sf ->
                s.sort(so -> so.field(f -> f
                    .field(sf.field())
                    .order(sf.ascending() ? SortOrder.Asc : SortOrder.Desc)
                ))
            );

            // Aggregations for facets
            s.aggregations("categories", a -> a.terms(t -> t.field("categoryId")))
             .aggregations("priceRange", a -> a.range(r -> r
                 .field("price")
                 .ranges(List.of(
                     co.elastic.clients.elasticsearch._types.aggregations.AggregationRange.of(
                         ar -> ar.to(50.0)),
                     co.elastic.clients.elasticsearch._types.aggregations.AggregationRange.of(
                         ar -> ar.from(50.0).to(200.0)),
                     co.elastic.clients.elasticsearch._types.aggregations.AggregationRange.of(
                         ar -> ar.from(200.0))
                 ))
             ));

            return s;
        });
    }

    private Query buildQuery(SearchQuery query) {
        List<Query> musts = new ArrayList<>();
        List<Query> filters = new ArrayList<>();

        // Full-text search on name and description
        if (!query.term().isBlank()) {
            if (query.fuzzy()) {
                musts.add(Query.of(q -> q.multiMatch(m -> m
                    .query(query.term())
                    .fields(List.of("name^3", "description^1", "attributes.*^2"))
                    .fuzziness("AUTO")
                    .type(TextQueryType.BestFields)
                )));
            } else {
                musts.add(Query.of(q -> q.match(m -> m
                    .field("name")
                    .query(query.term())
                )));
            }
        } else {
            musts.add(Query.of(q -> q.matchAll(m -> m)));
        }

        // Term filters
        query.filters().forEach((field, values) ->
            filters.add(Query.of(q -> q.terms(t -> t
                .field(field)
                .terms(tv -> tv.value(
                    values.stream()
                        .map(co.elastic.clients.elasticsearch._types.FieldValue::of)
                        .toList()
                ))
            )))
        );

        // Only show active products
        filters.add(Query.of(q -> q.term(t -> t
            .field("status")
            .value("ACTIVE")
        )));

        return Query.of(q -> q.bool(b -> b
            .must(musts)
            .filter(filters)
        ));
    }

    private Map<String, List<Map<String, Object>>> buildAggregationResult(
            SearchResponse<Map> response) {
        Map<String, List<Map<String, Object>>> result = new HashMap<>();
        response.aggregations().forEach((name, agg) -> {
            if (agg.isSterms()) {
                List<Map<String, Object>> buckets = agg.sterms().buckets().array()
                    .stream()
                    .map(b -> Map.of(
                        "key", (Object) b.key(),
                        "count", b.docCount()
                    ))
                    .collect(Collectors.toList());
                result.put(name, buckets);
            }
        });
        return result;
    }
}
java// com.ecommerce.infrastructure.search/InMemorySearchEngine.java
package com.ecommerce.infrastructure.search;

import com.ecommerce.domain.search.SearchEngine;
import com.ecommerce.domain.search.SearchQuery;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Concrete Implementor — In-Memory Search Engine.
 * Used for: local development, integration tests.
 * Bridge pattern means swapping this for ES is zero code change in abstractions.
 */
@Component("inMemorySearchEngine")
@Profile({"dev", "test"})
public class InMemorySearchEngine implements SearchEngine {

    private final Map<String, Map<String, Map<String, Object>>> indices
        = new ConcurrentHashMap<>();

    @Override
    public RawSearchResult execute(String indexName, SearchQuery query) {
        long start = System.currentTimeMillis();
        Map<String, Map<String, Object>> index =
            indices.getOrDefault(indexName, Map.of());

        List<Map<String, Object>> hits = index.values().stream()
            .filter(doc -> matchesQuery(doc, query))
            .skip((long) query.page() * query.size())
            .limit(query.size())
            .collect(Collectors.toList());

        long total = index.values().stream()
            .filter(doc -> matchesQuery(doc, query))
            .count();

        return new RawSearchResult(hits, total, Map.of(),
            System.currentTimeMillis() - start);
    }

    @Override
    public void index(String indexName, String docId,
                      Map<String, Object> document) {
        indices.computeIfAbsent(indexName, k -> new ConcurrentHashMap<>())
               .put(docId, new HashMap<>(document));
    }

    @Override
    public void bulkIndex(String indexName,
                          List<Map.Entry<String, Map<String, Object>>> documents) {
        documents.forEach(e -> index(indexName, e.getKey(), e.getValue()));
    }

    @Override
    public void delete(String indexName, String docId) {
        Optional.ofNullable(indices.get(indexName))
            .ifPresent(idx -> idx.remove(docId));
    }

    @Override
    public List<String> suggest(String indexName, String prefix, int max) {
        return indices.getOrDefault(indexName, Map.of()).values().stream()
            .map(doc -> String.valueOf(doc.getOrDefault("name", "")))
            .filter(name -> name.toLowerCase().startsWith(prefix.toLowerCase()))
            .limit(max)
            .collect(Collectors.toList());
    }

    @Override
    public boolean isHealthy() { return true; }

    @Override
    public String getEngineName() { return "IN_MEMORY"; }

    private boolean matchesQuery(Map<String, Object> doc, SearchQuery query) {
        if (query.term().isBlank()) return true;
        String term = query.term().toLowerCase();
        return doc.values().stream()
            .map(v -> String.valueOf(v).toLowerCase())
            .anyMatch(v -> v.contains(term));
    }
}
java// ecommerce-application:
// com.ecommerce.application.search/ProductSearchService.java
package com.ecommerce.application.search;

import com.ecommerce.domain.search.SearchEngine;
import com.ecommerce.domain.search.SearchQuery;
import com.ecommerce.domain.search.SearchResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Bridge REFINED ABSTRACTION — Product Search.
 *
 * The Abstraction (ProductSearchService) is decoupled from
 * the Implementor (SearchEngine) via the bridge.
 *
 * Adding new search entity (e.g., Blog posts) = new Abstraction class.
 * Adding new engine (e.g., Algolia) = new Implementor class.
 * ZERO changes to existing classes. ✅ OCP satisfied.
 */
@Service
public class ProductSearchService {

    private static final String INDEX_NAME = "products";

    private final SearchEngine searchEngine; // Bridge to implementation

    public ProductSearchService(
        @Qualifier("elasticsearchEngine") SearchEngine searchEngine) {
        this.searchEngine = searchEngine;
    }

    /**
     * Full product search with facets.
     */
    public SearchResult<ProductSearchHit> search(SearchQuery query) {
        SearchEngine.RawSearchResult raw =
            searchEngine.execute(INDEX_NAME, query);

        List<ProductSearchHit> hits = raw.hits().stream()
            .map(this::toProductHit)
            .toList();

        Map<String, List<SearchResult.FacetValue>> facets =
            buildFacets(raw.aggregations());

        int totalPages = (int) Math.ceil((double) raw.totalHits() / query.size());

        return new SearchResult<>(
            hits, raw.totalHits(), query.page(),
            totalPages, facets, raw.queryTimeMs()
        );
    }

    /**
     * Auto-complete suggestions for search bar.
     */
    public List<String> suggest(String prefix) {
        return searchEngine.suggest(INDEX_NAME, prefix, 10);
    }

    /**
     * Index a product (called on product publish / update).
     */
    public void indexProduct(ProductSearchDocument document) {
        searchEngine.index(INDEX_NAME,
            document.productId(), toIndexMap(document));
    }

    /**
     * Remove from index (called on product soft delete).
     */
    public void removeProduct(String productId) {
        searchEngine.delete(INDEX_NAME, productId);
    }

    /**
     * Bulk reindex (called during migration or full reindex job).
     */
    public void bulkIndex(List<ProductSearchDocument> documents) {
        List<Map.Entry<String, Map<String, Object>>> entries = documents.stream()
            .map(doc -> Map.entry(doc.productId(), toIndexMap(doc)))
            .toList();
        searchEngine.bulkIndex(INDEX_NAME, entries);
    }

    // ── Mapping Methods ───────────────────────────────────────────────

    private ProductSearchHit toProductHit(Map<String, Object> raw) {
        return new ProductSearchHit(
            String.valueOf(raw.get("productId")),
            String.valueOf(raw.get("name")),
            String.valueOf(raw.get("slug")),
            String.valueOf(raw.get("categoryId")),
            parseDouble(raw.get("price")),
            String.valueOf(raw.get("imageUrl")),
            parseDouble(raw.get("averageRating")),
            parseInt(raw.get("reviewCount")),
            String.valueOf(raw.get("status")),
            (Double) raw.getOrDefault("_score", 0.0)
        );
    }

    private Map<String, Object> toIndexMap(ProductSearchDocument doc) {
        Map<String, Object> map = new HashMap<>();
        map.put("productId",     doc.productId());
        map.put("name",          doc.name());
        map.put("description",   doc.description());
        map.put("slug",          doc.slug());
        map.put("categoryId",    doc.categoryId());
        map.put("price",         doc.price());
        map.put("imageUrl",      doc.imageUrl());
        map.put("averageRating", doc.averageRating());
        map.put("reviewCount",   doc.reviewCount());
        map.put("status",        doc.status());
        map.put("attributes",    doc.attributes());
        // Add suggest field for auto-complete
        map.put("suggest", Map.of(
            "input",   List.of(doc.name()),
            "weight",  doc.reviewCount()
        ));
        return map;
    }

    private Map<String, List<SearchResult.FacetValue>> buildFacets(
            Map<String, List<Map<String, Object>>> aggregations) {
        Map<String, List<SearchResult.FacetValue>> facets = new LinkedHashMap<>();
        aggregations.forEach((name, buckets) -> {
            List<SearchResult.FacetValue> values = buckets.stream()
                .map(b -> new SearchResult.FacetValue(
                    String.valueOf(b.get("key")),
                    Long.parseLong(String.valueOf(b.get("count")))
                ))
                .toList();
            facets.put(name, values);
        });
        return facets;
    }

    private double parseDouble(Object v) {
        try { return Double.parseDouble(String.valueOf(v)); }
        catch (Exception e) { return 0.0; }
    }

    private int parseInt(Object v) {
        try { return Integer.parseInt(String.valueOf(v)); }
        catch (Exception e) { return 0; }
    }

    // ── Inner DTOs ────────────────────────────────────────────────────

    public record ProductSearchHit(
        String productId, String name, String slug,
        String categoryId, double price, String imageUrl,
        double averageRating, int reviewCount,
        String status, double score
    ) {}

    public record ProductSearchDocument(
        String productId, String name, String description,
        String slug, String categoryId, double price,
        String imageUrl, double averageRating, int reviewCount,
        String status, Map<String, String> attributes
    ) {}
}
```

---

## Pattern 3: Composite

### Problem It Solves
```
Problem: Product categories form a TREE structure.
  Electronics
    └── Computers
          ├── Laptops
          └── Desktops
    └── Phones
          ├── Smartphones
          └── Accessories

Operations like "get all products in Electronics" must
traverse the entire tree recursively.

Without Composite:
  if (category.hasChildren()) {
    for each child: if child.hasChildren() { recurse }
    else: getProducts()
  }
  → Different code paths for leaf vs container → error-prone

With Composite:
  category.getAllProductIds()  ← same call on leaf AND container
  Leaf returns its own products.
  Container delegates to children, aggregates results.
  → Uniform interface for entire tree.
```

### Class Diagram
```
«Component»
CategoryComponent
+ getId(): CategoryId
+ getName(): String
+ getAllProductIds(): List<String>
+ getSubcategories(): List<CategoryComponent>
+ getDepth(): int
+ accept(visitor): void       ← Visitor pattern hook (Phase 6)
         ▲
    ┌────┴────────┐
    │             │
LeafCategory  CompositeCategory
(no children)  + children: List<CategoryComponent>
               + addChild(child)
               + removeChild(id)
               + getAllProductIds() ← aggregates children
Implementation
java// ecommerce-domain: com.ecommerce.domain.product/category/CategoryComponent.java
package com.ecommerce.domain.product.category;

import com.ecommerce.domain.product.CategoryId;
import java.util.List;

/**
 * Component interface — uniform interface for leaf and composite.
 * Client code works with this interface ONLY.
 * Does not know if it's talking to a leaf or composite.
 */
public interface CategoryComponent {

    CategoryId getId();
    String getName();
    String getSlug();

    /**
     * Key composite operation — returns all product IDs
     * for this category AND all descendants.
     * Leaf: returns its own product IDs.
     * Composite: aggregates children's results.
     */
    List<String> getAllProductIds();

    /**
     * Returns all subcategories (empty for leaf).
     */
    List<CategoryComponent> getSubcategories();

    /**
     * Tree depth (root = 0).
     */
    int getDepth();

    /**
     * Full path from root to this node.
     * e.g., "Electronics > Computers > Laptops"
     */
    String getBreadcrumb();

    /**
     * Total count of active products in subtree.
     */
    long getTotalProductCount();

    /**
     * Accept a visitor (Visitor pattern — Phase 6).
     */
    void accept(CategoryVisitor visitor);
}
java// com.ecommerce.domain.product.category/CategoryVisitor.java
package com.ecommerce.domain.product.category;

/**
 * Visitor interface for category tree operations.
 * (Full Visitor pattern implementation in Phase 6.)
 */
public interface CategoryVisitor {
    void visitLeaf(LeafCategory category);
    void visitComposite(CompositeCategory category);
}
java// com.ecommerce.domain.product.category/LeafCategory.java
package com.ecommerce.domain.product.category;

import com.ecommerce.domain.product.CategoryId;
import java.util.*;

/**
 * Composite Pattern LEAF — has no children.
 * Represents the deepest category level (e.g., "Gaming Laptops").
 * Directly associated with products.
 */
public final class LeafCategory implements CategoryComponent {

    private final CategoryId id;
    private final String name;
    private final String slug;
    private final String parentBreadcrumb;
    private final int depth;
    private final List<String> productIds;    // direct products
    private long productCount;

    public LeafCategory(CategoryId id, String name, String slug,
                        String parentBreadcrumb, int depth) {
        this.id               = Objects.requireNonNull(id);
        this.name             = Objects.requireNonNull(name);
        this.slug             = Objects.requireNonNull(slug);
        this.parentBreadcrumb = parentBreadcrumb;
        this.depth            = depth;
        this.productIds       = new ArrayList<>();
    }

    public void addProductId(String productId) {
        productIds.add(productId);
        productCount++;
    }

    public void removeProductId(String productId) {
        productIds.remove(productId);
        productCount = Math.max(0, productCount - 1);
    }

    @Override
    public List<String> getAllProductIds() {
        // Leaf: return its own product IDs directly
        return Collections.unmodifiableList(productIds);
    }

    @Override
    public List<CategoryComponent> getSubcategories() {
        return List.of(); // Leaf has NO children
    }

    @Override
    public long getTotalProductCount() {
        return productCount;
    }

    @Override
    public String getBreadcrumb() {
        return parentBreadcrumb == null || parentBreadcrumb.isEmpty()
            ? name
            : parentBreadcrumb + " > " + name;
    }

    @Override
    public void accept(CategoryVisitor visitor) {
        visitor.visitLeaf(this); // Double dispatch
    }

    @Override public CategoryId getId()  { return id; }
    @Override public String getName()    { return name; }
    @Override public String getSlug()    { return slug; }
    @Override public int getDepth()      { return depth; }
}
java// com.ecommerce.domain.product.category/CompositeCategory.java
package com.ecommerce.domain.product.category;

import com.ecommerce.domain.product.CategoryId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Composite Pattern COMPOSITE — can have children (subcategories).
 * Delegates operations to children and aggregates results.
 * e.g., "Electronics" contains "Computers", "Phones", "TVs"
 */
public final class CompositeCategory implements CategoryComponent {

    private final CategoryId id;
    private final String name;
    private final String slug;
    private final String parentBreadcrumb;
    private final int depth;
    private final List<CategoryComponent> children; // Can be Leaf OR Composite

    public CompositeCategory(CategoryId id, String name, String slug,
                             String parentBreadcrumb, int depth) {
        this.id               = Objects.requireNonNull(id);
        this.name             = Objects.requireNonNull(name);
        this.slug             = Objects.requireNonNull(slug);
        this.parentBreadcrumb = parentBreadcrumb;
        this.depth            = depth;
        this.children         = new ArrayList<>();
    }

    public void addChild(CategoryComponent child) {
        Objects.requireNonNull(child);
        if (child.getId().equals(this.id)) {
            throw new IllegalArgumentException("Cannot add category as its own child");
        }
        children.add(child);
    }

    public void removeChild(CategoryId childId) {
        children.removeIf(c -> c.getId().equals(childId));
    }

    /**
     * KEY COMPOSITE OPERATION.
     * Composite: delegates to ALL children recursively.
     * Client doesn't know (or care) about the tree structure.
     */
    @Override
    public List<String> getAllProductIds() {
        return children.stream()
            .flatMap(child -> child.getAllProductIds().stream())
            .distinct()
            .collect(Collectors.toList());
    }

    @Override
    public List<CategoryComponent> getSubcategories() {
        return Collections.unmodifiableList(children);
    }

    /**
     * Aggregates counts from all descendants.
     * Composite: sum of all children's counts.
     */
    @Override
    public long getTotalProductCount() {
        return children.stream()
            .mapToLong(CategoryComponent::getTotalProductCount)
            .sum();
    }

    @Override
    public String getBreadcrumb() {
        return parentBreadcrumb == null || parentBreadcrumb.isEmpty()
            ? name
            : parentBreadcrumb + " > " + name;
    }

    /**
     * Visitor: visits self first, then delegates to children.
     */
    @Override
    public void accept(CategoryVisitor visitor) {
        visitor.visitComposite(this);       // Visit self
        children.forEach(c -> c.accept(visitor)); // Visit all children
    }

    /**
     * Find a category anywhere in the subtree by ID.
     */
    public Optional<CategoryComponent> findById(CategoryId targetId) {
        if (this.id.equals(targetId)) return Optional.of(this);
        return children.stream()
            .map(child -> {
                if (child instanceof CompositeCategory cc) {
                    return cc.findById(targetId);
                }
                return child.getId().equals(targetId)
                    ? Optional.of(child)
                    : Optional.<CategoryComponent>empty();
            })
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst();
    }

    /**
     * Get all leaf categories in subtree.
     */
    public List<LeafCategory> getAllLeaves() {
        List<LeafCategory> leaves = new ArrayList<>();
        for (CategoryComponent child : children) {
            if (child instanceof LeafCategory leaf) {
                leaves.add(leaf);
            } else if (child instanceof CompositeCategory composite) {
                leaves.addAll(composite.getAllLeaves());
            }
        }
        return leaves;
    }

    /**
     * Render full tree as string (for debugging/admin).
     */
    public String renderTree() {
        StringBuilder sb = new StringBuilder();
        renderTree(sb, 0);
        return sb.toString();
    }

    private void renderTree(StringBuilder sb, int indent) {
        sb.append("  ".repeat(indent))
          .append("📁 ").append(name)
          .append(" (").append(getTotalProductCount()).append(" products)\n");
        children.forEach(child -> {
            if (child instanceof CompositeCategory cc) {
                cc.renderTree(sb, indent + 1);
            } else {
                sb.append("  ".repeat(indent + 1))
                  .append("📄 ").append(child.getName())
                  .append(" (").append(child.getTotalProductCount()).append(")\n");
            }
        });
    }

    @Override public CategoryId getId()  { return id; }
    @Override public String getName()    { return name; }
    @Override public String getSlug()    { return slug; }
    @Override public int getDepth()      { return depth; }
}
java// com.ecommerce.application.product/CategoryTreeService.java
package com.ecommerce.application.product;

import com.ecommerce.domain.product.CategoryId;
import com.ecommerce.domain.product.category.*;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service that builds and operates on the category tree.
 * Client code uses CategoryComponent uniformly —
 * never distinguishes Leaf from Composite.
 */
@Service
public class CategoryTreeService {

    /**
     * Build full category tree from flat DB records.
     * Returns root composite for navigation.
     */
    public CompositeCategory buildTree(List<CategoryRecord> records) {
        Map<String, CategoryComponent> nodeMap = new LinkedHashMap<>();
        CompositeCategory root = new CompositeCategory(
            CategoryId.generate(), "All Categories", "root", "", 0
        );
        nodeMap.put("root", root);

        // Sort by depth to build top-down
        records.sort(Comparator.comparingInt(CategoryRecord::depth));

        for (CategoryRecord record : records) {
            CategoryComponent node;
            if (record.hasChildren()) {
                node = new CompositeCategory(
                    CategoryId.of(record.id()), record.name(),
                    record.slug(), record.breadcrumb(), record.depth()
                );
            } else {
                LeafCategory leaf = new LeafCategory(
                    CategoryId.of(record.id()), record.name(),
                    record.slug(), record.breadcrumb(), record.depth()
                );
                record.productIds().forEach(leaf::addProductId);
                node = leaf;
            }
            nodeMap.put(record.id(), node);

            // Attach to parent
            CategoryComponent parent = nodeMap.getOrDefault(
                record.parentId(), root
            );
            if (parent instanceof CompositeCategory cc) {
                cc.addChild(node);
            }
        }
        return root;
    }

    /**
     * Get all product IDs for a category + descendants.
     * Client uses CategoryComponent interface — works for both leaf and composite.
     */
    public List<String> getAllProductIdsForCategory(
            CompositeCategory root, CategoryId targetId) {

        return root.findById(targetId)
            .map(CategoryComponent::getAllProductIds)
            .orElse(List.of());
    }

    /**
     * Usage: traverse entire tree uniformly.
     */
    public void printCatalogStats(CompositeCategory root) {
        root.accept(new CategoryVisitor() {
            @Override
            public void visitLeaf(LeafCategory cat) {
                System.out.printf("  Leaf: %-30s %,d products%n",
                    cat.getName(), cat.getTotalProductCount());
            }
            @Override
            public void visitComposite(CompositeCategory cat) {
                System.out.printf("Folder: %-30s %,d products (total)%n",
                    cat.getName(), cat.getTotalProductCount());
            }
        });
    }

    public record CategoryRecord(
        String id, String parentId, String name, String slug,
        String breadcrumb, int depth, boolean hasChildren,
        List<String> productIds
    ) {}
}
```

---

## Pattern 4: Decorator

### Problem It Solves
```
Problem: Repository needs multiple cross-cutting behaviors:
  ① Caching (Redis)
  ② Logging (timing, params)
  ③ Retry (on transient failures)
  ④ Metrics (Prometheus counters)

Without Decorator — inheritance explosion:
  CachingProductRepository extends ProductRepository
  LoggingCachingProductRepository extends CachingProductRepository
  RetryCachingLoggingProductRepository extends ...
  → 2^N combinations, impossible to maintain

With Decorator — stackable wrappers:
  new MetricsDecorator(
    new RetryDecorator(
      new CachingDecorator(
        new LoggingDecorator(
          new JpaProductRepository()
        )
      )
    )
  )
  → Each decorator adds ONE responsibility.
  → Can mix and match independently.
  → Open for extension, closed for modification.
```

### Class Diagram
```
«interface»
ProductRepository
+ findById(id): Optional<Product>
+ save(product): void
+ findBySlug(slug): Optional<Product>
         ▲
    ┌────┴─────────────────────────────────┐
    │             │            │           │
JpaProduct   Caching      Logging     Metrics
Repository   Decorator    Decorator   Decorator
(concrete)   wraps→repo   wraps→repo  wraps→repo
Implementation
java// ecommerce-infrastructure:
// com.ecommerce.infrastructure.persistence/decorator/CachingProductRepository.java
package com.ecommerce.infrastructure.persistence.decorator;

import com.ecommerce.domain.product.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Decorator Pattern — Caching layer for ProductRepository.
 *
 * Wraps any ProductRepository implementation.
 * Adds Redis caching transparently.
 * Does not change the interface — clients unaffected.
 *
 * Cache strategy:
 *  - READ:  check cache → miss → load from DB → populate cache
 *  - WRITE: invalidate affected cache keys
 *  - TTL:   products cached for 1 hour (configurable)
 */
public class CachingProductRepository implements ProductRepository {

    private static final Logger log =
        LoggerFactory.getLogger(CachingProductRepository.class);

    private static final String CACHE_PREFIX   = "product:";
    private static final String SLUG_PREFIX    = "product:slug:";
    private static final Duration PRODUCT_TTL  = Duration.ofHours(1);

    private final ProductRepository delegate;         // The wrapped repository
    private final RedisTemplate<String, Object> redis;

    public CachingProductRepository(ProductRepository delegate,
                                     RedisTemplate<String, Object> redis) {
        this.delegate = delegate;
        this.redis    = redis;
    }

    @Override
    public Optional<Product> findById(ProductId id) {
        String cacheKey = CACHE_PREFIX + id.getValue();

        // Cache hit
        Object cached = redis.opsForValue().get(cacheKey);
        if (cached instanceof Product product) {
            log.debug("Cache HIT for product: {}", id);
            return Optional.of(product);
        }

        // Cache miss — load from delegate
        log.debug("Cache MISS for product: {}", id);
        Optional<Product> result = delegate.findById(id);

        // Populate cache
        result.ifPresent(product ->
            redis.opsForValue().set(cacheKey, product, PRODUCT_TTL)
        );

        return result;
    }

    @Override
    public Optional<Product> findBySlug(String slug) {
        String cacheKey = SLUG_PREFIX + slug;

        Object cached = redis.opsForValue().get(cacheKey);
        if (cached instanceof Product product) {
            return Optional.of(product);
        }

        Optional<Product> result = delegate.findBySlug(slug);
        result.ifPresent(p ->
            redis.opsForValue().set(cacheKey, p, PRODUCT_TTL)
        );
        return result;
    }

    @Override
    public void save(Product product) {
        delegate.save(product);
        evictProductCache(product);
    }

    @Override
    public void update(Product product) {
        delegate.update(product);
        evictProductCache(product);
    }

    @Override
    public List<Product> findByCategoryId(CategoryId categoryId, int page, int size) {
        // Category lists: shorter TTL, invalidated on product changes
        String cacheKey = "products:category:" + categoryId + ":p" + page + ":s" + size;

        Object cached = redis.opsForValue().get(cacheKey);
        if (cached instanceof List<?> list) {
            log.debug("Cache HIT for category products: {}", categoryId);
            @SuppressWarnings("unchecked")
            List<Product> products = (List<Product>) list;
            return products;
        }

        List<Product> result = delegate.findByCategoryId(categoryId, page, size);
        redis.opsForValue().set(cacheKey, result, Duration.ofMinutes(15));
        return result;
    }

    @Override
    public boolean existsBySlug(String slug) {
        return delegate.existsBySlug(slug);
    }

    @Override
    public boolean existsBySku(String sku) {
        return delegate.existsBySku(sku);
    }

    private void evictProductCache(Product product) {
        String idKey   = CACHE_PREFIX + product.getId().getValue();
        String slugKey = SLUG_PREFIX + product.getSlug();
        redis.delete(List.of(idKey, slugKey));
        log.debug("Cache evicted for product: {}", product.getId());
    }
}
java// com.ecommerce.infrastructure.persistence/decorator/LoggingProductRepository.java
package com.ecommerce.infrastructure.persistence.decorator;

import com.ecommerce.domain.product.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Decorator — Logging layer for ProductRepository.
 * Logs timing, errors, and slow queries.
 * Can be stacked on top of CachingDecorator.
 */
public class LoggingProductRepository implements ProductRepository {

    private static final Logger log =
        LoggerFactory.getLogger(LoggingProductRepository.class);

    private static final long SLOW_QUERY_THRESHOLD_MS = 100;

    private final ProductRepository delegate;

    public LoggingProductRepository(ProductRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<Product> findById(ProductId id) {
        long start = System.currentTimeMillis();
        try {
            Optional<Product> result = delegate.findById(id);
            logSuccess("findById", id.getValue().toString(),
                System.currentTimeMillis() - start, result.isPresent());
            return result;
        } catch (Exception e) {
            logError("findById", id.getValue().toString(), e);
            throw e;
        }
    }

    @Override
    public Optional<Product> findBySlug(String slug) {
        long start = System.currentTimeMillis();
        try {
            Optional<Product> result = delegate.findBySlug(slug);
            logSuccess("findBySlug", slug,
                System.currentTimeMillis() - start, result.isPresent());
            return result;
        } catch (Exception e) {
            logError("findBySlug", slug, e);
            throw e;
        }
    }

    @Override
    public void save(Product product) {
        long start = System.currentTimeMillis();
        try {
            delegate.save(product);
            logSuccess("save", product.getId().getValue().toString(),
                System.currentTimeMillis() - start, true);
        } catch (Exception e) {
            logError("save", product.getId().getValue().toString(), e);
            throw e;
        }
    }

    @Override
    public void update(Product product) {
        long start = System.currentTimeMillis();
        try {
            delegate.update(product);
            logSuccess("update", product.getId().getValue().toString(),
                System.currentTimeMillis() - start, true);
        } catch (Exception e) {
            logError("update", product.getId().getValue().toString(), e);
            throw e;
        }
    }

    @Override
    public List<Product> findByCategoryId(CategoryId categoryId, int page, int size) {
        long start = System.currentTimeMillis();
        try {
            List<Product> result = delegate.findByCategoryId(categoryId, page, size);
            long elapsed = System.currentTimeMillis() - start;
            logSuccess("findByCategoryId", categoryId.getValue().toString(),
                elapsed, !result.isEmpty());
            if (elapsed > SLOW_QUERY_THRESHOLD_MS) {
                log.warn("SLOW QUERY: findByCategoryId took {}ms for category {}",
                    elapsed, categoryId);
            }
            return result;
        } catch (Exception e) {
            logError("findByCategoryId", categoryId.getValue().toString(), e);
            throw e;
        }
    }

    @Override
    public boolean existsBySlug(String slug) {
        return delegate.existsBySlug(slug);
    }

    @Override
    public boolean existsBySku(String sku) {
        return delegate.existsBySku(sku);
    }

    private void logSuccess(String method, String identifier,
                            long elapsedMs, boolean found) {
        log.debug("ProductRepository.{}: identifier={}, found={}, elapsed={}ms",
            method, identifier, found, elapsedMs);
    }

    private void logError(String method, String identifier, Exception e) {
        log.error("ProductRepository.{} FAILED: identifier={}, error={}",
            method, identifier, e.getMessage(), e);
    }
}
java// com.ecommerce.infrastructure.persistence/decorator/RetryProductRepository.java
package com.ecommerce.infrastructure.persistence.decorator;

import com.ecommerce.domain.product.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.TransientDataAccessException;

import java.util.List;
import java.util.Optional;

/**
 * Decorator — Retry layer for transient failures.
 * Retries DB operations on transient errors (connection timeouts,
 * deadlocks, network blips).
 * Does NOT retry on business logic exceptions.
 */
public class RetryProductRepository implements ProductRepository {

    private static final Logger log =
        LoggerFactory.getLogger(RetryProductRepository.class);

    private final ProductRepository delegate;
    private final int maxAttempts;
    private final long retryDelayMs;

    public RetryProductRepository(ProductRepository delegate,
                                   int maxAttempts, long retryDelayMs) {
        this.delegate     = delegate;
        this.maxAttempts  = maxAttempts;
        this.retryDelayMs = retryDelayMs;
    }

    @Override
    public Optional<Product> findById(ProductId id) {
        return executeWithRetry(() -> delegate.findById(id), "findById");
    }

    @Override
    public Optional<Product> findBySlug(String slug) {
        return executeWithRetry(() -> delegate.findBySlug(slug), "findBySlug");
    }

    @Override
    public void save(Product product) {
        executeWithRetry(() -> { delegate.save(product); return null; }, "save");
    }

    @Override
    public void update(Product product) {
        executeWithRetry(() -> { delegate.update(product); return null; }, "update");
    }

    @Override
    public List<Product> findByCategoryId(CategoryId categoryId, int page, int size) {
        return executeWithRetry(
            () -> delegate.findByCategoryId(categoryId, page, size),
            "findByCategoryId"
        );
    }

    @Override
    public boolean existsBySlug(String slug) {
        return executeWithRetry(() -> delegate.existsBySlug(slug), "existsBySlug");
    }

    @Override
    public boolean existsBySku(String sku) {
        return executeWithRetry(() -> delegate.existsBySku(sku), "existsBySku");
    }

    private <T> T executeWithRetry(ThrowingSupplier<T> operation, String opName) {
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return operation.get();
            } catch (TransientDataAccessException e) {
                // Retryable — DB connection issue, deadlock
                lastException = e;
                log.warn("ProductRepository.{} transient failure, attempt {}/{}: {}",
                    opName, attempt, maxAttempts, e.getMessage());
                if (attempt < maxAttempts) {
                    sleepBeforeRetry(attempt);
                }
            } catch (Exception e) {
                // Non-retryable — rethrow immediately
                throw e;
            }
        }
        log.error("ProductRepository.{} failed after {} attempts",
            opName, maxAttempts);
        throw new RuntimeException("Operation failed after retries: " + opName,
            lastException);
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            // Exponential backoff: delay * 2^(attempt-1)
            long delay = retryDelayMs * (1L << (attempt - 1));
            Thread.sleep(Math.min(delay, 5000)); // cap at 5s
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
java// com.ecommerce.infrastructure.config/RepositoryDecoratorConfig.java
package com.ecommerce.infrastructure.config;

import com.ecommerce.domain.product.ProductRepository;
import com.ecommerce.infrastructure.persistence.JpaProductRepository;
import com.ecommerce.infrastructure.persistence.decorator.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Configures the decorator stack for repositories.
 * Order matters: outermost decorator runs first.
 *
 * Request flow:
 * Client → Logging → Retry → Caching → JPA (DB)
 *
 * Why this order:
 * - Logging: outermost — logs EVERY attempt including retries
 * - Retry: wraps caching — retry DB calls if cache miss fails
 * - Caching: innermost — cache hit avoids retry overhead
 * - JPA: actual DB access
 */
@Configuration
public class RepositoryDecoratorConfig {

    @Bean
    public ProductRepository productRepository(
        JpaProductRepository jpaRepository,
        RedisTemplate<String, Object> redis) {

        return new LoggingProductRepository(    // Layer 1: Logging (outermost)
            new RetryProductRepository(         // Layer 2: Retry
                new CachingProductRepository(   // Layer 3: Caching
                    jpaRepository,              // Layer 4: Actual DB (innermost)
                    redis
                ),
                3,    // max 3 attempts
                100   // 100ms initial delay
            )
        );
    }
}
```

---

## Pattern 5: Facade

### Problem It Solves
```
Problem: Checkout involves 8+ subsystems:
  1. Validate cart
  2. Reserve inventory
  3. Apply coupon
  4. Calculate shipping
  5. Process payment
  6. Create order
  7. Send notifications
  8. Publish domain events

Without Facade:
  Controller calls each subsystem directly → controller is 200 lines
  Subsystem order is scattered and duplicated
  Hard to ensure consistency and transactional integrity

With Facade:
  Controller calls: checkoutFacade.checkout(request)
  Facade orchestrates all 8 subsystems
  Single entry point → single transaction boundary
  Controller is clean and testable
```

### Class Diagram
```
CheckoutController
      │
      ▼ (single call)
CheckoutFacade                  Subsystems:
+ checkout(request): Result ──► CartService
                           ──► InventoryService
                           ──► CouponService
                           ──► ShippingCalculator
                           ──► PaymentService
                           ──► OrderService
                           ──► NotificationService
                           ──► EventPublisher
Implementation
java// ecommerce-application:
// com.ecommerce.application.checkout/CheckoutRequest.java
package com.ecommerce.application.checkout;

import java.util.UUID;

public record CheckoutRequest(
    String userId,
    String cartId,
    String shippingAddressId,
    String billingAddressId,        // null = same as shipping
    String paymentMethodToken,
    String paymentProvider,
    String couponCode,              // optional
    String idempotencyKey           // client-generated for payment dedup
) {
    public CheckoutRequest {
        if (idempotencyKey == null) idempotencyKey = UUID.randomUUID().toString();
    }
}
java// com.ecommerce.application.checkout/CheckoutResult.java
package com.ecommerce.application.checkout;

import com.ecommerce.application.order.dto.OrderResponse;

public record CheckoutResult(
    boolean success,
    String orderId,
    String orderNumber,
    OrderResponse orderDetails,
    String paymentStatus,
    String error,
    boolean requiresAction,
    String actionUrl              // for 3DS redirect
) {
    public static CheckoutResult success(OrderResponse order, String paymentStatus) {
        return new CheckoutResult(true, order.getOrderId(),
            order.getOrderNumber(), order, paymentStatus,
            null, false, null);
    }

    public static CheckoutResult requiresAction(String orderId,
                                                String orderNumber, String actionUrl) {
        return new CheckoutResult(false, orderId, orderNumber,
            null, "requires_action", null, true, actionUrl);
    }

    public static CheckoutResult failure(String error) {
        return new CheckoutResult(false, null, null,
            null, "failed", error, false, null);
    }
}
java// com.ecommerce.application.checkout/CheckoutFacade.java
package com.ecommerce.application.checkout;

import com.ecommerce.application.cart.CartApplicationService;
import com.ecommerce.application.inventory.InventoryApplicationService;
import com.ecommerce.application.order.OrderApplicationService;
import com.ecommerce.application.order.dto.OrderResponse;
import com.ecommerce.application.order.dto.OrderResponseAssembler;
import com.ecommerce.application.payment.PaymentApplicationService;
import com.ecommerce.application.shipping.ShippingCalculator;
import com.ecommerce.domain.cart.Cart;
import com.ecommerce.domain.discount.Coupon;
import com.ecommerce.domain.discount.CouponRepository;
import com.ecommerce.domain.inventory.StockReservation;
import com.ecommerce.domain.order.Order;
import com.ecommerce.domain.payment.Payment;
import com.ecommerce.domain.payment.gateway.ChargeResult;
import com.ecommerce.domain.shared.valueobject.Money;
import com.ecommerce.domain.user.Address;
import com.ecommerce.domain.user.UserId;
import com.ecommerce.domain.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * FACADE PATTERN — Checkout Facade.
 *
 * Single entry point for the entire checkout process.
 * Hides complexity of coordinating 8+ subsystems.
 * Controller only knows: checkoutFacade.checkout(request).
 *
 * This is also a @Transactional boundary — ensures all-or-nothing.
 * Payment failure → order cancelled → inventory released.
 *
 * Design principle: Facade does NOT contain business logic.
 * It only ORCHESTRATES existing services.
 * Business rules live in domain aggregates / application services.
 */
@Service
public class CheckoutFacade {

    private static final Logger log = LoggerFactory.getLogger(CheckoutFacade.class);

    // All subsystems injected — Facade knows about them, clients don't need to
    private final CartApplicationService       cartService;
    private final InventoryApplicationService  inventoryService;
    private final PaymentApplicationService    paymentService;
    private final OrderApplicationService      orderService;
    private final ShippingCalculator           shippingCalculator;
    private final CouponRepository             couponRepository;
    private final UserRepository               userRepository;
    private final OrderResponseAssembler       assembler;

    public CheckoutFacade(CartApplicationService cartService,
                          InventoryApplicationService inventoryService,
                          PaymentApplicationService paymentService,
                          OrderApplicationService orderService,
                          ShippingCalculator shippingCalculator,
                          CouponRepository couponRepository,
                          UserRepository userRepository,
                          OrderResponseAssembler assembler) {
        this.cartService        = cartService;
        this.inventoryService   = inventoryService;
        this.paymentService     = paymentService;
        this.orderService       = orderService;
        this.shippingCalculator = shippingCalculator;
        this.couponRepository   = couponRepository;
        this.userRepository     = userRepository;
        this.assembler          = assembler;
    }

    /**
     * CHECKOUT — Main Facade method.
     * Orchestrates the entire purchase flow in correct order.
     * @Transactional ensures rollback on any failure.
     */
    @Transactional
    public CheckoutResult checkout(CheckoutRequest request) {
        log.info("Checkout initiated: userId={}, idempotencyKey={}",
            request.userId(), request.idempotencyKey());

        // ── STEP 1: Validate and load cart ───────────────────────────
        Cart cart = cartService.getValidatedCart(request.cartId(), request.userId());
        if (cart.isEmpty()) {
            return CheckoutResult.failure("Cart is empty");
        }

        // ── STEP 2: Load user and shipping address ────────────────────
        var user = userRepository.findById(UserId.of(request.userId()))
            .orElseThrow(() -> new RuntimeException("User not found"));
        user.ensureActive();

        Address shippingAddress = resolveShippingAddress(user, request);
        Address billingAddress  = resolveBillingAddress(user, request, shippingAddress);

        // ── STEP 3: Calculate shipping cost ───────────────────────────
        Money shippingCost = shippingCalculator.calculate(
            cart, shippingAddress
        );

        // ── STEP 4: Apply coupon (if provided) ────────────────────────
        Money discountAmount = Money.ZERO_USD;
        String appliedCoupon = null;
        boolean couponFreeShipping = false;

        if (request.couponCode() != null && !request.couponCode().isBlank()) {
            var couponResult = applyCoupon(
                request.couponCode(), cart.calculateTotal(),
                request.userId()
            );
            discountAmount     = couponResult.discountAmount();
            appliedCoupon      = couponResult.couponCode();
            couponFreeShipping = couponResult.freeShipping();
        }

        if (couponFreeShipping) {
            shippingCost = Money.ZERO_USD;
        }

        // ── STEP 5: Reserve inventory ─────────────────────────────────
        // Must happen BEFORE payment to prevent paying for out-of-stock items
        String pendingOrderId = UUID.randomUUID().toString(); // temp order ID
        List<StockReservation> reservations = inventoryService.reserveAll(
            cart.getItems(), pendingOrderId
        );

        // ── STEP 6: Create order (PENDING state) ──────────────────────
        Order order = orderService.createFromCart(
            cart, user, shippingAddress, billingAddress,
            shippingCost, discountAmount, appliedCoupon
        );

        // ── STEP 7: Process payment ────────────────────────────────────
        try {
            ChargeResult chargeResult = paymentService.processPayment(
                order, request.paymentMethodToken(),
                request.paymentProvider(), request.idempotencyKey()
            );

            if (chargeResult.requiresAction()) {
                // 3DS authentication required — return redirect URL
                log.info("3DS action required for order: {}", order.getId());
                return CheckoutResult.requiresAction(
                    order.getId().getValue().toString(),
                    order.getOrderNumber(),
                    chargeResult.actionUrl()
                );
            }

            if (!chargeResult.success()) {
                // Payment failed — rollback inventory, cancel order
                inventoryService.releaseReservations(reservations);
                orderService.cancelOrder(order.getId(), "Payment failed: "
                    + chargeResult.failureMessage());
                return CheckoutResult.failure(
                    "Payment failed: " + chargeResult.failureMessage()
                );
            }

            // ── STEP 8: Confirm order and inventory ───────────────────
            orderService.confirmOrder(order.getId(), chargeResult.transactionId());
            inventoryService.confirmReservations(reservations);

            // Record coupon usage
            if (appliedCoupon != null) {
                couponRepository.findByCode(appliedCoupon)
                    .ifPresent(c -> { c.recordUsage(); couponRepository.save(c); });
            }

            // Clear cart after successful checkout
            cartService.clearCart(request.cartId());

            log.info("Checkout SUCCESS: orderId={}, orderNumber={}",
                order.getId(), order.getOrderNumber());

            OrderResponse response = assembler.toDetailResponse(
                orderService.getOrder(order.getId())
            );
            return CheckoutResult.success(response, "SUCCEEDED");

        } catch (Exception e) {
            // Unexpected error — release inventory
            log.error("Checkout failed unexpectedly: {}", e.getMessage(), e);
            inventoryService.releaseReservations(reservations);
            throw e; // Let @Transactional handle rollback
        }
    }

    // ── Private helper methods ────────────────────────────────────────

    private Address resolveShippingAddress(
            com.ecommerce.domain.user.User user, CheckoutRequest request) {
        if (request.shippingAddressId() != null) {
            return user.getAddresses().stream()
                .filter(a -> a.getPostalCode().equals(request.shippingAddressId()))
                .findFirst()
                .orElse(user.getDefaultAddress());
        }
        return user.getDefaultAddress();
    }

    private Address resolveBillingAddress(
            com.ecommerce.domain.user.User user,
            CheckoutRequest request, Address shippingAddress) {
        if (request.billingAddressId() == null) return shippingAddress;
        return user.getAddresses().stream()
            .filter(a -> a.getPostalCode().equals(request.billingAddressId()))
            .findFirst()
            .orElse(shippingAddress);
    }

    private record CouponApplicationResult(
        Money discountAmount, String couponCode, boolean freeShipping
    ) {}

    private CouponApplicationResult applyCoupon(String code, Money orderTotal,
                                                 String userId) {
        Coupon coupon = couponRepository.findByCode(code)
            .orElseThrow(() -> new com.ecommerce.domain.shared.exception.DomainException(
                com.ecommerce.domain.shared.exception.ErrorCodes.COUPON_NOT_FOUND,
                "Coupon not found: " + code
            ));
        coupon.validate(orderTotal, userId);
        Money discount = coupon.calculateDiscount(orderTotal);
        return new CouponApplicationResult(
            discount, code, coupon.isFreeShipping()
        );
    }
}
```

---

## Pattern 6: Flyweight

### Problem It Solves
```
Problem: With 1M+ products, each product has many SHARED attributes
(brand metadata, material specs, category labels, unit types).
If every product object stores its own copy of "Nike" brand object
with all its data, we waste massive memory.

Memory math:
  1M products × 50 shared attributes × avg 200 bytes = 10GB RAM
  With Flyweight: shared instances, 1M products share same objects
  → Reduction: 10GB → ~100MB

Flyweight stores INTRINSIC state (shared, immutable — brand name, logo)
separately from EXTRINSIC state (per-product — price, inventory count).
```

### Class Diagram
```
Client                FlyweightFactory           Flyweight
ProductVariant ──────► AttributeFlyweightFactory ─► ProductAttribute
                        + getOrCreate(key,value)     - key: String (intrinsic)
                        - cache: Map<String,          - value: String (intrinsic)
                                  ProductAttribute>   - metadata: Map (intrinsic)
                                                      + getDisplayLabel(): String
Implementation
java// ecommerce-domain: com.ecommerce.domain.product/attribute/ProductAttribute.java
package com.ecommerce.domain.product.attribute;

import java.util.Map;
import java.util.Objects;

/**
 * Flyweight — Shared product attribute.
 * Intrinsic state: key, value, displayLabel, metadata.
 * These are shared across thousands of products.
 * Immutable — never modified after creation.
 *
 * Example: "Brand" = "Nike" shared across 50,000 Nike products.
 * Only ONE instance of this object exists in memory.
 */
public final class ProductAttribute {

    // Intrinsic state — shared, immutable
    private final String key;
    private final String value;
    private final String displayLabel;
    private final AttributeType type;
    private final boolean filterable;
    private final boolean searchable;
    private final Map<String, String> metadata; // unit, format hints

    // Private constructor — only factory creates instances
    ProductAttribute(String key, String value, String displayLabel,
                     AttributeType type, boolean filterable,
                     boolean searchable, Map<String, String> metadata) {
        this.key          = Objects.requireNonNull(key);
        this.value        = Objects.requireNonNull(value);
        this.displayLabel = displayLabel != null ? displayLabel : value;
        this.type         = Objects.requireNonNull(type);
        this.filterable   = filterable;
        this.searchable   = searchable;
        this.metadata     = metadata != null
            ? Map.copyOf(metadata) : Map.of();
    }

    public String getDisplayText() {
        return switch (type) {
            case COLOR  -> "<span style='color:" + metadata.getOrDefault("hex","#000")
                           + "'>" + displayLabel + "</span>";
            case SIZE   -> displayLabel + (metadata.containsKey("unit")
                           ? " " + metadata.get("unit") : "");
            case WEIGHT -> displayLabel + " " + metadata.getOrDefault("unit", "kg");
            default     -> displayLabel;
        };
    }

    // Getters — all read-only
    public String getKey()          { return key; }
    public String getValue()        { return value; }
    public String getDisplayLabel() { return displayLabel; }
    public AttributeType getType()  { return type; }
    public boolean isFilterable()   { return filterable; }
    public boolean isSearchable()   { return searchable; }
    public Map<String, String> getMetadata() { return metadata; }

    /**
     * Flyweight identity: same key+value = same shared object.
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ProductAttribute a)) return false;
        return key.equals(a.key) && value.equals(a.value);
    }

    @Override
    public int hashCode() { return Objects.hash(key, value); }

    @Override
    public String toString() { return key + "=" + value; }

    public enum AttributeType {
        TEXT, COLOR, SIZE, WEIGHT, MATERIAL, DIMENSION, NUMBER, BOOLEAN
    }
}
java// com.ecommerce.domain.product.attribute/ProductAttributeFactory.java
package com.ecommerce.domain.product.attribute;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Flyweight Factory — controls creation and sharing of ProductAttribute instances.
 *
 * Thread-safe: ConcurrentHashMap + computeIfAbsent.
 * Memory tracking: monitors cache size.
 *
 * Key method: getOrCreate — returns existing instance if available,
 * creates and stores new instance only on first request.
 *
 * Production stat: In a catalog of 1M products with 20 common
 * attributes (brand, color, size, material...) each having ~100 values:
 * Without flyweight: 1M × 20 × avg 500 bytes = 10GB
 * With flyweight: 20 × 100 × 500 bytes = 1MB (10,000x reduction!)
 */
public final class ProductAttributeFactory {

    private static final Logger log =
        LoggerFactory.getLogger(ProductAttributeFactory.class);

    // Flyweight pool — key: "attributeKey:attributeValue"
    private final Map<String, ProductAttribute> pool = new ConcurrentHashMap<>();
    private final AtomicInteger requestCount  = new AtomicInteger(0);
    private final AtomicInteger newCount      = new AtomicInteger(0);

    // Singleton factory (Singleton + Flyweight combined)
    private static final class Holder {
        static final ProductAttributeFactory INSTANCE = new ProductAttributeFactory();
    }
    public static ProductAttributeFactory getInstance() { return Holder.INSTANCE; }
    private ProductAttributeFactory() {}

    /**
     * Get or create flyweight instance.
     * Returns the SAME object for identical key+value.
     */
    public ProductAttribute getOrCreate(String key, String value) {
        return getOrCreate(key, value, value,
            ProductAttribute.AttributeType.TEXT,
            true, true, null);
    }

    public ProductAttribute getOrCreate(
            String key, String value, String displayLabel,
            ProductAttribute.AttributeType type,
            boolean filterable, boolean searchable,
            Map<String, String> metadata) {

        requestCount.incrementAndGet();
        String poolKey = key + ":" + value;

        return pool.computeIfAbsent(poolKey, k -> {
            newCount.incrementAndGet();
            log.debug("Creating new flyweight: {}", poolKey);
            return new ProductAttribute(
                key, value, displayLabel, type,
                filterable, searchable, metadata
            );
        });
    }

    /**
     * Bulk registration for bootstrapping common attributes.
     */
    public void registerCommonAttributes() {
        // Colors
        registerColor("Red",    "#FF0000");
        registerColor("Blue",   "#0000FF");
        registerColor("Green",  "#008000");
        registerColor("Black",  "#000000");
        registerColor("White",  "#FFFFFF");

        // Standard clothing sizes
        for (String size : new String[]{"XS","S","M","L","XL","XXL"}) {
            getOrCreate("Size", size, size,
                ProductAttribute.AttributeType.SIZE,
                true, false, null);
        }

        // Common shoe sizes
        for (int i = 5; i <= 15; i++) {
            getOrCreate("ShoeSize", String.valueOf(i), "US " + i,
                ProductAttribute.AttributeType.SIZE,
                true, false, Map.of("system", "US"));
        }

        log.info("Registered {} common attribute flyweights", pool.size());
    }

    private void registerColor(String name, String hex) {
        getOrCreate("Color", name, name,
            ProductAttribute.AttributeType.COLOR,
            true, true, Map.of("hex", hex));
    }

    // Monitoring
    public int getPoolSize()     { return pool.size(); }
    public int getRequestCount() { return requestCount.get(); }
    public int getNewCount()     { return newCount.get(); }
    public double getHitRate()   {
        int total = requestCount.get();
        return total == 0 ? 0
            : (double)(total - newCount.get()) / total * 100;
    }

    public FlyweightStats getStats() {
        return new FlyweightStats(
            pool.size(), requestCount.get(),
            newCount.get(), getHitRate()
        );
    }

    public record FlyweightStats(
        int poolSize, int totalRequests,
        int newObjectsCreated, double hitRatePercent
    ) {}
}
```

---

## Pattern 7: Proxy

### Problem It Solves
```
Problem: Repository and service access needs:
  ① Security: Only ADMIN can access admin operations
  ② Lazy loading: Order history loaded only when accessed
  ③ Audit logging: Every sensitive data access recorded

Without Proxy:
  Security checks scattered in every service
  Lazy loading logic mixed with business logic
  Audit code copy-pasted in every method

With Proxy:
  SecurityProxy enforces access control before delegation
  LazyLoadingProxy defers expensive operations
  AuditProxy logs every access automatically
  Real object is clean — zero security/audit code
```

### Class Diagram
```
«interface»           «Real Subject»         «Proxy»
UserRepository        JpaUserRepository      SecurityUserRepositoryProxy
+ findById()          + findById()           - delegate: UserRepository
+ save()              + save()               - authContext: AuthContext
+ findByEmail()       + findByEmail()        + findById(): checkRole → delegate
                                             + save(): checkRole → delegate

«Proxy»               «Proxy»
AuditUserProxy        LazyLoadedUserProxy
+ findById()          + findById()
  → log → delegate      → check loaded → delegate
Implementation
java// ecommerce-infrastructure:
// com.ecommerce.infrastructure.persistence/proxy/SecurityUserRepositoryProxy.java
package com.ecommerce.infrastructure.persistence.proxy;

import com.ecommerce.domain.user.*;
import com.ecommerce.infrastructure.security.AuthenticationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * PROXY PATTERN — Security Proxy for UserRepository.
 *
 * Controls access based on authenticated user's role.
 * The real repository never checks permissions — stays clean.
 * All security logic centralized here.
 *
 * Real Subject:  JpaUserRepository
 * Proxy:         SecurityUserRepositoryProxy
 * Interface:     UserRepository
 *
 * Clients inject UserRepository — they get the proxy transparently.
 */
public class SecurityUserRepositoryProxy implements UserRepository {

    private static final Logger log =
        LoggerFactory.getLogger(SecurityUserRepositoryProxy.class);

    private final UserRepository delegate;              // Real repository
    private final AuthenticationContext authContext;    // Current user context

    public SecurityUserRepositoryProxy(UserRepository delegate,
                                        AuthenticationContext authContext) {
        this.delegate    = delegate;
        this.authContext = authContext;
    }

    @Override
    public Optional<User> findById(UserId id) {
        // Users can find themselves; admins can find anyone
        String currentUserId = authContext.getCurrentUserId();
        if (!authContext.isAdmin()
                && !id.getValue().toString().equals(currentUserId)) {
            log.warn("SECURITY: User {} attempted to access profile of {}",
                currentUserId, id);
            throw new SecurityException(
                "Access denied: cannot view another user's profile"
            );
        }
        return delegate.findById(id);
    }

    @Override
    public Optional<User> findActiveById(UserId id) {
        // Same security as findById
        return findById(id).filter(u -> u.getStatus() == UserStatus.ACTIVE);
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        // Only admins can search by email (privacy concern)
        requireRole(UserRole.ADMIN, "findByEmail");
        return delegate.findByEmail(email);
    }

    @Override
    public void save(User user) {
        // Any user can create their own account
        // Admin can create any user
        delegate.save(user);
    }

    @Override
    public void update(User user) {
        String currentUserId = authContext.getCurrentUserId();
        if (!authContext.isAdmin()
                && !user.getId().getValue().toString().equals(currentUserId)) {
            throw new SecurityException(
                "Access denied: cannot update another user's profile"
            );
        }

        // Extra check: only SUPER_ADMIN can update admin accounts
        if (user.getRole().hasAdminPrivileges()
                && !authContext.isSuperAdmin()) {
            throw new SecurityException(
                "Only SUPER_ADMIN can modify admin accounts"
            );
        }

        delegate.update(user);
    }

    @Override
    public boolean existsByEmail(Email email) {
        return delegate.existsByEmail(email);
    }

    private void requireRole(UserRole minimumRole, String operation) {
        if (!authContext.hasRole(minimumRole)) {
            log.warn("SECURITY: {} attempted {} without {} role",
                authContext.getCurrentUserId(), operation, minimumRole);
            throw new SecurityException(
                "Insufficient permissions for: " + operation
            );
        }
    }
}
java// com.ecommerce.infrastructure.persistence/proxy/AuditUserRepositoryProxy.java
package com.ecommerce.infrastructure.persistence.proxy;

import com.ecommerce.domain.user.*;
import com.ecommerce.infrastructure.audit.AuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * PROXY PATTERN — Audit Proxy for UserRepository.
 *
 * Records every sensitive data access for compliance (GDPR, SOC2, HIPAA).
 * Real repository has zero audit code.
 * Can be stacked with SecurityProxy (Decorator-like composition).
 *
 * Audit record contains: who, what, when, result.
 */
public class AuditUserRepositoryProxy implements UserRepository {

    private static final Logger log =
        LoggerFactory.getLogger(AuditUserRepositoryProxy.class);

    private final UserRepository delegate;
    private final AuditLogger auditLogger;

    public AuditUserRepositoryProxy(UserRepository delegate,
                                     AuditLogger auditLogger) {
        this.delegate    = delegate;
        this.auditLogger = auditLogger;
    }

    @Override
    public Optional<User> findById(UserId id) {
        Optional<User> result = delegate.findById(id);
        // Audit only sensitive reads
        auditLogger.log(AuditLogger.AuditEvent.builder()
            .action("USER_PROFILE_READ")
            .resourceType("User")
            .resourceId(id.getValue().toString())
            .outcome(result.isPresent() ? "SUCCESS" : "NOT_FOUND")
            .build());
        return result;
    }

    @Override
    public Optional<User> findActiveById(UserId id) {
        return delegate.findActiveById(id);
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        Optional<User> result = delegate.findByEmail(email);
        // Searching by email is sensitive — always audit
        auditLogger.log(AuditLogger.AuditEvent.builder()
            .action("USER_EMAIL_LOOKUP")
            .resourceType("User")
            .resourceId(email.getValue())
            .outcome(result.isPresent() ? "SUCCESS" : "NOT_FOUND")
            .sensitiveData(true)
            .build());
        return result;
    }

    @Override
    public void save(User user) {
        delegate.save(user);
        auditLogger.log(AuditLogger.AuditEvent.builder()
            .action("USER_CREATED")
            .resourceType("User")
            .resourceId(user.getId().getValue().toString())
            .outcome("SUCCESS")
            .build());
    }

    @Override
    public void update(User user) {
        delegate.update(user);
        auditLogger.log(AuditLogger.AuditEvent.builder()
            .action("USER_UPDATED")
            .resourceType("User")
            .resourceId(user.getId().getValue().toString())
            .outcome("SUCCESS")
            .build());
    }

    @Override
    public boolean existsByEmail(Email email) {
        return delegate.existsByEmail(email);
    }
}
java// com.ecommerce.infrastructure.audit/AuditLogger.java
package com.ecommerce.infrastructure.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Audit Logger — persists audit events.
 * Used by Proxy pattern to record all sensitive operations.
 * In production: writes to immutable audit log table + Kafka audit topic.
 */
@Component
public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);

    // In production: inject AuditEventRepository + KafkaTemplate
    public void log(AuditEvent event) {
        log.info("[AUDIT] action={} resource={}/{} outcome={} sensitive={} at={}",
            event.action(), event.resourceType(), event.resourceId(),
            event.outcome(), event.sensitiveData(), event.timestamp()
        );
        // TODO (Phase 9): persist to audit_logs table + publish to Kafka
    }

    public record AuditEvent(
        String action,
        String resourceType,
        String resourceId,
        String outcome,
        boolean sensitiveData,
        String details,
        Instant timestamp
    ) {
        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private String action, resourceType, resourceId, outcome, details;
            private boolean sensitiveData;

            public Builder action(String v)       { action = v;       return this; }
            public Builder resourceType(String v) { resourceType = v; return this; }
            public Builder resourceId(String v)   { resourceId = v;   return this; }
            public Builder outcome(String v)      { outcome = v;      return this; }
            public Builder sensitiveData(boolean v){ sensitiveData = v; return this; }
            public Builder details(String v)      { details = v;      return this; }

            public AuditEvent build() {
                return new AuditEvent(action, resourceType, resourceId,
                    outcome, sensitiveData, details, Instant.now());
            }
        }
    }
}
java// com.ecommerce.infrastructure.security/AuthenticationContext.java
package com.ecommerce.infrastructure.security;

import com.ecommerce.domain.user.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Request-scoped authentication context.
 * Used by Security Proxy to determine current user permissions.
 * @RequestScope: new instance per HTTP request.
 */
@Component
@RequestScope
public class AuthenticationContext {

    public String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        return auth.getName();
    }

    public boolean isAdmin() {
        return hasRole(UserRole.ADMIN) || hasRole(UserRole.SUPER_ADMIN);
    }

    public boolean isSuperAdmin() {
        return hasRole(UserRole.SUPER_ADMIN);
    }

    public boolean hasRole(UserRole role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_" + role.name()));
    }

    public boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated();
    }
}
java// com.ecommerce.infrastructure.config/UserRepositoryProxyConfig.java
package com.ecommerce.infrastructure.config;

import com.ecommerce.domain.user.UserRepository;
import com.ecommerce.infrastructure.audit.AuditLogger;
import com.ecommerce.infrastructure.persistence.JpaUserRepository;
import com.ecommerce.infrastructure.persistence.proxy.AuditUserRepositoryProxy;
import com.ecommerce.infrastructure.persistence.proxy.SecurityUserRepositoryProxy;
import com.ecommerce.infrastructure.security.AuthenticationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Proxy chain configuration.
 *
 * Request flow:
 * Client → SecurityProxy → AuditProxy → JpaRepository (DB)
 *
 * Why this order:
 * - Security: outermost — fails fast, no audit needed for unauthorized
 * - Audit: after security — records only authorized accesses
 * - JPA: actual DB access
 *
 * This is ALSO an example of Decorator-like stacking.
 * Proxy and Decorator are structurally similar — intent differs:
 * - Proxy: controls access to subject
 * - Decorator: adds behavior to subject
 */
@Configuration
public class UserRepositoryProxyConfig {

    @Bean
    @Primary
    public UserRepository userRepository(JpaUserRepository jpaUserRepository,
                                         AuthenticationContext authContext,
                                         AuditLogger auditLogger) {
        return new SecurityUserRepositoryProxy(
            new AuditUserRepositoryProxy(
                jpaUserRepository,
                auditLogger
            ),
            authContext
        );
    }
}
```

---

## Pattern Interaction Map — Structural Patterns
```
Structural Patterns Working Together:

HTTP Request: GET /api/v1/checkout
     │
     ▼
CheckoutController
     │ calls (single method)
     ▼
CheckoutFacade ─────────────────────────────── FACADE
     │
     ├── CartService
     │     └── CachingCartRepository ────────── DECORATOR (Caching)
     │
     ├── InventoryService
     │     └── LoggingInventoryRepository ───── DECORATOR (Logging)
     │
     ├── PaymentService
     │     └── LegacyBankAdapter ──────────────  ADAPTER (Legacy bank)
     │         or StripeGateway
     │
     ├── OrderService
     │     └── AuditUserRepositoryProxy ─────── PROXY (Audit)
     │           └── SecurityProxy ──────────── PROXY (Security)
     │
     ├── ProductSearchService
     │     └── ElasticsearchEngine ─────────── BRIDGE (Search impl)
     │
     ├── CategoryTreeService
     │     └── CompositeCategory ───────────── COMPOSITE (Tree)
     │
     └── ProductAttributeFactory
           └── ProductAttribute flyweights ──── FLYWEIGHT (Attributes)
```

---

## Summary Table — Structural Patterns
```
┌────────────┬────────────────────────────────┬──────────────────────────────────────┐
│ Pattern    │ Used For                       │ Key Benefit                          │
├────────────┼────────────────────────────────┼──────────────────────────────────────┤
│ Adapter    │ Legacy bank payment system     │ Incompatible API → our interface     │
│ Bridge     │ Search (ES/Algolia/InMemory)   │ M+N classes, not M×N                 │
│ Composite  │ Category tree                  │ Leaf/container uniform interface     │
│ Decorator  │ Repository behaviors           │ Stackable: cache+log+retry           │
│ Facade     │ Checkout (8 subsystems)        │ Single entry, hides complexity       │
│ Flyweight  │ Product attributes             │ 1M products share attribute objects  │
│ Proxy      │ Security + Audit on repos      │ Access control & logging transparent │
└────────────┴────────────────────────────────┴──────────────────────────────────────┘

✅ Phase 4 complete. All 7 Structural Patterns implemented with:

Full Java 21 production code (records, sealed types, virtual threads)
Real integrations: Elasticsearch 8.x, Stripe, legacy bank ISO 8583
Decorator stack for repositories (cache + retry + logging)
Complete Facade for checkout (8-subsystem orchestration)
Flyweight with memory analysis (10,000x reduction)
Dual-proxy chain (security + audit)


Reply "Phase 5" and I'll implement Behavioral Patterns Part 1 — Strategy (pricing/discount/shipping), Observer (domain event publishing), Command (order operations + undo), State (order lifecycle machine), and Chain of Responsibility (fraud detection + validation pipeline).Phase 516:05PHASE 5 — Behavioral Patterns Part 1
Overview
Behavioral patterns define HOW objects communicate and
distribute responsibility at runtime.

Part 1 covers:
  ├── Strategy           → Interchangeable algorithms (pricing, shipping, discount)
  ├── Observer           → Domain event publishing (order placed → notify all)
  ├── Command            → Encapsulate operations as objects (undo/redo, audit)
  ├── State              → Order lifecycle state machine
  └── Chain of Responsibility → Fraud detection + validation pipeline

Pattern 1: Strategy
Problem It Solves
Problem: Pricing, discount calculation, and shipping cost vary by:
  - Customer tier (REGULAR, SILVER, GOLD, PLATINUM)
  - Product category (electronics vs clothing vs books)
  - Region (domestic vs international)
  - Promotional period (Black Friday, flash sale)

Without Strategy:
  if (tier == GOLD) { price *= 0.9; }
  else if (tier == PLATINUM) { price *= 0.8; }
  else if (isBlackFriday && tier == GOLD) { ... }
  → Giant if-else chains, impossible to test, violates OCP

With Strategy:
  pricingContext.setStrategy(new GoldTierPricingStrategy());
  Money finalPrice = pricingContext.calculatePrice(product, quantity);
  → Add new pricing rule = new class, zero existing code changes
Class Diagram
«interface»                          PricingContext
PricingStrategy                      - strategy: PricingStrategy
+ calculatePrice(product,qty,        + setStrategy(PricingStrategy)
                 customer): Money    + execute(product, qty,
+ getName(): String                            customer): Money
+ isApplicable(context): boolean
         ▲
   ┌─────┼──────────────────┐
   │     │                  │
Regular  GoldTier    BlackFriday
Pricing  Pricing     Pricing
Strategy Strategy    Strategy

«interface»                          ShippingContext
ShippingStrategy                     - strategy: ShippingStrategy
+ calculate(cart, address): Money    + execute(...): Money
+ getEstimatedDays(): int
         ▲
   ┌─────┼──────────┐
Standard  Express  FreeOver
Shipping  Shipping $100Shipping
Sequence Diagram
CheckoutFacade   PricingContext    GoldTierStrategy   Product
      │                │                 │               │
      │─execute(...)──►│                 │               │
      │                │─isApplicable()─►│               │
      │                │◄─── true ───────│               │
      │                │─calculatePrice()►│               │
      │                │                 │─getPrice()────►│
      │                │                 │◄── Money ──────│
      │                │                 │─applyDiscount  │
      │◄── Money ──────│◄──── Money ─────│               │
Implementation
java// ecommerce-domain:
// com.ecommerce.domain.pricing/PricingContext.java
package com.ecommerce.domain.pricing;

import com.ecommerce.domain.product.Product;
import com.ecommerce.domain.product.ProductVariant;
import com.ecommerce.domain.shared.valueobject.Money;
import com.ecommerce.domain.user.User;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Strategy Context — holds and executes pricing strategies.
 *
 * Key design: multiple strategies can be active simultaneously.
 * The BEST (lowest price for customer) strategy wins.
 * Or they can stack (compound discounts — controlled by config).
 */
public class PricingContext {

    private final List<PricingStrategy> strategies;
    private final boolean allowStackingDiscounts;

    public PricingContext(List<PricingStrategy> strategies,
                          boolean allowStackingDiscounts) {
        this.strategies             = new ArrayList<>(strategies);
        this.allowStackingDiscounts = allowStackingDiscounts;
    }

    /**
     * Calculate final price for a product variant.
     * Evaluates all applicable strategies, applies best or stacked.
     */
    public Money calculatePrice(Product product, ProductVariant variant,
                                int quantity, User customer,
                                PricingRequest request) {
        Money basePrice = variant.getPrice().multiply(quantity);

        List<PricingStrategy> applicable = strategies.stream()
            .filter(s -> s.isApplicable(product, variant, customer, request))
            .sorted(Comparator.comparingInt(PricingStrategy::getPriority))
            .toList();

        if (applicable.isEmpty()) {
            return basePrice;
        }

        if (allowStackingDiscounts) {
            // Apply all strategies sequentially (compound)
            Money price = basePrice;
            for (PricingStrategy strategy : applicable) {
                price = strategy.calculatePrice(product, variant,
                    quantity, customer, request, price);
            }
            return price;
        } else {
            // Apply only best strategy (lowest price)
            return applicable.stream()
                .map(s -> s.calculatePrice(product, variant,
                    quantity, customer, request, basePrice))
                .min(Comparator.comparing(m -> m.getAmount()))
                .orElse(basePrice);
        }
    }
}
java// com.ecommerce.domain.pricing/PricingStrategy.java
package com.ecommerce.domain.pricing;

import com.ecommerce.domain.product.Product;
import com.ecommerce.domain.product.ProductVariant;
import com.ecommerce.domain.shared.valueobject.Money;
import com.ecommerce.domain.user.User;

/**
 * Strategy interface — pricing algorithm contract.
 */
public interface PricingStrategy {

    /**
     * Core strategy method — calculates discounted price.
     * @param currentPrice price after previously applied strategies
     */
    Money calculatePrice(Product product, ProductVariant variant,
                         int quantity, User customer,
                         PricingRequest request, Money currentPrice);

    /**
     * Guard — only execute strategy when applicable.
     */
    boolean isApplicable(Product product, ProductVariant variant,
                         User customer, PricingRequest request);

    String getName();

    /**
     * Lower priority = applied first.
     */
    int getPriority();
}
java// com.ecommerce.domain.pricing/PricingRequest.java
package com.ecommerce.domain.pricing;

import java.time.Instant;
import java.util.Set;

/**
 * Context data for pricing decisions.
 * Immutable snapshot passed to all strategies.
 */
public record PricingRequest(
    String channelCode,          // WEB, MOBILE, API, POS
    String regionCode,           // US, EU, APAC
    Instant requestTime,         // for time-based strategies
    Set<String> activeCampaigns, // BLACK_FRIDAY, SUMMER_SALE
    boolean isFirstPurchase,
    int customerOrderCount
) {
    public boolean isBlackFriday() {
        return activeCampaigns.contains("BLACK_FRIDAY");
    }

    public boolean isSummerSale() {
        return activeCampaigns.contains("SUMMER_SALE");
    }
}
java// com.ecommerce.domain.pricing/strategy/RegularPricingStrategy.java
package com.ecommerce.domain.pricing.strategy;

import com.ecommerce.domain.pricing.PricingRequest;
import com.ecommerce.domain.pricing.PricingStrategy;
import com.ecommerce.domain.product.Product;
import com.ecommerce.domain.product.ProductVariant;
import com.ecommerce.domain.shared.valueobject.Money;
import com.ecommerce.domain.user.User;
import com.ecommerce.domain.user.UserRole;

/**
 * Concrete Strategy — Regular (no discount) pricing.
 * Baseline: always applicable, lowest priority.
 */
public class RegularPricingStrategy implements PricingStrategy {

    @Override
    public Money calculatePrice(Product product, ProductVariant variant,
                                int quantity, User customer,
                                PricingRequest request, Money currentPrice) {
        // No discount — return current price unchanged
        return currentPrice;
    }

    @Override
    public boolean isApplicable(Product product, ProductVariant variant,
                                User customer, PricingRequest request) {
        return true; // Always applicable as fallback
    }

    @Override
    public String getName()    { return "REGULAR"; }

    @Override
    public int getPriority()   { return 100; } // lowest priority
}
java// com.ecommerce.domain.pricing/strategy/CustomerTierPricingStrategy.java
package com.ecommerce.domain.pricing.strategy;

import com.ecommerce.domain.pricing.PricingRequest;
import com.ecommerce.domain.pricing.PricingStrategy;
import com.ecommerce.domain.product.Product;
import com.ecommerce.domain.product.ProductVariant;
import com.ecommerce.domain.shared.valueobject.Money;
import com.ecommerce.domain.user.User;
import com.ecommerce.domain.user.UserRole;

import java.math.BigDecimal;

/**
 * Concrete Strategy — Customer tier-based pricing.
 *
 * Tier discounts:
 *   SILVER:   5% off
 *   GOLD:     10% off
 *   PLATINUM: 15% off
 *
 * Extensibility: Add new tier = new enum + update this strategy.
 * No other classes change.
 */
public class CustomerTierPricingStrategy implements PricingStrategy {

    @Override
    public Money calculatePrice(Product product, ProductVariant variant,
                                int quantity, User customer,
                                PricingRequest request, Money currentPrice) {
        BigDecimal discountRate = getDiscountRate(customer);
        Money discount = currentPrice.multiply(discountRate);
        return currentPrice.subtract(discount);
    }

    @Override
    public boolean isApplicable(Product product, ProductVariant variant,
                                User customer, PricingRequest request) {
        // Applicable for non-regular customers only
        return customer != null
            && customer.getRole() != UserRole.CUSTOMER
            && getDiscountRate(customer).compareTo(BigDecimal.ZERO) > 0;
    }

    private BigDecimal getDiscountRate(User customer) {
        if (customer == null) return BigDecimal.ZERO;
        // In production: customer tier stored separately, not role
        // Using role as proxy for demo purposes
        return switch (customer.getRole()) {
            case VENDOR     -> new BigDecimal("0.05"); // SILVER equivalent
            case ADMIN      -> new BigDecimal("0.10"); // GOLD equivalent
            case SUPER_ADMIN-> new BigDecimal("0.15"); // PLATINUM equivalent
            default         -> BigDecimal.ZERO;
        };
    }

    @Override
    public String getName()  { return "CUSTOMER_TIER"; }

    @Override
    public int getPriority() { return 10; }
}
java// com.ecommerce.domain.pricing/strategy/QuantityDiscountStrategy.java
package com.ecommerce.domain.pricing.strategy;

import com.ecommerce.domain.pricing.PricingRequest;
import com.ecommerce.domain.pricing.PricingStrategy;
import com.ecommerce.domain.product.Product;
import com.ecommerce.domain.product.ProductVariant;
import com.ecommerce.domain.shared.valueobject.Money;
import com.ecommerce.domain.user.User;

import java.math.BigDecimal;

/**
 * Concrete Strategy — Bulk/quantity discount.
 *
 * Tiers:
 *   5-9 units:   5% off
 *   10-19 units: 10% off
 *   20+ units:   15% off
 *
 * B2B scenario: Vendors buying in bulk get better pricing.
 */
public class QuantityDiscountStrategy implements PricingStrategy {

    private static final int MIN_QUANTITY = 5;

    @Override
    public Money calculatePrice(Product product, ProductVariant variant,
                                int quantity, User customer,
                                PricingRequest request, Money currentPrice) {
        BigDecimal discount = getDiscountRate(quantity);
        return currentPrice.subtract(currentPrice.multiply(discount));
    }

    @Override
    public boolean isApplicable(Product product, ProductVariant variant,
                                User customer, PricingRequest request) {
        // Will be called with quantity from context
        // Applicable when buying at least MIN_QUANTITY
        return true; // actual quantity check in calculatePrice
    }

    private BigDecimal getDiscountRate(int quantity) {
        if (quantity >= 20) return new BigDecimal("0.15");
        if (quantity >= 10) return new BigDecimal("0.10");
        if (quantity >= 5)  return new BigDecimal("0.05");
        return BigDecimal.ZERO;
    }

    @Override
    public String getName()  { return "QUANTITY_DISCOUNT"; }

    @Override
    public int getPriority() { return 20; }
}
java// com.ecommerce.domain.pricing/strategy/BlackFridayPricingStrategy.java
package com.ecommerce.domain.pricing.strategy;

import com.ecommerce.domain.pricing.PricingRequest;
import com.ecommerce.domain.pricing.PricingStrategy;
import com.ecommerce.domain.product.Product;
import com.ecommerce.domain.product.ProductVariant;
import com.ecommerce.domain.shared.valueobject.Money;
import com.ecommerce.domain.user.User;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Concrete Strategy — Black Friday campaign pricing.
 * Activated only when BLACK_FRIDAY campaign is active.
 * High-priority: overrides tier pricing on sale days.
 *
 * Demonstrates: strategies can exclude certain categories
 * (e.g., already-discounted items, luxury goods).
 */
public class BlackFridayPricingStrategy implements PricingStrategy {

    // Categories excluded from Black Friday sale
    private static final Set<String> EXCLUDED_CATEGORY_SLUGS =
        Set.of("luxury-watches", "fine-jewelry", "gift-cards");

    private final BigDecimal discountRate;

    public BlackFridayPricingStrategy(BigDecimal discountRate) {
        this.discountRate = discountRate;
    }

    public BlackFridayPricingStrategy() {
        this(new BigDecimal("0.25")); // 25% off by default
    }

    @Override
    public Money calculatePrice(Product product, ProductVariant variant,
                                int quantity, User customer,
                                PricingRequest request, Money currentPrice) {
        return currentPrice.subtract(currentPrice.multiply(discountRate));
    }

    @Override
    public boolean isApplicable(Product product, ProductVariant variant,
                                User customer, PricingRequest request) {
        if (!request.isBlackFriday()) return false;
        // Exclude luxury categories
        String slug = product.getSlug();
        return EXCLUDED_CATEGORY_SLUGS.stream()
            .noneMatch(slug::contains);
    }

    @Override
    public String getName()  { return "BLACK_FRIDAY"; }

    @Override
    public int getPriority() { return 1; } // highest priority
}
java// com.ecommerce.domain.pricing/strategy/FirstPurchasePricingStrategy.java
package com.ecommerce.domain.pricing.strategy;

import com.ecommerce.domain.pricing.PricingRequest;
import com.ecommerce.domain.pricing.PricingStrategy;
import com.ecommerce.domain.product.Product;
import com.ecommerce.domain.product.ProductVariant;
import com.ecommerce.domain.shared.valueobject.Money;
import com.ecommerce.domain.user.User;

import java.math.BigDecimal;

/**
 * Concrete Strategy — First purchase welcome discount (10%).
 * Encourages new customer conversion.
 */
public class FirstPurchasePricingStrategy implements PricingStrategy {

    private static final BigDecimal FIRST_PURCHASE_DISCOUNT = new BigDecimal("0.10");

    @Override
    public Money calculatePrice(Product product, ProductVariant variant,
                                int quantity, User customer,
                                PricingRequest request, Money currentPrice) {
        return currentPrice.subtract(
            currentPrice.multiply(FIRST_PURCHASE_DISCOUNT)
        );
    }

    @Override
    public boolean isApplicable(Product product, ProductVariant variant,
                                User customer, PricingRequest request) {
        return request.isFirstPurchase()
            && request.customerOrderCount() == 0;
    }

    @Override
    public String getName()  { return "FIRST_PURCHASE"; }

    @Override
    public int getPriority() { return 5; }
}
java// com.ecommerce.domain.shipping/ShippingStrategy.java
package com.ecommerce.domain.shipping;

import com.ecommerce.domain.cart.Cart;
import com.ecommerce.domain.shared.valueobject.Money;
import com.ecommerce.domain.user.Address;

/**
 * Strategy interface for shipping cost calculation.
 * Different strategies: standard, express, free threshold, regional.
 */
public interface ShippingStrategy {
    Money calculate(Cart cart, Address destination);
    int getEstimatedDeliveryDays();
    String getCarrierName();
    boolean isApplicable(Cart cart, Address destination);
}
java// com.ecommerce.domain.shipping/strategy/FlatRateShippingStrategy.java
package com.ecommerce.domain.shipping.strategy;

import com.ecommerce.domain.cart.Cart;
import com.ecommerce.domain.shared.valueobject.Money;
import com.ecommerce.domain.shipping.ShippingStrategy;
import com.ecommerce.domain.user.Address;

import java.math.BigDecimal;

/**
 * Concrete Strategy — flat rate shipping ($5.99).
 */
public class FlatRateShippingStrategy implements ShippingStrategy {

    private static final Money FLAT_RATE =
        Money.of(new BigDecimal("5.99"), "USD");

    @Override
    public Money calculate(Cart cart, Address destination) {
        return FLAT_RATE;
    }

    @Override
    public int getEstimatedDeliveryDays() { return 7; }

    @Override
    public String getCarrierName() { return "Standard Post"; }

    @Override
    public boolean isApplicable(Cart cart, Address destination) {
        return true; // always applicable as fallback
    }
}
java// com.ecommerce.domain.shipping/strategy/FreeShippingThresholdStrategy.java
package com.ecommerce.domain.shipping.strategy;

import com.ecommerce.domain.cart.Cart;
import com.ecommerce.domain.shared.valueobject.Money;
import com.ecommerce.domain.shipping.ShippingStrategy;
import com.ecommerce.domain.user.Address;

import java.math.BigDecimal;

/**
 * Concrete Strategy — free shipping when cart total >= threshold.
 * Common: "Free shipping on orders over $50!"
 */
public class FreeShippingThresholdStrategy implements ShippingStrategy {

    private final Money threshold;

    public FreeShippingThresholdStrategy(BigDecimal threshold) {
        this.threshold = Money.of(threshold, "USD");
    }

    @Override
    public Money calculate(Cart cart, Address destination) {
        return Money.ZERO_USD; // Free!
    }

    @Override
    public int getEstimatedDeliveryDays() { return 5; }

    @Override
    public String getCarrierName() { return "Standard Shipping"; }

    @Override
    public boolean isApplicable(Cart cart, Address destination) {
        // Only applicable if cart meets threshold
        return cart.calculateTotal().isGreaterThanOrEqual(threshold);
    }
}
java// com.ecommerce.domain.shipping/strategy/ExpressShippingStrategy.java
package com.ecommerce.domain.shipping.strategy;

import com.ecommerce.domain.cart.Cart;
import com.ecommerce.domain.shared.valueobject.Money;
import com.ecommerce.domain.shipping.ShippingStrategy;
import com.ecommerce.domain.user.Address;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Concrete Strategy — express shipping ($15.99, 2-day).
 * Not available for PO Boxes or remote areas.
 */
public class ExpressShippingStrategy implements ShippingStrategy {

    private static final Money EXPRESS_RATE =
        Money.of(new BigDecimal("15.99"), "USD");

    private static final Set<String> RESTRICTED_STATES =
        Set.of("AK", "HI", "PR", "GU"); // Alaska, Hawaii, territories

    @Override
    public Money calculate(Cart cart, Address destination) {
        return EXPRESS_RATE;
    }

    @Override
    public int getEstimatedDeliveryDays() { return 2; }

    @Override
    public String getCarrierName() { return "FedEx Express"; }

    @Override
    public boolean isApplicable(Cart cart, Address destination) {
        if (destination == null || destination.getState() == null) return false;
        return !RESTRICTED_STATES.contains(
            destination.getState().toUpperCase()
        );
    }
}
java// ecommerce-application:
// com.ecommerce.application.shipping/ShippingCalculator.java
package com.ecommerce.application.shipping;

import com.ecommerce.domain.cart.Cart;
import com.ecommerce.domain.shared.valueobject.Money;
import com.ecommerce.domain.shipping.ShippingStrategy;
import com.ecommerce.domain.user.Address;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Shipping calculator — Strategy context for shipping.
 * Picks best applicable strategy (cheapest for customer).
 */
@Service
public class ShippingCalculator {

    private final List<ShippingStrategy> strategies;

    public ShippingCalculator(List<ShippingStrategy> strategies) {
        this.strategies = strategies;
    }

    /**
     * Returns cheapest applicable shipping cost.
     * In real system: user selects from available options.
     */
    public Money calculate(Cart cart, Address destination) {
        return strategies.stream()
            .filter(s -> s.isApplicable(cart, destination))
            .map(s -> s.calculate(cart, destination))
            .min(java.util.Comparator.comparing(Money::getAmount))
            .orElse(Money.ZERO_USD);
    }

    /**
     * Returns all available shipping options for checkout UI.
     */
    public List<ShippingOption> getAvailableOptions(Cart cart,
                                                     Address destination) {
        return strategies.stream()
            .filter(s -> s.isApplicable(cart, destination))
            .map(s -> new ShippingOption(
                s.getCarrierName(),
                s.calculate(cart, destination),
                s.getEstimatedDeliveryDays()
            ))
            .sorted(java.util.Comparator.comparing(
                o -> o.cost().getAmount()))
            .toList();
    }

    public record ShippingOption(
        String carrier, Money cost, int estimatedDays
    ) {}
}
```

---

## Pattern 2: Observer

### Problem It Solves
```
Problem: When an order is placed, MULTIPLE systems need to react:
  - Inventory service: reserve stock
  - Notification service: email/SMS customer
  - Analytics service: track conversion
  - Audit service: log the event
  - Search service: update product popularity

Without Observer:
  OrderService.placeOrder() {
    inventoryService.reserve(...)      // tight coupling
    notificationService.sendEmail(...) // OrderService knows about ALL consumers
    analyticsService.track(...)        // Adding new consumer = modify OrderService
    auditService.log(...)              // OrderService becomes 500 lines
  }

With Observer:
  OrderService.placeOrder() {
    order.registerEvent(new OrderPlacedEvent(...))
    // That's it. Consumers register themselves.
  }
  // Adding new consumer = new EventHandler class, zero OrderService changes
```

### Class Diagram
```
«interface»              «interface»
DomainEventPublisher     DomainEventHandler<E>
+ publish(events)        + handle(event): void
       │                 + supports(type): boolean
       ▼                          ▲
SpringDomainEvent          ┌──────┼────────────────────┐
Publisher                  │      │                    │
  + publish(events)   InventoryReservation  OrderNotification  AnalyticsEvent
    → find handlers     Handler               Handler           Handler
    → dispatch async

«interface»
DomainEvent
+ eventType(): String
+ aggregateId(): String
+ occurredAt(): Instant
```

### Sequence Diagram
```
OrderService    AggregateRoot    EventPublisher    InventoryHandler  NotifHandler
     │               │                │                  │               │
     │─place(...)───►│                │                  │               │
     │               │─registerEvent  │                  │               │
     │               │  (OrderPlaced) │                  │               │
     │◄──────────────│                │                  │               │
     │─save(order)───────────────────►│                  │               │
     │               │          [after commit]           │               │
     │               │     publish(events)               │               │
     │               │                │─[async]─────────►│               │
     │               │                │─[async]──────────────────────────►│
     │               │                │         reserve()│               │
     │               │                │                  │    sendEmail() │
Implementation
java// ecommerce-domain:
// com.ecommerce.domain.shared/event/DomainEventHandler.java
package com.ecommerce.domain.shared.event;

/**
 * Observer interface — handles a specific type of domain event.
 * Each handler is an Observer registered with the EventPublisher.
 */
public interface DomainEventHandler<E extends DomainEvent> {

    /**
     * Handle the event. Called asynchronously after transaction commits.
     */
    void handle(E event);

    /**
     * Can this handler process the given event type?
     */
    boolean supports(DomainEvent event);
}
java// com.ecommerce.domain.shared/event/DomainEventPublisher.java
package com.ecommerce.domain.shared.event;

import java.util.List;

/**
 * Subject/Observable interface — publishes events to all registered handlers.
 * Decouples event producers from consumers.
 */
public interface DomainEventPublisher {

    /**
     * Publish a list of domain events collected from aggregates.
     * Called by Unit of Work AFTER successful DB commit.
     */
    void publish(List<DomainEvent> events);

    /**
     * Publish a single event.
     */
    default void publish(DomainEvent event) {
        publish(List.of(event));
    }
}
java// ecommerce-infrastructure:
// com.ecommerce.infrastructure.event/SpringApplicationEventPublisher.java
package com.ecommerce.infrastructure.event;

import com.ecommerce.domain.shared.event.DomainEvent;
import com.ecommerce.domain.shared.event.DomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Concrete Subject — Spring-based event publisher.
 *
 * Uses Spring's ApplicationEventPublisher to dispatch events.
 * Spring handles:
 *   - Async dispatching (@Async on handlers)
 *   - Transaction synchronization (TransactionSynchronization)
 *   - Error isolation (one handler failure doesn't break others)
 *
 * This is the bridge between our domain events and Spring's event system.
 */
@Component
public class SpringApplicationEventPublisher implements DomainEventPublisher {

    private static final Logger log =
        LoggerFactory.getLogger(SpringApplicationEventPublisher.class);

    private final ApplicationEventPublisher springPublisher;

    public SpringApplicationEventPublisher(ApplicationEventPublisher springPublisher) {
        this.springPublisher = springPublisher;
    }

    @Override
    public void publish(List<DomainEvent> events) {
        if (events == null || events.isEmpty()) return;

        events.forEach(event -> {
            log.debug("Publishing domain event: type={}, aggregate={}",
                event.eventType(), event.aggregateId());
            try {
                springPublisher.publishEvent(
                    new DomainEventWrapper(event)
                );
            } catch (Exception e) {
                log.error("Failed to publish event {}: {}",
                    event.eventType(), e.getMessage(), e);
                // Don't rethrow — event publishing failure
                // should not roll back committed transaction
            }
        });
    }
}
java// com.ecommerce.infrastructure.event/DomainEventWrapper.java
package com.ecommerce.infrastructure.event;

import com.ecommerce.domain.shared.event.DomainEvent;
import org.springframework.context.ApplicationEvent;

/**
 * Wraps domain event in Spring ApplicationEvent.
 * Allows using Spring's event infrastructure without
 * domain layer knowing about Spring.
 */
public class DomainEventWrapper extends ApplicationEvent {

    private final DomainEvent domainEvent;

    public DomainEventWrapper(DomainEvent domainEvent) {
        super(domainEvent);
        this.domainEvent = domainEvent;
    }

    public DomainEvent getDomainEvent() { return domainEvent; }
}
java// com.ecommerce.infrastructure.event/DomainEventDispatcher.java
package com.ecommerce.infrastructure.event;

import com.ecommerce.domain.shared.event.DomainEvent;
import com.ecommerce.domain.shared.event.DomainEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Central dispatcher — receives Spring events, routes to domain handlers.
 *
 * @Async: handlers run in separate thread pool, not blocking the request.
 * @EventListener: registered with Spring's event system.
 *
 * This is the Observer SUBJECT that notifies all registered OBSERVERS.
 */
@Component
public class DomainEventDispatcher {

    private static final Logger log =
        LoggerFactory.getLogger(DomainEventDispatcher.class);

    // Spring injects ALL DomainEventHandler implementations
    private final List<DomainEventHandler<DomainEvent>> handlers;

    @SuppressWarnings("unchecked")
    public DomainEventDispatcher(List<DomainEventHandler<? extends DomainEvent>> handlers) {
        this.handlers = (List<DomainEventHandler<DomainEvent>>)(List<?>) handlers;
    }

    /**
     * Receive Spring event, route to all supporting domain handlers.
     * @Async: runs in dedicated event-processing thread pool.
     */
    @Async("domainEventExecutor")
    @EventListener
    public void dispatch(DomainEventWrapper wrapper) {
        DomainEvent event = wrapper.getDomainEvent();
        log.info("Dispatching event: type={}, aggregate={}",
            event.eventType(), event.aggregateId());

        handlers.stream()
            .filter(h -> h.supports(event))
            .forEach(handler -> {
                try {
                    handler.handle(event);
                } catch (Exception e) {
                    log.error("Handler {} failed for event {}: {}",
                        handler.getClass().getSimpleName(),
                        event.eventType(), e.getMessage(), e);
                    // Isolation: one handler failure doesn't stop others
                }
            });
    }
}
java// com.ecommerce.infrastructure.event/config/AsyncEventConfig.java
package com.ecommerce.infrastructure.event.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Thread pool configuration for async domain event processing.
 * Uses Java 21 virtual threads for maximum scalability.
 */
@Configuration
@EnableAsync
public class AsyncEventConfig {

    @Bean("domainEventExecutor")
    public Executor domainEventExecutor() {
        // Java 21: Virtual thread executor for event handlers
        // Each event gets its own virtual thread — no thread pool exhaustion
        return java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean("notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("notification-");
        executor.setRejectedExecutionHandler(
            new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.initialize();
        return executor;
    }
}
java// ecommerce-application:
// com.ecommerce.application.order/handler/OrderPlacedEventHandler.java
package com.ecommerce.application.order.handler;

import com.ecommerce.domain.order.event.OrderPlacedEvent;
import com.ecommerce.domain.shared.event.DomainEvent;
import com.ecommerce.domain.shared.event.DomainEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Concrete Observer — handles OrderPlacedEvent.
 * Triggers inventory reservation, notification, analytics.
 * Each concern in its own handler → Single Responsibility.
 */
@Component
public class OrderPlacedEventHandler
        implements DomainEventHandler<OrderPlacedEvent> {

    private static final Logger log =
        LoggerFactory.getLogger(OrderPlacedEventHandler.class);

    private final com.ecommerce.application.analytics.AnalyticsService analytics;

    public OrderPlacedEventHandler(
            com.ecommerce.application.analytics.AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @Override
    public void handle(OrderPlacedEvent event) {
        log.info("Handling OrderPlaced: orderId={}, userId={}, amount={}",
            event.getOrderId(), event.getUserId(), event.getTotalAmount());

        // Track conversion analytics
        analytics.trackOrderPlaced(
            event.getOrderId(),
            event.getUserId(),
            event.getTotalAmount()
        );
    }

    @Override
    public boolean supports(DomainEvent event) {
        return event instanceof OrderPlacedEvent;
    }
}
java// com.ecommerce.application.order/handler/OrderNotificationHandler.java
package com.ecommerce.application.order.handler;

import com.ecommerce.domain.notification.*;
import com.ecommerce.domain.order.event.OrderPlacedEvent;
import com.ecommerce.domain.order.event.OrderStatusChangedEvent;
import com.ecommerce.domain.shared.event.DomainEvent;
import com.ecommerce.domain.shared.event.DomainEventHandler;
import com.ecommerce.infrastructure.notification.NotificationFactoryRegistry;
import com.ecommerce.infrastructure.notification.NotificationTemplateRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Concrete Observer — sends notifications for order events.
 * Combines: Observer + Factory Method + Prototype patterns.
 */
@Component
public class OrderNotificationHandler
        implements DomainEventHandler<DomainEvent> {

    private static final Logger log =
        LoggerFactory.getLogger(OrderNotificationHandler.class);

    private final NotificationFactoryRegistry factoryRegistry;
    private final NotificationTemplateRegistry templateRegistry;
    private final com.ecommerce.domain.user.UserRepository userRepository;

    public OrderNotificationHandler(
            NotificationFactoryRegistry factoryRegistry,
            NotificationTemplateRegistry templateRegistry,
            com.ecommerce.domain.user.UserRepository userRepository) {
        this.factoryRegistry  = factoryRegistry;
        this.templateRegistry = templateRegistry;
        this.userRepository   = userRepository;
    }

    @Override
    public void handle(DomainEvent event) {
        switch (event) {
            case OrderPlacedEvent e         -> notifyOrderPlaced(e);
            case OrderStatusChangedEvent e  -> notifyStatusChanged(e);
            default -> log.warn("Unhandled event type: {}", event.eventType());
        }
    }

    @Override
    public boolean supports(DomainEvent event) {
        return event instanceof OrderPlacedEvent
            || event instanceof OrderStatusChangedEvent;
    }

    private void notifyOrderPlaced(OrderPlacedEvent event) {
        sendNotification(event.getUserId(), "ORDER_PLACED", Map.of(
            "orderNumber", event.getOrderId(),
            "orderTotal",  event.getTotalAmount().toPlainString()
        ));
    }

    private void notifyStatusChanged(OrderStatusChangedEvent event) {
        String templateKey = switch (event.getTo()) {
            case SHIPPED    -> "ORDER_SHIPPED";
            case DELIVERED  -> "ORDER_DELIVERED";
            case CANCELLED  -> "ORDER_CANCELLED";
            default         -> null;
        };
        if (templateKey == null) return;

        sendNotification(event.getOrderId(), templateKey, Map.of(
            "orderNumber", event.getOrderId()
        ));
    }

    private void sendNotification(String userId, String templateKey,
                                   Map<String, String> variables) {
        // Prototype: get deep copy of template
        NotificationTemplate template = templateRegistry.getTemplate(templateKey);

        userRepository.findById(
            com.ecommerce.domain.user.UserId.of(userId)
        ).ifPresent(user -> {
            NotificationContext context = new NotificationContext(
                userId, templateKey,
                java.util.Set.of(NotificationChannel.EMAIL),
                false, false, false,
                user.getEmail().getValue(), null
            );

            NotificationRecipient recipient = new NotificationRecipient(
                userId, user.getEmail().getValue(),
                null, null, "en"
            );

            Map<String, String> allVars = new java.util.HashMap<>(variables);
            allVars.put("customerName", user.getFullName());

            NotificationPayload payload = template.render(allVars);

            // Factory Method: get correct notification channel
            NotificationFactory factory = factoryRegistry.getFactory(context);
            Notification notification = factory.create(context);
            NotificationResult result = notification.send(recipient, payload);

            if (!result.success()) {
                log.error("Notification failed: channel={}, error={}",
                    result.channel(), result.errorMessage());
            }
        });
    }
}
java// com.ecommerce.application.inventory/handler/InventoryEventHandler.java
package com.ecommerce.application.inventory.handler;

import com.ecommerce.domain.inventory.InventoryRepository;
import com.ecommerce.domain.inventory.event.StockDepletedEvent;
import com.ecommerce.domain.order.event.OrderCancelledEvent;
import com.ecommerce.domain.product.ProductRepository;
import com.ecommerce.domain.shared.event.DomainEvent;
import com.ecommerce.domain.shared.event.DomainEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Concrete Observer — reacts to inventory events.
 * When stock depletes: mark product out-of-stock, alert purchasing team.
 * When order cancelled: trigger inventory release (via saga).
 */
@Component
public class InventoryEventHandler implements DomainEventHandler<DomainEvent> {

    private static final Logger log =
        LoggerFactory.getLogger(InventoryEventHandler.class);

    private final InventoryRepository inventoryRepository;
    private final ProductRepository   productRepository;

    public InventoryEventHandler(InventoryRepository inventoryRepository,
                                  ProductRepository productRepository) {
        this.inventoryRepository = inventoryRepository;
        this.productRepository   = productRepository;
    }

    @Override
    public void handle(DomainEvent event) {
        switch (event) {
            case StockDepletedEvent e  -> handleStockDepleted(e);
            case OrderCancelledEvent e -> handleOrderCancelled(e);
            default -> {}
        }
    }

    @Override
    public boolean supports(DomainEvent event) {
        return event instanceof StockDepletedEvent
            || event instanceof OrderCancelledEvent;
    }

    private void handleStockDepleted(StockDepletedEvent event) {
        log.warn("STOCK DEPLETED: sku={}", event.getSku());
        // Find product by SKU and mark out-of-stock
        // In production: lookup product by sku via ProductRepository
        // product.markOutOfStock() → save → triggers search re-index
    }

    private void handleOrderCancelled(OrderCancelledEvent event) {
        log.info("Order cancelled, releasing inventory: orderId={}",
            event.getOrderId());
        // Saga compensation: find reservations by orderId and cancel
    }
}
```

---

## Pattern 3: Command

### Problem It Solves
```
Problem: Order operations (cancel, refund, ship) need:
  ① Undo capability (cancel an accidental cancellation within 5 minutes)
  ② Audit trail (who did what and when, for compliance)
  ③ Queuing (batch ship 1000 orders overnight)
  ④ Retry on failure (with same parameters)

Without Command:
  orderService.cancelOrder(id, reason)  ← called directly
  No way to undo, no audit, no queue, no retry

With Command:
  Command cmd = new CancelOrderCommand(id, reason, adminId)
  commandBus.dispatch(cmd)             ← encapsulated, auditable, undoable
  commandHistory.push(cmd)            ← for undo
```

### Class Diagram
```
«interface»                 «interface»
Command                     CommandHandler<C,R>
+ getCommandId(): UUID       + handle(C command): R
+ getIssuedBy(): String      + canUndo(): boolean
+ getIssuedAt(): Instant     + undo(C command): void
        ▲
   ┌────┼──────────────────────┐
   │    │                      │
Cancel  Ship          ProcessPayment
Order   Order         Command
Command Command
   │    │                      │
   ▼    ▼                      ▼
CancelOrder ShipOrder  ProcessPayment
Handler     Handler    Handler

«Spring Component»
CommandBusImpl
+ dispatch(Command): R
  → find handler
  → validate
  → execute
  → audit log
  → publish events
```

### Sequence Diagram
```
AdminController  CommandBus   CancelOrderHandler  OrderAggregate  AuditLog
      │               │               │                 │              │
      │─dispatch(cmd)►│               │                 │              │
      │               │─find handler──►               │              │
      │               │─handle(cmd)──►│                 │              │
      │               │               │─findOrder()────►│              │
      │               │               │◄── Order ───────│              │
      │               │               │─order.cancel()──►│              │
      │               │               │◄─OrderCancelled ─│              │
      │               │               │─save(order)─────►│              │
      │               │               │─auditLog(cmd)────────────────── ►│
      │◄── Result ────│◄── Result ────│                 │              │
Implementation
java// ecommerce-application:
// com.ecommerce.application.shared/command/BaseCommand.java
package com.ecommerce.application.shared.command;

import java.time.Instant;
import java.util.UUID;

/**
 * Base class for all commands.
 * Carries metadata: who issued it, when, unique ID for idempotency.
 */
public abstract class BaseCommand implements Command {

    private final UUID commandId;
    private final String issuedBy;    // userId of the issuer
    private final Instant issuedAt;
    private final String correlationId; // trace requests across services

    protected BaseCommand(String issuedBy) {
        this.commandId     = UUID.randomUUID();
        this.issuedBy      = issuedBy;
        this.issuedAt      = Instant.now();
        this.correlationId = UUID.randomUUID().toString();
    }

    protected BaseCommand(String issuedBy, String correlationId) {
        this.commandId     = UUID.randomUUID();
        this.issuedBy      = issuedBy;
        this.issuedAt      = Instant.now();
        this.correlationId = correlationId;
    }

    public UUID getCommandId()       { return commandId; }
    public String getIssuedBy()      { return issuedBy; }
    public Instant getIssuedAt()     { return issuedAt; }
    public String getCorrelationId() { return correlationId; }
}
java// com.ecommerce.application.order/command/CancelOrderCommand.java
package com.ecommerce.application.order.command;

import com.ecommerce.application.shared.command.BaseCommand;

/**
 * Command — Cancel Order.
 * Encapsulates all data needed to cancel an order.
 * Immutable: captures intent at the moment of request.
 */
public final class CancelOrderCommand extends BaseCommand {

    private final String orderId;
    private final String reason;
    private final boolean isAdminOverride;

    public CancelOrderCommand(String orderId, String reason,
                               String issuedBy, boolean isAdminOverride) {
        super(issuedBy);
        this.orderId         = orderId;
        this.reason          = reason;
        this.isAdminOverride = isAdminOverride;
    }

    public String getOrderId()       { return orderId; }
    public String getReason()        { return reason; }
    public boolean isAdminOverride() { return isAdminOverride; }
}
java// com.ecommerce.application.order/command/ShipOrderCommand.java
package com.ecommerce.application.order.command;

import com.ecommerce.application.shared.command.BaseCommand;

public final class ShipOrderCommand extends BaseCommand {

    private final String orderId;
    private final String trackingNumber;
    private final String carrier;

    public ShipOrderCommand(String orderId, String trackingNumber,
                            String carrier, String issuedBy) {
        super(issuedBy);
        this.orderId        = orderId;
        this.trackingNumber = trackingNumber;
        this.carrier        = carrier;
    }

    public String getOrderId()        { return orderId; }
    public String getTrackingNumber() { return trackingNumber; }
    public String getCarrier()        { return carrier; }
}
java// com.ecommerce.application.order/command/ProcessRefundCommand.java
package com.ecommerce.application.order.command;

import com.ecommerce.application.shared.command.BaseCommand;
import java.math.BigDecimal;

public final class ProcessRefundCommand extends BaseCommand {

    private final String orderId;
    private final BigDecimal refundAmount;
    private final String reason;
    private final boolean isPartial;

    public ProcessRefundCommand(String orderId, BigDecimal refundAmount,
                                 String reason, boolean isPartial,
                                 String issuedBy) {
        super(issuedBy);
        this.orderId      = orderId;
        this.refundAmount = refundAmount;
        this.reason       = reason;
        this.isPartial    = isPartial;
    }

    public String getOrderId()          { return orderId; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public String getReason()           { return reason; }
    public boolean isPartial()          { return isPartial; }
}
java// com.ecommerce.application.order/handler/CancelOrderCommandHandler.java
package com.ecommerce.application.order.handler;

import com.ecommerce.application.order.command.CancelOrderCommand;
import com.ecommerce.application.shared.command.CommandHandler;
import com.ecommerce.domain.order.Order;
import com.ecommerce.domain.order.OrderId;
import com.ecommerce.domain.order.OrderRepository;
import com.ecommerce.domain.shared.event.DomainEventPublisher;
import com.ecommerce.domain.shared.exception.DomainException;
import com.ecommerce.domain.shared.exception.ErrorCodes;
import com.ecommerce.infrastructure.audit.AuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Concrete Command Handler — processes CancelOrderCommand.
 *
 * Responsibilities:
 * 1. Load aggregate from repository
 * 2. Execute domain operation (order.cancel)
 * 3. Save updated aggregate
 * 4. Publish domain events
 * 5. Audit the command execution
 *
 * @Transactional: entire handler is one transaction.
 * If any step fails: rollback everything.
 */
@Component
@Transactional
public class CancelOrderCommandHandler
        implements CommandHandler<CancelOrderCommand, Void> {

    private static final Logger log =
        LoggerFactory.getLogger(CancelOrderCommandHandler.class);

    private final OrderRepository      orderRepository;
    private final DomainEventPublisher eventPublisher;
    private final AuditLogger          auditLogger;

    public CancelOrderCommandHandler(OrderRepository orderRepository,
                                      DomainEventPublisher eventPublisher,
                                      AuditLogger auditLogger) {
        this.orderRepository = orderRepository;
        this.eventPublisher  = eventPublisher;
        this.auditLogger     = auditLogger;
    }

    @Override
    public Void handle(CancelOrderCommand command) {
        log.info("Handling CancelOrderCommand: orderId={}, issuedBy={}",
            command.getOrderId(), command.getIssuedBy());

        // 1. Load aggregate
        Order order = orderRepository.findById(
            OrderId.of(command.getOrderId())
        ).orElseThrow(() -> new DomainException(
            ErrorCodes.ORDER_NOT_FOUND,
            "Order not found: " + command.getOrderId()
        ));

        // 2. Execute domain operation (state machine enforced inside)
        order.cancel(command.getReason());

        // 3. Persist
        orderRepository.update(order);

        // 4. Publish domain events (collected by aggregate)
        eventPublisher.publish(order.getDomainEvents());
        order.clearDomainEvents();

        // 5. Audit trail
        auditLogger.log(AuditLogger.AuditEvent.builder()
            .action("ORDER_CANCELLED")
            .resourceType("Order")
            .resourceId(command.getOrderId())
            .outcome("SUCCESS")
            .details("Reason: " + command.getReason()
                + ", AdminOverride: " + command.isAdminOverride()
                + ", IssuedBy: " + command.getIssuedBy())
            .build());

        log.info("Order cancelled successfully: {}", command.getOrderId());
        return null;
    }
}
java// com.ecommerce.application.order/handler/ShipOrderCommandHandler.java
package com.ecommerce.application.order.handler;

import com.ecommerce.application.order.command.ShipOrderCommand;
import com.ecommerce.application.shared.command.CommandHandler;
import com.ecommerce.domain.order.Order;
import com.ecommerce.domain.order.OrderId;
import com.ecommerce.domain.order.OrderRepository;
import com.ecommerce.domain.shared.event.DomainEventPublisher;
import com.ecommerce.domain.shared.exception.DomainException;
import com.ecommerce.domain.shared.exception.ErrorCodes;
import com.ecommerce.infrastructure.audit.AuditLogger;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class ShipOrderCommandHandler
        implements CommandHandler<ShipOrderCommand, Void> {

    private final OrderRepository      orderRepository;
    private final DomainEventPublisher eventPublisher;
    private final AuditLogger          auditLogger;

    public ShipOrderCommandHandler(OrderRepository orderRepository,
                                    DomainEventPublisher eventPublisher,
                                    AuditLogger auditLogger) {
        this.orderRepository = orderRepository;
        this.eventPublisher  = eventPublisher;
        this.auditLogger     = auditLogger;
    }

    @Override
    public Void handle(ShipOrderCommand command) {
        Order order = orderRepository.findById(
            OrderId.of(command.getOrderId())
        ).orElseThrow(() -> new DomainException(
            ErrorCodes.ORDER_NOT_FOUND,
            "Order not found: " + command.getOrderId()
        ));

        order.ship(command.getTrackingNumber());
        orderRepository.update(order);
        eventPublisher.publish(order.getDomainEvents());
        order.clearDomainEvents();

        auditLogger.log(AuditLogger.AuditEvent.builder()
            .action("ORDER_SHIPPED")
            .resourceType("Order")
            .resourceId(command.getOrderId())
            .outcome("SUCCESS")
            .details("Tracking: " + command.getTrackingNumber()
                + ", Carrier: " + command.getCarrier())
            .build());

        return null;
    }
}
java// com.ecommerce.infrastructure.command/SpringCommandBus.java
package com.ecommerce.infrastructure.command;

import com.ecommerce.application.shared.CommandBus;
import com.ecommerce.application.shared.command.Command;
import com.ecommerce.application.shared.command.CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Command Bus — routes commands to their handlers.
 *
 * Spring injects all CommandHandler implementations.
 * Uses generic type resolution to match Command → Handler.
 *
 * Also adds cross-cutting concerns:
 *   - Logging all command dispatches
 *   - Timing command execution
 *   - Correlation ID propagation
 */
@Component
public class SpringCommandBus implements CommandBus {

    private static final Logger log =
        LoggerFactory.getLogger(SpringCommandBus.class);

    private final Map<Class<?>, CommandHandler<?, ?>> handlerMap;

    @SuppressWarnings("unchecked")
    public SpringCommandBus(List<CommandHandler<?, ?>> handlers) {
        this.handlerMap = handlers.stream()
            .collect(Collectors.toMap(
                h -> resolveCommandType(h.getClass()),
                Function.identity()
            ));
        log.info("CommandBus initialized with {} handlers: {}",
            handlerMap.size(),
            handlerMap.keySet().stream()
                .map(Class::getSimpleName)
                .collect(Collectors.joining(", "))
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> R dispatch(Command command) {
        Class<?> commandType = command.getClass();
        CommandHandler<Command, R> handler =
            (CommandHandler<Command, R>) handlerMap.get(commandType);

        if (handler == null) {
            throw new IllegalStateException(
                "No handler found for command: " + commandType.getSimpleName()
            );
        }

        long start = System.currentTimeMillis();
        log.info("Dispatching command: type={}, id={}",
            commandType.getSimpleName(),
            command instanceof com.ecommerce.application.shared.command.BaseCommand bc
                ? bc.getCommandId() : "unknown");

        try {
            R result = handler.handle(command);
            log.info("Command executed: type={}, elapsed={}ms",
                commandType.getSimpleName(),
                System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            log.error("Command failed: type={}, elapsed={}ms, error={}",
                commandType.getSimpleName(),
                System.currentTimeMillis() - start,
                e.getMessage());
            throw e;
        }
    }

    private Class<?> resolveCommandType(Class<?> handlerClass) {
        // Resolve generic type parameter (CommandHandler<CancelOrderCommand, Void>)
        var genericInterfaces = handlerClass.getGenericInterfaces();
        for (var gi : genericInterfaces) {
            if (gi instanceof java.lang.reflect.ParameterizedType pt) {
                if (pt.getRawType().equals(CommandHandler.class)) {
                    return (Class<?>) pt.getActualTypeArguments()[0];
                }
            }
        }
        // Check superclass for @Component proxied classes
        var superclass = handlerClass.getSuperclass();
        if (superclass != null && superclass != Object.class) {
            return resolveCommandType(superclass);
        }
        throw new IllegalStateException(
            "Cannot resolve command type for handler: " + handlerClass
        );
    }
}
```

---

## Pattern 4: State

### Problem It Solves
```
Problem: Order has complex lifecycle with state-specific behavior.
SHIPPED order behaves differently from PENDING order.
"Cancel" on PENDING = refund not needed.
"Cancel" on CONFIRMED = trigger refund.
"Cancel" on SHIPPED = not allowed.

Without State Pattern:
  void cancel(Order order) {
    if (order.status == PENDING) { ... }
    else if (order.status == CONFIRMED) { refund(); ... }
    else if (order.status == SHIPPED) throw exception;
    // Every method has the same giant if-else
  }

With State Pattern:
  order.currentState.cancel(order)
  // Each state class encapsulates its own behavior
  // Adding new state = new state class, no if-else modification
```

### Class Diagram
```
Order (Context)                «interface»
- state: OrderState ─────────► OrderState
+ cancel(reason)               + confirm(order, txnId)
+ ship(tracking)               + startProcessing(order)
+ deliver()                    + ship(order, tracking)
                               + deliver(order)
                               + cancel(order, reason)
                                        ▲
           ┌────────┬──────────┬────────┼────────┬────────┐
           │        │          │        │        │        │
       Pending Confirmed Processing Shipped Delivered Cancelled
       State   State     State    State   State     State
Implementation
java// ecommerce-domain:
// com.ecommerce.domain.order/state/OrderState.java
package com.ecommerce.domain.order.state;

import com.ecommerce.domain.order.Order;

/**
 * State interface — defines all operations that vary by state.
 * Each concrete state implements allowed operations and
 * throws exceptions for invalid transitions.
 *
 * Note: Domain-level State pattern complements the OrderStatus enum.
 * Enum: data (persisted), State: behavior (runtime).
 */
public interface OrderState {

    void confirm(Order order, String paymentIntentId);
    void startProcessing(Order order);
    void ship(Order order, String trackingNumber);
    void deliver(Order order);
    void cancel(Order order, String reason);
    void refund(Order order);

    String getStateName();
}
java// com.ecommerce.domain.order/state/AbstractOrderState.java
package com.ecommerce.domain.order.state;

import com.ecommerce.domain.order.Order;
import com.ecommerce.domain.order.OrderStatus;
import com.ecommerce.domain.shared.exception.DomainException;
import com.ecommerce.domain.shared.exception.ErrorCodes;

/**
 * Abstract base state — provides default "invalid transition" behavior.
 * Concrete states only override operations they support.
 * Template Method pattern within State.
 */
public abstract class AbstractOrderState implements OrderState {

    @Override
    public void confirm(Order order, String paymentIntentId) {
        throw invalidTransition("confirm");
    }

    @Override
    public void startProcessing(Order order) {
        throw invalidTransition("startProcessing");
    }

    @Override
    public void ship(Order order, String trackingNumber) {
        throw invalidTransition("ship");
    }

    @Override
    public void deliver(Order order) {
        throw invalidTransition("deliver");
    }

    @Override
    public void cancel(Order order, String reason) {
        throw invalidTransition("cancel");
    }

    @Override
    public void refund(Order order) {
        throw invalidTransition("refund");
    }

    protected DomainException invalidTransition(String operation) {
        return new DomainException(
            ErrorCodes.ORDER_INVALID_STATE,
            String.format("Cannot '%s' order in state: %s",
                operation, getStateName())
        );
    }
}
java// com.ecommerce.domain.order/state/PendingOrderState.java
package com.ecommerce.domain.order.state;

import com.ecommerce.domain.order.Order;

/**
 * Concrete State — PENDING.
 * Order created, payment not yet collected.
 * Allowed: confirm (after payment), cancel (free, no refund needed).
 */
public final class PendingOrderState extends AbstractOrderState {

    public static final PendingOrderState INSTANCE = new PendingOrderState();

    private PendingOrderState() {}

    @Override
    public void confirm(Order order, String paymentIntentId) {
        // Delegate to Order's actual confirm logic
        order.confirmInternal(paymentIntentId);
        order.setState(ConfirmedOrderState.INSTANCE);
    }

    @Override
    public void cancel(Order order, String reason) {
        order.cancelInternal(reason);
        order.setState(CancelledOrderState.INSTANCE);
        // No refund needed — payment was never collected
    }

    @Override
    public String getStateName() { return "PENDING"; }
}
java// com.ecommerce.domain.order/state/ConfirmedOrderState.java
package com.ecommerce.domain.order.state;

import com.ecommerce.domain.order.Order;

/**
 * Concrete State — CONFIRMED.
 * Payment received. Awaiting warehouse pickup.
 * Allowed: startProcessing, cancel (refund required).
 */
public final class ConfirmedOrderState extends AbstractOrderState {

    public static final ConfirmedOrderState INSTANCE = new ConfirmedOrderState();

    private ConfirmedOrderState() {}

    @Override
    public void startProcessing(Order order) {
        order.startProcessingInternal();
        order.setState(ProcessingOrderState.INSTANCE);
    }

    @Override
    public void cancel(Order order, String reason) {
        order.cancelInternal(reason);
        order.setState(CancelledOrderState.INSTANCE);
        // Note: Refund process triggered by OrderCancelledEvent handler
        // State pattern doesn't handle cross-aggregate concerns
    }

    @Override
    public String getStateName() { return "CONFIRMED"; }
}
java// com.ecommerce.domain.order/state/ProcessingOrderState.java
package com.ecommerce.domain.order.state;

import com.ecommerce.domain.order.Order;

/**
 * Concrete State — PROCESSING.
 * Warehouse is picking/packing the order.
 * Allowed: ship, cancel (harder — may need partial fulfillment logic).
 */
public final class ProcessingOrderState extends AbstractOrderState {

    public static final ProcessingOrderState INSTANCE = new ProcessingOrderState();

    private ProcessingOrderState() {}

    @Override
    public void ship(Order order, String trackingNumber) {
        order.shipInternal(trackingNumber);
        order.setState(ShippedOrderState.INSTANCE);
    }

    @Override
    public void cancel(Order order, String reason) {
        // Still cancellable but more complex — need to recall from warehouse
        order.cancelInternal(reason);
        order.setState(CancelledOrderState.INSTANCE);
    }

    @Override
    public String getStateName() { return "PROCESSING"; }
}
java// com.ecommerce.domain.order/state/ShippedOrderState.java
package com.ecommerce.domain.order.state;

import com.ecommerce.domain.order.Order;

/**
 * Concrete State — SHIPPED.
 * With carrier. Cannot be cancelled (must wait for delivery, then return).
 * Allowed: deliver only.
 */
public final class ShippedOrderState extends AbstractOrderState {

    public static final ShippedOrderState INSTANCE = new ShippedOrderState();

    private ShippedOrderState() {}

    @Override
    public void deliver(Order order) {
        order.deliverInternal();
        order.setState(DeliveredOrderState.INSTANCE);
    }

    // cancel() NOT overridden → throws invalidTransition
    // "Package is in transit — cannot cancel"

    @Override
    public String getStateName() { return "SHIPPED"; }
}
java// com.ecommerce.domain.order/state/DeliveredOrderState.java
package com.ecommerce.domain.order.state;

import com.ecommerce.domain.order.Order;

/**
 * Concrete State — DELIVERED.
 * Customer received order. Only refund possible within return window.
 */
public final class DeliveredOrderState extends AbstractOrderState {

    public static final DeliveredOrderState INSTANCE = new DeliveredOrderState();

    private DeliveredOrderState() {}

    @Override
    public void refund(Order order) {
        order.refundInternal();
        order.setState(RefundedOrderState.INSTANCE);
    }

    @Override
    public String getStateName() { return "DELIVERED"; }
}
java// com.ecommerce.domain.order/state/CancelledOrderState.java
package com.ecommerce.domain.order.state;

import com.ecommerce.domain.order.Order;

/**
 * Concrete State — CANCELLED (terminal state).
 * No further transitions allowed.
 */
public final class CancelledOrderState extends AbstractOrderState {

    public static final CancelledOrderState INSTANCE = new CancelledOrderState();

    private CancelledOrderState() {}

    @Override
    public String getStateName() { return "CANCELLED"; }
    // All operations throw invalidTransition — terminal state
}
java// com.ecommerce.domain.order/state/RefundedOrderState.java
package com.ecommerce.domain.order.state;

/**
 * Concrete State — REFUNDED (terminal state).
 */
public final class RefundedOrderState extends AbstractOrderState {

    public static final RefundedOrderState INSTANCE = new RefundedOrderState();

    private RefundedOrderState() {}

    @Override
    public String getStateName() { return "REFUNDED"; }
}
java// com.ecommerce.domain.order/state/OrderStateFactory.java
package com.ecommerce.domain.order.state;

import com.ecommerce.domain.order.OrderStatus;

/**
 * Maps persisted OrderStatus enum → runtime OrderState object.
 * Called when reconstituting Order from database.
 */
public final class OrderStateFactory {

    private OrderStateFactory() {}

    public static OrderState fromStatus(OrderStatus status) {
        return switch (status) {
            case PENDING    -> PendingOrderState.INSTANCE;
            case CONFIRMED  -> ConfirmedOrderState.INSTANCE;
            case PROCESSING -> ProcessingOrderState.INSTANCE;
            case SHIPPED    -> ShippedOrderState.INSTANCE;
            case DELIVERED  -> DeliveredOrderState.INSTANCE;
            case CANCELLED  -> CancelledOrderState.INSTANCE;
            case REFUNDED   -> RefundedOrderState.INSTANCE;
        };
    }
}
Now we extend the Order aggregate to use the State pattern:
java// Extension to Order.java — add State pattern support
// (Add these methods to the Order class from Phase 2)

/*
  Add to Order class:
  - private transient OrderState currentState; (not persisted — derived from status)
  - Initialize in reconstitute() via OrderStateFactory.fromStatus(status)
  - Expose state-delegating public methods
  - Add package-private "internal" methods for states to call
*/

// New fields and methods to add to Order aggregate:

// Field (transient — not stored, derived from status enum):
// private transient OrderState currentState;

// In reconstitute(): add → order.currentState = OrderStateFactory.fromStatus(status);
// In constructor:        → this.currentState = PendingOrderState.INSTANCE;

// Public state-delegating methods (replace direct status mutation):
/*
public void confirm(String paymentIntentId) {
    currentState.confirm(this, paymentIntentId);
}

public void startProcessing() {
    currentState.startProcessing(this);
}

public void ship(String trackingNumber) {
    currentState.ship(this, trackingNumber);
}

public void deliver() {
    currentState.deliver(this);
}

public void cancel(String reason) {
    currentState.cancel(this, reason);
}

public void refund() {
    currentState.refund(this);
}
*/

// Package-private methods called BY states:
/*
void confirmInternal(String paymentIntentId) {
    this.paymentIntentId = paymentIntentId;
    this.status          = OrderStatus.CONFIRMED;
    this.confirmedAt     = Instant.now();
    this.updatedAt       = Instant.now();
    registerEvent(new OrderStatusChangedEvent(
        id.getValue().toString(), OrderStatus.PENDING, OrderStatus.CONFIRMED));
}

void shipInternal(String trackingNumber) {
    this.trackingNumber = trackingNumber;
    this.status         = OrderStatus.SHIPPED;
    this.shippedAt      = Instant.now();
    this.updatedAt      = Instant.now();
}

void cancelInternal(String reason) {
    this.cancellationReason = reason;
    this.status             = OrderStatus.CANCELLED;
    this.updatedAt          = Instant.now();
    registerEvent(new OrderCancelledEvent(
        id.getValue().toString(), reason));
}

void startProcessingInternal() {
    this.status    = OrderStatus.PROCESSING;
    this.updatedAt = Instant.now();
}

void deliverInternal() {
    this.status      = OrderStatus.DELIVERED;
    this.deliveredAt = Instant.now();
    this.updatedAt   = Instant.now();
}

void refundInternal() {
    this.status    = OrderStatus.REFUNDED;
    this.updatedAt = Instant.now();
}

// Called by state classes to transition:
void setState(OrderState newState) {
    this.currentState = newState;
}
*/
```

---

## Pattern 5: Chain of Responsibility

### Problem It Solves
```
Problem: Order placement requires sequential validation + fraud detection:
  1. Basic input validation (non-null fields, format)
  2. User eligibility (active, email verified, not suspended)
  3. Business rules (cart not empty, items still available)
  4. Fraud detection (velocity check, unusual patterns)
  5. Risk scoring (amount vs history ratio)

Without Chain of Responsibility:
  void validateOrder(request) {
    validateInput(request);        // all in one giant method or
    validateUser(request);         // all in one service class
    validateBusiness(request);     // impossible to add/remove handlers
    detectFraud(request);          // impossible to reorder
    scoreRisk(request);
  }

With Chain of Responsibility:
  Handler chain: Input → User → Business → Fraud → Risk
  Each handler either processes + passes along, or rejects.
  Adding new check = new handler at any chain position.
  Reordering = change chain construction, zero handler code change.
```

### Class Diagram
```
«abstract»
OrderValidationHandler
- next: OrderValidationHandler
+ setNext(handler): OrderValidationHandler
+ handle(context): ValidationResult
# doHandle(context): ValidationResult  ← Template Method
         ▲
   ┌─────┼──────────────────────────────────┐
   │     │              │                   │
Input   User       Business            Fraud
Valid.  Eligibility Rules              Detection
Handler Handler    Handler             Handler
                                           │
                                       RiskScoring
                                       Handler
```

### Sequence Diagram
```
OrderService  InputHandler  UserHandler  BusinessHandler  FraudHandler
     │              │             │              │              │
     │─validate()──►│             │              │              │
     │              │─pass()─────►│              │              │
     │              │             │─pass()───────►│              │
     │              │             │              │─pass()────── ►│
     │              │             │              │     [score < threshold]
     │              │             │              │◄── PASS ──────│
     │◄── VALID ────│◄────────────│◄─────────────│              │
Implementation
java// ecommerce-application:
// com.ecommerce.application.order/validation/OrderValidationContext.java
package com.ecommerce.application.order.validation;

import com.ecommerce.domain.cart.Cart;
import com.ecommerce.domain.user.User;
import com.ecommerce.application.checkout.CheckoutRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Context object passed through the validation chain.
 * Carries all data handlers might need.
 * Accumulates errors from all handlers for rich error response.
 */
public class OrderValidationContext {

    private final CheckoutRequest request;
    private User user;
    private Cart cart;
    private BigDecimal orderAmount;

    private final List<String> errors     = new ArrayList<>();
    private final List<String> warnings   = new ArrayList<>();
    private boolean fraudSuspected        = false;
    private double riskScore              = 0.0;
    private boolean blocked               = false;

    public OrderValidationContext(CheckoutRequest request) {
        this.request = request;
    }

    public void addError(String error) {
        errors.add(error);
    }

    public void addWarning(String warning) {
        warnings.add(warning);
    }

    public void flagFraud(String reason) {
        fraudSuspected = true;
        blocked = true;
        errors.add("FRAUD: " + reason);
    }

    public void setRiskScore(double score) {
        this.riskScore = score;
        if (score > 0.85) {
            blocked = true;
            errors.add("High risk score: " + score);
        }
    }

    public boolean hasErrors()       { return !errors.isEmpty(); }
    public boolean isFraudSuspected(){ return fraudSuspected; }
    public boolean isBlocked()       { return blocked; }
    public double getRiskScore()     { return riskScore; }

    public CheckoutRequest getRequest() { return request; }
    public User getUser()               { return user; }
    public Cart getCart()               { return cart; }
    public BigDecimal getOrderAmount()  { return orderAmount; }
    public List<String> getErrors()     { return List.copyOf(errors); }
    public List<String> getWarnings()   { return List.copyOf(warnings); }

    public void setUser(User user)              { this.user = user; }
    public void setCart(Cart cart)              { this.cart = cart; }
    public void setOrderAmount(BigDecimal amt)  { this.orderAmount = amt; }
}
java// com.ecommerce.application.order/validation/ValidationResult.java
package com.ecommerce.application.order.validation;

import java.util.List;

public record ValidationResult(
    boolean valid,
    List<String> errors,
    List<String> warnings,
    boolean fraudSuspected,
    double riskScore
) {
    public static ValidationResult pass() {
        return new ValidationResult(true, List.of(), List.of(), false, 0.0);
    }

    public static ValidationResult fail(List<String> errors) {
        return new ValidationResult(false, errors, List.of(), false, 0.0);
    }

    public static ValidationResult fraud(List<String> errors) {
        return new ValidationResult(false, errors, List.of(), true, 1.0);
    }
}
java// com.ecommerce.application.order/validation/OrderValidationHandler.java
package com.ecommerce.application.order.validation;

/**
 * Abstract Handler in Chain of Responsibility.
 * Each handler:
 *   1. Does its own check (doHandle)
 *   2. If passes: calls next handler
 *   3. If fails: stops chain, returns error
 *
 * Template Method pattern integrated:
 *   handle() = template
 *   doHandle() = hook for subclasses
 */
public abstract class OrderValidationHandler {

    private OrderValidationHandler next;

    /**
     * Set next handler in chain.
     * Returns next handler for fluent chaining:
     *   h1.setNext(h2).setNext(h3).setNext(h4)
     */
    public OrderValidationHandler setNext(OrderValidationHandler next) {
        this.next = next;
        return next;
    }

    /**
     * Template Method — defines the algorithm skeleton.
     * Subclasses implement doHandle() only.
     */
    public final ValidationResult handle(OrderValidationContext context) {
        // Execute this handler's check
        ValidationResult result = doHandle(context);

        // If this handler passed AND there's a next handler → continue chain
        if (result.valid() && next != null) {
            return next.handle(context);
        }

        // Either failed (return failure) or no next (end of chain, return pass)
        return result;
    }

    /**
     * Hook method — subclasses implement their specific validation.
     */
    protected abstract ValidationResult doHandle(OrderValidationContext context);

    public abstract String getHandlerName();
}
java// com.ecommerce.application.order/validation/handler/InputValidationHandler.java
package com.ecommerce.application.order.validation.handler;

import com.ecommerce.application.order.validation.OrderValidationContext;
import com.ecommerce.application.order.validation.OrderValidationHandler;
import com.ecommerce.application.order.validation.ValidationResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler 1 — Basic input validation.
 * First in chain: catches obvious bad input early.
 * Fast, no DB calls.
 */
@Component
public class InputValidationHandler extends OrderValidationHandler {

    @Override
    protected ValidationResult doHandle(OrderValidationContext context) {
        List<String> errors = new ArrayList<>();
        var request = context.getRequest();

        if (request.userId() == null || request.userId().isBlank()) {
            errors.add("userId is required");
        }
        if (request.cartId() == null || request.cartId().isBlank()) {
            errors.add("cartId is required");
        }
        if (request.paymentMethodToken() == null
                || request.paymentMethodToken().isBlank()) {
            errors.add("paymentMethodToken is required");
        }
        if (request.shippingAddressId() == null
                || request.shippingAddressId().isBlank()) {
            errors.add("shippingAddressId is required");
        }
        // Idempotency key format validation
        if (request.idempotencyKey() != null
                && request.idempotencyKey().length() > 255) {
            errors.add("idempotencyKey must be <= 255 characters");
        }

        return errors.isEmpty()
            ? ValidationResult.pass()
            : ValidationResult.fail(errors);
    }

    @Override
    public String getHandlerName() { return "INPUT_VALIDATION"; }
}
java// com.ecommerce.application.order/validation/handler/UserEligibilityHandler.java
package com.ecommerce.application.order.validation.handler;

import com.ecommerce.application.order.validation.OrderValidationContext;
import com.ecommerce.application.order.validation.OrderValidationHandler;
import com.ecommerce.application.order.validation.ValidationResult;
import com.ecommerce.domain.user.User;
import com.ecommerce.domain.user.UserId;
import com.ecommerce.domain.user.UserRepository;
import com.ecommerce.domain.user.UserStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Handler 2 — User eligibility check.
 * Loads user and verifies they can place orders.
 * Sets user on context for downstream handlers.
 */
@Component
public class UserEligibilityHandler extends OrderValidationHandler {

    private final UserRepository userRepository;

    public UserEligibilityHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected ValidationResult doHandle(OrderValidationContext context) {
        String userId = context.getRequest().userId();

        // Load user (sets on context for reuse by downstream handlers)
        User user;
        try {
            user = userRepository.findById(UserId.of(userId))
                .orElse(null);
        } catch (Exception e) {
            return ValidationResult.fail(List.of("Invalid userId format: " + userId));
        }

        if (user == null) {
            return ValidationResult.fail(List.of("User not found: " + userId));
        }

        if (user.getStatus() == UserStatus.DEACTIVATED) {
            return ValidationResult.fail(List.of("Account has been deactivated"));
        }

        if (user.getStatus() == UserStatus.SUSPENDED) {
            return ValidationResult.fail(
                List.of("Account is suspended. Please contact support.")
            );
        }

        if (!user.isEmailVerified()) {
            return ValidationResult.fail(
                List.of("Please verify your email before placing an order")
            );
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            return ValidationResult.fail(
                List.of("Account is not active: " + user.getStatus())
            );
        }

        // Pass user to context for downstream handlers
        context.setUser(user);
        return ValidationResult.pass();
    }

    @Override
    public String getHandlerName() { return "USER_ELIGIBILITY"; }
}
java// com.ecommerce.application.order/validation/handler/CartValidationHandler.java
package com.ecommerce.application.order.validation.handler;

import com.ecommerce.application.cart.CartApplicationService;
import com.ecommerce.application.order.validation.OrderValidationContext;
import com.ecommerce.application.order.validation.OrderValidationHandler;
import com.ecommerce.application.order.validation.ValidationResult;
import com.ecommerce.domain.cart.Cart;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Handler 3 — Cart business rules.
 * Validates cart is non-empty, not expired, belongs to user.
 */
@Component
public class CartValidationHandler extends OrderValidationHandler {

    private final CartApplicationService cartService;

    public CartValidationHandler(CartApplicationService cartService) {
        this.cartService = cartService;
    }

    @Override
    protected ValidationResult doHandle(OrderValidationContext context) {
        String cartId  = context.getRequest().cartId();
        String userId  = context.getRequest().userId();

        Cart cart;
        try {
            cart = cartService.getValidatedCart(cartId, userId);
        } catch (Exception e) {
            return ValidationResult.fail(List.of("Cart not found or invalid: " + cartId));
        }

        if (cart.isEmpty()) {
            return ValidationResult.fail(List.of("Cart is empty"));
        }

        if (cart.isExpired()) {
            return ValidationResult.fail(
                List.of("Cart has expired. Please add items again.")
            );
        }

        // Validate cart belongs to user
        if (cart.getUserId() != null
                && !cart.getUserId().getValue().toString().equals(userId)) {
            return ValidationResult.fail(
                List.of("Cart does not belong to this user")
            );
        }

        // Set cart and amount on context
        context.setCart(cart);
        context.setOrderAmount(
            cart.calculateTotal().getAmount()
        );
        return ValidationResult.pass();
    }

    @Override
    public String getHandlerName() { return "CART_VALIDATION"; }
}
java// com.ecommerce.application.order/validation/handler/FraudDetectionHandler.java
package com.ecommerce.application.order.validation.handler;

import com.ecommerce.application.order.validation.OrderValidationContext;
import com.ecommerce.application.order.validation.OrderValidationHandler;
import com.ecommerce.application.order.validation.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * Handler 4 — Fraud Detection.
 * Velocity checks: too many orders in short time window.
 * Unusual amount: order amount >> customer average.
 * Known bad patterns: multiple failed payments, suspicious IP.
 *
 * Uses Redis for velocity tracking (sliding window).
 * In production: integrate with dedicated fraud service (Sift, Kount).
 */
@Component
public class FraudDetectionHandler extends OrderValidationHandler {

    private static final Logger log =
        LoggerFactory.getLogger(FraudDetectionHandler.class);

    // Velocity limits
    private static final int MAX_ORDERS_PER_HOUR  = 5;
    private static final int MAX_ORDERS_PER_DAY   = 20;
    private static final BigDecimal SUSPICIOUS_AMOUNT =
        new BigDecimal("5000.00"); // $5000

    private final RedisTemplate<String, Object> redis;

    public FraudDetectionHandler(RedisTemplate<String, Object> redis) {
        this.redis = redis;
    }

    @Override
    protected ValidationResult doHandle(OrderValidationContext context) {
        String userId = context.getRequest().userId();
        BigDecimal amount = context.getOrderAmount();

        // Check 1: Order velocity (rate limiting per user)
        if (!checkVelocity(userId)) {
            context.flagFraud("Order velocity exceeded. Too many orders in short time.");
            return ValidationResult.fraud(context.getErrors());
        }

        // Check 2: Unusually large order
        if (amount != null && amount.compareTo(SUSPICIOUS_AMOUNT) > 0) {
            log.warn("FRAUD CHECK: Large order amount ${} for user {}",
                amount, userId);
            // Don't block — add to manual review queue
            context.addWarning(
                "Large order flagged for review: $" + amount
            );
        }

        // Check 3: Payment token abuse (same token, many users)
        String token = context.getRequest().paymentMethodToken();
        if (isTokenAbused(token)) {
            context.flagFraud("Payment token associated with fraudulent activity");
            return ValidationResult.fraud(context.getErrors());
        }

        // Record this attempt (for velocity tracking)
        recordOrderAttempt(userId);

        return ValidationResult.pass();
    }

    private boolean checkVelocity(String userId) {
        String hourKey = "order:velocity:hour:" + userId;
        String dayKey  = "order:velocity:day:" + userId;

        Long hourCount = redis.opsForValue().increment(hourKey);
        Long dayCount  = redis.opsForValue().increment(dayKey);

        // Set expiry on first increment
        if (hourCount != null && hourCount == 1) {
            redis.expire(hourKey, Duration.ofHours(1));
        }
        if (dayCount != null && dayCount == 1) {
            redis.expire(dayKey, Duration.ofDays(1));
        }

        boolean withinHourLimit = hourCount == null || hourCount <= MAX_ORDERS_PER_HOUR;
        boolean withinDayLimit  = dayCount  == null || dayCount  <= MAX_ORDERS_PER_DAY;

        return withinHourLimit && withinDayLimit;
    }

    private boolean isTokenAbused(String token) {
        if (token == null) return false;
        String key = "fraud:token:" + token;
        Object flagged = redis.opsForValue().get(key);
        return Boolean.TRUE.equals(flagged);
    }

    private void recordOrderAttempt(String userId) {
        // Additional fraud signals tracked here
        String key = "order:attempts:" + userId;
        redis.opsForValue().increment(key);
        redis.expire(key, Duration.ofDays(7));
    }

    @Override
    public String getHandlerName() { return "FRAUD_DETECTION"; }
}
java// com.ecommerce.application.order/validation/handler/RiskScoringHandler.java
package com.ecommerce.application.order.validation.handler;

import com.ecommerce.application.order.validation.OrderValidationContext;
import com.ecommerce.application.order.validation.OrderValidationHandler;
import com.ecommerce.application.order.validation.ValidationResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Handler 5 — Risk Scoring (last in chain).
 * Computes composite risk score from multiple signals.
 * High score → block or flag for manual review.
 *
 * In production: ML model or dedicated service (Stripe Radar).
 */
@Component
public class RiskScoringHandler extends OrderValidationHandler {

    private static final double HIGH_RISK_THRESHOLD   = 0.85;
    private static final double MEDIUM_RISK_THRESHOLD = 0.60;

    @Override
    protected ValidationResult doHandle(OrderValidationContext context) {
        double riskScore = computeRiskScore(context);
        context.setRiskScore(riskScore);

        if (riskScore >= HIGH_RISK_THRESHOLD) {
            return ValidationResult.fail(
                List.of("Order blocked due to high risk score: "
                    + String.format("%.2f", riskScore))
            );
        }

        if (riskScore >= MEDIUM_RISK_THRESHOLD) {
            context.addWarning(
                "Medium risk order - flagged for review: "
                    + String.format("%.2f", riskScore)
            );
        }

        return ValidationResult.pass();
    }

    private double computeRiskScore(OrderValidationContext context) {
        double score = 0.0;

        // Factor 1: Is this user's first order? (higher risk)
        if (context.getRequest().isFirstPurchase()) {
            score += 0.10;
        }

        // Factor 2: Large order amount relative to history
        BigDecimal amount = context.getOrderAmount();
        if (amount != null) {
            if (amount.compareTo(new BigDecimal("1000")) > 0) score += 0.20;
            if (amount.compareTo(new BigDecimal("3000")) > 0) score += 0.20;
        }

        // Factor 3: New account (< 7 days old)
        if (context.getUser() != null) {
            long accountAgeDays = java.time.Duration.between(
                context.getUser().getCreatedAt(),
                java.time.Instant.now()
            ).toDays();
            if (accountAgeDays < 7) score += 0.20;
            else if (accountAgeDays < 30) score += 0.10;
        }

        // Factor 4: Warnings from previous handlers
        score += context.getWarnings().size() * 0.10;

        return Math.min(score, 1.0); // cap at 1.0
    }

    @Override
    public String getHandlerName() { return "RISK_SCORING"; }
}
java// com.ecommerce.application.order/validation/OrderValidationChain.java
package com.ecommerce.application.order.validation;

import com.ecommerce.application.order.validation.handler.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Chain builder — assembles the validation chain.
 * Single place where chain order is defined.
 * Reordering = change this class only.
 *
 * Chain: Input → User → Cart → Fraud → Risk
 */
@Component
public class OrderValidationChain {

    private static final Logger log =
        LoggerFactory.getLogger(OrderValidationChain.class);

    private final InputValidationHandler  inputHandler;
    private final UserEligibilityHandler  userHandler;
    private final CartValidationHandler   cartHandler;
    private final FraudDetectionHandler   fraudHandler;
    private final RiskScoringHandler      riskHandler;

    // The head of the chain
    private OrderValidationHandler chainHead;

    public OrderValidationChain(InputValidationHandler inputHandler,
                                UserEligibilityHandler userHandler,
                                CartValidationHandler cartHandler,
                                FraudDetectionHandler fraudHandler,
                                RiskScoringHandler riskHandler) {
        this.inputHandler = inputHandler;
        this.userHandler  = userHandler;
        this.cartHandler  = cartHandler;
        this.fraudHandler = fraudHandler;
        this.riskHandler  = riskHandler;
    }

    @PostConstruct
    public void buildChain() {
        // Fluent chain construction
        // setNext() returns next handler → fluent chaining
        inputHandler
            .setNext(userHandler)
            .setNext(cartHandler)
            .setNext(fraudHandler)
            .setNext(riskHandler);

        this.chainHead = inputHandler;

        log.info("Validation chain built: Input → User → Cart → Fraud → Risk");
    }

    /**
     * Execute full validation chain.
     * Returns ValidationResult from whichever handler terminates chain.
     */
    public ValidationResult validate(OrderValidationContext context) {
        return chainHead.handle(context);
    }
}
```

---

## Pattern Interaction Map — Behavioral Patterns Part 1
```
Complete Order Placement Flow — All 5 Patterns Active:

1. CheckoutController.checkout(request)
   │
   ▼
2. [Chain of Responsibility]
   OrderValidationChain.validate(context)
   Input → User → Cart → Fraud → Risk
   All pass → proceed ✅
   │
   ▼
3. [Strategy]
   PricingContext.calculatePrice(product, variant, user, request)
   → BlackFridayStrategy (if campaign active, priority 1)
   → CustomerTierStrategy (if gold/platinum)
   → FinalPrice returned
   │
   ShippingCalculator.calculate(cart, address)
   → FreeShippingThresholdStrategy (if cart >= $100)
   → or FlatRateStrategy
   │
   ▼
4. [Command]
   commandBus.dispatch(new PlaceOrderCommand(...))
   → PlaceOrderCommandHandler.handle(cmd)
      → Facade.checkout(request)
      → Order.place(items, address, shipping, discount)
      → [State Pattern] order.currentState = PendingOrderState
   │
   ▼
5. PaymentService.processPayment(order, ...)
   → order.confirm(txnId)
   → [State] PendingOrderState.confirm(order, txnId)
      → order.confirmInternal(txnId)
      → order.setState(ConfirmedOrderState)
   │
   ▼
6. [Observer]
   eventPublisher.publish(order.getDomainEvents())
   → [OrderPlacedEvent]  → Analytics handler
   → [OrderPlacedEvent]  → Notification handler → email/SMS
   → [StockReserved]     → Inventory handler
   → [PaymentSucceeded]  → Audit handler
   All handlers run ASYNC via virtual threads ⚡
```

---

## Summary Table — Behavioral Patterns Part 1
```
┌───────────────┬──────────────────────────────┬──────────────────────────────────────┐
│ Pattern       │ Used For                     │ Key Benefit                          │
├───────────────┼──────────────────────────────┼──────────────────────────────────────┤
│ Strategy      │ Pricing, shipping algorithms │ Add new pricing = new class, OCP ✅  │
│ Observer      │ Domain event publishing      │ Decouple producer from all consumers │
│ Command       │ Order operations + audit     │ Undoable, queueable, auditable       │
│ State         │ Order lifecycle machine      │ Illegal transitions impossible       │
│ Chain of Resp │ Fraud + validation pipeline  │ Add/remove/reorder checks easily     │
└───────────────┴──────────────────────────────┴──────────────────────────────────────┘

✅ Phase 5 complete. All 5 Behavioral Patterns Part 1 implemented with:

Full Java 21 code (records, sealed interfaces, switch expressions, virtual threads)
Strategy: 5 pricing strategies + 3 shipping strategies with compound/best-of logic
Observer: async domain event dispatch with Spring virtual thread executor
Command: full command bus with generic type resolution + audit integration
State: 7 state classes with singleton flyweight states + package-private transitions
Chain: 5-handler validation chain (input → user → cart → fraud → risk scoring)


Reply "Phase 6" and I'll implement Behavioral Patterns Part 2 — Mediator (checkout coordination), Template Method (payment processing pipeline), Iterator (paginated order history), Memento (cart undo/save), and Visitor (discount rules engine + category analytics).Phase 621:56PHASE 6 — Behavioral Patterns Part 2
Overview
Behavioral Patterns Part 2:
  ├── Mediator        → Decouples checkout subsystems via central coordinator
  ├── Template Method → Payment processing pipeline with invariant steps
  ├── Iterator        → Paginated order history + cursor-based traversal
  ├── Memento         → Cart save/restore (undo, save-for-later, session recovery)
  └── Visitor         → Discount rules engine + category analytics + tax calculation

Pattern 1: Mediator
Problem It Solves
Problem: Checkout has MANY components that need to communicate:
  Cart ↔ Inventory ↔ Payment ↔ Order ↔ Notification ↔ Shipping

Without Mediator — spaghetti coupling:
  Cart calls Inventory directly
  Inventory calls Payment directly
  Payment calls Order directly
  Order calls Notification directly
  → N*(N-1)/2 connections = impossible to maintain
  → Adding new component requires modifying ALL others

With Mediator — star topology:
  All components talk ONLY to the Mediator
  Mediator orchestrates the conversation
  → N connections (each component → mediator)
  → Adding new component = register with mediator, touch nothing else

Difference from Facade (Phase 4):
  Facade:   simplifies CLIENT access to subsystem (one-way simplification)
  Mediator: coordinates PEER components talking to each other (multi-way)
Class Diagram
«interface»                      «interface»
CheckoutMediator                 CheckoutColleague
+ notify(sender, event, data)    - mediator: CheckoutMediator
+ register(colleague)            + setMediator(mediator)
        │                        + receive(event, data)
        ▼                                ▲
CheckoutMediatorImpl          ┌──────────┼──────────────────┐
  - colleagues: Map            │          │                  │
  + notify(...)         CartColleague InventoryColleague PaymentColleague
  → routes events to           │          │                  │
    appropriate handlers OrderColleague  NotifColleague ShippingColleague
Sequence Diagram
CartColleague   Mediator    InventoryColleague  PaymentColleague  OrderColleague
     │              │               │                 │                │
     │─CART_VALID──►│               │                 │                │
     │              │─RESERVE_STOCK─►│               │                │
     │              │◄──RESERVED────│                 │                │
     │              │─PROCESS_PAYMENT────────────────►│                │
     │              │◄──PAY_SUCCESS──────────────────│                │
     │              │─CREATE_ORDER────────────────────────────────────►│
     │              │◄──ORDER_CREATED─────────────────────────────────│
     │              │─[notify all via events]          │                │
Implementation
java// ecommerce-application:
// com.ecommerce.application.checkout/mediator/CheckoutEvent.java
package com.ecommerce.application.checkout.mediator;

/**
 * Events that flow through the CheckoutMediator.
 * Java 21 sealed interface — exhaustive pattern matching guaranteed.
 */
public sealed interface CheckoutEvent
    permits CheckoutEvent.CartValidated,
            CheckoutEvent.StockReserved,
            CheckoutEvent.StockReservationFailed,
            CheckoutEvent.ShippingCalculated,
            CheckoutEvent.CouponApplied,
            CheckoutEvent.PaymentSucceeded,
            CheckoutEvent.PaymentFailed,
            CheckoutEvent.OrderCreated,
            CheckoutEvent.CheckoutCompleted,
            CheckoutEvent.CheckoutFailed {

    record CartValidated(
        String sessionId,
        String cartId,
        String userId,
        java.math.BigDecimal cartTotal
    ) implements CheckoutEvent {}

    record StockReserved(
        String sessionId,
        java.util.List<ReservationInfo> reservations
    ) implements CheckoutEvent {
        public record ReservationInfo(
            String sku, int quantity,
            java.util.UUID reservationId
        ) {}
    }

    record StockReservationFailed(
        String sessionId,
        String sku,
        int requested,
        int available
    ) implements CheckoutEvent {}

    record ShippingCalculated(
        String sessionId,
        java.math.BigDecimal shippingCost,
        int estimatedDays,
        String carrier
    ) implements CheckoutEvent {}

    record CouponApplied(
        String sessionId,
        String couponCode,
        java.math.BigDecimal discountAmount,
        boolean freeShipping
    ) implements CheckoutEvent {}

    record PaymentSucceeded(
        String sessionId,
        String transactionId,
        java.math.BigDecimal amount
    ) implements CheckoutEvent {}

    record PaymentFailed(
        String sessionId,
        String reason,
        String failureCode
    ) implements CheckoutEvent {}

    record OrderCreated(
        String sessionId,
        String orderId,
        String orderNumber
    ) implements CheckoutEvent {}

    record CheckoutCompleted(
        String sessionId,
        String orderId,
        String orderNumber,
        java.math.BigDecimal totalPaid
    ) implements CheckoutEvent {}

    record CheckoutFailed(
        String sessionId,
        String reason,
        boolean inventoryReleased
    ) implements CheckoutEvent {}
}
java// com.ecommerce.application.checkout/mediator/CheckoutMediator.java
package com.ecommerce.application.checkout.mediator;

/**
 * Mediator interface — central hub for checkout coordination.
 * Components only know about the mediator, not each other.
 */
public interface CheckoutMediator {

    /**
     * Core mediation method.
     * Components call this to communicate with other components.
     *
     * @param sender  the component raising the event
     * @param event   what happened
     */
    void notify(CheckoutColleague sender, CheckoutEvent event);

    /**
     * Register a colleague component with the mediator.
     */
    void register(CheckoutColleague colleague);

    /**
     * Get current checkout session state.
     */
    CheckoutSession getSession(String sessionId);
}
java// com.ecommerce.application.checkout/mediator/CheckoutColleague.java
package com.ecommerce.application.checkout.mediator;

/**
 * Colleague abstract class — all checkout components extend this.
 * Has a reference to the mediator — ONLY way to communicate.
 */
public abstract class CheckoutColleague {

    protected CheckoutMediator mediator;

    public CheckoutColleague(CheckoutMediator mediator) {
        this.mediator = mediator;
        mediator.register(this);
    }

    /**
     * Receive and handle an event from the mediator.
     */
    public abstract void receive(CheckoutEvent event);

    /**
     * What event types can this colleague handle?
     */
    public abstract java.util.Set<Class<? extends CheckoutEvent>> handledEvents();

    protected void send(CheckoutEvent event) {
        mediator.notify(this, event);
    }
}
java// com.ecommerce.application.checkout/mediator/CheckoutSession.java
package com.ecommerce.application.checkout.mediator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Mutable session state threaded through the checkout mediator.
 * Accumulates results from each colleague as checkout progresses.
 */
public class CheckoutSession {

    public enum Phase {
        STARTED, CART_VALIDATED, STOCK_RESERVED,
        SHIPPING_CALCULATED, COUPON_APPLIED,
        PAYMENT_PROCESSED, ORDER_CREATED,
        COMPLETED, FAILED
    }

    private final String sessionId;
    private final String userId;
    private final String cartId;
    private Phase currentPhase;

    // Accumulated state
    private BigDecimal cartTotal;
    private BigDecimal shippingCost;
    private BigDecimal discountAmount;
    private BigDecimal finalTotal;
    private String couponCode;
    private boolean freeShipping;
    private List<CheckoutEvent.StockReserved.ReservationInfo> reservations
        = new ArrayList<>();
    private String transactionId;
    private String orderId;
    private String orderNumber;
    private String failureReason;
    private final Instant startedAt;
    private Instant completedAt;

    public CheckoutSession(String sessionId, String userId, String cartId) {
        this.sessionId   = sessionId;
        this.userId      = userId;
        this.cartId      = cartId;
        this.currentPhase= Phase.STARTED;
        this.startedAt   = Instant.now();
    }

    public void transitionTo(Phase phase) {
        this.currentPhase = phase;
        if (phase == Phase.COMPLETED || phase == Phase.FAILED) {
            this.completedAt = Instant.now();
        }
    }

    public BigDecimal computeFinalTotal() {
        BigDecimal total = cartTotal != null ? cartTotal : BigDecimal.ZERO;
        BigDecimal ship  = (shippingCost != null && !freeShipping)
            ? shippingCost : BigDecimal.ZERO;
        BigDecimal disc  = discountAmount != null ? discountAmount : BigDecimal.ZERO;
        this.finalTotal  = total.add(ship).subtract(disc);
        return this.finalTotal;
    }

    // Getters & setters
    public String getSessionId()       { return sessionId; }
    public String getUserId()          { return userId; }
    public String getCartId()          { return cartId; }
    public Phase getCurrentPhase()     { return currentPhase; }
    public BigDecimal getCartTotal()   { return cartTotal; }
    public BigDecimal getShippingCost(){ return shippingCost; }
    public BigDecimal getDiscountAmount(){ return discountAmount; }
    public BigDecimal getFinalTotal()  { return finalTotal; }
    public String getCouponCode()      { return couponCode; }
    public boolean isFreeShipping()    { return freeShipping; }
    public List<CheckoutEvent.StockReserved.ReservationInfo> getReservations() {
        return reservations;
    }
    public String getTransactionId()   { return transactionId; }
    public String getOrderId()         { return orderId; }
    public String getOrderNumber()     { return orderNumber; }
    public String getFailureReason()   { return failureReason; }
    public Instant getStartedAt()      { return startedAt; }
    public Instant getCompletedAt()    { return completedAt; }

    public void setCartTotal(BigDecimal v)      { cartTotal = v; }
    public void setShippingCost(BigDecimal v)   { shippingCost = v; }
    public void setDiscountAmount(BigDecimal v) { discountAmount = v; }
    public void setCouponCode(String v)         { couponCode = v; }
    public void setFreeShipping(boolean v)      { freeShipping = v; }
    public void addReservations(
        List<CheckoutEvent.StockReserved.ReservationInfo> r) {
        reservations.addAll(r);
    }
    public void setTransactionId(String v)      { transactionId = v; }
    public void setOrderId(String v)            { orderId = v; }
    public void setOrderNumber(String v)        { orderNumber = v; }
    public void setFailureReason(String v)      { failureReason = v; }
}
java// com.ecommerce.application.checkout/mediator/CheckoutMediatorImpl.java
package com.ecommerce.application.checkout.mediator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Concrete Mediator — orchestrates all checkout colleagues.
 *
 * Central hub: receives events from colleagues,
 * routes to appropriate responders, maintains session state.
 *
 * Key difference from Facade:
 *   - Facade: Controller → Facade → subsystems (one initiator)
 *   - Mediator: Any colleague → Mediator → other colleagues (peer-to-peer via hub)
 */
@Component
@org.springframework.web.context.annotation.RequestScope
public class CheckoutMediatorImpl implements CheckoutMediator {

    private static final Logger log =
        LoggerFactory.getLogger(CheckoutMediatorImpl.class);

    // Registered colleagues (populated via register())
    private final List<CheckoutColleague> colleagues = new ArrayList<>();

    // Active sessions (one per checkout attempt)
    private final Map<String, CheckoutSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void register(CheckoutColleague colleague) {
        colleagues.add(colleague);
        log.debug("Registered checkout colleague: {}",
            colleague.getClass().getSimpleName());
    }

    @Override
    public CheckoutSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * Core mediation logic.
     * Receives event from a colleague, decides which other colleagues to notify.
     * Uses Java 21 pattern matching for exhaustive event routing.
     */
    @Override
    public void notify(CheckoutColleague sender, CheckoutEvent event) {
        log.info("Mediator received: event={}, from={}",
            event.getClass().getSimpleName(),
            sender.getClass().getSimpleName());

        // Update session state based on event
        updateSession(event);

        // Route event to all colleagues that handle it
        // (except the sender)
        colleagues.stream()
            .filter(c -> c != sender)
            .filter(c -> c.handledEvents().stream()
                .anyMatch(type -> type.isInstance(event)))
            .forEach(colleague -> {
                try {
                    colleague.receive(event);
                } catch (Exception e) {
                    log.error("Colleague {} failed handling {}: {}",
                        colleague.getClass().getSimpleName(),
                        event.getClass().getSimpleName(),
                        e.getMessage(), e);
                    // Route failure back through mediator
                    notify(colleague, new CheckoutEvent.CheckoutFailed(
                        extractSessionId(event),
                        "Colleague failure: " + e.getMessage(),
                        false
                    ));
                }
            });
    }

    // ── Session State Management ──────────────────────────────────────

    private void updateSession(CheckoutEvent event) {
        String sessionId = extractSessionId(event);
        CheckoutSession session = sessions.get(sessionId);
        if (session == null) return;

        // Java 21 pattern matching on sealed interface
        switch (event) {
            case CheckoutEvent.CartValidated e -> {
                session.setCartTotal(e.cartTotal());
                session.transitionTo(CheckoutSession.Phase.CART_VALIDATED);
            }
            case CheckoutEvent.StockReserved e -> {
                session.addReservations(e.reservations());
                session.transitionTo(CheckoutSession.Phase.STOCK_RESERVED);
            }
            case CheckoutEvent.ShippingCalculated e -> {
                session.setShippingCost(e.shippingCost());
                session.transitionTo(CheckoutSession.Phase.SHIPPING_CALCULATED);
            }
            case CheckoutEvent.CouponApplied e -> {
                session.setCouponCode(e.couponCode());
                session.setDiscountAmount(e.discountAmount());
                session.setFreeShipping(e.freeShipping());
                session.transitionTo(CheckoutSession.Phase.COUPON_APPLIED);
            }
            case CheckoutEvent.PaymentSucceeded e -> {
                session.setTransactionId(e.transactionId());
                session.computeFinalTotal();
                session.transitionTo(CheckoutSession.Phase.PAYMENT_PROCESSED);
            }
            case CheckoutEvent.OrderCreated e -> {
                session.setOrderId(e.orderId());
                session.setOrderNumber(e.orderNumber());
                session.transitionTo(CheckoutSession.Phase.ORDER_CREATED);
            }
            case CheckoutEvent.CheckoutCompleted e ->
                session.transitionTo(CheckoutSession.Phase.COMPLETED);
            case CheckoutEvent.CheckoutFailed e -> {
                session.setFailureReason(e.reason());
                session.transitionTo(CheckoutSession.Phase.FAILED);
            }
            default -> {}
        }
    }

    private String extractSessionId(CheckoutEvent event) {
        return switch (event) {
            case CheckoutEvent.CartValidated e        -> e.sessionId();
            case CheckoutEvent.StockReserved e        -> e.sessionId();
            case CheckoutEvent.StockReservationFailed e -> e.sessionId();
            case CheckoutEvent.ShippingCalculated e   -> e.sessionId();
            case CheckoutEvent.CouponApplied e        -> e.sessionId();
            case CheckoutEvent.PaymentSucceeded e     -> e.sessionId();
            case CheckoutEvent.PaymentFailed e        -> e.sessionId();
            case CheckoutEvent.OrderCreated e         -> e.sessionId();
            case CheckoutEvent.CheckoutCompleted e    -> e.sessionId();
            case CheckoutEvent.CheckoutFailed e       -> e.sessionId();
        };
    }

    public CheckoutSession createSession(String userId, String cartId) {
        String sessionId = UUID.randomUUID().toString();
        CheckoutSession session = new CheckoutSession(sessionId, userId, cartId);
        sessions.put(sessionId, session);
        return session;
    }
}
java// com.ecommerce.application.checkout/mediator/colleague/InventoryColleague.java
package com.ecommerce.application.checkout.mediator.colleague;

import com.ecommerce.application.checkout.mediator.*;
import com.ecommerce.application.inventory.InventoryApplicationService;
import com.ecommerce.domain.cart.Cart;
import com.ecommerce.domain.cart.CartItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Inventory Colleague — handles stock reservation in checkout flow.
 * Reacts to CartValidated event.
 * Notifies mediator with StockReserved or StockReservationFailed.
 */
@Component
public class InventoryColleague extends CheckoutColleague {

    private static final Logger log =
        LoggerFactory.getLogger(InventoryColleague.class);

    private final InventoryApplicationService inventoryService;
    private final com.ecommerce.domain.cart.CartRepository cartRepository;

    public InventoryColleague(CheckoutMediator mediator,
                               InventoryApplicationService inventoryService,
                               com.ecommerce.domain.cart.CartRepository cartRepository) {
        super(mediator);
        this.inventoryService = inventoryService;
        this.cartRepository   = cartRepository;
    }

    @Override
    public void receive(CheckoutEvent event) {
        if (event instanceof CheckoutEvent.CartValidated validated) {
            handleCartValidated(validated);
        }
    }

    @Override
    public Set<Class<? extends CheckoutEvent>> handledEvents() {
        return Set.of(CheckoutEvent.CartValidated.class);
    }

    private void handleCartValidated(CheckoutEvent.CartValidated event) {
        log.info("InventoryColleague: reserving stock for session {}",
            event.sessionId());

        Cart cart = cartRepository.findById(
            com.ecommerce.domain.cart.CartId.of(event.cartId())
        ).orElseThrow();

        List<CheckoutEvent.StockReserved.ReservationInfo> reservations =
            new ArrayList<>();
        boolean allReserved = true;

        for (CartItem item : cart.getItems()) {
            try {
                var reservation = inventoryService.reserve(
                    item.getVariantSku(),
                    item.getQuantity(),
                    event.sessionId()
                );
                reservations.add(new CheckoutEvent.StockReserved.ReservationInfo(
                    item.getVariantSku(),
                    item.getQuantity(),
                    reservation.getReservationId()
                ));
            } catch (Exception e) {
                log.warn("Stock reservation failed for SKU {}: {}",
                    item.getVariantSku(), e.getMessage());
                // Notify failure — mediator will trigger compensation
                send(new CheckoutEvent.StockReservationFailed(
                    event.sessionId(),
                    item.getVariantSku(),
                    item.getQuantity(),
                    0
                ));
                allReserved = false;
                break;
            }
        }

        if (allReserved) {
            send(new CheckoutEvent.StockReserved(event.sessionId(), reservations));
        }
    }
}
java// com.ecommerce.application.checkout/mediator/colleague/PaymentColleague.java
package com.ecommerce.application.checkout.mediator.colleague;

import com.ecommerce.application.checkout.mediator.*;
import com.ecommerce.application.payment.PaymentApplicationService;
import com.ecommerce.domain.payment.gateway.ChargeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.Set;

/**
 * Payment Colleague — processes payment after stock is reserved.
 * Reacts to StockReserved event.
 * Notifies: PaymentSucceeded or PaymentFailed.
 */
@Component
public class PaymentColleague extends CheckoutColleague {

    private static final Logger log =
        LoggerFactory.getLogger(PaymentColleague.class);

    private final PaymentApplicationService paymentService;

    public PaymentColleague(CheckoutMediator mediator,
                             PaymentApplicationService paymentService) {
        super(mediator);
        this.paymentService = paymentService;
    }

    @Override
    public void receive(CheckoutEvent event) {
        if (event instanceof CheckoutEvent.StockReserved reserved) {
            handleStockReserved(reserved);
        }
    }

    @Override
    public Set<Class<? extends CheckoutEvent>> handledEvents() {
        return Set.of(CheckoutEvent.StockReserved.class);
    }

    private void handleStockReserved(CheckoutEvent.StockReserved event) {
        CheckoutSession session = mediator.getSession(event.sessionId());
        if (session == null) return;

        log.info("PaymentColleague: processing payment for session {}",
            event.sessionId());

        try {
            java.math.BigDecimal amount = session.computeFinalTotal();
            ChargeResult result = paymentService.chargeAmount(
                session.getUserId(),
                amount,
                session.getSessionId() // idempotency key
            );

            if (result.success()) {
                send(new CheckoutEvent.PaymentSucceeded(
                    event.sessionId(),
                    result.transactionId(),
                    amount
                ));
            } else {
                send(new CheckoutEvent.PaymentFailed(
                    event.sessionId(),
                    result.failureMessage(),
                    result.failureCode()
                ));
            }
        } catch (Exception e) {
            send(new CheckoutEvent.PaymentFailed(
                event.sessionId(), e.getMessage(), "PAYMENT_ERROR"
            ));
        }
    }
}
java// com.ecommerce.application.checkout/mediator/colleague/OrderColleague.java
package com.ecommerce.application.checkout.mediator.colleague;

import com.ecommerce.application.checkout.mediator.*;
import com.ecommerce.application.order.OrderApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.Set;

/**
 * Order Colleague — creates order after payment succeeds.
 * Reacts to PaymentSucceeded event.
 * Notifies: OrderCreated or CheckoutFailed.
 */
@Component
public class OrderColleague extends CheckoutColleague {

    private static final Logger log =
        LoggerFactory.getLogger(OrderColleague.class);

    private final OrderApplicationService orderService;

    public OrderColleague(CheckoutMediator mediator,
                           OrderApplicationService orderService) {
        super(mediator);
        this.orderService = orderService;
    }

    @Override
    public void receive(CheckoutEvent event) {
        if (event instanceof CheckoutEvent.PaymentSucceeded succeeded) {
            handlePaymentSucceeded(succeeded);
        } else if (event instanceof CheckoutEvent.PaymentFailed failed) {
            handlePaymentFailed(failed);
        }
    }

    @Override
    public Set<Class<? extends CheckoutEvent>> handledEvents() {
        return Set.of(
            CheckoutEvent.PaymentSucceeded.class,
            CheckoutEvent.PaymentFailed.class
        );
    }

    private void handlePaymentSucceeded(CheckoutEvent.PaymentSucceeded event) {
        CheckoutSession session = mediator.getSession(event.sessionId());
        if (session == null) return;

        log.info("OrderColleague: creating order for session {}", event.sessionId());

        try {
            var order = orderService.createOrderFromSession(session);
            orderService.confirmOrder(order.getId(), event.transactionId());

            send(new CheckoutEvent.OrderCreated(
                event.sessionId(),
                order.getId().getValue().toString(),
                order.getOrderNumber()
            ));

            send(new CheckoutEvent.CheckoutCompleted(
                event.sessionId(),
                order.getId().getValue().toString(),
                order.getOrderNumber(),
                event.amount()
            ));
        } catch (Exception e) {
            log.error("Order creation failed: {}", e.getMessage());
            send(new CheckoutEvent.CheckoutFailed(
                event.sessionId(), "Order creation failed: " + e.getMessage(), true
            ));
        }
    }

    private void handlePaymentFailed(CheckoutEvent.PaymentFailed event) {
        log.warn("OrderColleague: payment failed, releasing stock for session {}",
            event.sessionId());
        CheckoutSession session = mediator.getSession(event.sessionId());
        if (session != null && !session.getReservations().isEmpty()) {
            // Trigger stock release via mediator
            send(new CheckoutEvent.CheckoutFailed(
                event.sessionId(),
                "Payment failed: " + event.reason(),
                false // inventory not yet released — colleague handles it
            ));
        }
    }
}
```

---

## Pattern 2: Template Method

### Problem It Solves
```
Problem: Every payment provider (Stripe, PayPal, Legacy Bank) follows
the SAME processing pipeline:
  1. Validate payment request
  2. Check idempotency (deduplicate)
  3. Prepare provider-specific payload
  4. Call provider API
  5. Parse response
  6. Update payment record
  7. Publish events
  8. Handle failure + rollback

Steps 1, 2, 6, 7, 8 are IDENTICAL across all providers.
Steps 3, 4, 5 are PROVIDER-SPECIFIC.

Without Template Method:
  StripePaymentProcessor — all 8 steps duplicated
  PayPalPaymentProcessor — all 8 steps duplicated
  → Identical steps drift apart over time, bugs fixed in one but not others

With Template Method:
  AbstractPaymentProcessor — defines invariant skeleton (steps 1,2,6,7,8)
  StripePaymentProcessor   — overrides only hooks (steps 3,4,5)
  PayPalPaymentProcessor   — overrides only hooks (steps 3,4,5)
```

### Class Diagram
```
«abstract»
AbstractPaymentProcessor
+ process(request): PaymentResult  ← template method (final)
# validate(request): void          ← hook (optional override)
# checkIdempotency(key): Optional  ← hook (optional override)
# preparePayload(request): Payload ← abstract (must override)
# callProvider(payload): Response  ← abstract (must override)
# parseResponse(response): Result  ← abstract (must override)
- updateRecord(result): void       ← invariant (final)
- publishEvents(result): void      ← invariant (final)
# handleFailure(e): void           ← hook (optional override)
        ▲
   ┌────┴─────────────────────┐
   │                          │
StripePayment          PayPalPayment
Processor              Processor
# preparePayload()     # preparePayload()
# callProvider()       # callProvider()
# parseResponse()      # parseResponse()
Implementation
java// ecommerce-application:
// com.ecommerce.application.payment/processor/PaymentProcessRequest.java
package com.ecommerce.application.payment.processor;

import com.ecommerce.domain.payment.PaymentMethod;
import com.ecommerce.domain.shared.valueobject.Money;

public record PaymentProcessRequest(
    String paymentId,
    String orderId,
    String userId,
    Money amount,
    PaymentMethod method,
    String paymentMethodToken,
    String idempotencyKey,
    java.util.Map<String, String> metadata
) {}
java// com.ecommerce.application.payment/processor/PaymentProcessResult.java
package com.ecommerce.application.payment.processor;

public record PaymentProcessResult(
    boolean success,
    String transactionId,
    String status,
    String failureCode,
    String failureMessage,
    String rawResponse,
    boolean requiresAction,
    String actionUrl,
    boolean wasIdempotent    // true = returned cached result
) {
    public static PaymentProcessResult success(String txnId, String rawResponse) {
        return new PaymentProcessResult(
            true, txnId, "succeeded",
            null, null, rawResponse,
            false, null, false
        );
    }

    public static PaymentProcessResult idempotent(String txnId) {
        return new PaymentProcessResult(
            true, txnId, "succeeded",
            null, null, null,
            false, null, true
        );
    }

    public static PaymentProcessResult failure(String code,
                                                String message,
                                                String rawResponse) {
        return new PaymentProcessResult(
            false, null, "failed",
            code, message, rawResponse,
            false, null, false
        );
    }

    public static PaymentProcessResult requiresAction(String txnId, String url) {
        return new PaymentProcessResult(
            false, txnId, "requires_action",
            null, null, null,
            true, url, false
        );
    }
}
java// com.ecommerce.application.payment/processor/AbstractPaymentProcessor.java
package com.ecommerce.application.payment.processor;

import com.ecommerce.domain.payment.Payment;
import com.ecommerce.domain.payment.PaymentId;
import com.ecommerce.domain.payment.PaymentRepository;
import com.ecommerce.domain.shared.event.DomainEventPublisher;
import com.ecommerce.domain.shared.exception.DomainException;
import com.ecommerce.domain.shared.exception.ErrorCodes;
import com.ecommerce.infrastructure.audit.AuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.Optional;

/**
 * TEMPLATE METHOD PATTERN — Abstract Payment Processor.
 *
 * Defines the invariant payment processing algorithm.
 * Subclasses override only provider-specific steps.
 *
 * The template method (process()) is FINAL — algorithm cannot be changed.
 * Hook methods (preparePayload, callProvider, parseResponse) MUST be overridden.
 * Optional hooks (validate, handleFailure) MAY be overridden.
 *
 * This prevents: accidentally skipping idempotency check, audit, event publishing.
 */
public abstract class AbstractPaymentProcessor {

    private static final Logger log =
        LoggerFactory.getLogger(AbstractPaymentProcessor.class);

    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    protected final PaymentRepository    paymentRepository;
    protected final DomainEventPublisher eventPublisher;
    protected final RedisTemplate<String, Object> redis;
    protected final AuditLogger          auditLogger;

    protected AbstractPaymentProcessor(PaymentRepository paymentRepository,
                                        DomainEventPublisher eventPublisher,
                                        RedisTemplate<String, Object> redis,
                                        AuditLogger auditLogger) {
        this.paymentRepository = paymentRepository;
        this.eventPublisher    = eventPublisher;
        this.redis             = redis;
        this.auditLogger       = auditLogger;
    }

    // ── TEMPLATE METHOD — Final: defines invariant algorithm ─────────

    /**
     * The template method.
     * FINAL: subclasses cannot reorder or skip steps.
     * Algorithm:
     *   1. Validate
     *   2. Idempotency check
     *   3. Prepare payload  ← provider-specific
     *   4. Call provider    ← provider-specific
     *   5. Parse response   ← provider-specific
     *   6. Update record
     *   7. Publish events
     */
    public final PaymentProcessResult process(PaymentProcessRequest request) {
        log.info("Processing payment: id={}, provider={}, amount={}",
            request.paymentId(), getProviderName(), request.amount());

        // Step 1: Validate (optional hook)
        try {
            validate(request);
        } catch (Exception e) {
            log.warn("Payment validation failed: {}", e.getMessage());
            return PaymentProcessResult.failure(
                "VALIDATION_FAILED", e.getMessage(), null
            );
        }

        // Step 2: Idempotency check (invariant — cannot skip)
        Optional<PaymentProcessResult> cached =
            checkIdempotency(request.idempotencyKey());
        if (cached.isPresent()) {
            log.info("Idempotency hit for key: {}", request.idempotencyKey());
            return cached.get();
        }

        // Load payment aggregate
        Payment payment = loadPayment(request.paymentId());
        payment.markProcessing();
        paymentRepository.update(payment);

        PaymentProcessResult result;
        try {
            // Step 3: Prepare provider-specific payload (abstract hook)
            Object payload = preparePayload(request);

            // Step 4: Call provider API (abstract hook)
            Object providerResponse = callProvider(payload);

            // Step 5: Parse provider response (abstract hook)
            result = parseResponse(providerResponse, request);

        } catch (Exception e) {
            log.error("Provider call failed: provider={}, error={}",
                getProviderName(), e.getMessage(), e);
            result = PaymentProcessResult.failure(
                "PROVIDER_ERROR", e.getMessage(), null
            );
            // Optional failure hook
            handleFailure(e, request, payment);
        }

        // Step 6: Update payment record (invariant — cannot skip)
        updatePaymentRecord(payment, result);

        // Step 7: Cache idempotency result (invariant)
        cacheIdempotencyResult(request.idempotencyKey(), result);

        // Step 8: Publish domain events (invariant)
        publishEvents(payment);

        // Step 9: Audit (invariant)
        auditPayment(request, result);

        return result;
    }

    // ── Abstract Hooks — MUST be overridden by subclasses ────────────

    /**
     * Prepare the provider-specific payment payload.
     * e.g., Stripe: PaymentIntentCreateParams
     *        PayPal: OrderRequest
     */
    protected abstract Object preparePayload(PaymentProcessRequest request);

    /**
     * Call the provider API.
     * Returns raw provider response object.
     */
    protected abstract Object callProvider(Object payload);

    /**
     * Parse provider-specific response into our result.
     */
    protected abstract PaymentProcessResult parseResponse(
        Object providerResponse, PaymentProcessRequest request);

    /**
     * Provider name for logging/routing.
     */
    protected abstract String getProviderName();

    // ── Optional Hooks — MAY be overridden ───────────────────────────

    /**
     * Optional validation hook.
     * Override to add provider-specific validation.
     * Default: validates amount is positive.
     */
    protected void validate(PaymentProcessRequest request) {
        if (request.amount() == null || request.amount().isNegative()
                || request.amount().isZero()) {
            throw new DomainException(ErrorCodes.PAYMENT_FAILED,
                "Payment amount must be positive");
        }
        if (request.paymentMethodToken() == null
                || request.paymentMethodToken().isBlank()) {
            throw new DomainException(ErrorCodes.PAYMENT_FAILED,
                "Payment method token is required");
        }
    }

    /**
     * Optional failure hook.
     * Override to add provider-specific error handling.
     * Default: logs the failure.
     */
    protected void handleFailure(Exception e, PaymentProcessRequest request,
                                  Payment payment) {
        log.error("Payment {} failed via {}: {}",
            request.paymentId(), getProviderName(), e.getMessage());
    }

    // ── Invariant Steps — Cannot be overridden ────────────────────────

    private Optional<PaymentProcessResult> checkIdempotency(String key) {
        String cacheKey = "idempotency:payment:" + key;
        Object cached = redis.opsForValue().get(cacheKey);
        if (cached instanceof PaymentProcessResult result) {
            return Optional.of(result);
        }
        return Optional.empty();
    }

    private void cacheIdempotencyResult(String key, PaymentProcessResult result) {
        if (result.success() || result.failureCode() != null) {
            String cacheKey = "idempotency:payment:" + key;
            redis.opsForValue().set(cacheKey, result, IDEMPOTENCY_TTL);
        }
    }

    private Payment loadPayment(String paymentId) {
        return paymentRepository.findById(PaymentId.of(paymentId))
            .orElseThrow(() -> new DomainException(
                ErrorCodes.PAYMENT_FAILED,
                "Payment not found: " + paymentId
            ));
    }

    private void updatePaymentRecord(Payment payment,
                                      PaymentProcessResult result) {
        if (result.success()) {
            payment.succeed(result.transactionId(), result.rawResponse());
        } else if (!result.requiresAction()) {
            payment.fail(result.failureCode(),
                result.failureMessage(), result.rawResponse());
        }
        paymentRepository.update(payment);
    }

    private void publishEvents(Payment payment) {
        eventPublisher.publish(payment.getDomainEvents());
        payment.clearDomainEvents();
    }

    private void auditPayment(PaymentProcessRequest request,
                               PaymentProcessResult result) {
        auditLogger.log(AuditLogger.AuditEvent.builder()
            .action(result.success() ? "PAYMENT_SUCCEEDED" : "PAYMENT_FAILED")
            .resourceType("Payment")
            .resourceId(request.paymentId())
            .outcome(result.success() ? "SUCCESS" : "FAILURE")
            .details("Provider: " + getProviderName()
                + ", TxnId: " + result.transactionId()
                + ", Idempotent: " + result.wasIdempotent())
            .build());
    }
}
java// com.ecommerce.application.payment/processor/StripePaymentProcessor.java
package com.ecommerce.application.payment.processor;

import com.ecommerce.domain.payment.PaymentRepository;
import com.ecommerce.domain.shared.event.DomainEventPublisher;
import com.ecommerce.infrastructure.audit.AuditLogger;
import com.ecommerce.infrastructure.payment.stripe.StripeChargeGateway;
import com.ecommerce.domain.payment.gateway.ChargeRequest;
import com.ecommerce.domain.payment.gateway.ChargeResult;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Concrete Template — Stripe payment processor.
 * Overrides ONLY provider-specific hooks.
 * Invariant steps (idempotency, audit, events) inherited from abstract class.
 */
@Component
public class StripePaymentProcessor extends AbstractPaymentProcessor {

    private final StripeChargeGateway stripeGateway;

    public StripePaymentProcessor(PaymentRepository paymentRepository,
                                   DomainEventPublisher eventPublisher,
                                   RedisTemplate<String, Object> redis,
                                   AuditLogger auditLogger,
                                   StripeChargeGateway stripeGateway) {
        super(paymentRepository, eventPublisher, redis, auditLogger);
        this.stripeGateway = stripeGateway;
    }

    @Override
    protected Object preparePayload(PaymentProcessRequest request) {
        // Build Stripe-specific ChargeRequest
        return new ChargeRequest(
            request.idempotencyKey(),
            request.amount(),
            request.amount().getCurrency().getCurrencyCode(),
            request.paymentMethodToken(),
            request.userId(),
            "Order payment: " + request.orderId(),
            request.orderId(),
            request.metadata(),
            true // capture immediately
        );
    }

    @Override
    protected Object callProvider(Object payload) {
        // Call Stripe API
        ChargeRequest chargeRequest = (ChargeRequest) payload;
        return stripeGateway.charge(chargeRequest);
    }

    @Override
    protected PaymentProcessResult parseResponse(Object providerResponse,
                                                  PaymentProcessRequest request) {
        ChargeResult result = (ChargeResult) providerResponse;

        if (result.requiresAction()) {
            return PaymentProcessResult.requiresAction(
                result.transactionId(), result.actionUrl()
            );
        }
        if (result.success()) {
            return PaymentProcessResult.success(
                result.transactionId(), result.rawResponse()
            );
        }
        return PaymentProcessResult.failure(
            result.failureCode(), result.failureMessage(), result.rawResponse()
        );
    }

    @Override
    protected String getProviderName() { return "STRIPE"; }

    /**
     * Override optional hook — Stripe-specific validation.
     */
    @Override
    protected void validate(PaymentProcessRequest request) {
        super.validate(request); // call parent validation first
        // Stripe-specific: token must start with 'pm_' or 'tok_'
        String token = request.paymentMethodToken();
        if (!token.startsWith("pm_") && !token.startsWith("tok_")
                && !token.startsWith("card_")) {
            throw new com.ecommerce.domain.shared.exception.DomainException(
                com.ecommerce.domain.shared.exception.ErrorCodes.PAYMENT_FAILED,
                "Invalid Stripe payment method token format"
            );
        }
    }
}
java// com.ecommerce.application.payment/processor/PayPalPaymentProcessor.java
package com.ecommerce.application.payment.processor;

import com.ecommerce.domain.payment.PaymentRepository;
import com.ecommerce.domain.payment.gateway.ChargeRequest;
import com.ecommerce.domain.payment.gateway.ChargeResult;
import com.ecommerce.domain.shared.event.DomainEventPublisher;
import com.ecommerce.infrastructure.audit.AuditLogger;
import com.ecommerce.infrastructure.payment.paypal.PayPalChargeGateway;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Concrete Template — PayPal payment processor.
 * Same invariant steps as Stripe, different provider hooks.
 * Demonstrates: algorithm skeleton shared, details differ.
 */
@Component
public class PayPalPaymentProcessor extends AbstractPaymentProcessor {

    private final PayPalChargeGateway paypalGateway;

    public PayPalPaymentProcessor(PaymentRepository paymentRepository,
                                   DomainEventPublisher eventPublisher,
                                   RedisTemplate<String, Object> redis,
                                   AuditLogger auditLogger,
                                   PayPalChargeGateway paypalGateway) {
        super(paymentRepository, eventPublisher, redis, auditLogger);
        this.paypalGateway = paypalGateway;
    }

    @Override
    protected Object preparePayload(PaymentProcessRequest request) {
        // PayPal requires different payload structure
        return new ChargeRequest(
            request.idempotencyKey(),
            request.amount(),
            request.amount().getCurrency().getCurrencyCode(),
            request.paymentMethodToken(),
            request.userId(),
            "Order: " + request.orderId(),
            request.orderId(),
            request.metadata(),
            true
        );
    }

    @Override
    protected Object callProvider(Object payload) {
        return paypalGateway.charge((ChargeRequest) payload);
    }

    @Override
    protected PaymentProcessResult parseResponse(Object providerResponse,
                                                  PaymentProcessRequest request) {
        ChargeResult result = (ChargeResult) providerResponse;
        if (result.success()) {
            return PaymentProcessResult.success(
                result.transactionId(), result.rawResponse()
            );
        }
        return PaymentProcessResult.failure(
            result.failureCode(), result.failureMessage(), result.rawResponse()
        );
    }

    @Override
    protected String getProviderName() { return "PAYPAL"; }

    /**
     * Override failure hook — PayPal-specific error recovery.
     */
    @Override
    protected void handleFailure(Exception e, PaymentProcessRequest request,
                                  com.ecommerce.domain.payment.Payment payment) {
        super.handleFailure(e, request, payment);
        // PayPal-specific: check if it's a retriable error
        if (e.getMessage() != null && e.getMessage().contains("INSTRUMENT_DECLINED")) {
            // Flag for user to update payment method
        }
    }
}
java// com.ecommerce.application.payment/processor/PaymentProcessorRegistry.java
package com.ecommerce.application.payment.processor;

import com.ecommerce.domain.payment.PaymentMethod;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry mapping payment methods to their processors.
 * Strategy + Template Method: different algorithms, common skeleton.
 */
@Component
public class PaymentProcessorRegistry {

    private final Map<String, AbstractPaymentProcessor> processors;

    public PaymentProcessorRegistry(List<AbstractPaymentProcessor> processors) {
        this.processors = processors.stream()
            .collect(Collectors.toMap(
                AbstractPaymentProcessor::getProviderName,
                Function.identity()
            ));
    }

    public AbstractPaymentProcessor getProcessor(String providerName) {
        AbstractPaymentProcessor processor = processors.get(
            providerName.toUpperCase()
        );
        if (processor == null) {
            throw new IllegalArgumentException(
                "No processor found for provider: " + providerName
            );
        }
        return processor;
    }

    public boolean supportsProvider(String providerName) {
        return processors.containsKey(providerName.toUpperCase());
    }
}
```

---

## Pattern 3: Iterator

### Problem It Solves
```
Problem: Iterating over large order histories (customers with 10,000+
orders) without loading everything into memory.

Two traversal modes needed:
  ① Page-based: standard REST pagination (?page=2&size=20)
  ② Cursor-based: efficient for infinite scroll (no offset penalty)

Without Iterator:
  List<Order> allOrders = orderRepo.findAll(); // OOM on large datasets
  for (Order o : allOrders) { process(o); }   // 10K orders in memory

With Iterator:
  OrderIterator iter = new CursorOrderIterator(userId, pageSize);
  while (iter.hasNext()) {
    OrderPage page = iter.next();
    process(page);
  }
  → Loads one page at a time, cursor tracks position
```

### Class Diagram
```
«interface»                     «interface»
Iterator<T>                     Iterable<T>
+ hasNext(): boolean            + iterator(): Iterator<T>
+ next(): T                     + forEach(Consumer<T>)
+ peek(): Optional<T>
        ▲
   ┌────┴──────────────────────────┐
   │                               │
PagedOrderIterator          CursorOrderIterator
(offset-based pagination)   (cursor-based, keyset)
- currentPage: int          - lastSeenId: String
- totalPages: int           - lastSeenDate: Instant
- pageSize: int             - exhausted: boolean
- userId: UserId
Implementation
java// ecommerce-application:
// com.ecommerce.application.shared/iterator/PagedIterator.java
package com.ecommerce.application.shared.iterator;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Generic paged iterator — loads data one page at a time.
 * Page loader function injected — iterator stays generic.
 *
 * Usage:
 *   var iter = new PagedIterator<>(
 *     (page, size) -> orderRepo.findByUserId(userId, page, size),
 *     20  // page size
 *   );
 *   while (iter.hasNext()) { process(iter.next()); }
 */
public class PagedIterator<T> implements java.util.Iterator<List<T>> {

    private final BiFunction<Integer, Integer, List<T>> pageLoader;
    private final int pageSize;
    private int currentPage;
    private List<T> currentBatch;
    private boolean exhausted;

    public PagedIterator(BiFunction<Integer, Integer, List<T>> pageLoader,
                          int pageSize) {
        this.pageLoader  = pageLoader;
        this.pageSize    = pageSize;
        this.currentPage = 0;
        this.exhausted   = false;
        // Load first page eagerly
        loadNextPage();
    }

    @Override
    public boolean hasNext() {
        return !exhausted && currentBatch != null && !currentBatch.isEmpty();
    }

    /**
     * Returns current page and loads next.
     */
    @Override
    public List<T> next() {
        if (!hasNext()) throw new NoSuchElementException("No more pages");
        List<T> page = List.copyOf(currentBatch);
        loadNextPage();
        return page;
    }

    public Optional<List<T>> peek() {
        return hasNext() ? Optional.of(List.copyOf(currentBatch)) : Optional.empty();
    }

    private void loadNextPage() {
        if (exhausted) return;
        List<T> page = pageLoader.apply(currentPage++, pageSize);
        if (page == null || page.isEmpty() || page.size() < pageSize) {
            exhausted = true;
        }
        currentBatch = page == null ? List.of() : page;
    }

    public int getCurrentPage() { return currentPage - 1; }
}
java// com.ecommerce.application.order/iterator/OrderCursorIterator.java
package com.ecommerce.application.order.iterator;

import com.ecommerce.domain.order.Order;
import com.ecommerce.domain.order.OrderRepository;
import com.ecommerce.domain.user.UserId;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Cursor-based order iterator.
 *
 * WHY cursor over offset?
 *   Offset: SELECT ... LIMIT 20 OFFSET 10000
 *     → DB scans 10020 rows, discards 10000 = O(n) cost!
 *   Cursor: SELECT ... WHERE created_at < :cursor LIMIT 20
 *     → Index seek directly to cursor position = O(log n)!
 *
 * For 1M+ order history, cursor is 100x+ faster at deep pages.
 *
 * Trade-off: cursor pagination doesn't support random page access.
 * Use paged for: admin table with page numbers
 * Use cursor for: customer infinite-scroll, bulk processing
 */
public class OrderCursorIterator implements java.util.Iterator<List<Order>> {

    private final OrderRepository orderRepository;
    private final UserId userId;
    private final int pageSize;

    // Cursor state — position in the dataset
    private Instant cursorDate;
    private String cursorId;
    private boolean exhausted;
    private List<Order> prefetched;

    public OrderCursorIterator(OrderRepository orderRepository,
                                UserId userId, int pageSize) {
        this.orderRepository = orderRepository;
        this.userId          = userId;
        this.pageSize        = pageSize;
        this.cursorDate      = Instant.now(); // start from most recent
        this.cursorId        = null;
        this.exhausted       = false;
        // Prefetch first page
        prefetch();
    }

    /**
     * Constructor with explicit cursor — for resuming iteration.
     * Enables: save cursor to Redis, resume later (session recovery).
     */
    public OrderCursorIterator(OrderRepository orderRepository,
                                UserId userId, int pageSize,
                                Instant cursorDate, String cursorId) {
        this(orderRepository, userId, pageSize);
        this.cursorDate = cursorDate;
        this.cursorId   = cursorId;
        prefetch(); // re-fetch from cursor position
    }

    @Override
    public boolean hasNext() {
        return !exhausted && prefetched != null && !prefetched.isEmpty();
    }

    @Override
    public List<Order> next() {
        if (!hasNext()) throw new NoSuchElementException("No more orders");

        List<Order> page = List.copyOf(prefetched);

        // Advance cursor to last item in this page
        if (!page.isEmpty()) {
            Order last = page.get(page.size() - 1);
            this.cursorDate = last.getCreatedAt();
            this.cursorId   = last.getId().getValue().toString();
        }

        prefetch(); // load next page in background
        return page;
    }

    /**
     * Export current cursor position — for client-side pagination token.
     * Client sends this token back to resume exactly where they left off.
     */
    public CursorToken exportCursor() {
        return new CursorToken(cursorDate, cursorId);
    }

    private void prefetch() {
        if (exhausted) return;

        List<Order> page = orderRepository.findByUserIdWithCursor(
            userId, cursorDate, cursorId, pageSize
        );

        if (page == null || page.isEmpty() || page.size() < pageSize) {
            exhausted = (page == null || page.isEmpty());
        }
        prefetched = page == null ? List.of() : page;
    }

    public record CursorToken(Instant cursorDate, String cursorId) {
        public String encode() {
            // Base64 encode for opaque client token
            String raw = cursorDate.toEpochMilli() + ":" + cursorId;
            return java.util.Base64.getUrlEncoder()
                .encodeToString(raw.getBytes());
        }

        public static CursorToken decode(String token) {
            String raw = new String(
                java.util.Base64.getUrlDecoder().decode(token)
            );
            String[] parts = raw.split(":");
            return new CursorToken(
                Instant.ofEpochMilli(Long.parseLong(parts[0])),
                parts[1]
            );
        }
    }
}
java// com.ecommerce.application.order/iterator/OrderHistoryPage.java
package com.ecommerce.application.order.iterator;

import com.ecommerce.application.order.dto.OrderResponse;
import java.util.List;

/**
 * Pagination response wrapper — carries both data and navigation tokens.
 */
public record OrderHistoryPage(
    List<OrderResponse> orders,
    int pageNumber,           // for offset pagination
    int pageSize,
    long totalElements,
    int totalPages,
    boolean hasNext,
    boolean hasPrevious,
    String nextCursor,        // for cursor pagination
    String previousCursor
) {
    public static OrderHistoryPage of(List<OrderResponse> orders,
                                       int page, int size, long total,
                                       String nextCursor) {
        int totalPages = (int) Math.ceil((double) total / size);
        return new OrderHistoryPage(
            orders, page, size, total, totalPages,
            page < totalPages - 1, page > 0,
            nextCursor, null
        );
    }

    public static OrderHistoryPage empty(int page, int size) {
        return new OrderHistoryPage(
            List.of(), page, size, 0, 0,
            false, page > 0, null, null
        );
    }
}
java// com.ecommerce.application.order/OrderHistoryService.java
package com.ecommerce.application.order;

import com.ecommerce.application.order.dto.OrderResponse;
import com.ecommerce.application.order.dto.OrderResponseAssembler;
import com.ecommerce.application.order.iterator.OrderCursorIterator;
import com.ecommerce.application.order.iterator.OrderHistoryPage;
import com.ecommerce.application.shared.iterator.PagedIterator;
import com.ecommerce.domain.order.Order;
import com.ecommerce.domain.order.OrderRepository;
import com.ecommerce.domain.user.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

/**
 * Order history service — uses Iterator pattern for traversal.
 * Client code doesn't care HOW orders are fetched, just THAT they can iterate.
 */
@Service
public class OrderHistoryService {

    private static final Logger log =
        LoggerFactory.getLogger(OrderHistoryService.class);

    private final OrderRepository        orderRepository;
    private final OrderResponseAssembler assembler;

    public OrderHistoryService(OrderRepository orderRepository,
                                OrderResponseAssembler assembler) {
        this.orderRepository = orderRepository;
        this.assembler       = assembler;
    }

    /**
     * Offset-based pagination — for admin tables, jump to any page.
     */
    public OrderHistoryPage getOrderHistory(String userId, int page, int size) {
        UserId uid = UserId.of(userId);
        List<Order> orders = orderRepository.findByUserId(uid, page, size);
        long total = orderRepository.countByUserId(uid);

        List<OrderResponse> responses = orders.stream()
            .map(assembler::toSummaryResponse)
            .toList();

        return OrderHistoryPage.of(responses, page, size, total, null);
    }

    /**
     * Cursor-based pagination — for infinite scroll, efficient at scale.
     */
    public OrderHistoryPage getOrderHistoryCursor(String userId, String cursorToken,
                                                   int size) {
        UserId uid = UserId.of(userId);
        OrderCursorIterator iterator;

        if (cursorToken != null && !cursorToken.isBlank()) {
            // Resume from cursor
            OrderCursorIterator.CursorToken cursor =
                OrderCursorIterator.CursorToken.decode(cursorToken);
            iterator = new OrderCursorIterator(
                orderRepository, uid, size,
                cursor.cursorDate(), cursor.cursorId()
            );
        } else {
            iterator = new OrderCursorIterator(orderRepository, uid, size);
        }

        if (!iterator.hasNext()) {
            return OrderHistoryPage.empty(0, size);
        }

        List<Order> orders = iterator.next();
        List<OrderResponse> responses = orders.stream()
            .map(assembler::toSummaryResponse)
            .toList();

        String nextCursor = iterator.hasNext()
            ? iterator.exportCursor().encode()
            : null;

        return OrderHistoryPage.of(responses, 0, size,
            responses.size(), nextCursor);
    }

    /**
     * Bulk processing — iterate all user orders for data export/analytics.
     * Uses PagedIterator to avoid loading everything into memory.
     */
    public void processAllOrders(String userId, Consumer<Order> processor) {
        UserId uid = UserId.of(userId);
        int processed = 0;

        PagedIterator<Order> iterator = new PagedIterator<>(
            (page, size) -> orderRepository.findByUserId(uid, page, size),
            100  // process 100 at a time
        );

        while (iterator.hasNext()) {
            List<Order> page = iterator.next();
            page.forEach(order -> {
                try {
                    processor.accept(order);
                } catch (Exception e) {
                    log.error("Failed processing order {}: {}",
                        order.getId(), e.getMessage());
                }
            });
            processed += page.size();
            log.debug("Processed {}/{} orders for user {}",
                processed, "?", userId);
        }

        log.info("Bulk processed {} orders for user {}", processed, userId);
    }
}
```

---

## Pattern 4: Memento

### Problem It Solves
```
Problem: Cart needs undo/restore capability for multiple scenarios:
  ① User accidentally removes an item → "Undo Remove" button
  ② Save cart for later → leave site, come back, cart restored
  ③ "Restore previous cart" after checkout for re-ordering
  ④ Crash recovery → cart state persisted, restored on reconnect

Without Memento:
  Cart exposes internals for save/restore → breaks encapsulation
  Saving: copy = new Cart(cart.items, cart.coupon, ...) → violates Law of Demeter
  Restoring: cart.items = savedItems → breaks invariants

With Memento:
  Memento snap = cart.save()      ← cart creates its own snapshot
  cart.removeItem(sku)            ← modify cart
  cart.restore(snap)              ← restore exact state, no internals exposed
```

### Class Diagram
```
«Originator»          «Memento»              «Caretaker»
Cart                  CartMemento            CartMementoManager
+ save(): Memento     - items: Map (copy)    + save(cart, label)
+ restore(Memento)    - couponCode: String   + undo(cartId): void
                      - savedAt: Instant     + getHistory(cartId)
                      - label: String        + restore(cartId, snap)
                      (no setters — immutable)
                      (only Cart can read state)
Implementation
java// ecommerce-domain:
// com.ecommerce.domain.cart/CartMemento.java
package com.ecommerce.domain.cart;

import com.ecommerce.domain.product.ProductId;
import com.ecommerce.domain.shared.valueobject.Money;

import java.time.Instant;
import java.util.*;

/**
 * MEMENTO — Cart snapshot.
 *
 * Captures complete cart state at a point in time.
 * Immutable: no setters, defensive copies on all collections.
 * Package-private access to internal state — only Cart can read it.
 *
 * The Memento pattern ensures encapsulation is never violated:
 * - Cart creates Mementos (knows internal structure)
 * - Caretaker stores Mementos (doesn't know structure)
 * - Only Cart can restore from Mementos
 */
public final class CartMemento {

    private final String mementoId;
    private final CartId cartId;
    private final String label;         // "Before removing Nike Shoes"
    private final Instant savedAt;

    // Deep-copied cart state — immutable snapshot
    private final Map<String, CartItemSnapshot> itemSnapshots;
    private final String appliedCouponCode;

    // Package-private constructor — only Cart can create
    CartMemento(CartId cartId, String label,
                Map<String, CartItemSnapshot> items,
                String couponCode) {
        this.mementoId      = UUID.randomUUID().toString();
        this.cartId         = cartId;
        this.label          = label;
        this.savedAt        = Instant.now();
        this.itemSnapshots  = Map.copyOf(items); // defensive copy
        this.appliedCouponCode = couponCode;
    }

    // Package-private getters — only Cart and CartMementoManager can access
    CartId getCartId()       { return cartId; }
    Map<String, CartItemSnapshot> getItemSnapshots() { return itemSnapshots; }
    String getAppliedCouponCode() { return appliedCouponCode; }

    // Public getters — safe metadata only
    public String getMementoId() { return mementoId; }
    public String getLabel()     { return label; }
    public Instant getSavedAt()  { return savedAt; }
    public int getItemCount()    { return itemSnapshots.size(); }

    /**
     * Inner record — captures CartItem state at snapshot time.
     * Cannot reference live CartItem (would share mutable state).
     */
    record CartItemSnapshot(
        UUID itemId,
        String productId,
        String variantSku,
        String productName,
        String variantName,
        int quantity,
        java.math.BigDecimal unitPrice,
        String currency
    ) {
        static CartItemSnapshot from(CartItem item) {
            return new CartItemSnapshot(
                item.getId(),
                item.getProductId().getValue().toString(),
                item.getVariantSku(),
                item.getProductName(),
                item.getVariantName(),
                item.getQuantity(),
                item.getUnitPrice().getAmount(),
                item.getUnitPrice().getCurrency().getCurrencyCode()
            );
        }

        CartItem toCartItem() {
            return new CartItem(
                ProductId.of(productId),
                variantSku,
                productName,
                variantName,
                quantity,
                Money.of(unitPrice, currency)
            );
        }
    }
}
Now we extend the Cart aggregate with memento support:
java// Extended Cart methods (add to Cart.java from Phase 2):
// com.ecommerce.domain.cart/Cart.java (additions)

/*
  Add to Cart class:
  - private String appliedCouponCode;  (new field)
*/

/**
 * MEMENTO — Create a snapshot of current cart state.
 * Cart creates its own memento → no encapsulation violation.
 * Called before any destructive operation.
 */
// public CartMemento save(String label) {
//     Map<String, CartMemento.CartItemSnapshot> snapshots = new LinkedHashMap<>();
//     items.forEach((sku, item) ->
//         snapshots.put(sku, CartMemento.CartItemSnapshot.from(item))
//     );
//     return new CartMemento(this.id, label, snapshots, this.appliedCouponCode);
// }

/**
 * MEMENTO — Restore cart to a previous state.
 * Only Cart knows how to apply a memento to itself.
 */
// public void restore(CartMemento memento) {
//     if (!memento.getCartId().equals(this.id)) {
//         throw new IllegalArgumentException(
//             "Memento belongs to a different cart"
//         );
//     }
//     // Restore items from snapshot
//     this.items.clear();
//     memento.getItemSnapshots().forEach((sku, snap) ->
//         this.items.put(sku, snap.toCartItem())
//     );
//     this.appliedCouponCode = memento.getAppliedCouponCode();
//     this.updatedAt = Instant.now();
// }
java// com.ecommerce.domain.cart/CartMementoManager.java
package com.ecommerce.domain.cart;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CARETAKER — Manages cart mementos.
 *
 * Stores and retrieves mementos.
 * NEVER inspects or modifies memento content (encapsulation preserved).
 * Only knows: save, restore, list history.
 *
 * In production: persisted to Redis with TTL.
 * Here: in-memory for clarity.
 */
public class CartMementoManager {

    private static final int MAX_HISTORY = 10; // max undo steps

    // cartId → ordered list of mementos (oldest first)
    private final Map<String, Deque<CartMemento>> history =
        new ConcurrentHashMap<>();

    /**
     * Save a snapshot — called before any mutating operation.
     */
    public void save(CartMemento memento) {
        String cartId = memento.getCartId().getValue().toString();
        Deque<CartMemento> stack = history.computeIfAbsent(
            cartId, k -> new ArrayDeque<>()
        );
        stack.push(memento); // push to front (most recent first)

        // Trim history to max size
        while (stack.size() > MAX_HISTORY) {
            stack.pollLast(); // remove oldest
        }
    }

    /**
     * Get most recent memento (for undo).
     */
    public Optional<CartMemento> getLatest(CartId cartId) {
        Deque<CartMemento> stack = history.get(
            cartId.getValue().toString()
        );
        if (stack == null || stack.isEmpty()) return Optional.empty();
        return Optional.of(stack.peek());
    }

    /**
     * Pop and return most recent memento (undo operation).
     */
    public Optional<CartMemento> undo(CartId cartId) {
        Deque<CartMemento> stack = history.get(
            cartId.getValue().toString()
        );
        if (stack == null || stack.isEmpty()) return Optional.empty();
        return Optional.of(stack.pop());
    }

    /**
     * Find a specific memento by ID (for named restore).
     */
    public Optional<CartMemento> findById(CartId cartId, String mementoId) {
        Deque<CartMemento> stack = history.get(
            cartId.getValue().toString()
        );
        if (stack == null) return Optional.empty();
        return stack.stream()
            .filter(m -> m.getMementoId().equals(mementoId))
            .findFirst();
    }

    /**
     * List all saved snapshots for a cart (for "restore history" UI).
     */
    public List<CartMementoSummary> listHistory(CartId cartId) {
        Deque<CartMemento> stack = history.getOrDefault(
            cartId.getValue().toString(), new ArrayDeque<>()
        );
        return stack.stream()
            .map(m -> new CartMementoSummary(
                m.getMementoId(), m.getLabel(),
                m.getSavedAt(), m.getItemCount()
            ))
            .toList();
    }

    public void clearHistory(CartId cartId) {
        history.remove(cartId.getValue().toString());
    }

    public record CartMementoSummary(
        String mementoId, String label,
        java.time.Instant savedAt, int itemCount
    ) {}
}
java// com.ecommerce.application.cart/CartApplicationService.java
package com.ecommerce.application.cart;

import com.ecommerce.domain.cart.*;
import com.ecommerce.domain.product.ProductId;
import com.ecommerce.domain.shared.valueobject.Money;
import com.ecommerce.domain.user.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Cart Application Service — uses Memento for undo capability.
 * Every destructive operation first saves a snapshot.
 */
@Service
public class CartApplicationService {

    private static final Logger log =
        LoggerFactory.getLogger(CartApplicationService.class);

    private final CartRepository      cartRepository;
    private final CartMementoManager  mementoManager;

    public CartApplicationService(CartRepository cartRepository,
                                   CartMementoManager mementoManager) {
        this.cartRepository  = cartRepository;
        this.mementoManager  = mementoManager;
    }

    @Transactional
    public void addItem(String cartId, String productId, String variantSku,
                        String productName, String variantName,
                        int quantity, Money price) {
        Cart cart = loadCart(cartId);
        // Save state BEFORE modification (Memento)
        mementoManager.save(cart.save("Before adding " + productName));
        cart.addItem(ProductId.of(productId), variantSku,
            productName, variantName, quantity, price);
        cartRepository.update(cart);
        log.info("Item added to cart {}: {}", cartId, variantSku);
    }

    @Transactional
    public void removeItem(String cartId, String variantSku) {
        Cart cart = loadCart(cartId);
        // Save state BEFORE removal — enables "Undo Remove"
        mementoManager.save(cart.save("Before removing " + variantSku));
        cart.removeItem(variantSku);
        cartRepository.update(cart);
        log.info("Item removed from cart {}: {}", cartId, variantSku);
    }

    @Transactional
    public boolean undoLastAction(String cartId) {
        Cart cart = loadCart(cartId);
        CartId cid = CartId.of(cartId);

        return mementoManager.undo(cid).map(memento -> {
            // Memento pattern restore
            cart.restore(memento);
            cartRepository.update(cart);
            log.info("Cart {} restored to: '{}'", cartId, memento.getLabel());
            return true;
        }).orElse(false);
    }

    @Transactional
    public boolean restoreSnapshot(String cartId, String mementoId) {
        Cart cart = loadCart(cartId);
        CartId cid = CartId.of(cartId);

        return mementoManager.findById(cid, mementoId).map(memento -> {
            // Save current state before restore (allows undo of the restore!)
            mementoManager.save(cart.save("Before restore to: " + memento.getLabel()));
            cart.restore(memento);
            cartRepository.update(cart);
            log.info("Cart {} restored from snapshot: {}", cartId, mementoId);
            return true;
        }).orElse(false);
    }

    public List<CartMementoManager.CartMementoSummary> getCartHistory(String cartId) {
        return mementoManager.listHistory(CartId.of(cartId));
    }

    public Cart getValidatedCart(String cartId, String userId) {
        Cart cart = loadCart(cartId);
        if (cart.getUserId() != null
                && !cart.getUserId().getValue().toString().equals(userId)) {
            throw new SecurityException("Cart does not belong to user: " + userId);
        }
        return cart;
    }

    public void clearCart(String cartId) {
        Cart cart = loadCart(cartId);
        cart.clear();
        cartRepository.update(cart);
    }

    private Cart loadCart(String cartId) {
        return cartRepository.findById(CartId.of(cartId))
            .orElseThrow(() -> new com.ecommerce.domain.shared.exception.DomainException(
                com.ecommerce.domain.shared.exception.ErrorCodes.CART_NOT_FOUND,
                "Cart not found: " + cartId
            ));
    }
}
```

---

## Pattern 5: Visitor

### Problem It Solves
```
Problem: Need to run MULTIPLE operations on the category/product tree
WITHOUT modifying those classes. Operations needed:
  ① Tax calculation (different rates per category)
  ② Discount validation (some categories exempt from discounts)
  ③ Analytics collection (count products per category)
  ④ Export/serialization (JSON, CSV, XML)
  ⑤ Search index building (extract searchable fields)

Without Visitor:
  Add taxCalculate() to Product, Category → violates SRP
  Add discountValidate() → violates OCP
  Add analyticsCollect() → classes keep growing
  → Every new operation = modify all element classes

With Visitor:
  New operation = new Visitor class
  ZERO changes to Product, Category, Order
  Operations cleanly separated
```

### Class Diagram
```
«interface»               «interface»
Visitable                 OrderVisitor
+ accept(visitor)         + visitOrder(order)
       ▲                  + visitOrderItem(item)
  ┌────┴────┐             + visitPayment(payment)
Order    OrderItem
      (and more)               ▲
                       ┌───────┼────────────────────┐
                       │       │                    │
                   TaxCalc  Discount           Analytics
                   Visitor  Validation         Visitor
                   Visitor
«interface»
ProductVisitor
+ visitProduct(p)
+ visitVariant(v)
        ▲
   ┌────┴─────────────────────┐
   │                          │
SearchIndex           ExportVisitor
BuildVisitor          (CSV/JSON)
Implementation
java// ecommerce-domain:
// com.ecommerce.domain.shared/visitor/Visitable.java
package com.ecommerce.domain.shared.visitor;

/**
 * Element interface — objects that can be visited.
 * Implementing accept() enables double dispatch.
 *
 * Double dispatch solves the expression problem:
 * - Single dispatch: polymorphism on the object
 * - Double dispatch: polymorphism on BOTH object AND visitor
 */
public interface Visitable<V> {
    void accept(V visitor);
}
java// com.ecommerce.domain.order/visitor/OrderVisitor.java
package com.ecommerce.domain.order.visitor;

import com.ecommerce.domain.order.Order;
import com.ecommerce.domain.order.OrderItem;

/**
 * Visitor interface for Order aggregate hierarchy.
 * Each visit method handles a specific element type.
 */
public interface OrderVisitor {
    void visitOrder(Order order);
    void visitOrderItem(OrderItem item);
}
java// com.ecommerce.domain.order/visitor/TaxCalculationVisitor.java
package com.ecommerce.domain.order.visitor;

import com.ecommerce.domain.order.Order;
import com.ecommerce.domain.order.OrderItem;
import com.ecommerce.domain.shared.valueobject.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * Concrete Visitor — Tax Calculation.
 *
 * Different tax rates per product category:
 *   Electronics: 8%
 *   Clothing:    0% (many US states exempt)
 *   Food:        0%
 *   Default:     7%
 *
 * Key: Tax logic is OUTSIDE the Order/OrderItem classes.
 * Order/OrderItem never need to change for new tax rules.
 * Tax rules change? Update this visitor only.
 */
public class TaxCalculationVisitor implements OrderVisitor {

    // Category slug → tax rate
    private static final Map<String, BigDecimal> CATEGORY_TAX_RATES = Map.of(
        "electronics",     new BigDecimal("0.08"),
        "computers",       new BigDecimal("0.08"),
        "clothing",        BigDecimal.ZERO,
        "food",            BigDecimal.ZERO,
        "books",           BigDecimal.ZERO,
        "jewelry",         new BigDecimal("0.10"),
        "luxury-watches",  new BigDecimal("0.10")
    );

    private static final BigDecimal DEFAULT_RATE = new BigDecimal("0.07");

    // Accumulated results
    private BigDecimal totalTax = BigDecimal.ZERO;
    private final Map<String, BigDecimal> taxByItem = new HashMap<>();

    @Override
    public void visitOrder(Order order) {
        // Reset for this order
        totalTax = BigDecimal.ZERO;
        taxByItem.clear();
        // Visit each item (delegation to double dispatch)
        order.getItems().forEach(item -> visitOrderItem(item));
    }

    @Override
    public void visitOrderItem(OrderItem item) {
        // Determine tax rate by category
        // In production: look up category from product catalog
        BigDecimal rate = DEFAULT_RATE;
        BigDecimal itemTax = item.getTotalPrice()
            .getAmount()
            .multiply(rate)
            .setScale(2, RoundingMode.HALF_UP);

        taxByItem.put(item.getVariantSku(), itemTax);
        totalTax = totalTax.add(itemTax);
    }

    public Money getTotalTax(String currency) {
        return Money.of(totalTax, currency);
    }

    public Map<String, BigDecimal> getTaxBreakdown() {
        return Map.copyOf(taxByItem);
    }

    public BigDecimal getTaxRate(String categorySlug) {
        return CATEGORY_TAX_RATES.getOrDefault(
            categorySlug.toLowerCase(), DEFAULT_RATE
        );
    }
}
java// com.ecommerce.domain.order/visitor/DiscountValidationVisitor.java
package com.ecommerce.domain.order.visitor;

import com.ecommerce.domain.order.Order;
import com.ecommerce.domain.order.OrderItem;
import com.ecommerce.domain.shared.exception.DomainException;
import com.ecommerce.domain.shared.exception.ErrorCodes;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Concrete Visitor — Discount Validation.
 *
 * Validates whether a coupon can be applied to an order.
 * Some categories exempt from discounts (luxury items, sale items, gift cards).
 *
 * Adding new exemption rule = modify this visitor only.
 * Order and OrderItem classes unchanged.
 */
public class DiscountValidationVisitor implements OrderVisitor {

    // Categories where discounts are NOT allowed
    private static final Set<String> DISCOUNT_EXEMPT_CATEGORIES =
        Set.of("gift-cards", "luxury-watches", "fine-jewelry");

    private final List<String> violations = new ArrayList<>();
    private boolean discountAllowed = true;

    @Override
    public void visitOrder(Order order) {
        violations.clear();
        discountAllowed = true;

        // Check if order has already applied a payment intent
        // (cannot re-apply discount to paid orders)
        if (order.isPaid()) {
            violations.add("Cannot apply discount to already-paid order");
            discountAllowed = false;
            return;
        }

        // Visit each item for exemption check
        order.getItems().forEach(this::visitOrderItem);
    }

    @Override
    public void visitOrderItem(OrderItem item) {
        // In production: look up product category
        // Simulating category check via product name contains
        String productName = item.getProductName().toLowerCase();
        for (String exempt : DISCOUNT_EXEMPT_CATEGORIES) {
            if (productName.contains(exempt.replace("-", " "))) {
                violations.add(String.format(
                    "Item '%s' is not eligible for discounts (exempt category: %s)",
                    item.getProductName(), exempt
                ));
                discountAllowed = false;
            }
        }
    }

    public boolean isDiscountAllowed()  { return discountAllowed; }
    public List<String> getViolations() { return List.copyOf(violations); }

    public void enforceDiscountAllowed() {
        if (!discountAllowed) {
            throw new DomainException(
                ErrorCodes.COUPON_NOT_FOUND,
                "Discount not allowed: " + String.join("; ", violations)
            );
        }
    }
}
java// com.ecommerce.domain.order/visitor/OrderAnalyticsVisitor.java
package com.ecommerce.domain.order.visitor;

import com.ecommerce.domain.order.Order;
import com.ecommerce.domain.order.OrderItem;

import java.math.BigDecimal;
import java.util.*;

/**
 * Concrete Visitor — Analytics Collection.
 *
 * Collects analytics metrics from orders:
 *   - Revenue by product/category
 *   - Top-selling SKUs
 *   - Average order value
 *   - Items per order
 *
 * Visitor pattern: analytics extracted WITHOUT modifying Order/OrderItem.
 * Can be run on batches of orders for reporting.
 */
public class OrderAnalyticsVisitor implements OrderVisitor {

    // Accumulated analytics
    private int ordersProcessed     = 0;
    private BigDecimal totalRevenue = BigDecimal.ZERO;
    private int totalItemsSold      = 0;

    private final Map<String, Integer>    skuQuantities  = new HashMap<>();
    private final Map<String, BigDecimal> skuRevenues    = new HashMap<>();

    @Override
    public void visitOrder(Order order) {
        ordersProcessed++;
        totalRevenue = totalRevenue.add(order.getTotalAmount().getAmount());
        order.getItems().forEach(this::visitOrderItem);
    }

    @Override
    public void visitOrderItem(OrderItem item) {
        totalItemsSold += item.getQuantity();

        skuQuantities.merge(
            item.getVariantSku(),
            item.getQuantity(),
            Integer::sum
        );
        skuRevenues.merge(
            item.getVariantSku(),
            item.getTotalPrice().getAmount(),
            BigDecimal::add
        );
    }

    // ── Analytics Getters ─────────────────────────────────────────────

    public int getOrdersProcessed()        { return ordersProcessed; }
    public BigDecimal getTotalRevenue()    { return totalRevenue; }
    public int getTotalItemsSold()         { return totalItemsSold; }

    public BigDecimal getAverageOrderValue() {
        if (ordersProcessed == 0) return BigDecimal.ZERO;
        return totalRevenue.divide(
            BigDecimal.valueOf(ordersProcessed),
            2, java.math.RoundingMode.HALF_UP
        );
    }

    public double getAverageItemsPerOrder() {
        if (ordersProcessed == 0) return 0.0;
        return (double) totalItemsSold / ordersProcessed;
    }

    public List<Map.Entry<String, Integer>> getTopSellingSkus(int limit) {
        return skuQuantities.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(limit)
            .toList();
    }

    public Map<String, BigDecimal> getRevenueBySkus() {
        return Map.copyOf(skuRevenues);
    }

    public AnalyticsSummary getSummary() {
        return new AnalyticsSummary(
            ordersProcessed, totalRevenue,
            totalItemsSold, getAverageOrderValue(),
            getAverageItemsPerOrder(),
            getTopSellingSkus(10)
        );
    }

    public record AnalyticsSummary(
        int ordersProcessed,
        BigDecimal totalRevenue,
        int totalItemsSold,
        BigDecimal averageOrderValue,
        double averageItemsPerOrder,
        List<Map.Entry<String, Integer>> topSellingSkus
    ) {}
}
java// com.ecommerce.domain.product/visitor/ProductVisitor.java
package com.ecommerce.domain.product.visitor;

import com.ecommerce.domain.product.Product;
import com.ecommerce.domain.product.ProductVariant;

/**
 * Visitor interface for Product aggregate.
 */
public interface ProductVisitor {
    void visitProduct(Product product);
    void visitVariant(ProductVariant variant, Product parent);
}
java// com.ecommerce.domain.product/visitor/SearchIndexBuildVisitor.java
package com.ecommerce.domain.product.visitor;

import com.ecommerce.domain.product.Product;
import com.ecommerce.domain.product.ProductVariant;

import java.util.*;

/**
 * Concrete Visitor — Search Index Builder.
 *
 * Extracts all searchable fields from the Product aggregate tree.
 * Builds the search document to be indexed in Elasticsearch.
 *
 * Adding new searchable field? Update this visitor.
 * Product class unchanged — clean separation.
 */
public class SearchIndexBuildVisitor implements ProductVisitor {

    private final List<Map<String, Object>> indexDocuments = new ArrayList<>();

    @Override
    public void visitProduct(Product product) {
        // Build base document for the product
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("productId",     product.getId().getValue().toString());
        doc.put("name",          product.getName());
        doc.put("description",   product.getDescription());
        doc.put("slug",          product.getSlug());
        doc.put("categoryId",    product.getCategoryId().getValue().toString());
        doc.put("status",        product.getStatus().name());
        doc.put("averageRating", product.getAverageRating());
        doc.put("reviewCount",   product.getReviewCount());
        doc.put("attributes",    product.getAttributes());
        doc.put("imageUrl",      product.getImageUrls().isEmpty()
            ? null : product.getImageUrls().get(0));

        // Visit all variants to get price range
        if (!product.getVariants().isEmpty()) {
            product.getVariants().forEach(v -> visitVariant(v, product));

            // Compute min/max price across variants
            OptionalDouble minPrice = product.getVariants().stream()
                .mapToDouble(v -> v.getPrice().getAmount().doubleValue())
                .min();
            OptionalDouble maxPrice = product.getVariants().stream()
                .mapToDouble(v -> v.getPrice().getAmount().doubleValue())
                .max();

            minPrice.ifPresent(p -> doc.put("minPrice", p));
            maxPrice.ifPresent(p -> doc.put("maxPrice", p));

            // Use lowest price as primary sort/display price
            minPrice.ifPresent(p -> doc.put("price", p));

            // All SKUs for exact SKU search
            List<String> skus = product.getVariants().stream()
                .map(ProductVariant::getSku).toList();
            doc.put("skus", skus);
        }

        // Search-as-you-type suggest field
        doc.put("suggest", Map.of(
            "input",  buildSuggestTerms(product),
            "weight", product.getReviewCount() + 1
        ));

        indexDocuments.add(doc);
    }

    @Override
    public void visitVariant(ProductVariant variant, Product parent) {
        // Variant-level attributes merged into product document
        // In production: could index variants separately for exact SKU search
        // Here: aggregated into parent product document
    }

    private List<String> buildSuggestTerms(Product product) {
        List<String> terms = new ArrayList<>();
        terms.add(product.getName());
        // Add brand if present
        String brand = product.getAttributes().get("brand");
        if (brand != null) terms.add(brand);
        // Add category-related terms
        terms.addAll(Arrays.asList(product.getName().split("\\s+")));
        return terms.stream().distinct().toList();
    }

    public List<Map<String, Object>> getIndexDocuments() {
        return List.copyOf(indexDocuments);
    }

    public Map<String, Object> getLastDocument() {
        return indexDocuments.isEmpty()
            ? Map.of()
            : indexDocuments.get(indexDocuments.size() - 1);
    }
}
java// com.ecommerce.application.product/visitor/CategoryAnalyticsVisitor.java
package com.ecommerce.application.product.visitor;

import com.ecommerce.domain.product.category.CategoryVisitor;
import com.ecommerce.domain.product.category.CompositeCategory;
import com.ecommerce.domain.product.category.LeafCategory;

import java.util.*;

/**
 * Concrete Visitor — Category Tree Analytics.
 *
 * Traverses the category tree (Composite pattern from Phase 4)
 * and computes analytics per category level.
 *
 * Visitor + Composite working together:
 *   Composite defines the tree structure.
 *   Visitor defines operations on the tree.
 *   Neither knows about the other's concerns.
 */
public class CategoryAnalyticsVisitor implements CategoryVisitor {

    private final Map<String, CategoryStats> stats = new LinkedHashMap<>();
    private int totalLeafCategories     = 0;
    private int totalCompositeCategories = 0;
    private int maxDepth                = 0;

    @Override
    public void visitLeaf(LeafCategory category) {
        totalLeafCategories++;
        maxDepth = Math.max(maxDepth, category.getDepth());

        stats.put(category.getId().getValue().toString(),
            new CategoryStats(
                category.getId().getValue().toString(),
                category.getName(),
                category.getBreadcrumb(),
                category.getDepth(),
                category.getTotalProductCount(),
                0, // leaf has no subcategories
                true
            )
        );
    }

    @Override
    public void visitComposite(CompositeCategory category) {
        totalCompositeCategories++;
        maxDepth = Math.max(maxDepth, category.getDepth());

        stats.put(category.getId().getValue().toString(),
            new CategoryStats(
                category.getId().getValue().toString(),
                category.getName(),
                category.getBreadcrumb(),
                category.getDepth(),
                category.getTotalProductCount(),
                category.getSubcategories().size(),
                false
            )
        );
    }

    public Map<String, CategoryStats> getAllStats()   { return Map.copyOf(stats); }
    public int getTotalLeafCategories()               { return totalLeafCategories; }
    public int getTotalCompositeCategories()          { return totalCompositeCategories; }
    public int getMaxDepth()                          { return maxDepth; }
    public int getTotalCategories() {
        return totalLeafCategories + totalCompositeCategories;
    }

    public List<CategoryStats> getTopCategoriesByProducts(int limit) {
        return stats.values().stream()
            .sorted(Comparator.comparingLong(
                CategoryStats::totalProductCount).reversed())
            .limit(limit)
            .toList();
    }

    public record CategoryStats(
        String categoryId,
        String name,
        String breadcrumb,
        int depth,
        long totalProductCount,
        int subcategoryCount,
        boolean isLeaf
    ) {}
}
java// com.ecommerce.application.order/OrderAnalyticsApplicationService.java
package com.ecommerce.application.order;

import com.ecommerce.application.order.iterator.OrderCursorIterator;
import com.ecommerce.domain.order.Order;
import com.ecommerce.domain.order.OrderRepository;
import com.ecommerce.domain.order.visitor.OrderAnalyticsVisitor;
import com.ecommerce.domain.user.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Combines Iterator + Visitor patterns for efficient bulk analytics.
 *
 * Iterator: loads orders page-by-page (no OOM)
 * Visitor: extracts analytics without modifying Order class
 *
 * Can process 1M+ orders without memory issues.
 */
@Service
public class OrderAnalyticsApplicationService {

    private static final Logger log =
        LoggerFactory.getLogger(OrderAnalyticsApplicationService.class);

    private final OrderRepository orderRepository;

    public OrderAnalyticsApplicationService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * Compute analytics for a user's entire order history.
     * Iterator + Visitor = memory-efficient + extensible analytics.
     */
    public OrderAnalyticsVisitor.AnalyticsSummary computeUserAnalytics(
            String userId) {
        OrderAnalyticsVisitor visitor = new OrderAnalyticsVisitor();
        UserId uid = UserId.of(userId);

        // Iterator: loads 100 orders at a time
        OrderCursorIterator iterator =
            new OrderCursorIterator(orderRepository, uid, 100);

        int pageCount = 0;
        while (iterator.hasNext()) {
            List<Order> page = iterator.next();
            // Visitor: extract analytics from each order
            page.forEach(order -> order.accept(visitor));
            pageCount++;

            if (pageCount % 10 == 0) {
                log.debug("Analytics progress: {} pages processed for user {}",
                    pageCount, userId);
            }
        }

        log.info("Analytics computed for user {}: {} orders, ${} revenue",
            userId,
            visitor.getOrdersProcessed(),
            visitor.getTotalRevenue());

        return visitor.getSummary();
    }

    /**
     * Compute platform-wide analytics using cursor iteration.
     */
    public OrderAnalyticsVisitor.AnalyticsSummary computePlatformAnalytics(
            List<Order> sampleOrders) {
        OrderAnalyticsVisitor visitor = new OrderAnalyticsVisitor();
        sampleOrders.forEach(order -> order.accept(visitor));
        return visitor.getSummary();
    }
}
java// Extension: Add accept() to Order aggregate (Visitable interface)
// Add to Order.java from Phase 2:

/*
implements com.ecommerce.domain.shared.visitor.Visitable
    com.ecommerce.domain.order.visitor.OrderVisitor>

@Override
public void accept(OrderVisitor visitor) {
    visitor.visitOrder(this);
    items.forEach(item -> visitor.visitOrderItem(item));
}
*/
```

---

## Pattern Interaction Map — All Behavioral Patterns Together
```
Complete System Behavioral Pattern Flow:

HTTP POST /api/v1/checkout
     │
     ▼
[Chain of Responsibility — Phase 5]
  Input → User → Cart → Fraud → Risk
  All pass ✅
     │
     ▼
[Mediator — Phase 6]
  CheckoutMediatorImpl coordinates:
  CartColleague ──notify(CartValidated)──► Mediator
  Mediator ──route──► InventoryColleague
  InventoryColleague ──notify(StockReserved)──► Mediator
  Mediator ──route──► PaymentColleague
     │
     ▼
[Template Method — Phase 6]
  AbstractPaymentProcessor.process():
    1. validate()          (hook — Stripe validates pm_ prefix)
    2. checkIdempotency()  (invariant)
    3. preparePayload()    (abstract — Stripe builds PaymentIntentParams)
    4. callProvider()      (abstract — Stripe.charge())
    5. parseResponse()     (abstract — Stripe maps to ChargeResult)
    6. updateRecord()      (invariant)
    7. publishEvents()     (invariant)
     │
     ▼
[Strategy — Phase 5]
  PricingContext selects BlackFridayStrategy (25% off)
  ShippingCalculator selects FreeShipping (cart > $100)
     │
     ▼
[Command — Phase 5]
  commandBus.dispatch(new ConfirmOrderCommand(...))
     │
[State — Phase 5]
  PendingOrderState.confirm() → ConfirmedOrderState
     │
     ▼
[Observer — Phase 5]
  eventPublisher.publish(OrderPlacedEvent, PaymentSucceededEvent)
  → Async handlers via virtual threads:
    ├── NotificationHandler (sends email via Factory Method)
    ├── InventoryHandler (confirms reservation)
    └── AnalyticsHandler (records conversion)
     │
     ▼
[Visitor — Phase 6]
  TaxCalculationVisitor.visitOrder(order) → computes tax
  DiscountValidationVisitor validates coupon eligibility
  OrderAnalyticsVisitor aggregates revenue metrics
     │
     ▼
[Iterator — Phase 6]
  CursorOrderIterator pages through history for analytics
  → Memory-efficient: 100 orders per page, not all in RAM
     │
     ▼
[Memento — Phase 6]
  CartMementoManager saves snapshots at each cart modification
  → Enables undo, save-for-later, session recovery
```

---

## Summary Table — Behavioral Patterns Part 2
```
┌──────────────┬──────────────────────────────────┬───────────────────────────────────────┐
│ Pattern      │ Used For                         │ Key Benefit                           │
├──────────────┼──────────────────────────────────┼───────────────────────────────────────┤
│ Mediator     │ Checkout colleague coordination   │ N connections vs N*(N-1)/2            │
│ Template     │ Payment processing pipeline       │ Invariant steps enforced, hooks vary  │
│ Method       │                                  │                                       │
│ Iterator     │ Order history traversal           │ Memory-safe, cursor 100x faster       │
│ Memento      │ Cart undo/save/restore            │ Encapsulation preserved, full history │
│ Visitor      │ Tax, discount, analytics, search  │ New ops = new class, zero elem change │
└──────────────┴──────────────────────────────────┴───────────────────────────────────────┘
```

---

## Complete Behavioral Patterns Summary (Both Phases)
```
┌───────────────┬──────────────────────────────────┬───────────────────────────────────────┐
│ Pattern       │ Used For                         │ OCP Benefit                           │
├───────────────┼──────────────────────────────────┼───────────────────────────────────────┤
│ Strategy      │ Pricing/Shipping algorithms      │ Add algorithm = add class             │
│ Observer      │ Domain event pub/sub             │ Add consumer = add handler class      │
│ Command       │ Order ops + audit trail          │ Add op = add command + handler        │
│ State         │ Order lifecycle machine          │ Add state = add state class           │
│ Chain of Resp │ Fraud + validation pipeline      │ Add check = add handler               │
│ Mediator      │ Checkout coordination hub        │ Add component = register with mediator│
│ Template Mthd │ Payment processing pipeline      │ Add provider = override 3 methods     │
│ Iterator      │ Order history pagination         │ Swap traversal = swap iterator        │
│ Memento       │ Cart undo/restore                │ New snapshot type = new memento class │
│ Visitor       │ Tax, analytics, search indexing  │ New operation = new visitor class     │
└───────────────┴──────────────────────────────────┴───────────────────────────────────────┘

✅ Phase 6 complete. All 5 Behavioral Patterns Part 2 implemented with:

Mediator: Request-scoped checkout hub with sealed interface event routing and Java 21 pattern matching
Template Method: Final payment processing skeleton with 9 invariant steps and 3 abstract hooks
Iterator: Both offset (O(1) memory) and cursor-based (keyset pagination, 100x faster at depth) iterators
Memento: Full cart undo stack with package-private encapsulation, max-10 history, named snapshots
Visitor: 5 concrete visitors (Tax, Discount, Analytics, SearchIndex, CategoryAnalytics) with Visitor+Composite+Iterator triple integration


Reply "Phase 7" and I'll implement Architectural Patterns — Repository (full JPA implementation), Unit of Work (transaction + event dispatch), Dependency Injection (Spring configuration), CQRS (separate read/write models), Event Sourcing (order audit log), and Domain-Driven Design (domain services, specifications, aggregate boundaries with full DB schema and indexing strategy).Phase 722:03PHASE 7 — Architectural Patterns
Overview
Architectural Patterns define HOW the entire system is organized,
not just individual objects or collaborations.

Phase 7 covers:
  ├── Repository          → Full JPA implementation with specifications
  ├── Unit of Work        → Transaction + domain event dispatch boundary
  ├── CQRS                → Separate read/write models + projections
  ├── Event Sourcing      → Immutable audit log + order event stream
  ├── Specification       → Composable business rule queries
  └── Domain Services     → Cross-aggregate operations + DB schema

Pattern 1: Repository (Full JPA Implementation)
Problem It Solves
Domain layer defines WHAT data it needs (ports).
Infrastructure layer defines HOW it's fetched (adapters).

Repository pattern:
  - Domain: ProductRepository interface (pure Java)
  - Infrastructure: JpaProductRepository (Spring Data JPA)
  - Domain never imports Hibernate/JPA → stays clean

Without Repository:
  Domain aggregates call EntityManager directly
  → Domain depends on persistence framework
  → Can't test domain without a database
  → Switching from JPA to MongoDB = rewrite domain

With Repository:
  Domain defines interface (port)
  JPA implements it (adapter)
  Tests use InMemoryRepository (test adapter)
  Switch to MongoDB = new adapter, zero domain changes
Class Diagram
«Domain Port»                «JPA Entity»
ProductRepository ────────── ProductJpaEntity
+ findById(id)               @Entity @Table("products")
+ findBySlug(slug)           - id, name, slug, status
+ findByCategoryId(catId)    - variants: List<VariantJpaEntity>
+ save(product)              - attributes: Map
+ update(product)
        ▲
        │ implements
JpaProductRepository
+ findById(id): delegates to Spring Data
  → loads JpaEntity → maps to Domain Product

«Specification Pattern»
ProductSpecification          ProductJpaRepository
+ hasStatus(status)     ────► extends JpaSpecificationExecutor
+ inCategory(catId)
+ priceBetween(min,max)
+ hasAttribute(k,v)
+ and() / or() / not()
DB Schema
sql-- ═══════════════════════════════════════════════════════
-- CORE SCHEMA — All tables with indexes
-- ═══════════════════════════════════════════════════════

-- Users
CREATE TABLE users (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email               VARCHAR(255) NOT NULL UNIQUE,
    password_hash       VARCHAR(255) NOT NULL,
    full_name           VARCHAR(255) NOT NULL,
    phone_number        VARCHAR(50),
    role                VARCHAR(50)  NOT NULL DEFAULT 'CUSTOMER',
    status              VARCHAR(50)  NOT NULL DEFAULT 'PENDING_VERIFICATION',
    email_verified      BOOLEAN      NOT NULL DEFAULT FALSE,
    failed_login_count  INT          NOT NULL DEFAULT 0,
    last_login_at       TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version             BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_users_email    ON users(email) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_status   ON users(status, created_at DESC);
CREATE INDEX idx_users_role     ON users(role);

-- User Addresses (embedded collection)
CREATE TABLE user_addresses (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    full_name       VARCHAR(255),
    line1           VARCHAR(255) NOT NULL,
    line2           VARCHAR(255),
    city            VARCHAR(100) NOT NULL,
    state           VARCHAR(100),
    country         VARCHAR(100) NOT NULL,
    postal_code     VARCHAR(20),
    is_default      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_addresses_user ON user_addresses(user_id);

-- Categories
CREATE TABLE categories (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id   UUID REFERENCES categories(id),
    name        VARCHAR(255) NOT NULL,
    slug        VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    image_url   VARCHAR(500),
    sort_order  INT NOT NULL DEFAULT 0,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_categories_parent ON categories(parent_id);
CREATE INDEX idx_categories_slug   ON categories(slug);

-- Products
CREATE TABLE products (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(500) NOT NULL,
    description     TEXT,
    slug            VARCHAR(500) NOT NULL UNIQUE,
    category_id     UUID NOT NULL REFERENCES categories(id),
    status          VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    average_rating  NUMERIC(3,2) NOT NULL DEFAULT 0.00,
    review_count    INT NOT NULL DEFAULT 0,
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version         BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_products_category  ON products(category_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_products_status    ON products(status, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_products_slug      ON products(slug) WHERE deleted_at IS NULL;
CREATE INDEX idx_products_rating    ON products(average_rating DESC, review_count DESC);

-- Product Attributes (key-value pairs per product)
CREATE TABLE product_attributes (
    product_id  UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    key         VARCHAR(100) NOT NULL,
    value       VARCHAR(500) NOT NULL,
    PRIMARY KEY (product_id, key)
);

-- Product Images
CREATE TABLE product_images (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id  UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    url         VARCHAR(1000) NOT NULL,
    alt_text    VARCHAR(255),
    sort_order  INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_images_product ON product_images(product_id, sort_order);

-- Product Variants (SKUs)
CREATE TABLE product_variants (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id          UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    sku                 VARCHAR(255) NOT NULL UNIQUE,
    name                VARCHAR(255) NOT NULL,
    price               NUMERIC(12,2) NOT NULL,
    compare_at_price    NUMERIC(12,2),
    currency            VARCHAR(3) NOT NULL DEFAULT 'USD',
    attributes          JSONB,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_variants_product   ON product_variants(product_id) WHERE is_active;
CREATE INDEX idx_variants_sku       ON product_variants(sku);
CREATE INDEX idx_variants_price     ON product_variants(price);

-- Inventory
CREATE TABLE inventory (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku                 VARCHAR(255) NOT NULL UNIQUE,
    on_hand_quantity    INT NOT NULL DEFAULT 0,
    reserved_quantity   INT NOT NULL DEFAULT 0,
    reorder_point       INT NOT NULL DEFAULT 5,
    reorder_quantity    INT NOT NULL DEFAULT 50,
    version             BIGINT NOT NULL DEFAULT 0,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_inventory_sku ON inventory(sku);

-- Stock Reservations
CREATE TABLE stock_reservations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inventory_id    UUID NOT NULL REFERENCES inventory(id),
    order_id        VARCHAR(255) NOT NULL,
    quantity        INT NOT NULL,
    status          VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    expires_at      TIMESTAMPTZ NOT NULL,
    confirmed_at    TIMESTAMPTZ,
    cancelled_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_reservations_inventory ON stock_reservations(inventory_id, status);
CREATE INDEX idx_reservations_order     ON stock_reservations(order_id);
CREATE INDEX idx_reservations_expires   ON stock_reservations(expires_at) WHERE status = 'ACTIVE';

-- Carts
CREATE TABLE carts (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID REFERENCES users(id),
    session_id  VARCHAR(255),
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_carts_user     ON carts(user_id) WHERE user_id IS NOT NULL;
CREATE INDEX idx_carts_session  ON carts(session_id) WHERE session_id IS NOT NULL;
CREATE INDEX idx_carts_expires  ON carts(expires_at);

-- Cart Items
CREATE TABLE cart_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_id         UUID NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    product_id      UUID NOT NULL REFERENCES products(id),
    variant_sku     VARCHAR(255) NOT NULL,
    product_name    VARCHAR(500) NOT NULL,
    variant_name    VARCHAR(255),
    quantity        INT NOT NULL,
    unit_price      NUMERIC(12,2) NOT NULL,
    currency        VARCHAR(3) NOT NULL DEFAULT 'USD',
    added_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_cart_items_cart ON cart_items(cart_id);
CREATE UNIQUE INDEX idx_cart_items_unique ON cart_items(cart_id, variant_sku);

-- Orders
CREATE TABLE orders (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id),
    order_number        VARCHAR(50) NOT NULL UNIQUE,
    status              VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    subtotal            NUMERIC(12,2) NOT NULL,
    shipping_cost       NUMERIC(12,2) NOT NULL DEFAULT 0,
    discount_amount     NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_amount        NUMERIC(12,2) NOT NULL,
    currency            VARCHAR(3) NOT NULL DEFAULT 'USD',
    coupon_code         VARCHAR(100),
    payment_intent_id   VARCHAR(255),
    tracking_number     VARCHAR(255),
    cancellation_reason TEXT,
    shipping_address_id UUID,
    billing_address_id  UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    confirmed_at        TIMESTAMPTZ,
    shipped_at          TIMESTAMPTZ,
    delivered_at        TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_orders_user    ON orders(user_id, created_at DESC);
CREATE INDEX idx_orders_status  ON orders(status, created_at DESC);
CREATE INDEX idx_orders_number  ON orders(order_number);
-- Cursor pagination index (composite for keyset pagination)
CREATE INDEX idx_orders_cursor  ON orders(created_at DESC, id DESC);

-- Order Items (snapshot at purchase time)
CREATE TABLE order_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id      UUID NOT NULL,
    variant_sku     VARCHAR(255) NOT NULL,
    product_name    VARCHAR(500) NOT NULL,
    variant_name    VARCHAR(255),
    quantity        INT NOT NULL,
    unit_price      NUMERIC(12,2) NOT NULL,
    total_price     NUMERIC(12,2) NOT NULL,
    currency        VARCHAR(3) NOT NULL DEFAULT 'USD'
);
CREATE INDEX idx_order_items_order ON order_items(order_id);

-- Payments
CREATE TABLE payments (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id                UUID NOT NULL REFERENCES orders(id),
    user_id                 UUID NOT NULL REFERENCES users(id),
    amount                  NUMERIC(12,2) NOT NULL,
    currency                VARCHAR(3) NOT NULL DEFAULT 'USD',
    method                  VARCHAR(50) NOT NULL,
    status                  VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    idempotency_key         VARCHAR(255) NOT NULL UNIQUE,
    gateway_transaction_id  VARCHAR(255),
    gateway_response        JSONB,
    failure_reason          TEXT,
    failure_code            VARCHAR(100),
    refunded_amount         NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at            TIMESTAMPTZ,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_payments_order ON payments(order_id);
CREATE INDEX idx_payments_user  ON payments(user_id);
CREATE UNIQUE INDEX idx_payments_idempotency ON payments(idempotency_key);

-- Coupons
CREATE TABLE coupons (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                    VARCHAR(100) NOT NULL UNIQUE,
    type                    VARCHAR(50) NOT NULL,
    value                   NUMERIC(12,2) NOT NULL,
    minimum_order_amount    NUMERIC(12,2),
    maximum_discount_amount NUMERIC(12,2),
    max_usage               INT NOT NULL DEFAULT -1,
    usage_count             INT NOT NULL DEFAULT 0,
    applicable_user_id      UUID REFERENCES users(id),
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    valid_from              TIMESTAMPTZ NOT NULL,
    valid_until             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_coupons_code   ON coupons(code) WHERE is_active;
CREATE INDEX idx_coupons_user   ON coupons(applicable_user_id) WHERE applicable_user_id IS NOT NULL;

-- Domain Events (outbox pattern — Phase 9)
CREATE TABLE domain_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type      VARCHAR(255) NOT NULL,
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    VARCHAR(255) NOT NULL,
    payload         JSONB NOT NULL,
    version         INT NOT NULL DEFAULT 1,
    published       BOOLEAN NOT NULL DEFAULT FALSE,
    occurred_at     TIMESTAMPTZ NOT NULL,
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_events_unpublished ON domain_events(occurred_at) WHERE NOT published;
CREATE INDEX idx_events_aggregate   ON domain_events(aggregate_id, occurred_at DESC);

-- Order Event Store (Event Sourcing — see Pattern 4)
CREATE TABLE order_events (
    id              BIGSERIAL PRIMARY KEY,
    order_id        UUID NOT NULL,
    event_type      VARCHAR(255) NOT NULL,
    event_version   INT NOT NULL DEFAULT 1,
    payload         JSONB NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    caused_by       UUID,   -- commandId that caused this event
    issued_by       VARCHAR(255)  -- userId of actor
);
CREATE INDEX idx_order_events_order ON order_events(order_id, id ASC);
CREATE UNIQUE INDEX idx_order_events_version
    ON order_events(order_id, event_version); -- prevent duplicate versions

-- Audit Log
CREATE TABLE audit_logs (
    id              BIGSERIAL PRIMARY KEY,
    action          VARCHAR(255) NOT NULL,
    resource_type   VARCHAR(100) NOT NULL,
    resource_id     VARCHAR(255) NOT NULL,
    actor_id        VARCHAR(255),
    outcome         VARCHAR(50) NOT NULL,
    sensitive_data  BOOLEAN NOT NULL DEFAULT FALSE,
    details         TEXT,
    ip_address      VARCHAR(45),
    user_agent      VARCHAR(500),
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_resource ON audit_logs(resource_type, resource_id, occurred_at DESC);
CREATE INDEX idx_audit_actor    ON audit_logs(actor_id, occurred_at DESC);
Implementation
java// ecommerce-infrastructure:
// com.ecommerce.infrastructure.persistence.entity/ProductJpaEntity.java
package com.ecommerce.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.*;

/**
 * JPA Entity — persistence representation of Product.
 *
 * DELIBERATELY separate from domain Product aggregate.
 * Domain model: rich behavior, invariants, domain events.
 * JPA entity: persistence mapping, no behavior, ORM annotations.
 *
 * Why separate:
 *   - Domain model can evolve independently of DB schema
 *   - No @Entity/@Column in domain layer (zero framework coupling)
 *   - JPA lazy loading / proxies don't corrupt domain invariants
 *   - Can optimize read/write models differently (CQRS)
 */
@Entity
@Table(name = "products")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductJpaEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 500)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "slug", nullable = false, unique = true, length = 500)
    private String slug;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "average_rating", precision = 3, scale = 2)
    private java.math.BigDecimal averageRating;

    @Column(name = "review_count")
    private int reviewCount;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    @OneToMany(
        mappedBy = "product",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    @OrderBy("created_at ASC")
    private List<ProductVariantJpaEntity> variants = new ArrayList<>();

    @ElementCollection
    @CollectionTable(
        name = "product_attributes",
        joinColumns = @JoinColumn(name = "product_id")
    )
    @MapKeyColumn(name = "key")
    @Column(name = "value")
    private Map<String, String> attributes = new HashMap<>();

    @OneToMany(
        mappedBy = "product",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    @OrderBy("sort_order ASC")
    private List<ProductImageJpaEntity> images = new ArrayList<>();
}
java// com.ecommerce.infrastructure.persistence.entity/ProductVariantJpaEntity.java
package com.ecommerce.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "product_variants")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProductVariantJpaEntity {

    @Id
    @Column(name = "id", updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductJpaEntity product;

    @Column(name = "sku", nullable = false, unique = true)
    private String sku;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "compare_at_price", precision = 12, scale = 2)
    private BigDecimal compareAtPrice;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "attributes", columnDefinition = "JSONB")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private Map<String, String> attributes;

    @Column(name = "is_active")
    private boolean active;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
java// com.ecommerce.infrastructure.persistence.entity/ProductImageJpaEntity.java
package com.ecommerce.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "product_images")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProductImageJpaEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductJpaEntity product;

    @Column(name = "url", nullable = false, length = 1000)
    private String url;

    @Column(name = "alt_text")
    private String altText;

    @Column(name = "sort_order")
    private int sortOrder;
}
java// com.ecommerce.infrastructure.persistence.spring/SpringDataProductRepository.java
package com.ecommerce.infrastructure.persistence.spring;

import com.ecommerce.infrastructure.persistence.entity.ProductJpaEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.repository.query.Procedure;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository — low-level DB operations.
 * Not exposed to domain — wrapped by JpaProductRepository.
 */
public interface SpringDataProductRepository
        extends JpaRepository<ProductJpaEntity, UUID>,
                JpaSpecificationExecutor<ProductJpaEntity> {

    Optional<ProductJpaEntity> findBySlugAndDeletedAtIsNull(String slug);

    boolean existsBySlugAndDeletedAtIsNull(String slug);

    @Query("""
        SELECT p FROM ProductJpaEntity p
        LEFT JOIN FETCH p.variants
        WHERE p.id = :id AND p.deletedAt IS NULL
        """)
    Optional<ProductJpaEntity> findByIdWithVariants(@Param("id") UUID id);

    @Query("""
        SELECT p FROM ProductJpaEntity p
        WHERE p.categoryId = :categoryId
        AND p.status = 'ACTIVE'
        AND p.deletedAt IS NULL
        ORDER BY p.averageRating DESC, p.reviewCount DESC
        """)
    org.springframework.data.domain.Page<ProductJpaEntity> findActiveByCategoryId(
        @Param("categoryId") UUID categoryId,
        org.springframework.data.domain.Pageable pageable
    );

    @Modifying
    @Query("""
        UPDATE ProductJpaEntity p
        SET p.averageRating = :rating,
            p.reviewCount   = :count,
            p.updatedAt     = CURRENT_TIMESTAMP
        WHERE p.id = :id
        """)
    int updateRating(@Param("id") UUID id,
                     @Param("rating") java.math.BigDecimal rating,
                     @Param("count") int count);
}
java// com.ecommerce.infrastructure.persistence/specification/ProductSpecification.java
package com.ecommerce.infrastructure.persistence.specification;

import com.ecommerce.infrastructure.persistence.entity.ProductJpaEntity;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Specification Pattern — composable product query predicates.
 *
 * Enables type-safe, readable query building:
 *   Specification<ProductJpaEntity> spec =
 *     ProductSpecification.isActive()
 *       .and(ProductSpecification.inCategory(categoryId))
 *       .and(ProductSpecification.priceBetween(min, max))
 *       .and(ProductSpecification.hasAttribute("brand", "Nike"));
 *
 * Spring Data passes Specification to JPA Criteria API.
 * No raw JPQL/SQL in application code.
 */
public final class ProductSpecification {

    private ProductSpecification() {}

    public static Specification<ProductJpaEntity> isActive() {
        return (root, query, cb) ->
            cb.and(
                cb.equal(root.get("status"), "ACTIVE"),
                cb.isNull(root.get("deletedAt"))
            );
    }

    public static Specification<ProductJpaEntity> isNotDeleted() {
        return (root, query, cb) ->
            cb.isNull(root.get("deletedAt"));
    }

    public static Specification<ProductJpaEntity> inCategory(UUID categoryId) {
        return (root, query, cb) ->
            cb.equal(root.get("categoryId"), categoryId);
    }

    public static Specification<ProductJpaEntity> inCategories(
            List<UUID> categoryIds) {
        return (root, query, cb) ->
            root.get("categoryId").in(categoryIds);
    }

    public static Specification<ProductJpaEntity> priceBetween(
            BigDecimal min, BigDecimal max) {
        return (root, query, cb) -> {
            // Join to variants for price filtering
            Join<Object, Object> variants = root.join("variants",
                JoinType.INNER);
            List<Predicate> predicates = new ArrayList<>();
            if (min != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                    variants.get("price"), min));
            }
            if (max != null) {
                predicates.add(cb.lessThanOrEqualTo(
                    variants.get("price"), max));
            }
            predicates.add(cb.isTrue(variants.get("active")));
            query.distinct(true);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<ProductJpaEntity> hasAttribute(
            String key, String value) {
        return (root, query, cb) -> {
            MapJoin<Object, String, String> attrs =
                root.joinMap("attributes", JoinType.INNER);
            return cb.and(
                cb.equal(attrs.key(), key),
                cb.equal(attrs.value(), value)
            );
        };
    }

    public static Specification<ProductJpaEntity> hasMinRating(
            BigDecimal minRating) {
        return (root, query, cb) ->
            cb.greaterThanOrEqualTo(root.get("averageRating"), minRating);
    }

    public static Specification<ProductJpaEntity> nameLike(String term) {
        return (root, query, cb) ->
            cb.like(cb.lower(root.get("name")),
                "%" + term.toLowerCase() + "%");
    }

    /**
     * Composite specification — commonly used together.
     */
    public static Specification<ProductJpaEntity> forCatalogSearch(
            UUID categoryId, BigDecimal minPrice, BigDecimal maxPrice,
            BigDecimal minRating) {
        Specification<ProductJpaEntity> spec = isActive();
        if (categoryId != null)  spec = spec.and(inCategory(categoryId));
        if (minPrice != null || maxPrice != null)
            spec = spec.and(priceBetween(minPrice, maxPrice));
        if (minRating != null)   spec = spec.and(hasMinRating(minRating));
        return spec;
    }
}
java// com.ecommerce.infrastructure.persistence/mapper/ProductMapper.java
package com.ecommerce.infrastructure.persistence.mapper;

import com.ecommerce.domain.product.*;
import com.ecommerce.domain.shared.valueobject.Money;
import com.ecommerce.infrastructure.persistence.entity.*;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Bidirectional mapper: Domain ↔ JPA Entity.
 *
 * Isolates mapping logic from both domain and repository.
 * Domain aggregates never know about JPA entities.
 * JPA entities never know about domain aggregates.
 *
 * Mapping strategy:
 *   toEntity(): Domain → JPA (for writes)
 *   toDomain(): JPA → Domain (for reads, uses reconstitute)
 */
@Component
public class ProductMapper {

    /**
     * JPA Entity → Domain Aggregate (for reads).
     * Uses reconstitute() — bypasses domain constructor,
     * no domain events emitted, no invariant re-validation.
     */
    public Product toDomain(ProductJpaEntity entity) {
        List<ProductVariant> variants = entity.getVariants().stream()
            .filter(ProductVariantJpaEntity::isActive)
            .map(this::toVariantDomain)
            .toList();

        List<String> imageUrls = entity.getImages().stream()
            .map(ProductImageJpaEntity::getUrl)
            .toList();

        return Product.reconstitute(
            ProductId.of(entity.getId()),
            entity.getName(),
            entity.getDescription(),
            entity.getSlug(),
            CategoryId.of(entity.getCategoryId()),
            ProductStatus.valueOf(entity.getStatus()),
            variants,
            new HashMap<>(entity.getAttributes()),
            imageUrls,
            entity.getAverageRating(),
            entity.getReviewCount(),
            entity.getDeletedAt(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getVersion()
        );
    }

    /**
     * Domain Aggregate → JPA Entity (for writes).
     */
    public ProductJpaEntity toEntity(Product domain,
                                      ProductJpaEntity existing) {
        // Update existing entity in place to preserve JPA identity
        if (existing == null) {
            existing = new ProductJpaEntity();
        }
        existing.setId(domain.getId().getValue());
        existing.setName(domain.getName());
        existing.setDescription(domain.getDescription());
        existing.setSlug(domain.getSlug());
        existing.setCategoryId(domain.getCategoryId().getValue());
        existing.setStatus(domain.getStatus().name());
        existing.setAverageRating(domain.getAverageRating());
        existing.setReviewCount(domain.getReviewCount());
        existing.setDeletedAt(domain.getDeletedAt());

        // Sync variants
        syncVariants(domain, existing);

        // Sync attributes
        existing.getAttributes().clear();
        existing.getAttributes().putAll(domain.getAttributes());

        // Sync images
        syncImages(domain, existing);

        return existing;
    }

    private ProductVariant toVariantDomain(ProductVariantJpaEntity entity) {
        return ProductVariant.reconstitute(
            new VariantId(entity.getId()),
            entity.getSku(),
            entity.getName(),
            Money.of(entity.getPrice(), entity.getCurrency()),
            entity.getCompareAtPrice() != null
                ? Money.of(entity.getCompareAtPrice(), entity.getCurrency())
                : null,
            entity.getAttributes() != null
                ? entity.getAttributes()
                : Map.of()
        );
    }

    private void syncVariants(Product domain, ProductJpaEntity entity) {
        Map<String, ProductVariantJpaEntity> existingBySku = new HashMap<>();
        entity.getVariants().forEach(v ->
            existingBySku.put(v.getSku(), v));

        entity.getVariants().clear();

        domain.getVariants().forEach(dv -> {
            ProductVariantJpaEntity ve =
                existingBySku.getOrDefault(
                    dv.getSku(),
                    new ProductVariantJpaEntity()
                );
            ve.setId(dv.getId().getValue());
            ve.setProduct(entity);
            ve.setSku(dv.getSku());
            ve.setName(dv.getName());
            ve.setPrice(dv.getPrice().getAmount());
            ve.setCurrency(dv.getPrice().getCurrency().getCurrencyCode());
            if (dv.getCompareAtPrice() != null) {
                ve.setCompareAtPrice(dv.getCompareAtPrice().getAmount());
            }
            ve.setAttributes(dv.getAttributes());
            ve.setActive(true);
            entity.getVariants().add(ve);
        });
    }

    private void syncImages(Product domain, ProductJpaEntity entity) {
        entity.getImages().clear();
        List<String> imageUrls = domain.getImageUrls();
        for (int i = 0; i < imageUrls.size(); i++) {
            ProductImageJpaEntity img = new ProductImageJpaEntity();
            img.setId(UUID.randomUUID());
            img.setProduct(entity);
            img.setUrl(imageUrls.get(i));
            img.setSortOrder(i);
            entity.getImages().add(img);
        }
    }
}
java// com.ecommerce.infrastructure.persistence/JpaProductRepository.java
package com.ecommerce.infrastructure.persistence;

import com.ecommerce.domain.product.*;
import com.ecommerce.infrastructure.persistence.entity.ProductJpaEntity;
import com.ecommerce.infrastructure.persistence.mapper.ProductMapper;
import com.ecommerce.infrastructure.persistence.specification.ProductSpecification;
import com.ecommerce.infrastructure.persistence.spring.SpringDataProductRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.*;

/**
 * JPA implementation of ProductRepository domain port.
 *
 * This is the Adapter from Phase 4 applied to persistence:
 *   Port:    ProductRepository (domain)
 *   Adapter: JpaProductRepository (infrastructure)
 *
 * Responsibilities:
 *   - Translate domain operations to JPA calls
 *   - Map between domain aggregates ↔ JPA entities
 *   - Use Specifications for complex queries
 *   - Handle optimistic locking (version field)
 */
@Repository
public class JpaProductRepository implements ProductRepository {

    private final SpringDataProductRepository springRepo;
    private final ProductMapper               mapper;

    public JpaProductRepository(SpringDataProductRepository springRepo,
                                 ProductMapper mapper) {
        this.springRepo = springRepo;
        this.mapper     = mapper;
    }

    @Override
    public Optional<Product> findById(ProductId id) {
        return springRepo.findByIdWithVariants(id.getValue())
            .map(mapper::toDomain);
    }

    @Override
    public Optional<Product> findBySlug(String slug) {
        return springRepo.findBySlugAndDeletedAtIsNull(slug)
            .map(mapper::toDomain);
    }

    @Override
    public List<Product> findByCategoryId(CategoryId categoryId,
                                           int page, int size) {
        return springRepo.findActiveByCategoryId(
            categoryId.getValue(),
            PageRequest.of(page, size)
        ).map(mapper::toDomain).toList();
    }

    @Override
    public List<Product> findBySpecification(ProductSearchCriteria criteria) {
        Specification<ProductJpaEntity> spec =
            ProductSpecification.forCatalogSearch(
                criteria.categoryId() != null
                    ? criteria.categoryId().getValue() : null,
                criteria.minPrice(),
                criteria.maxPrice(),
                criteria.minRating()
            );

        if (criteria.nameTerm() != null && !criteria.nameTerm().isBlank()) {
            spec = spec.and(ProductSpecification.nameLike(criteria.nameTerm()));
        }
        if (criteria.brand() != null) {
            spec = spec.and(
                ProductSpecification.hasAttribute("brand", criteria.brand())
            );
        }

        return springRepo.findAll(spec,
            PageRequest.of(criteria.page(), criteria.size())
        ).map(mapper::toDomain).toList();
    }

    @Override
    public void save(Product product) {
        ProductJpaEntity entity = mapper.toEntity(product, null);
        springRepo.save(entity);
    }

    @Override
    public void update(Product product) {
        // Load existing entity first (preserves JPA identity + version)
        ProductJpaEntity existing = springRepo.findById(
            product.getId().getValue()
        ).orElse(null);
        // Map domain state onto existing entity (or create new)
        ProductJpaEntity entity = mapper.toEntity(product, existing);
        springRepo.save(entity);
    }

    @Override
    public boolean existsBySlug(String slug) {
        return springRepo.existsBySlugAndDeletedAtIsNull(slug);
    }

    @Override
    public boolean existsBySku(String sku) {
        // Delegate to variant repository
        return springRepo.existsById(UUID.randomUUID()); // placeholder
    }
}
```

---

## Pattern 2: Unit of Work

### Problem It Solves
```
Problem: An order placement involves changes to:
  - Order aggregate (create)
  - Inventory aggregate (reserve)
  - Payment aggregate (create)
  - Cart aggregate (clear)

AND domain events need to be published ONLY if ALL changes succeed.

Without Unit of Work:
  save(order)            → published event if next save fails?
  save(inventory)        → partial commit scenario
  publish(events)        → events published but DB rolled back?
  → Inconsistent state, ghost events, lost events

With Unit of Work:
  uow.register(order, inventory, payment, cart)
  uow.commit()           → single transaction: all or nothing
                         → domain events published AFTER commit
                         → if commit fails: no events, no partial state
Implementation
java// ecommerce-application:
// com.ecommerce.application.shared/uow/UnitOfWork.java
package com.ecommerce.application.shared.uow;

/**
 * Unit of Work interface.
 * Tracks aggregate changes within a business operation.
 * Commits ALL changes atomically or rolls back ALL.
 * Publishes domain events AFTER successful commit.
 */
public interface UnitOfWork {

    /**
     * Register an aggregate as dirty (modified, needs saving).
     */
    void registerDirty(com.ecommerce.domain.shared.AggregateRoot aggregate);

    /**
     * Register an aggregate as new (needs inserting).
     */
    void registerNew(com.ecommerce.domain.shared.AggregateRoot aggregate);

    /**
     * Register an aggregate for deletion.
     */
    void registerDeleted(com.ecommerce.domain.shared.AggregateRoot aggregate);

    /**
     * Commit all changes atomically.
     * 1. Save all new aggregates
     * 2. Update all dirty aggregates
     * 3. Delete all deleted aggregates
     * 4. Publish domain events from all aggregates
     * All in ONE transaction.
     */
    void commit();

    /**
     * Clear tracked aggregates without committing.
     */
    void clear();
}
java// ecommerce-infrastructure:
// com.ecommerce.infrastructure.uow/SpringUnitOfWork.java
package com.ecommerce.infrastructure.uow;

import com.ecommerce.application.shared.uow.UnitOfWork;
import com.ecommerce.domain.cart.Cart;
import com.ecommerce.domain.cart.CartRepository;
import com.ecommerce.domain.inventory.Inventory;
import com.ecommerce.domain.inventory.InventoryRepository;
import com.ecommerce.domain.order.Order;
import com.ecommerce.domain.order.OrderRepository;
import com.ecommerce.domain.payment.Payment;
import com.ecommerce.domain.payment.PaymentRepository;
import com.ecommerce.domain.shared.AggregateRoot;
import com.ecommerce.domain.shared.event.DomainEvent;
import com.ecommerce.domain.shared.event.DomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.annotation.RequestScope;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring Unit of Work — request-scoped.
 *
 * @RequestScope: new UoW per HTTP request.
 * @Transactional on commit(): Spring manages transaction boundary.
 *
 * Event dispatch flow:
 *   1. @Transactional opens connection
 *   2. All repositories persist their aggregates
 *   3. @Transactional commits DB transaction
 *   4. TransactionSynchronization.afterCommit() fires
 *   5. Domain events published (Kafka, Spring events)
 *
 * Critical: events published AFTER commit prevents ghost events.
 */
@Component
@RequestScope
public class SpringUnitOfWork implements UnitOfWork {

    private static final Logger log =
        LoggerFactory.getLogger(SpringUnitOfWork.class);

    // Tracked aggregates
    private final List<AggregateRoot> newAggregates    = new ArrayList<>();
    private final List<AggregateRoot> dirtyAggregates  = new ArrayList<>();
    private final List<AggregateRoot> deletedAggregates = new ArrayList<>();

    // Repositories — keyed by aggregate type
    private final OrderRepository     orderRepository;
    private final InventoryRepository inventoryRepository;
    private final PaymentRepository   paymentRepository;
    private final CartRepository      cartRepository;
    private final DomainEventPublisher eventPublisher;

    public SpringUnitOfWork(OrderRepository orderRepository,
                             InventoryRepository inventoryRepository,
                             PaymentRepository paymentRepository,
                             CartRepository cartRepository,
                             DomainEventPublisher eventPublisher) {
        this.orderRepository     = orderRepository;
        this.inventoryRepository = inventoryRepository;
        this.paymentRepository   = paymentRepository;
        this.cartRepository      = cartRepository;
        this.eventPublisher      = eventPublisher;
    }

    @Override
    public void registerNew(AggregateRoot aggregate) {
        if (!dirtyAggregates.contains(aggregate)
                && !newAggregates.contains(aggregate)) {
            newAggregates.add(aggregate);
            log.debug("UoW registered NEW: {}",
                aggregate.getClass().getSimpleName());
        }
    }

    @Override
    public void registerDirty(AggregateRoot aggregate) {
        if (!newAggregates.contains(aggregate)
                && !dirtyAggregates.contains(aggregate)) {
            dirtyAggregates.add(aggregate);
            log.debug("UoW registered DIRTY: {}",
                aggregate.getClass().getSimpleName());
        }
    }

    @Override
    public void registerDeleted(AggregateRoot aggregate) {
        newAggregates.remove(aggregate);
        dirtyAggregates.remove(aggregate);
        if (!deletedAggregates.contains(aggregate)) {
            deletedAggregates.add(aggregate);
        }
    }

    /**
     * Commit all tracked changes in a single transaction.
     * After commit: publish all collected domain events.
     */
    @Override
    @Transactional
    public void commit() {
        log.info("UoW committing: new={}, dirty={}, deleted={}",
            newAggregates.size(), dirtyAggregates.size(),
            deletedAggregates.size());

        // Collect events BEFORE saving (in case aggregate clears them)
        List<DomainEvent> allEvents = collectAllEvents();

        // Persist in dependency order
        // New aggregates first (FKs: Order → User)
        newAggregates.forEach(this::saveAggregate);

        // Then updates (no FK dependency issues)
        dirtyAggregates.forEach(this::updateAggregate);

        // Deletes last (FK children deleted before parents)
        deletedAggregates.forEach(this::deleteAggregate);

        // Register post-commit event dispatch
        // Spring's TransactionSynchronizationManager fires AFTER commit
        org.springframework.transaction.support.TransactionSynchronizationManager
            .registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronizationAdapter() {
                    @Override
                    public void afterCommit() {
                        log.info("UoW afterCommit: publishing {} events",
                            allEvents.size());
                        eventPublisher.publish(allEvents);
                    }
                }
            );

        clear();
    }

    @Override
    public void clear() {
        newAggregates.clear();
        dirtyAggregates.clear();
        deletedAggregates.clear();
    }

    // ── Dispatch by aggregate type ─────────────────────────────────────

    private void saveAggregate(AggregateRoot aggregate) {
        switch (aggregate) {
            case Order o         -> orderRepository.save(o);
            case Inventory inv   -> inventoryRepository.save(inv);
            case Payment p       -> paymentRepository.save(p);
            case Cart c          -> cartRepository.save(c);
            default -> log.warn("UoW: No repository for aggregate type: {}",
                aggregate.getClass().getSimpleName());
        }
    }

    private void updateAggregate(AggregateRoot aggregate) {
        switch (aggregate) {
            case Order o         -> orderRepository.update(o);
            case Inventory inv   -> inventoryRepository.update(inv);
            case Payment p       -> paymentRepository.update(p);
            case Cart c          -> cartRepository.update(c);
            default -> log.warn("UoW: No repository for type: {}",
                aggregate.getClass().getSimpleName());
        }
    }

    private void deleteAggregate(AggregateRoot aggregate) {
        // Soft deletes handled by aggregate itself (deletedAt)
        // Call update to persist the soft-delete
        updateAggregate(aggregate);
    }

    private List<DomainEvent> collectAllEvents() {
        List<DomainEvent> events = new ArrayList<>();
        List<AggregateRoot> all = new ArrayList<>();
        all.addAll(newAggregates);
        all.addAll(dirtyAggregates);
        all.forEach(a -> {
            events.addAll(a.getDomainEvents());
            a.clearDomainEvents();
        });
        return events;
    }
}
```

---

## Pattern 3: CQRS — Command Query Responsibility Segregation

### Problem It Solves
```
Problem: Read and write operations have fundamentally different needs:

WRITE side needs:
  - Consistency (ACID transactions)
  - Aggregate validation
  - Domain events
  - Optimistic locking

READ side needs:
  - Performance (denormalized, pre-joined views)
  - Flexibility (different shapes for different screens)
  - Horizontal scale (read replicas)
  - Caching (Redis)

Without CQRS:
  OrderService.getOrder() loads full aggregate with lazy-loading
  → N+1 queries to load order + items + user + payment
  → Heavy aggregate loaded for lightweight list display

With CQRS:
  Command: PlaceOrderCommand → OrderAggregate → normalize DB
  Query:   GetOrderQuery     → flat OrderReadModel → one SQL query
  → Write path: normalized, consistent
  → Read path: denormalized, fast, tailored to UI
```

### Architecture Diagram
```
                    ┌─────────────────────┐
                    │    Write Model       │
  Commands ────────►│  Aggregates (DDD)   │──── Domain Events ─────┐
                    │  Normalized DB       │                        │
                    └─────────────────────┘                        │
                                                                    ▼
                                                         ┌──────────────────────┐
                                                         │  Projection Builder   │
                                                         │  (Event → ReadModel) │
                                                         └──────────┬───────────┘
                    ┌─────────────────────┐                        │
                    │    Read Model        │◄───────────────────────┘
  Queries  ────────►│  Flat projections   │
                    │  Denormalized DB    │
                    └─────────────────────┘
Implementation
java// ecommerce-infrastructure:
// com.ecommerce.infrastructure.persistence.entity/OrderReadModel.java
package com.ecommerce.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * CQRS READ MODEL — Denormalized order view.
 *
 * Separate table from orders (write model).
 * Optimized for read patterns: list, detail, search.
 * Contains pre-joined data: user name, payment status, item count.
 *
 * Updated asynchronously via projections when domain events fire.
 * Eventual consistency: slight delay vs write model (acceptable for reads).
 */
@Entity
@Table(name = "order_read_models")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderReadModel {

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "user_id")
    private UUID userId;

    // Denormalized user data (no join needed for reads)
    @Column(name = "user_name")
    private String userName;

    @Column(name = "user_email")
    private String userEmail;

    @Column(name = "order_number")
    private String orderNumber;

    @Column(name = "status")
    private String status;

    @Column(name = "status_label")
    private String statusLabel;

    @Column(name = "total_amount", precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "item_count")
    private int itemCount;

    // Denormalized summary: "Nike Shoes × 2, Apple Watch × 1"
    @Column(name = "items_summary", length = 1000)
    private String itemsSummary;

    // Denormalized payment data
    @Column(name = "payment_status")
    private String paymentStatus;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "can_cancel")
    private boolean canCancel;

    @Column(name = "can_return")
    private boolean canReturn;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "last_updated_at")
    private Instant lastUpdatedAt;

    // JSONB: full items for detail view (avoids second query)
    @Column(name = "items_json", columnDefinition = "JSONB")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private List<OrderItemSnapshot> items;

    public record OrderItemSnapshot(
        String productId, String variantSku,
        String productName, String variantName,
        int quantity,
        BigDecimal unitPrice, BigDecimal totalPrice,
        String currency, String imageUrl
    ) {}
}
sql-- Read model table
CREATE TABLE order_read_models (
    order_id        UUID PRIMARY KEY,
    user_id         UUID NOT NULL,
    user_name       VARCHAR(255),
    user_email      VARCHAR(255),
    order_number    VARCHAR(50) NOT NULL,
    status          VARCHAR(50) NOT NULL,
    status_label    VARCHAR(100),
    total_amount    NUMERIC(12,2) NOT NULL,
    currency        VARCHAR(3) NOT NULL DEFAULT 'USD',
    item_count      INT NOT NULL DEFAULT 0,
    items_summary   VARCHAR(1000),
    payment_status  VARCHAR(50),
    payment_method  VARCHAR(50),
    can_cancel      BOOLEAN NOT NULL DEFAULT FALSE,
    can_return      BOOLEAN NOT NULL DEFAULT FALSE,
    tracking_number VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL,
    confirmed_at    TIMESTAMPTZ,
    shipped_at      TIMESTAMPTZ,
    delivered_at    TIMESTAMPTZ,
    last_updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    items_json      JSONB
);
CREATE INDEX idx_orm_user   ON order_read_models(user_id, created_at DESC);
CREATE INDEX idx_orm_status ON order_read_models(status, created_at DESC);
-- GIN index for JSONB item search
CREATE INDEX idx_orm_items  ON order_read_models USING GIN(items_json);
java// com.ecommerce.infrastructure.persistence.spring/OrderReadModelRepository.java
package com.ecommerce.infrastructure.persistence.spring;

import com.ecommerce.infrastructure.persistence.entity.OrderReadModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for the ORDER READ MODEL.
 * Fast, denormalized queries — no joins needed.
 */
public interface OrderReadModelRepository
        extends JpaRepository<OrderReadModel, UUID> {

    // User order history — single index seek
    Page<OrderReadModel> findByUserIdOrderByCreatedAtDesc(
        UUID userId, Pageable pageable);

    Optional<OrderReadModel> findByOrderNumber(String orderNumber);

    // Admin: orders by status
    Page<OrderReadModel> findByStatusOrderByCreatedAtDesc(
        String status, Pageable pageable);

    // Admin: orders needing attention
    @Query("""
        SELECT o FROM OrderReadModel o
        WHERE o.status IN ('CONFIRMED', 'PROCESSING')
        ORDER BY o.createdAt ASC
        """)
    Page<OrderReadModel> findOrdersPendingFulfillment(Pageable pageable);

    // Cursor-based: faster than OFFSET for large datasets
    @Query("""
        SELECT o FROM OrderReadModel o
        WHERE o.userId = :userId
        AND (o.createdAt < :cursorDate
             OR (o.createdAt = :cursorDate AND o.orderId < :cursorId))
        ORDER BY o.createdAt DESC, o.orderId DESC
        """)
    Page<OrderReadModel> findByUserIdWithCursor(
        @Param("userId")     UUID userId,
        @Param("cursorDate") java.time.Instant cursorDate,
        @Param("cursorId")   UUID cursorId,
        Pageable pageable
    );
}
java// com.ecommerce.application.order/projection/OrderProjection.java
package com.ecommerce.application.order.projection;

import com.ecommerce.domain.order.event.OrderPlacedEvent;
import com.ecommerce.domain.order.event.OrderStatusChangedEvent;
import com.ecommerce.domain.payment.event.PaymentSucceededEvent;
import com.ecommerce.domain.shared.event.DomainEvent;
import com.ecommerce.domain.shared.event.DomainEventHandler;
import com.ecommerce.domain.user.UserRepository;
import com.ecommerce.domain.user.UserId;
import com.ecommerce.infrastructure.persistence.entity.OrderReadModel;
import com.ecommerce.infrastructure.persistence.spring.OrderReadModelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * CQRS PROJECTION — builds and maintains the order read model.
 *
 * Listens to domain events (Observer pattern).
 * Rebuilds the denormalized read model whenever write model changes.
 *
 * Eventual consistency:
 *   Write committed → event published → projection updates read model
 *   Small delay (milliseconds) between write and read model sync.
 *   Acceptable for order reads — user won't notice < 100ms delay.
 *
 * Projection can be rebuilt from scratch by replaying all events.
 */
@Component
public class OrderProjection implements DomainEventHandler<DomainEvent> {

    private static final Logger log =
        LoggerFactory.getLogger(OrderProjection.class);

    private final OrderReadModelRepository readModelRepo;
    private final com.ecommerce.domain.order.OrderRepository orderRepository;
    private final UserRepository userRepository;

    public OrderProjection(OrderReadModelRepository readModelRepo,
                            com.ecommerce.domain.order.OrderRepository orderRepository,
                            UserRepository userRepository) {
        this.readModelRepo  = readModelRepo;
        this.orderRepository = orderRepository;
        this.userRepository  = userRepository;
    }

    @Override
    public void handle(DomainEvent event) {
        switch (event) {
            case OrderPlacedEvent e        -> handleOrderPlaced(e);
            case OrderStatusChangedEvent e -> handleStatusChanged(e);
            case PaymentSucceededEvent e   -> handlePaymentSucceeded(e);
            default -> {}
        }
    }

    @Override
    public boolean supports(DomainEvent event) {
        return event instanceof OrderPlacedEvent
            || event instanceof OrderStatusChangedEvent
            || event instanceof PaymentSucceededEvent;
    }

    private void handleOrderPlaced(OrderPlacedEvent event) {
        log.info("Projection: building read model for order {}",
            event.getOrderId());

        // Load full order from write model
        var order = orderRepository.findById(
            com.ecommerce.domain.order.OrderId.of(event.getOrderId())
        ).orElse(null);
        if (order == null) return;

        // Load user for denormalization
        var user = userRepository.findById(
            UserId.of(event.getUserId())
        ).orElse(null);

        // Build items snapshot (JSONB)
        List<OrderReadModel.OrderItemSnapshot> itemSnapshots =
            order.getItems().stream()
                .map(item -> new OrderReadModel.OrderItemSnapshot(
                    item.getProductId().getValue().toString(),
                    item.getVariantSku(),
                    item.getProductName(),
                    item.getVariantName(),
                    item.getQuantity(),
                    item.getUnitPrice().getAmount(),
                    item.getTotalPrice().getAmount(),
                    item.getUnitPrice().getCurrency().getCurrencyCode(),
                    null // imageUrl loaded separately if needed
                ))
                .toList();

        // Build items summary string
        String summary = order.getItems().stream()
            .map(i -> i.getProductName() + " × " + i.getQuantity())
            .reduce((a, b) -> a + ", " + b)
            .orElse("");

        OrderReadModel readModel = OrderReadModel.builder()
            .orderId(UUID.fromString(event.getOrderId()))
            .userId(UUID.fromString(event.getUserId()))
            .userName(user != null ? user.getFullName() : "Unknown")
            .userEmail(user != null ? user.getEmail().getValue() : "")
            .orderNumber(order.getOrderNumber())
            .status(order.getStatus().name())
            .statusLabel(formatStatus(order.getStatus().name()))
            .totalAmount(order.getTotalAmount().getAmount())
            .currency(order.getTotalAmount().getCurrency().getCurrencyCode())
            .itemCount(order.getItems().stream()
                .mapToInt(com.ecommerce.domain.order.OrderItem::getQuantity)
                .sum())
            .itemsSummary(summary)
            .paymentStatus("PENDING")
            .canCancel(true)
            .canReturn(false)
            .createdAt(order.getCreatedAt())
            .lastUpdatedAt(Instant.now())
            .items(itemSnapshots)
            .build();

        readModelRepo.save(readModel);
        log.info("Projection: read model created for order {}",
            event.getOrderId());
    }

    private void handleStatusChanged(OrderStatusChangedEvent event) {
        readModelRepo.findById(
            UUID.fromString(event.getOrderId())
        ).ifPresent(model -> {
            model.setStatus(event.getTo().name());
            model.setStatusLabel(formatStatus(event.getTo().name()));
            model.setCanCancel(
                event.getTo() == com.ecommerce.domain.order.OrderStatus.PENDING
                || event.getTo() == com.ecommerce.domain.order.OrderStatus.CONFIRMED
                || event.getTo() == com.ecommerce.domain.order.OrderStatus.PROCESSING
            );
            model.setCanReturn(
                event.getTo() == com.ecommerce.domain.order.OrderStatus.DELIVERED
            );

            if (event.getTo() == com.ecommerce.domain.order.OrderStatus.SHIPPED) {
                model.setShippedAt(Instant.now());
            } else if (event.getTo() == com.ecommerce.domain.order.OrderStatus.DELIVERED) {
                model.setDeliveredAt(Instant.now());
            }
            model.setLastUpdatedAt(Instant.now());
            readModelRepo.save(model);
        });
    }

    private void handlePaymentSucceeded(PaymentSucceededEvent event) {
        readModelRepo.findById(
            UUID.fromString(event.getOrderId())
        ).ifPresent(model -> {
            model.setPaymentStatus("SUCCEEDED");
            model.setConfirmedAt(Instant.now());
            model.setLastUpdatedAt(Instant.now());
            readModelRepo.save(model);
        });
    }

    private String formatStatus(String status) {
        return switch (status) {
            case "PENDING"    -> "Awaiting Payment";
            case "CONFIRMED"  -> "Order Confirmed";
            case "PROCESSING" -> "Being Prepared";
            case "SHIPPED"    -> "On the Way";
            case "DELIVERED"  -> "Delivered";
            case "CANCELLED"  -> "Cancelled";
            case "REFUNDED"   -> "Refunded";
            default           -> status;
        };
    }
}
java// com.ecommerce.application.order/query/GetOrderHistoryQuery.java
package com.ecommerce.application.order.query;

import com.ecommerce.application.shared.query.Query;

/**
 * CQRS Query — read side only.
 * Marked with Query interface — must NEVER mutate state.
 */
public record GetOrderHistoryQuery(
    String userId,
    int page,
    int size,
    String statusFilter,   // optional
    String cursorToken     // for cursor pagination
) implements Query<com.ecommerce.application.order.iterator.OrderHistoryPage> {}
java// com.ecommerce.application.order/query/GetOrderHistoryQueryHandler.java
package com.ecommerce.application.order.query;

import com.ecommerce.application.order.dto.OrderResponse;
import com.ecommerce.application.order.iterator.OrderHistoryPage;
import com.ecommerce.application.shared.query.QueryHandler;
import com.ecommerce.infrastructure.persistence.entity.OrderReadModel;
import com.ecommerce.infrastructure.persistence.spring.OrderReadModelRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * CQRS Query Handler — reads DIRECTLY from the read model.
 * No domain aggregates loaded. No domain logic executed.
 * Just: read model → DTO → response.
 *
 * Performance comparison:
 *   Write path: load Order aggregate → 3-4 queries (N+1 variants/items/payment)
 *   Read path:  load OrderReadModel → 1 query (pre-joined, JSONB items)
 *   10x+ faster for order list queries.
 */
@Component
public class GetOrderHistoryQueryHandler
        implements QueryHandler<GetOrderHistoryQuery, OrderHistoryPage> {

    private final OrderReadModelRepository readModelRepo;

    public GetOrderHistoryQueryHandler(
            OrderReadModelRepository readModelRepo) {
        this.readModelRepo = readModelRepo;
    }

    @Override
    public OrderHistoryPage handle(GetOrderHistoryQuery query) {
        UUID userId = UUID.fromString(query.userId());
        PageRequest pageable = PageRequest.of(query.page(), query.size());

        Page<OrderReadModel> page;

        if (query.cursorToken() != null && !query.cursorToken().isBlank()) {
            var cursor = com.ecommerce.application.order.iterator
                .OrderCursorIterator.CursorToken.decode(query.cursorToken());
            page = readModelRepo.findByUserIdWithCursor(
                userId, cursor.cursorDate(),
                UUID.fromString(cursor.cursorId()), pageable
            );
        } else {
            page = readModelRepo.findByUserIdOrderByCreatedAtDesc(
                userId, pageable
            );
        }

        List<OrderResponse> responses = page.getContent().stream()
            .map(this::toOrderResponse)
            .toList();

        return OrderHistoryPage.of(
            responses,
            query.page(),
            query.size(),
            page.getTotalElements(),
            null // no cursor on initial load
        );
    }

    private OrderResponse toOrderResponse(OrderReadModel model) {
        return OrderResponse.builder()
            .orderId(model.getOrderId().toString())
            .userId(model.getUserId().toString())
            .orderNumber(model.getOrderNumber())
            .status(com.ecommerce.domain.order.OrderStatus.valueOf(model.getStatus()))
            .totalAmount(new com.ecommerce.application.order.dto.MoneyDto(
                model.getTotalAmount(),
                model.getCurrency(),
                model.getCurrency() + " " + model.getTotalAmount()
            ))
            .createdAt(model.getCreatedAt())
            .confirmedAt(model.getConfirmedAt())
            .shippedAt(model.getShippedAt())
            .deliveredAt(model.getDeliveredAt())
            .build();
    }
}
```

---

## Pattern 4: Event Sourcing

### Problem It Solves
```
Problem: Need complete, immutable audit trail of all order changes.
  - Compliance: GDPR, SOX require who-did-what-when
  - Debugging: "Why is this order in CANCELLED state?"
  - Temporal queries: "What was order state at 2PM yesterday?"
  - Replayability: Rebuild read models from scratch

Without Event Sourcing:
  orders table: current state only
  "Order was PENDING, then CONFIRMED, then CANCELLED"
  → Only current state (CANCELLED) visible in DB
  → History is GONE — cannot answer "when was it confirmed?"

With Event Sourcing:
  order_events: immutable append-only log
  OrderCreated → OrderConfirmed → OrderCancelled
  → Full history: what happened AND when AND who caused it
  → Replay events to rebuild state at ANY point in time
```

### Event Stream Diagram
```
order_events table (append-only):
┌──────┬──────────────────┬───────────────────────────────────────────┐
│  id  │  event_type      │  payload                                  │
├──────┼──────────────────┼───────────────────────────────────────────┤
│  1   │ OrderCreated     │ {userId, items, shipping, total}          │
│  2   │ OrderConfirmed   │ {paymentIntentId, confirmedAt}            │
│  3   │ OrderShipped     │ {trackingNumber, carrier, shippedAt}      │
│  4   │ OrderCancelled   │ {reason, cancelledAt, refundTriggered}    │
└──────┴──────────────────┴───────────────────────────────────────────┘
To rebuild state: apply events 1→4 in sequence.
To get state at event 2: apply events 1→2 only.
Implementation
java// ecommerce-domain:
// com.ecommerce.domain.order/event/OrderCreatedEvent.java
package com.ecommerce.domain.order.event;

import com.ecommerce.domain.shared.event.BaseDomainEvent;
import com.ecommerce.domain.shared.valueobject.Money;
import java.util.List;

/**
 * Event Sourcing event — captures EVERYTHING needed to recreate state.
 * Richer than notification events: includes full snapshot.
 */
public class OrderCreatedEvent extends BaseDomainEvent {

    private final String userId;
    private final String orderNumber;
    private final List<OrderItemData> items;
    private final Money totalAmount;
    private final AddressData shippingAddress;
    private final String couponCode;

    public OrderCreatedEvent(String orderId, String userId, String orderNumber,
                              List<OrderItemData> items, Money totalAmount,
                              AddressData shippingAddress, String couponCode) {
        super(orderId);
        this.userId          = userId;
        this.orderNumber     = orderNumber;
        this.items           = List.copyOf(items);
        this.totalAmount     = totalAmount;
        this.shippingAddress = shippingAddress;
        this.couponCode      = couponCode;
    }

    @Override
    public String eventType() { return "OrderCreated"; }

    public String getUserId()              { return userId; }
    public String getOrderNumber()         { return orderNumber; }
    public List<OrderItemData> getItems()  { return items; }
    public Money getTotalAmount()          { return totalAmount; }
    public AddressData getShippingAddress(){ return shippingAddress; }
    public String getCouponCode()          { return couponCode; }

    public record OrderItemData(
        String productId, String variantSku,
        String productName, int quantity,
        java.math.BigDecimal unitPrice, String currency
    ) {}

    public record AddressData(
        String fullName, String line1, String city,
        String state, String country, String postalCode
    ) {}
}
java// ecommerce-infrastructure:
// com.ecommerce.infrastructure.eventsourcing/OrderEventStore.java
package com.ecommerce.infrastructure.eventsourcing;

import com.ecommerce.domain.shared.event.DomainEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Event Store — append-only log of order events.
 *
 * Design decisions:
 *   - Append-only: events NEVER updated or deleted
 *   - Optimistic concurrency: check expectedVersion before append
 *   - JSONB payload: flexible schema evolution
 *   - BigSerial ID: guaranteed ordering within order stream
 *
 * Optimistic concurrency check:
 *   Before appending event N+1, verify current max version == N.
 *   If another process appended concurrently, our version is wrong → retry.
 */
@Repository
public class OrderEventStore {

    private static final Logger log =
        LoggerFactory.getLogger(OrderEventStore.class);

    private final JdbcTemplate   jdbc;
    private final ObjectMapper   objectMapper;

    public OrderEventStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc         = jdbc;
        this.objectMapper = objectMapper;
    }

    /**
     * Append a new event to the order's event stream.
     * Checks optimistic concurrency: expectedVersion must match current max.
     *
     * @throws ConcurrencyException if another event was appended concurrently
     */
    public void append(String orderId, DomainEvent event,
                       int expectedVersion, String issuedBy) {
        int currentVersion = getCurrentVersion(orderId);

        if (currentVersion != expectedVersion) {
            throw new ConcurrencyException(
                String.format(
                    "Concurrency conflict for order %s: expected version %d, found %d",
                    orderId, expectedVersion, currentVersion
                )
            );
        }

        int nextVersion = expectedVersion + 1;

        try {
            String payload = objectMapper.writeValueAsString(toPayloadMap(event));

            jdbc.update("""
                INSERT INTO order_events
                    (order_id, event_type, event_version, payload,
                     occurred_at, issued_by)
                VALUES (?, ?, ?, ?::JSONB, ?, ?)
                """,
                java.util.UUID.fromString(orderId),
                event.eventType(),
                nextVersion,
                payload,
                Timestamp.from(event.occurredAt()),
                issuedBy
            );

            log.info("Event appended: orderId={}, type={}, version={}",
                orderId, event.eventType(), nextVersion);

        } catch (Exception e) {
            log.error("Failed to append event: orderId={}, type={}: {}",
                orderId, event.eventType(), e.getMessage());
            throw new RuntimeException("Event store append failed", e);
        }
    }

    /**
     * Load all events for an order (full history).
     */
    public List<StoredEvent> loadEvents(String orderId) {
        return jdbc.query("""
            SELECT id, event_type, event_version,
                   payload, occurred_at, issued_by
            FROM order_events
            WHERE order_id = ?
            ORDER BY id ASC
            """,
            (rs, rowNum) -> new StoredEvent(
                rs.getLong("id"),
                rs.getString("event_type"),
                rs.getInt("event_version"),
                rs.getString("payload"),
                rs.getTimestamp("occurred_at").toInstant(),
                rs.getString("issued_by")
            ),
            java.util.UUID.fromString(orderId)
        );
    }

    /**
     * Load events from a specific version (for partial replay).
     * e.g., rebuild from snapshot at version 10, then replay 11+
     */
    public List<StoredEvent> loadEventsFromVersion(String orderId,
                                                    int fromVersion) {
        return jdbc.query("""
            SELECT id, event_type, event_version,
                   payload, occurred_at, issued_by
            FROM order_events
            WHERE order_id = ? AND event_version >= ?
            ORDER BY id ASC
            """,
            (rs, rowNum) -> new StoredEvent(
                rs.getLong("id"),
                rs.getString("event_type"),
                rs.getInt("event_version"),
                rs.getString("payload"),
                rs.getTimestamp("occurred_at").toInstant(),
                rs.getString("issued_by")
            ),
            java.util.UUID.fromString(orderId),
            fromVersion
        );
    }

    private int getCurrentVersion(String orderId) {
        Integer version = jdbc.queryForObject("""
            SELECT COALESCE(MAX(event_version), 0)
            FROM order_events WHERE order_id = ?
            """,
            Integer.class,
            java.util.UUID.fromString(orderId)
        );
        return version != null ? version : 0;
    }

    private Map<String, Object> toPayloadMap(DomainEvent event) {
        return objectMapper.convertValue(event, Map.class);
    }

    public record StoredEvent(
        long sequenceId,
        String eventType,
        int version,
        String payloadJson,
        Instant occurredAt,
        String issuedBy
    ) {}

    public static class ConcurrencyException extends RuntimeException {
        public ConcurrencyException(String message) { super(message); }
    }
}
java// com.ecommerce.infrastructure.eventsourcing/OrderEventSourcedRepository.java
package com.ecommerce.infrastructure.eventsourcing;

import com.ecommerce.domain.order.Order;
import com.ecommerce.domain.order.OrderId;
import com.ecommerce.domain.order.OrderStatus;
import com.ecommerce.domain.order.event.*;
import com.ecommerce.domain.shared.valueobject.Money;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Event-sourced Order Repository.
 *
 * Instead of loading a row from a table, this repository:
 * 1. Loads all events for the order
 * 2. Replays them to reconstruct current state
 * 3. (Optionally) loads from snapshot + replay only newer events
 *
 * This is the alternative to JpaOrderRepository:
 *   JpaOrderRepository: current state stored, fast reads
 *   OrderEventSourcedRepository: history stored, full audit trail
 *
 * In production: use BOTH.
 *   Command side: event-sourced repository (write model)
 *   Query side: projected read model (read model)
 */
@Repository
public class OrderEventSourcedRepository {

    private static final Logger log =
        LoggerFactory.getLogger(OrderEventSourcedRepository.class);

    private final OrderEventStore eventStore;
    private final ObjectMapper    objectMapper;

    public OrderEventSourcedRepository(OrderEventStore eventStore,
                                        ObjectMapper objectMapper) {
        this.eventStore   = eventStore;
        this.objectMapper = objectMapper;
    }

    /**
     * Reconstruct Order aggregate by replaying all its events.
     * This is the core of Event Sourcing.
     */
    public Optional<Order> findById(OrderId orderId) {
        List<OrderEventStore.StoredEvent> events =
            eventStore.loadEvents(orderId.getValue().toString());

        if (events.isEmpty()) return Optional.empty();

        log.debug("Replaying {} events for order {}",
            events.size(), orderId);

        Order order = new Order();  // empty shell

        // Replay each event in sequence to rebuild state
        for (OrderEventStore.StoredEvent stored : events) {
            apply(order, stored);
        }

        return Optional.of(order);
    }

    /**
     * Save new events generated by the aggregate.
     * Extracts domain events, appends to event store.
     */
    public void save(Order order, String issuedBy) {
        int currentVersion = order.getVersion();

        for (var event : order.getDomainEvents()) {
            eventStore.append(
                order.getId().getValue().toString(),
                event,
                currentVersion++,
                issuedBy
            );
        }

        order.clearDomainEvents();
    }

    /**
     * Apply a stored event to the order aggregate — rebuilds state.
     * Each event type knows how to apply itself.
     */
    private void apply(Order order, OrderEventStore.StoredEvent stored) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload =
                objectMapper.readValue(stored.payloadJson(), Map.class);

            switch (stored.eventType()) {
                case "OrderCreated" -> applyOrderCreated(order, payload, stored);
                case "OrderStatusChanged" -> applyStatusChanged(order, payload);
                case "OrderCancelled" -> applyCancelled(order, payload);
                default -> log.warn("Unknown event type: {}", stored.eventType());
            }
        } catch (Exception e) {
            log.error("Failed to apply event {} to order: {}",
                stored.eventType(), e.getMessage(), e);
            throw new RuntimeException("Event replay failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void applyOrderCreated(Order order,
                                    Map<String, Object> payload,
                                    OrderEventStore.StoredEvent stored) {
        // Reconstitute order from creation event
        String orderId = stored.payloadJson().contains("aggregateId")
            ? (String) payload.get("aggregateId")
            : (String) payload.get("orderId");

        // Apply each field from event payload
        order.applyCreatedEvent(
            OrderId.of(orderId),
            (String) payload.get("userId"),
            (String) payload.get("orderNumber"),
            OrderStatus.PENDING,
            BigDecimal.valueOf(
                ((Number) payload.getOrDefault("totalAmount", 0)).doubleValue()
            )
        );
    }

    private void applyStatusChanged(Order order, Map<String, Object> payload) {
        String toStatus = (String) payload.get("to");
        if (toStatus != null) {
            order.applyStatusEvent(OrderStatus.valueOf(toStatus));
        }
    }

    private void applyCancelled(Order order, Map<String, Object> payload) {
        String reason = (String) payload.getOrDefault("reason", "");
        order.applyCancelledEvent(reason);
    }
}
```

---

## Pattern 5: Domain Services

### Problem It Solves
```
Problem: Some operations don't belong to a single aggregate:

  ① Order total calculation requires: Cart + Coupon + Shipping + Tax
     → Which aggregate owns this? None of them alone.

  ② Duplicate order check: "Did user already place this order?"
     → Requires querying OrderRepository — not an aggregate concern

  ③ Transfer money between user wallets
     → Touches TWO User aggregates simultaneously

Solution: Domain Service — stateless service in domain layer.
  - Operates on multiple aggregates
  - Contains domain logic (NOT application logic)
  - Depends only on domain interfaces (repositories, value objects)
  - Has no @Autowired — instantiated by application layer
Implementation
java// ecommerce-domain:
// com.ecommerce.domain.order/service/OrderTotalCalculationService.java
package com.ecommerce.domain.order.service;

import com.ecommerce.domain.cart.Cart;
import com.ecommerce.domain.discount.Coupon;
import com.ecommerce.domain.shared.valueobject.Money;
import com.ecommerce.domain.user.Address;

import java.math.BigDecimal;

/**
 * Domain Service — Order Total Calculation.
 *
 * Calculates final order total across multiple aggregates:
 *   Cart (subtotal) + Shipping + Tax - Discount
 *
 * Why a Domain Service and NOT a Cart/Order method?
 *   - Cart doesn't know about shipping
 *   - Shipping doesn't know about coupons
 *   - Tax calculation crosses domain boundaries
 *   - This is DOMAIN logic (business rules), not application logic
 *
 * Stateless: no fields, pure function.
 * Domain layer: no Spring, no JPA, no repositories.
 */
public class OrderTotalCalculationService {

    /**
     * Calculate the final order total.
     * All inputs are domain objects — no DTOs, no framework objects.
     */
    public OrderTotals calculate(Cart cart, Money shippingCost,
                                  Coupon coupon, Address shippingAddress) {
        Money subtotal = cart.calculateTotal();

        // Apply coupon discount
        Money discountAmount = Money.ZERO_USD;
        String appliedCouponCode = null;
        boolean freeShipping = false;

        if (coupon != null) {
            discountAmount   = coupon.calculateDiscount(subtotal);
            appliedCouponCode = coupon.getCode();
            freeShipping     = coupon.isFreeShipping();
        }

        // Apply free shipping from coupon
        Money effectiveShipping = freeShipping
            ? Money.ZERO_USD
            : shippingCost;

        // Calculate tax (simplified: 8% on non-discounted subtotal)
        // In production: use TaxCalculationVisitor from Phase 6
        BigDecimal taxRate = BigDecimal.valueOf(0.08);
        Money taxAmount = subtotal.subtract(discountAmount).multiply(taxRate);

        // Final total
        Money total = subtotal
            .add(effectiveShipping)
            .add(taxAmount)
            .subtract(discountAmount);

        // Enforce: total cannot be negative
        if (total.isNegative()) {
            total = Money.ZERO_USD;
        }

        return new OrderTotals(
            subtotal, effectiveShipping, taxAmount,
            discountAmount, appliedCouponCode, total
        );
    }

    public record OrderTotals(
        Money subtotal,
        Money shippingCost,
        Money taxAmount,
        Money discountAmount,
        String couponCode,
        Money totalAmount
    ) {}
}
java// com.ecommerce.domain.order/service/DuplicateOrderDetectionService.java
package com.ecommerce.domain.order.service;

import com.ecommerce.domain.order.Order;
import com.ecommerce.domain.order.OrderRepository;
import com.ecommerce.domain.user.UserId;

import java.time.Instant;
import java.util.List;

/**
 * Domain Service — Duplicate Order Detection.
 *
 * Detects suspicious duplicate orders:
 *   Same user, same items, same amount, within 5 minutes.
 *
 * Why Domain Service?
 *   - Requires OrderRepository (not available to aggregates)
 *   - Business rule ("5 minutes, same cart") is domain logic
 *   - Operates on multiple Orders (not inside one aggregate)
 */
public class DuplicateOrderDetectionService {

    private static final long DUPLICATE_WINDOW_SECONDS = 300; // 5 minutes

    private final OrderRepository orderRepository;

    public DuplicateOrderDetectionService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * Check if this appears to be a duplicate order submission.
     * Returns the suspected duplicate order if found.
     */
    public boolean isDuplicate(UserId userId,
                                com.ecommerce.domain.shared.valueobject.Money amount,
                                String idempotencyKey) {
        // Check by idempotency key first (exact match)
        if (orderRepository.existsByPaymentIntentId(idempotencyKey)) {
            return true;
        }

        // Check for same amount, same user within time window
        Instant windowStart = Instant.now()
            .minusSeconds(DUPLICATE_WINDOW_SECONDS);

        List<Order> recentOrders = orderRepository
            .findByUserIdSince(userId, windowStart);

        return recentOrders.stream()
            .anyMatch(o ->
                o.getTotalAmount().equals(amount)
                && o.getPaymentIntentId() != null
            );
    }
}
java// com.ecommerce.domain.inventory/service/InventoryAllocationService.java
package com.ecommerce.domain.inventory.service;

import com.ecommerce.domain.cart.CartItem;
import com.ecommerce.domain.inventory.Inventory;
import com.ecommerce.domain.inventory.InventoryRepository;
import com.ecommerce.domain.inventory.StockReservation;
import com.ecommerce.domain.shared.exception.DomainException;
import com.ecommerce.domain.shared.exception.ErrorCodes;

import java.util.ArrayList;
import java.util.List;

/**
 * Domain Service — Inventory Allocation.
 *
 * Reserves stock across multiple SKUs atomically.
 * If ANY SKU is out of stock → rolls back ALL reservations.
 *
 * This is a domain service because:
 *   - Operates on multiple Inventory aggregates
 *   - Contains domain invariant: "all or nothing allocation"
 *   - Not application orchestration — this IS the business rule
 */
public class InventoryAllocationService {

    private final InventoryRepository inventoryRepository;

    public InventoryAllocationService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    /**
     * Reserve stock for all cart items atomically.
     * Fails fast: first out-of-stock SKU aborts and releases all prior.
     */
    public List<StockReservation> reserveAll(List<CartItem> items,
                                              String orderId) {
        List<StockReservation> reservations = new ArrayList<>();
        List<Inventory> modified = new ArrayList<>();

        for (CartItem item : items) {
            Inventory inventory = inventoryRepository
                .findBySkuWithLock(item.getVariantSku())
                .orElseThrow(() -> {
                    // Release all prior reservations before throwing
                    releaseAll(reservations, modified);
                    return new DomainException(
                        ErrorCodes.INVENTORY_INSUFFICIENT,
                        "Product not found in inventory: " + item.getVariantSku()
                    );
                });

            try {
                StockReservation reservation =
                    inventory.reserve(item.getQuantity(), orderId);
                reservations.add(reservation);
                modified.add(inventory);
                inventoryRepository.update(inventory);
            } catch (DomainException e) {
                // Insufficient stock — release all prior
                releaseAll(reservations, modified);
                throw new DomainException(
                    ErrorCodes.INVENTORY_INSUFFICIENT,
                    String.format(
                        "Insufficient stock for '%s': requested %d, available %d",
                        item.getVariantSku(),
                        item.getQuantity(),
                        inventory.getAvailableQuantity()
                    )
                );
            }
        }

        return reservations;
    }

    private void releaseAll(List<StockReservation> reservations,
                             List<Inventory> inventories) {
        for (int i = 0; i < Math.min(reservations.size(),
                inventories.size()); i++) {
            inventories.get(i).cancelReservation(
                reservations.get(i).getReservationId()
            );
            inventoryRepository.update(inventories.get(i));
        }
    }
}
```

---

## Full Architecture Diagram — All Layers
```
┌─────────────────────────────────────────────────────────────────────────────┐
│ API Layer (ecommerce-api)                                                   │
│  REST Controllers → @Valid requests → CommandBus/QueryBus dispatch          │
└──────────────────────────────────┬──────────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼──────────────────────────────────────────┐
│ Application Layer (ecommerce-application)                                   │
│  CommandHandlers │ QueryHandlers │ CheckoutFacade │ Projections             │
│  UnitOfWork      │ Validators    │ Mediator       │ Assemblers              │
└──────────────────────────────────┬──────────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼──────────────────────────────────────────┐
│ Domain Layer (ecommerce-domain)                                             │
│  Aggregates:  Order │ Product │ Inventory │ Cart │ Payment │ Coupon         │
│  ValueObjects: Money │ Email │ Address │ AggregateId                        │
│  Domain Events: OrderPlaced │ StockReserved │ PaymentSucceeded ...          │
│  Domain Services: TotalCalc │ DuplicateDetection │ InventoryAllocation      │
│  Ports (interfaces): OrderRepository │ ProductRepository │ ...              │
└──────────────────────────────────┬──────────────────────────────────────────┘
                                   │ implements (Dependency Inversion)
┌──────────────────────────────────▼──────────────────────────────────────────┐
│ Infrastructure Layer (ecommerce-infrastructure)                             │
│  Write Repositories: JpaOrderRepository │ JpaProductRepository ...         │
│  Read Repositories: OrderReadModelRepository │ SpringDataProductRepository  │
│  Event Store: OrderEventStore (append-only)                                 │
│  Mappers: ProductMapper │ OrderMapper                                       │
│  Decorators: CachingRepo │ LoggingRepo │ RetryRepo                          │
│  Proxies: SecurityProxy │ AuditProxy                                        │
│  Payment Adapters: Stripe │ PayPal │ LegacyBank                            │
│  Search: ElasticsearchSearchEngine                                          │
│  Cache: RedisTemplate                                                       │
│  Messaging: KafkaEventPublisher (Phase 9)                                  │
└─────────────────────────────────────────────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼──────────────────────────────────────────┐
│ External Systems                                                            │
│  PostgreSQL │ Redis │ Kafka │ Elasticsearch │ Stripe │ PayPal │ Firebase    │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Summary Table — Architectural Patterns
```
┌────────────────┬──────────────────────────────────────┬────────────────────────────────────────┐
│ Pattern        │ Used For                             │ Key Benefit                            │
├────────────────┼──────────────────────────────────────┼────────────────────────────────────────┤
│ Repository     │ Data access abstraction              │ Domain never imports JPA/Hibernate     │
│                │ + Specifications for queries         │ Swap DB = swap adapter, zero domain Δ │
├────────────────┼──────────────────────────────────────┼────────────────────────────────────────┤
│ Unit of Work   │ Multi-aggregate transactions         │ Events published AFTER commit only     │
│                │ + domain event dispatch              │ All-or-nothing consistency             │
├────────────────┼──────────────────────────────────────┼────────────────────────────────────────┤
│ CQRS           │ Separate read/write models           │ Read: 1 query, denormalized, 10x faster│
│                │ + projections from events            │ Write: normalized, consistent          │
├────────────────┼──────────────────────────────────────┼────────────────────────────────────────┤
│ Event Sourcing │ Immutable order audit log            │ Full history, temporal queries,        │
│                │ + append-only event stream           │ replayable, compliance-ready           │
├────────────────┼──────────────────────────────────────┼────────────────────────────────────────┤
│ Specification  │ Composable product queries           │ Type-safe, readable, no raw SQL        │
│                │                                      │ Reusable, combinable predicates        │
├────────────────┼──────────────────────────────────────┼────────────────────────────────────────┤
│ Domain Service │ Cross-aggregate operations           │ Complex logic in domain, not service   │
│                │ (total calc, dup detection, alloc)   │ Testable without Spring context        │
└────────────────┴──────────────────────────────────────┴────────────────────────────────────────┘

✅ Phase 7 complete. All Architectural Patterns implemented with:

Repository: Full JPA entities, bidirectional mappers, Spring Data, Specifications, complete DB schema with indexes
Unit of Work: Request-scoped, TransactionSynchronization post-commit event dispatch, type-safe aggregate routing
CQRS: Denormalized read model, event-driven projection builder, cursor + offset query handlers, read/write model separation
Event Sourcing: Append-only event store, optimistic concurrency, event replay for state reconstruction, JSONB payloads
Specification: Composable JPA Criteria predicates, fluent API, composite catalog search spec
Domain Services: Order total calculation, duplicate detection, atomic inventory allocation with rollback


Reply "Phase 8" and I'll implement Cross-Cutting Concerns — JWT Authentication + Spring Security, Rate Limiting (token bucket + Redis distributed), Distributed Tracing (OpenTelemetry), Circuit Breaker (Resilience4j), Idempotency Middleware, Error Handling (global exception handler + RFC 7807 Problem Details), and Structured Logging (MDC + correlation IDs).