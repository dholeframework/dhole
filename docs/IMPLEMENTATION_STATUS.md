# Implementation Status

## Project

Dhole Framework

## Specification

```text
v0.1
```

## Current Milestone

```text
M2 — Configuration
```

## Current Slice

```text
M2 architecture decisions recorded; implementation starting
```

## Status

```text
M0 — Repository Foundation   COMPLETE
M1 — Core Runtime            COMPLETE (local build + GitHub Actions on ad60595)
M2 — Configuration           IN PROGRESS
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

Only dhole-core (M1 runtime) and examples/hello-api (M1 demo application) contain code.

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

Status: COMPLETE

```text
Public API:
[x] org.dhole.Dhole — run(Class<?>): bootstrap, register JVM shutdown hook, start
[x] org.dhole.application.Application — start(), stop(), context(), state()
[x] org.dhole.application.ApplicationContext — public interface, no members yet; see Known Issues
[x] org.dhole.application.ApplicationState — CREATED, STARTING, RUNNING, STOPPING, STOPPED, FAILED

Internal (org.dhole.internal.*, no compatibility guarantees):
[x] application.DefaultApplication — delegates lifecycle; shutdown() for the JVM hook
[x] application.DefaultApplicationContext — package-private
[x] application.ApplicationBuilder — assembles context + LifecycleManager + application
[x] bootstrap.Bootstrap — create(Class<?>).build(): prints "Dhole" banner, assembles application
[x] lifecycle.LifecycleManager — atomic (CAS) transitions and progress output:
    start(): CREATED -> STARTING -> RUNNING ("Application starting...", "Application ready.")
    stop():  RUNNING -> STOPPING -> STOPPED ("Application stopped.")
    other start()/stop() calls: IllegalStateException naming operation and current state
    stopIfRunning(): used by the shutdown hook; stops only from RUNNING, otherwise no-op
[x] examples/hello-api example.hello.App — Dhole.run(App.class); prints the roadmap M1 output

Not created (no M1 responsibility; owner decision): BootstrapContext, BootstrapException,
LifecycleException, StartupTask.

Amended roadmap §5 acceptance, verified against code/tests:
[x] application starts / changes state correctly / cannot start twice / stops
    (LifecycleManagerTest, DefaultApplicationTest)
[x] shutdown hook safe in every state reachable in M1 (CREATED, RUNNING, STOPPED:
    LifecycleManagerTest, DefaultApplicationTest; real JVM exit: DholeTest child process)
[x] Dhole.run() works (DholeTest, example.hello.App run manually)
[x] no HTTP/database/config; dhole-core has no project dependencies (verifyCoreIsolation)
[-] startup failure -> FAILED, shutdown after partial startup: conditional tests, deferred
    (no real fallible startup operation in M1); no artificial failure source introduced
```

---

## Tests

```text
M1 final (JDK 21.0.12, Windows 11, no VS Code running):
./gradlew clean build -Dkotlin.compiler.execution.strategy=in-process --warning-mode all
                                                        BUILD SUCCESSFUL, 59 tasks (50 executed), no warnings
  org.dhole.internal.application.DefaultApplicationTest 12 tests, PASSED
  org.dhole.internal.lifecycle.LifecycleManagerTest      9 tests, PASSED
  org.dhole.internal.application.ApplicationBuilderTest  4 tests, PASSED
  org.dhole.internal.bootstrap.BootstrapTest             3 tests, PASSED
  org.dhole.DholeTest                                    3 tests, PASSED (incl. child-JVM shutdown hook)
  org.dhole.application.ApplicationStateTest             1 test, PASSED
  org.dhole.BuildInfrastructureSmokeTest                 1 test, PASSED
  total                                                 33 tests, PASSED
  :dhole-core:verifyCoreIsolation                       PASSED
java -cp modules/dhole-core/build/classes/java/main;examples/hello-api/build/classes/java/main example.hello.App
  output: Dhole / (blank) / Application starting... / Application ready. / Application stopped.   exit 0

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
- FAILED remains in the lifecycle model but is unreachable in M1: no real fallible startup
  operation exists. The FAILED transition, rollback and the "startup failure -> FAILED" /
  "shutdown after partial startup" tests are mandatory in the first milestone that introduces
  a real fallible startup operation (not necessarily M4). No artificial failure source exists.
- Public StartupTask API and registration remain deferred (CORE_ARCHITECTURE.md §40, §64).
- Application.stop() stays strict (RUNNING only). The JVM shutdown hook uses the internal
  stopIfRunning() and is a no-op outside RUNNING. A JVM shutdown while start() is still in
  STARTING does not stop the application; M1 has nothing to release, so this is harmless now.
- Bootstrap.create(Class<?>) only validates the application class; the application root
  (CORE_ARCHITECTURE.md §9) has no M1 consumer and is not computed yet.
- M1 progress output is plain text on System.out (roadmap §5 "Saída"); structured
  logging belongs to observability (M12).
- DholeTest.runBootstrapsAndStartsTheApplication registers a real shutdown hook in the test
  JVM; it stops that application when the test JVM exits (output goes to a discarded stream).
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

### RESOLVED — M1 startup failure mechanism

```text
Blocker (recorded in 9c7d037): M1 required "startup failure -> FAILED" and "shutdown after
partial startup", but M1 has no fallible startup operation and no public way to register one.
Resolved by owner decision (docs(architecture): defer startup failure integration):
  - FAILED stays in the lifecycle model; it is reached only when a real operation fails;
  - no synthetic failure source is introduced;
  - the two tests become mandatory in the first milestone with a real fallible startup
    operation (IMPLEMENTATION_ROADMAP.md §5 "Testes condicionais");
  - public StartupTask registration stays deferred (CORE_ARCHITECTURE.md §40, §64);
    metadata discovery stays planned for M4 (METADATA_COMPILER.md §10).
```

### Owner decisions for the remaining M1 work

```text
- Bootstrap machinery is internal: ApplicationBuilder, DefaultApplication,
  DefaultApplicationContext, Bootstrap and LifecycleManager live under org.dhole.internal.*
  (CORE_ARCHITECTURE.md §52, §59). Public API stays Dhole, Application, ApplicationContext,
  ApplicationState. Internal types must not appear in public signatures. Types from the
  roadmap sketch are created only when they have a current M1 responsibility.
- No public StartupTask API or registration mechanism in M1.
```

### Owner decisions for M2 — Configuration

```text
- M2 is a standalone configuration subsystem in dhole-config, tested directly. It is not
  wired into Dhole.run(): no core SPI, no ServiceLoader, no alternative entry point,
  dhole-core stays independent of dhole-config. Startup integration (validation before
  RUNNING, configuration failure -> FAILED, safe shutdown after it) becomes mandatory once
  the real module/activation mechanism exists (no milestone assigned yet).
  (IMPLEMENTATION_ROADMAP.md §6)
- Environment is configuration API: org.dhole.config.Environment in dhole-config.
  ApplicationContext gets no environment()/settings() in M2; no bridging abstraction.
  (CORE_ARCHITECTURE.md §10, §21, §59)
- Settings lookup: exactly <application package>.config.Settings via the application class's
  class loader, requiring public static void configure(SettingsBuilder); absent class = no
  error; no scanning. (CONFIGURATION.md §2)
- Production (APP_ENV=production): .env not read; secrets must come from the process
  environment (no default, no .env); non-secret defaults allowed. APP_ENV must be exactly
  development, test or production; absent -> development. (CONFIGURATION.md §4, §5, §10)
```

---

## Last Verified Commit

```text
24fe83e chore: rename project to Dhole   (local build + GitHub Actions CI)
ad60595 docs(status): mark M1 complete locally   (local clean build + GitHub Actions "build" run 36237735431, success)
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
M2 — Configuration
```

Read before M2:

```text
docs/CONFIGURATION.md
docs/IMPLEMENTATION_ROADMAP.md (section 6)
docs/CORE_ARCHITECTURE.md (sections 10, 11)
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
