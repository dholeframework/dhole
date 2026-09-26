# Development

This document covers how the Dhole repository itself is built and the code conventions used inside it.
It does not describe how Dhole applications are built (see `BUILD_SYSTEM.md`).

## Internal build tool

Decision (M0, repository owner): the Dhole repository is built with **Gradle (Kotlin DSL)** through the committed Gradle Wrapper.

- Gradle is only the bootstrap build for developing Dhole itself.
- It does not change the product goal: Dhole applications are built with `dhole build`, `dhole run` and `dhole dev`, without user-maintained `pom.xml` or `build.gradle` files.
- No artifacts are published yet.

Layout:

```text
settings.gradle.kts                  project list (modules/, tools/, integrations/, examples/)
gradle/libs.versions.toml            dependency versions
build-logic/                         shared convention plugin: org.dhole.java-conventions
<project>/build.gradle.kts           applies org.dhole.java-conventions and declares project dependencies
```

Project names equal directory names, for example `:dhole-core`, `:dhole-cli`, `:hello-api`.

## Commands

```bash
./gradlew build                  # compile, test and run all checks
./gradlew test                   # run all tests
./gradlew :dhole-core:test         # run tests of one project
```

On Windows use `gradlew.bat`.

## Dhole distribution

The installable `dhole` command (CLI.md §17) is assembled by the repository build:

```bash
./gradlew :dhole-cli:dholeDistribution      # tools/dhole-cli/build/dhole/{bin,lib}
./gradlew :dhole-cli:dholeDistributionZip   # tools/dhole-cli/build/distributions/dhole-<version>.zip
```

`lib/dhole-distribution.idx` catalogs the bundled artifacts that applications may select (BUILD_SYSTEM.md §21). The `:dhole-cli` tests drive this distribution through `bin/dhole` / `bin/dhole.cmd` as black boxes. User-facing steps: `docs/GETTING_STARTED.md`.

## Java baseline

- Java 21, configured through the Gradle Java toolchain in `org.dhole.java-conventions`.
- The minimum Java version for the first public release is still an open decision.

## Checks

`./gradlew build` enforces:

- compilation with `-Xlint:all -Werror` (all javac warnings are errors);
- all tests passing (JUnit Jupiter);
- `:dhole-core:verifyCoreIsolation`: `dhole-core` must not depend on any other Dhole project.

CI (`.github/workflows/build.yml`) runs `./gradlew build` on Java 21.

## Module dependencies

- Dependencies between Dhole projects are declared explicitly in each `build.gradle.kts` with `api(project(":..."))` or `implementation(project(":..."))`.
- Use `api` only when the dependency's types appear in the project's public API.
- `dhole-core` depends on no other Dhole project.
- New external dependencies require a concrete current need and are declared in `gradle/libs.versions.toml`.

## Code conventions

Formatting:

- `.editorconfig` is authoritative: UTF-8, LF, 4-space indentation, final newline, no trailing whitespace.
- No automatic formatter is enforced yet.

Packages:

- Namespace `org.dhole.*` (for example `org.dhole.application`, `org.dhole.di`, `org.dhole.http`).
- Implementation-only packages use an `internal` segment (for example `org.dhole.di.internal`) and are not plugin API.

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
