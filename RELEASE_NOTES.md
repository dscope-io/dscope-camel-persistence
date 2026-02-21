# Release Notes

## v1.1.0 (2026-02-20)

### Highlights
- Updated release version from `1.0.2` to `1.1.0` across root and module POMs.
- Added composite persistence backend mode `redis_jdbc` (Redis primary, JDBC fallback/source of truth).
- Updated consumer dependency examples in `README.md` to `1.1.0`.

### Modules in this release
- `io.dscope.camel:camel-persistence:1.1.0` (parent POM)
- `io.dscope.camel:camel-persistence-core:1.1.0`
- `io.dscope.camel:camel-persistence-testkit:1.1.0`
- `io.dscope.camel:camel-persistence-redis:1.1.0`
- `io.dscope.camel:camel-persistence-jdbc:1.1.0`
- `io.dscope.camel:camel-persistence-ic4j:1.1.0`

### Validation
- Core module tests passed for composite store behavior and backend parsing.
- `REDIS_JDBC` fallback behavior validated with unit tests.

### Notes
- Publish this version with the standard Central flow before sharing dependency coordinates.

## v1.0.2 (2026-02-20)

### Highlights
- Updated release version from `1.0.0` to `1.0.2` across root and module POMs.
- Corrected Maven Central deployment identity to canonical root artifact: `io.dscope.camel:camel-persistence`.
- Updated consumer dependency examples in `README.md` to `1.0.2`.

### Modules in this release
- `io.dscope.camel:camel-persistence:1.0.2` (parent POM)
- `io.dscope.camel:camel-persistence-core:1.0.2`
- `io.dscope.camel:camel-persistence-testkit:1.0.2`
- `io.dscope.camel:camel-persistence-redis:1.0.2`
- `io.dscope.camel:camel-persistence-jdbc:1.0.2`
- `io.dscope.camel:camel-persistence-ic4j:1.0.2`

### Validation
- Reactor build completed successfully with Central release deploy flow.
- Sonatype Central deployment validated successfully with deployment ID:
	- `8411f557-df12-4fe8-b204-e59224630f2c`
- Deployment name used:
	- `io.dscope.camel:camel-persistence:1.0.2`

### Notes
- Maven Central search indexing may take additional time after validation/publish.

## v1.0.0 (2026-02-18)

### Highlights
- Promoted Camel Persistence modules to stable `1.0.0`.
- Updated root and module parent Maven versions from `0.1.0-SNAPSHOT` to `1.0.0`.

### Modules in this release
- `io.dscope.camel:camel-persistence:1.0.0` (parent POM)
- `io.dscope.camel:camel-persistence-core:1.0.0`
- `io.dscope.camel:camel-persistence-testkit:1.0.0`
- `io.dscope.camel:camel-persistence-redis:1.0.0`
- `io.dscope.camel:camel-persistence-jdbc:1.0.0`
- `io.dscope.camel:camel-persistence-ic4j:1.0.0`

### Validation
- Reactor build completed successfully with `mvn clean install`.
- Artifacts were installed to local Maven repository (`~/.m2/repository`).

### Notes
- This is a release draft and can be copied into a GitHub/GitLab release description.
