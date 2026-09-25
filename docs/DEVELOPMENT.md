# Development

This document covers how the JRF repository itself is built and the code conventions used inside it.
It does not describe how JRF applications are built (see `BUILD_SYSTEM.md`).

## Internal build tool

Decision (M0, repository owner): the JRF repository is built with **Gradle (Kotlin DSL)** through the committed Gradle Wrapper.

- Gradle is only the bootstrap build for developing JRF itself.
- It does not change the product goal: JRF applications are built with `jrf build`, `jrf run` and `jrf dev`, without user-maintained `pom.xml` or `build.gradle` files.
- No artifacts are published until the final group/package namespace is decided.

Layout:

```text
settings.gradle.kts                  project list (modules/, tools/, integrations/, examples/)
gradle/libs.versions.toml            dependency versions
build-logic/                         shared convention plugin: jrf.java-conventions
<project>/build.gradle.kts           applies jrf.java-conventions and declares project dependencies
```

Project names equal directory names, for example `:jrf-core`, `:jrf-cli`, `:hello-api`.

## Commands

```bash
./gradlew build                  # compile, test and run all checks
./gradlew test                   # run all tests
./gradlew :jrf-core:test         # run tests of one project
```

On Windows use `gradlew.bat`.

## Java baseline

- Java 21, configured through the Gradle Java toolchain in `jrf.java-conventions`.
- The minimum Java version for the first public release is still an open decision.

## Checks

`./gradlew build` enforces:

- compilation with `-Xlint:all -Werror` (all javac warnings are errors);
- all tests passing (JUnit Jupiter);
- `:jrf-core:verifyCoreIsolation`: `jrf-core` must not depend on any other JRF project.

CI (`.github/workflows/build.yml`) runs `./gradlew build` on Java 21.

## Module dependencies

- Dependencies between JRF projects are declared explicitly in each `build.gradle.kts` with `api(project(":..."))` or `implementation(project(":..."))`.
- Use `api` only when the dependency's types appear in the project's public API.
- `jrf-core` depends on no other JRF project.
- New external dependencies require a concrete current need and are declared in `gradle/libs.versions.toml`.

## Code conventions

Formatting:

- `.editorconfig` is authoritative: UTF-8, LF, 4-space indentation, final newline, no trailing whitespace.
- No automatic formatter is enforced yet.

Packages:

- Temporary namespace `jrf.*` (for example `jrf.application`, `jrf.di`, `jrf.http`).
- Implementation-only packages use an `internal` segment (for example `jrf.di.internal`) and are not plugin API.

Visibility:

- Prefer package-private classes and members; make a type public only when it is part of a deliberate API/SPI.
- Classes not designed for extension are `final`.

Nullability:

- Public API does not accept or return `null` unless documented.
- Absent return values use `Optional`.
- Public entry points validate required arguments with `Objects.requireNonNull`.
- No nullability annotation library is used in v0.1.

Tests:

- JUnit Jupiter.
- Test classes are named `<Subject>Test` and live in the same package as the subject.
- Test methods describe behavior in camelCase, for example `applicationCannotStartTwice`.
