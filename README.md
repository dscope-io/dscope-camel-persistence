# Camel Persistence

`camel-persistence` is a modular persistence layer for Camel-style flow state, with pluggable backends.

[![version](https://img.shields.io/badge/version-1.0.0-brightgreen)](https://github.com/dscope-io/dscope-camel-persistence/releases/tag/v1.0.0)
[![license](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)
[![java](https://img.shields.io/badge/java-21-orange)](https://openjdk.org/)

**Description:** Modular flow-state persistence for Apache Camel with pluggable JDBC and Redis backends.

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
- `camel-persistence-ic4j` — IC4J provider scaffold (currently not implemented)
- `camel-persistence-testkit` — backend contract test base (`FlowStateStoreContractSuite`)

## Version

Current stable release: `1.0.0`

## Build & Install (local Maven)

From repository root:

```bash
mvn clean install
```

This installs artifacts under `~/.m2/repository/io/dscope/camel/...`.

## Dependency Examples

Use `core` plus one backend module.

```xml
<dependency>
  <groupId>io.dscope.camel</groupId>
  <artifactId>camel-persistence-core</artifactId>
  <version>1.0.0</version>
</dependency>

<dependency>
  <groupId>io.dscope.camel</groupId>
  <artifactId>camel-persistence-jdbc</artifactId>
  <version>1.0.0</version>
</dependency>
```

For Redis backend, replace `camel-persistence-jdbc` with `camel-persistence-redis`.

## Configuration Properties

Core keys resolved by `PersistenceConfiguration.fromProperties(...)`:

- `camel.persistence.enabled` (default: `false`)
- `camel.persistence.backend` (default: `redis`; values: `redis`, `jdbc`, `ic4j`)
- `camel.persistence.snapshot-every-events` (default: `25`)
- `camel.persistence.max-replay-events` (default: `500`)
- `camel.persistence.read-batch-size` (default: `200`)
- `camel.persistence.redis.uri` (default: `redis://localhost:6379`)
- `camel.persistence.redis.key-prefix` (default: `camel:state`)
- `camel.persistence.jdbc.url` (default: `jdbc:derby:memory:camelPersistence;create=true`)
- `camel.persistence.jdbc.user` (default: empty)
- `camel.persistence.jdbc.password` (default: empty)

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

## Backend Notes

- Provider resolution uses Java `ServiceLoader`; backend modules register `FlowStateStoreProvider` via `META-INF/services`.
- `ic4j` provider currently throws `BackendUnavailableException` (scaffold only).

## Testing

```bash
mvn test
```

Redis contract tests expect Redis reachable at `redis://localhost:6379` by default.
You can override with:

```bash
mvn -Dcamel.persistence.test.redis.uri=redis://<host>:<port> test
```
