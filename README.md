# Camel Persistence

`camel-persistence` is a modular persistence layer for Camel-style flow state, with pluggable backends.

[![version](https://img.shields.io/badge/version-1.3.0-brightgreen)](https://github.com/dscope-io/dscope-camel-persistence/releases/tag/v1.3.0)
[![license](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)
[![java](https://img.shields.io/badge/java-21-orange)](https://openjdk.org/)

**Description:** Modular flow-state persistence for Apache Camel with pluggable JDBC, Redis, and IC4J/ICP backends.

## Documentation

- [Third-Party Component Integration Guide](docs/THIRD_PARTY_COMPONENT_INTEGRATION.md)
- [Maven Central Deployment Guide](docs/MAVEN_CENTRAL_DEPLOYMENT.md)

## Topics

[![apache-camel](https://img.shields.io/badge/topic-apache--camel-blue)](https://github.com/topics/apache-camel)
[![camel](https://img.shields.io/badge/topic-camel-blue)](https://github.com/topics/camel)
[![persistence](https://img.shields.io/badge/topic-persistence-blue)](https://github.com/topics/persistence)
[![event-sourcing](https://img.shields.io/badge/topic-event--sourcing-blue)](https://github.com/topics/event-sourcing)
[![jdbc](https://img.shields.io/badge/topic-jdbc-blue)](https://github.com/topics/jdbc)
[![redis](https://img.shields.io/badge/topic-redis-blue)](https://github.com/topics/redis)
[![java](https://img.shields.io/badge/topic-java-blue)](https://github.com/topics/java)
[![state-management](https://img.shields.io/badge/topic-state--management-blue)](https://github.com/topics/state-management)

## Modules

- `camel-persistence-core` — core contracts and factory (`FlowStateStore`, `PersistenceConfiguration`, `FlowStateStoreFactory`)
- `camel-persistence-jdbc` — JDBC-backed implementation
- `camel-persistence-redis` — Redis-backed implementation
- `camel-persistence-ic4j` — IC4J/ICP-backed implementation using the ICP Camel component
- `camel-persistence-testkit` — backend contract test base (`FlowStateStoreContractSuite`)

## Version

Current stable release: `1.3.0`

Canonical root artifact (parent POM): `io.dscope.camel:camel-persistence:1.3.0`

Canonical Maven package URL (PURL): `pkg:maven/io.dscope.camel/camel-persistence`

## Build & Install (local Maven)

From repository root:

```bash
mvn clean test install
```

This runs the reactor tests, packages each module, and installs artifacts under `~/.m2/repository/io/dscope/camel/...`.
Redis-backed tests use `redis://localhost:6379` by default when Redis is running locally.

## Developer Commands

From repository root:

```bash
# Full verification and local install
mvn clean test install

# Test only
mvn test

# Build one module with reactor dependencies
mvn -pl camel-persistence-core -am test
```

## Dependency Examples (for other projects)

Use `core` plus one backend module (`jdbc`, `redis`, or `ic4j`).

### Maven

```xml
<dependency>
  <groupId>io.dscope.camel</groupId>
  <artifactId>camel-persistence-core</artifactId>
  <version>1.3.0</version>
</dependency>

<dependency>
  <groupId>io.dscope.camel</groupId>
  <artifactId>camel-persistence-jdbc</artifactId>
  <version>1.3.0</version>
</dependency>
```

### Maven (Redis backend)

```xml
<dependency>
  <groupId>io.dscope.camel</groupId>
  <artifactId>camel-persistence-core</artifactId>
  <version>1.3.0</version>
</dependency>

<dependency>
  <groupId>io.dscope.camel</groupId>
  <artifactId>camel-persistence-redis</artifactId>
  <version>1.3.0</version>
</dependency>
```

### Maven (IC4J / ICP backend)

```xml
<dependency>
  <groupId>io.dscope.camel</groupId>
  <artifactId>camel-persistence-core</artifactId>
  <version>1.3.0</version>
</dependency>

<dependency>
  <groupId>io.dscope.camel</groupId>
  <artifactId>camel-persistence-ic4j</artifactId>
  <version>1.3.0</version>
</dependency>
```

For Redis cache plus IC4J durable persistence, include `camel-persistence-core`, `camel-persistence-redis`, and `camel-persistence-ic4j`, then set `camel.persistence.backend=redis_ic4j`.

### Gradle (Groovy DSL)

```groovy
dependencies {
  implementation 'io.dscope.camel:camel-persistence-core:1.3.0'
  implementation 'io.dscope.camel:camel-persistence-jdbc:1.3.0'
}
```

For Redis backend:

```groovy
dependencies {
  implementation 'io.dscope.camel:camel-persistence-core:1.3.0'
  implementation 'io.dscope.camel:camel-persistence-redis:1.3.0'
}
```

For IC4J / ICP backend:

```groovy
dependencies {
  implementation 'io.dscope.camel:camel-persistence-core:1.3.0'
  implementation 'io.dscope.camel:camel-persistence-ic4j:1.3.0'
}
```

### Gradle (Kotlin DSL)

```kotlin
dependencies {
  implementation("io.dscope.camel:camel-persistence-core:1.3.0")
  implementation("io.dscope.camel:camel-persistence-jdbc:1.3.0")
}
```

For Redis backend:

```kotlin
dependencies {
  implementation("io.dscope.camel:camel-persistence-core:1.3.0")
  implementation("io.dscope.camel:camel-persistence-redis:1.3.0")
}
```

For IC4J / ICP backend:

```kotlin
dependencies {
  implementation("io.dscope.camel:camel-persistence-core:1.3.0")
  implementation("io.dscope.camel:camel-persistence-ic4j:1.3.0")
}
```

## Configuration Properties

Core keys resolved by `PersistenceConfiguration.fromProperties(...)`:

- `camel.persistence.enabled` (default: `false`)
- `camel.persistence.backend` (default: `redis`; values: `redis`, `jdbc`, `redis_jdbc`, `redis_ic4j`, `ic4j`)
- `camel.persistence.snapshot-every-events` (default: `25`)
- `camel.persistence.max-replay-events` (default: `500`)
- `camel.persistence.read-batch-size` (default: `200`)
- `camel.persistence.redis.uri` (default: `redis://localhost:6379`)
- `camel.persistence.redis.key-prefix` (default: `camel:state`)
- `camel.persistence.jdbc.url` (default: `jdbc:derby:memory:camelPersistence;create=true`)
- `camel.persistence.jdbc.user` (default: empty)
- `camel.persistence.jdbc.password` (default: empty)
- `camel.persistence.icp.replica-url` (default: `http://127.0.0.1:4943/`)
- `camel.persistence.icp.canister-id` (required for `ic4j` and `redis_ic4j`)
- `camel.persistence.icp.fetch-root-key` (default: `true`; use `false` for mainnet)
- `camel.persistence.icp.load-idl` (default: `true`)
- `camel.persistence.icp.idl-file` (default: empty)
- `camel.persistence.icp.waiter-timeout` (default: `60`)
- `camel.persistence.icp.waiter-sleep` (default: `5`)

## Quick Start

```java
import io.dscope.camel.persistence.core.FlowStateStore;
import io.dscope.camel.persistence.core.FlowStateStoreFactory;
import io.dscope.camel.persistence.core.PersistenceConfiguration;

import java.util.Properties;

Properties props = new Properties();
props.setProperty("camel.persistence.enabled", "true");
props.setProperty("camel.persistence.backend", "jdbc");
props.setProperty("camel.persistence.jdbc.url", "jdbc:derby:memory:camelPersistence;create=true");

PersistenceConfiguration configuration = PersistenceConfiguration.fromProperties(props);
FlowStateStore store = FlowStateStoreFactory.create(configuration);

// Example read
var rehydrated = store.rehydrate("order", "order-123");
```

### Quick Start (Redis)

```java
import io.dscope.camel.persistence.core.FlowStateStore;
import io.dscope.camel.persistence.core.FlowStateStoreFactory;
import io.dscope.camel.persistence.core.PersistenceConfiguration;

import java.util.Properties;

Properties props = new Properties();
props.setProperty("camel.persistence.enabled", "true");
props.setProperty("camel.persistence.backend", "redis");
props.setProperty("camel.persistence.redis.uri", "redis://localhost:6379");
props.setProperty("camel.persistence.redis.key-prefix", "camel:state");

PersistenceConfiguration configuration = PersistenceConfiguration.fromProperties(props);
FlowStateStore store = FlowStateStoreFactory.create(configuration);

// Example read
var rehydrated = store.rehydrate("order", "order-123");
```

### Quick Start (IC4J / ICP)

```java
import io.dscope.camel.persistence.core.FlowStateStore;
import io.dscope.camel.persistence.core.FlowStateStoreFactory;
import io.dscope.camel.persistence.core.PersistenceConfiguration;

import java.util.Properties;

Properties props = new Properties();
props.setProperty("camel.persistence.enabled", "true");
props.setProperty("camel.persistence.backend", "ic4j");
props.setProperty("camel.persistence.icp.replica-url", "http://127.0.0.1:4943/");
props.setProperty("camel.persistence.icp.canister-id", "<dfx-canister-id>");

PersistenceConfiguration configuration = PersistenceConfiguration.fromProperties(props);
FlowStateStore store = FlowStateStoreFactory.create(configuration);

var rehydrated = store.rehydrate("order", "order-123");
```

## Backend Notes

- Provider resolution uses Java `ServiceLoader`; backend modules register `FlowStateStoreProvider` via `META-INF/services`.
- `ic4j` uses `org.ic4j:ic4j-camel-core:0.8.2` and expects a canister implementing the Candid contract in `camel-persistence-ic4j/src/main/icp/persistence.did`.
- `redis_ic4j` uses Redis as a best-effort cache over IC4J/ICP as the durable backend.

## Testing

```bash
mvn test
```

Redis contract tests expect Redis reachable at `redis://localhost:6379` by default.
You can override with:

```bash
mvn -Dcamel.persistence.test.redis.uri=redis://<host>:<port> test
```
