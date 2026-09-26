# Implementation Status

## Project

Dhole Framework

## Specification

```text
v0.1
```

## Current Milestone

```text
M1 — Core Runtime
```

## Current Slice

```text
Slice 4 — ApplicationContext (complete)
```

## Status

```text
M0 — Repository Foundation   COMPLETE
M1 — Core Runtime            IN PROGRESS — BLOCKED (Slices 1–4 complete; see BLOCKER)
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

## M1 Progress

### M1 — Core Runtime

Status: IN PROGRESS

```text
[ ] Dhole
[x] Application — start(), stop(), context(), state() (Slice 2; context() added in Slice 4)
[x] DefaultApplication — package-private final; basic transitions (Slice 3):
    CREATED -> STARTING -> RUNNING on start(), RUNNING -> STOPPING -> STOPPED on stop();
    other calls throw IllegalStateException; transitions claimed atomically (AtomicReference CAS)
    FAILED transition deferred until a fallible startup/shutdown operation exists; see Known Issues
[x] ApplicationState — org.dhole.application.ApplicationState + ApplicationStateTest (Slice 1)
[x] ApplicationContext — public interface, no members yet (Slice 4); see Known Issues
[x] DefaultApplicationContext — package-private final (Slice 4)
[ ] Bootstrap
[ ] LifecycleManager
[ ] shutdown handling
```

---

## Tests

```text
M1 Slice 4 (JDK 21.0.12, Windows 11):
./gradlew :dhole-core:check -Dkotlin.compiler.execution.strategy=in-process --warning-mode all
                                                        BUILD SUCCESSFUL
  org.dhole.application.DefaultApplicationTest          9 tests, PASSED
  org.dhole.application.ApplicationStateTest            1 test, PASSED
  org.dhole.BuildInfrastructureSmokeTest                1 test, PASSED
  :dhole-core:verifyCoreIsolation                       PASSED

M1 Slice 3 (JDK 21.0.12, Windows 11):
./gradlew :dhole-core:check -Dkotlin.compiler.execution.strategy=in-process --warning-mode all
                                                        BUILD SUCCESSFUL, no compiler warnings
  org.dhole.application.DefaultApplicationTest          7 tests, PASSED
  org.dhole.application.ApplicationStateTest            1 test, PASSED
  org.dhole.BuildInfrastructureSmokeTest                1 test, PASSED
  :dhole-core:verifyCoreIsolation                       PASSED
./gradlew build -Dkotlin.compiler.execution.strategy=in-process --warning-mode all
                                                        BUILD SUCCESSFUL, 37 tasks (2 executed)

M1 Slice 2 (JDK 21.0.12, Windows 11):
./gradlew :dhole-core:check -Dkotlin.compiler.execution.strategy=in-process --warning-mode all
                                                        BUILD SUCCESSFUL
  no new test: Application is a behavior-free interface; lifecycle behavior
  tests belong to DefaultApplication (Slice 3)
./gradlew build -Dkotlin.compiler.execution.strategy=in-process --warning-mode all
                                                        BUILD SUCCESSFUL

M1 Slice 1 (JDK 21.0.12, Windows 11):
./gradlew :dhole-core:check -Dkotlin.compiler.execution.strategy=in-process --warning-mode all
                                                        BUILD SUCCESSFUL
  org.dhole.application.ApplicationStateTest            1 test, PASSED
  org.dhole.BuildInfrastructureSmokeTest                1 test, PASSED
  :dhole-core:verifyCoreIsolation                       PASSED
./gradlew build -Dkotlin.compiler.execution.strategy=in-process --warning-mode all
  in the working copy                                   FAILED in :build-logic:compileKotlin — a concurrent VS Code
                                                        Gradle build was rewriting build-logic/build (not a code error)
  in an isolated clone with the Slice 1 files            BUILD SUCCESSFUL, 37 tasks executed, no warnings

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
- ApplicationContext declares no members yet. Every member in CORE_ARCHITECTURE.md §21
  (environment, settings, modules, plugins, lifecycle) depends on a type from a later milestone;
  each is added when its type exists.
- DefaultApplication never enters FAILED yet: no fallible startup/shutdown operation exists.
  The FAILED transition, rollback and "startup failure -> FAILED" / "shutdown after partial
  startup" tests are deferred to the slice that introduces the first fallible operation.
- DefaultApplication.stop() is only valid from RUNNING. Shutdown-hook behaviour for other
  states (CORE_ARCHITECTURE.md §41) is not decided yet and belongs to the shutdown handling slice.
- No automatic code formatter is enforced; formatting relies on .editorconfig.
- Local builds on low-memory machines may crash the Kotlin daemon while compiling build-logic;
  workaround: ./gradlew build -Dkotlin.compiler.execution.strategy=in-process
- On this development machine, builds fail from host memory exhaustion while the VS Code Java
  extension (language servers + its own Gradle daemons) is running; close it before building.
- While VS Code is open, its Gradle build can race with command-line builds on build-logic/build
  (stale or missing Kotlin DSL accessors). Close VS Code, then run ./gradlew -p build-logic clean.
```

---

## Architectural Decisions Requiring Future Confirmation

```text
- final minimum Java version for the first public release
- automatic formatter (if any)
```

These decisions must not be guessed silently.

### BLOCKER — M1 startup failure mechanism (open, awaiting owner decision)

```text
M1 requires real startup-failure behaviour and tests ("startup failure -> FAILED",
"shutdown after partial startup"), but the specification does not define a public M1
mechanism for registering fallible startup work:
  - StartupTask registration is defined only through metadata discovery
    (METADATA_COMPILER.md §10), which belongs to M4;
  - CORE_ARCHITECTURE.md §64 still lists the startup task API as an open decision;
  - Dhole.run(App.class) offers no way to register fallible work in M1.
M1 implementation is stopped at 2d3f6fa (Slice 4) until an architecture decision is made.
M1 acceptance criteria are unchanged. No StartupTask API has been added.
```

### Owner decisions already recorded for the remaining M1 work

```text
- Bootstrap machinery is internal: Bootstrap, BootstrapContext, ApplicationBuilder,
  DefaultApplication, DefaultApplicationContext and LifecycleManager move to
  org.dhole.internal.* (CORE_ARCHITECTURE.md §52, §59). Public API stays Dhole,
  Application, ApplicationContext, ApplicationState, and org.dhole.lifecycle types only
  where the specification requires them. Internal types must not appear in public signatures.
- If StartupTask is the M1 fallible operation: the §40 contract is used unchanged, tasks run in
  registration order, a throwing task stops startup, sets FAILED and is surfaced through the
  lifecycle exception with the original cause; stop/shutdown after a failed partial startup is
  safe and raises no further lifecycle failure; no module rollback is claimed in M1.
  This applies only once a registration mechanism is decided (see BLOCKER).
```

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
BLOCKED — wait for the owner's decision on the M1 startup failure mechanism
(see "BLOCKER" above). Do not continue M1 implementation and do not start M2 until then.
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
