# Implementation Status

## Project

Dhole Framework

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
M0 complete — no slice in progress
```

## Status

```text
COMPLETE
```

---

## Completed

### M0 — Repository Foundation

```text
[x] Confirm repository structure (modules/, tools/, integrations/, examples/, docs/)
[x] Decide internal bootstrap build tool: Gradle (Kotlin DSL) + Gradle Wrapper 9.7.1
[x] Define Java baseline for implementation: Java 21 (Gradle toolchain)
[x] Configure root build: 22 projects registered in settings.gradle.kts
[x] Shared conventions: build-logic/ plugin org.dhole.java-conventions
[x] Configure checks: javac -Xlint:all -Werror, :dhole-core:verifyCoreIsolation
[x] Configure test execution: JUnit Jupiter 6.1.3 (gradle/libs.versions.toml)
[x] Smoke test: org.dhole.BuildInfrastructureSmokeTest (dhole-core, no runtime behavior)
[x] Configure CI: .github/workflows/build.yml (./gradlew build on Java 21)
[x] Verify module dependency boundaries (dhole-core isolated; explicit project dependencies)
[x] Code conventions documented: docs/DEVELOPMENT.md
[x] .gitignore / .gitattributes / .editorconfig updated for Gradle and wrapper scripts
[x] Remote repository configured and pushed: https://github.com/dholeframework/dhole
[x] GitHub Actions CI ("build" workflow) passed on main
```

### Project identity migration

```text
[x] Project renamed to Dhole Framework (namespace org.dhole, CLI dhole, domain dhole.org)
[x] Directories and Gradle projects renamed jrf-* -> dhole-* (root project: dhole)
[x] Convention plugin renamed to org.dhole.java-conventions
[x] Java packages moved from jrf.* to org.dhole.*
[x] Specification references updated: dhole.toml, dhole.lock, .dhole/, META-INF/dhole/, DHOLE- diagnostics
[x] docs/REPOSITORY_STRUCTURE.md documents the internal Gradle build
[x] .gitignore: source packages named "build" are no longer ignored; IDE bin/ output at project roots ignored
```

Intentional project dependencies declared so far:

```text
dhole-tuprel  -> api(dhole-database)
hello-api     -> implementation(dhole-core)
bookstore-api -> implementation(dhole-core)
```

All modules other than the smoke test contain no source code yet.

---

## In Progress

None.

---

## Remaining M0 Work

```text
None.
```

---

## Not Started

### M1 — Core Runtime

Status: NOT STARTED

```text
[ ] Dhole
[ ] Application
[ ] DefaultApplication
[ ] ApplicationState
[ ] ApplicationContext
[ ] DefaultApplicationContext
[ ] Bootstrap
[ ] LifecycleManager
[ ] shutdown handling
```

M0 acceptance criteria are satisfied; M1 may start with Slice 1.

---

## Tests

```text
M0 verification summary:
  Local Gradle build                                    PASSED
  Smoke test (org.dhole.BuildInfrastructureSmokeTest)   PASSED
  dhole-core isolation verification                     PASSED
  GitHub Actions CI ("build" workflow, main)            PASSED on 24fe83e chore: rename project to Dhole

After rename to Dhole (JDK 21.0.12, Windows 11):
./gradlew clean build -Dkotlin.compiler.execution.strategy=in-process --warning-mode all --rerun-tasks
                                                        BUILD SUCCESSFUL, 58 tasks executed, no deprecation warnings
  org.dhole.BuildInfrastructureSmokeTest                1 test, PASSED
  :dhole-core:verifyCoreIsolation                       PASSED
Negative check: temporary dhole-core -> dhole-http dependency  verifyCoreIsolation FAILED as expected
Negative check (M0): temporary failing JUnit test      test task FAILED as expected
```

---

## Known Issues

```text
- No automatic code formatter is enforced; formatting relies on .editorconfig.
- Local builds on low-memory machines may crash the Kotlin daemon while compiling build-logic;
  workaround: ./gradlew build -Dkotlin.compiler.execution.strategy=in-process
- On this development machine, builds fail from host memory exhaustion while the VS Code Java
  extension (language servers + its own Gradle daemons) is running; close it before building.
```

---

## Architectural Decisions Requiring Future Confirmation

```text
- final minimum Java version for the first public release
- automatic formatter (if any)
```

These decisions must not be guessed silently.

---

## Last Verified Commit

```text
24fe83e chore: rename project to Dhole   (local build + GitHub Actions CI)
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
M1 — Core Runtime
Slice 1 — ApplicationState (org.dhole.application.ApplicationState in modules/dhole-core)
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
