# Implementation Status

## Project

Java Rest Framework (JRF)

## Specification

```text
v0.1
```

## Current Milestone

```text
M0 — Repository Foundation
```

## Current Slice

```text
M0 foundation implemented and verified locally; CI run pending
```

## Status

```text
M0 LOCALLY VERIFIED — waiting for first CI run on GitHub
```

---

## Completed

### M0 — Repository Foundation

```text
[x] Confirm repository structure (modules/, tools/, integrations/, examples/, docs/)
[x] Decide internal bootstrap build tool: Gradle (Kotlin DSL) + Gradle Wrapper 9.7.1
[x] Define Java baseline for implementation: Java 21 (Gradle toolchain)
[x] Configure root build: 22 projects registered in settings.gradle.kts
[x] Shared conventions: build-logic/ plugin jrf.java-conventions
[x] Configure checks: javac -Xlint:all -Werror, :jrf-core:verifyCoreIsolation
[x] Configure test execution: JUnit Jupiter 6.1.3 (gradle/libs.versions.toml)
[x] Smoke test: jrf.BuildInfrastructureSmokeTest (jrf-core, no runtime behavior)
[x] Configure CI: .github/workflows/build.yml (./gradlew build on Java 21)
[x] Verify module dependency boundaries (jrf-core isolated; explicit project dependencies)
[x] Code conventions documented: docs/DEVELOPMENT.md
[x] .gitignore / .gitattributes / .editorconfig updated for Gradle and wrapper scripts
```

Intentional project dependencies declared so far:

```text
jrf-tuprel    -> api(jrf-database)
hello-api     -> implementation(jrf-core)
bookstore-api -> implementation(jrf-core)
```

All modules other than the smoke test contain no source code yet.

---

## In Progress

None.

---

## Remaining M0 Work

```text
[ ] Push to the GitHub remote and confirm the "build" workflow passes (acceptance: "CI runs")
```

---

## Not Started

### M1 — Core Runtime

```text
[ ] Jrf
[ ] Application
[ ] DefaultApplication
[ ] ApplicationState
[ ] ApplicationContext
[ ] DefaultApplicationContext
[ ] Bootstrap
[ ] LifecycleManager
[ ] shutdown handling
```

Do not start M1 until M0 acceptance criteria are satisfied.

---

## Tests

```text
./gradlew build                                         PASSED (JDK 21.0.12, Windows 11)
  jrf.BuildInfrastructureSmokeTest                      1 test, PASSED
  :jrf-core:verifyCoreIsolation                         PASSED
Negative check: temporary jrf-core -> jrf-http dependency  verifyCoreIsolation FAILED as expected
Negative check: temporary failing JUnit test               :jrf-http:test FAILED as expected
CI workflow                                             NOT EXECUTED — no Git remote configured
```

---

## Known Issues

```text
- docs/REPOSITORY_STRUCTURE.md ("Regra importante") still says not to add a build root;
  superseded by the owner's Gradle decision. Needs owner-approved update.
- No automatic code formatter is enforced; formatting relies on .editorconfig.
- Local builds on low-memory machines may crash the Kotlin daemon while compiling build-logic;
  workaround: ./gradlew build -Dkotlin.compiler.execution.strategy=in-process
```

---

## Architectural Decisions Requiring Future Confirmation

```text
- final framework name
- final Java package/group namespace
- final minimum Java version for the first public release
- automatic formatter (if any)
```

These decisions must not be guessed silently.

---

## Last Verified Commit

```text
d14861f build: establish repository foundation
```

---

## Working Tree

Expected handoff state:

```text
clean
```

Every coding session must verify this using:

```bash
git status
```

---

## Next Recommended Action

```text
1. Add the GitHub remote, push, and confirm the "build" workflow passes. This closes M0.
2. Then start M1 — Core Runtime, Slice 1: jrf.application.ApplicationState in modules/jrf-core.
```

Read before M1:

```text
docs/CORE_ARCHITECTURE.md
docs/IMPLEMENTATION_ROADMAP.md (section 5)
docs/DEVELOPMENT.md
```

---

## Update Rule

At the end of each completed slice, update only the facts that changed:

```text
Current Milestone
Current Slice
Completed
In Progress
Not Started
Tests
Known Issues
Last Verified Commit
Next Recommended Action
```

Keep this document concise. It is a handoff state file, not a development diary.
