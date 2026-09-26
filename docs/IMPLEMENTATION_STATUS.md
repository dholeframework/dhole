# Implementation Status

## Project

Dhole Framework

## Specification

```text
v0.1
```

## Current Milestone

```text
M3 — Component Model + Dependency Injection
```

## Current Slice

```text
M4 architecture decisions recorded; implementation starting
```

## Status

```text
M0 — Repository Foundation   COMPLETE
M1 — Core Runtime            COMPLETE (local build + GitHub Actions on ad60595)
M2 — Configuration           COMPLETE (local build + GitHub Actions on 7f19906)
M3 — Component Model + DI    COMPLETE (local build + GitHub Actions on 0f83bd4)
M4 — Metadata Compiler       IN PROGRESS
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

Code exists in dhole-core (M1), dhole-config (M2), dhole-di (M3) and examples/hello-api (M1 demo).

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

## M2 Progress

### M2 — Configuration (standalone dhole-config)

Status: COMPLETE

```text
Public API (dhole-config):
[x] org.dhole.config.Environment — name(), isDevelopment(), isTest(), isProduction(), get(key)
[x] org.dhole.config.ConfigurationException — messages never contain secret values
[x] org.dhole.config.SettingsBuilder — environment(), app(Consumer<AppSettingsBuilder>)
[x] org.dhole.config.AppSettingsBuilder — name(String), port(int)
[x] org.dhole.config.AppSettings — record (name, port)
[x] org.dhole.config.SettingsRegistry — app()
[x] org.dhole.env.Env — env(name), env(name, default), envInt(name, default), envBool(name, default)

Internal (org.dhole.internal.config):
[x] DotEnvParser — KEY=VALUE, comments, blank lines, literal quotes; rejects malformed lines,
    invalid names, unterminated quotes, duplicates (errors show line and key, never the value)
[x] EnvironmentLoader / DefaultEnvironment — precedence process > .env; APP_ENV resolution;
    production skips .env entirely
[x] EnvResolver — Env backend bound per thread during Settings.configure (ThreadLocal, always
    removed); strict int/boolean conversion; required/default; production secret-default rule;
    records every resolved value and its source (PROCESS, DOT_ENV, DEFAULT)
[x] SecretNames — minimal isolated convention: name is SECRET/PASSWORD/KEY or ends with
    _SECRET/_PASSWORD/_KEY; display mask ********
[x] SettingsLookup — exactly <app package>.config.Settings, public static void configure(SettingsBuilder)
[x] DefaultSettingsBuilder — defaults (name = application class simple name, port 8080);
    validation: app.name not blank, app.port 0..65535; all problems reported together
[x] ConfigurationLoader / LoadedConfiguration — load + validate; masked report ("NAME    value")

Precedence implemented: process environment > .env (development/test only) > Settings defaults.

Roadmap §6 tests, verified:
[x] .env load / environment override / default value / required value missing
[x] invalid integer / invalid boolean / secret masking / production restrictions basic
Amended completion criteria, verified:
[x] typed configuration loaded and validated; precedence works; missing and malformed values
    fail loading; secrets masked; production restrictions enforced; dhole-config tested
    standalone; dhole-core independent of dhole-config (verifyCoreIsolation, no project deps)
[-] startup integration (validation before RUNNING, failure -> FAILED, safe shutdown):
    deferred until the module/activation mechanism exists; FAILED not activated by M2
[-] examples/hello-api unchanged: without runtime integration a Settings.java there would not
    be loaded; the roadmap's first Settings.java is exercised as test fixture
    org.dhole.testapps.roadmap.config.Settings
```

## M3 Progress

### M3 — Component Model + Dependency Injection (standalone dhole-di)

Status: COMPLETE

```text
Public API (dhole-di), the only types users see in M3:
[x] org.dhole.di.DependencyException, CircularDependencyException, AmbiguousDependencyException

Internal (org.dhole.internal.di, package-private). The container is internal API
(CORE_ARCHITECTURE.md §19, DI.md §13) and bindings are declared publicly through settings
(DI.md §7, §9), which needs config/DI integration that does not exist yet:
[x] ComponentDefinition — type, kind (CONSTRUCTOR/FACTORY/INSTANCE), scope, origin, description,
    dependencies, instantiator, ownership; the model M4 metadata can supply instead of reflection
[x] ConstructorDefinitions — the only reflection: exactly one public constructor of a public,
    concrete, top-level/static nested class; no heuristics, no setAccessible, private and
    package-private constructors never used; parameterized dependencies rejected for now
[x] ComponentRegistry — resolution rules: explicit binding > concrete class > single registered
    implementation of an interface/abstract type; none = missing, several = ambiguous
    (candidates sorted by name, never by registration order)
[x] ContainerBuilder + Binding — component(type[, scope]), bind(T).to(Impl) / toInstance(x)
    (not owned) / toOwnedInstance(x) (ownership transferred), provide(T[, scope], factory);
    duplicate bindings are a Binding Conflict
[x] DependencyGraph / DependencyNode / DependencyGraphBuilder — immutable validated DAG, built
    before any instance; cycles, missing, ambiguous and unusable constructors fail with the
    dependency path; dependencyOrder(); render() tree view (CLI format of COMPONENT_MODEL.md §53)
[x] DependencyContainer — resolve(T), graph(T), registeredGraph(), close();
    build() validates all registrations and creates registered singletons dependencies first
[x] Factory / FactoryContext — factory bindings; factories are graph leaves, runtime cycle guard;
    null or wrongly typed results fail; exceptions wrapped with cause

Semantics:
- Scopes: SINGLETON (default, COMPONENT_MODEL.md §27) once per container; PROTOTYPE on every
  resolution. No static state; containers never share instances. REQUEST deferred to HTTP.
- Thread safety: resolutions that create singletons or run factories hold a ReentrantLock
  (no virtual-thread pinning); singletons are published only when their resolution succeeds;
  prototype-only resolutions over existing singletons are lock-free.
- Ownership: owned = AutoCloseable singletons the container created + toOwnedInstance. External
  instances are never closed. Prototypes belong to their receiver.
- close(): reverse creation order (dependents before dependencies), every resource attempted,
  failures reported in one DependencyException with suppressed causes; idempotent.
- Rollback: a failed startup or resolution closes every owned resource it created (prototypes
  included) in reverse order, publishes nothing, rethrows the original failure with cleanup
  failures as suppressed. This is container rollback, not ApplicationState.FAILED (dhole-di is
  not connected to Dhole.run()).

Roadmap §7 tests, verified: simple dependency, nested dependencies, singleton reuse, prototype
recreation, circular dependency, ambiguous interface, explicit binding, factory binding,
AutoCloseable cleanup, startup rollback.
Completion criteria, verified: constructor injection works; no @Autowired/@Inject (no annotations
in dhole-di); dependency graph is inspectable; cycles fail early (graph validation, container
build); container cleanup works.
```

---

## Tests

```text
M3 final (JDK 21.0.12, Windows 11, no VS Code running):
./gradlew clean build -Dkotlin.compiler.execution.strategy=in-process --warning-mode all
                                                        BUILD SUCCESSFUL, 65 tasks (56 executed), no warnings
  dhole-di:     DependencyContainerTest 22, ComponentRegistryTest 18,
                ResourceLifecycleTest 16, DependencyGraphTest 13             69 tests, PASSED
  dhole-config: unchanged M2 suite                                          91 test cases, PASSED
  dhole-core:   unchanged M1 suite                                          33 tests, PASSED
  total                                                                    193 test cases, PASSED
  :dhole-core:verifyCoreIsolation                       PASSED

M2 final (JDK 21.0.12, Windows 11, no VS Code running):
./gradlew clean build -Dkotlin.compiler.execution.strategy=in-process --warning-mode all
                                                        BUILD SUCCESSFUL, 62 tasks (53 executed), no warnings
  dhole-config: ConfigurationLoaderTest 27, EnvTest 22, EnvironmentLoaderTest 21,
                SecretNamesTest 12, DotEnvParserTest 9                     91 test cases, PASSED
  dhole-core:   unchanged M1 suite                                          33 tests, PASSED
  total                                                                    124 test cases, PASSED
  :dhole-core:verifyCoreIsolation                       PASSED

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
- dhole-config is not connected to Dhole.run(); configuration FAILED integration waits for the
  module/activation mechanism (IMPLEMENTATION_ROADMAP.md §6 "Integração no startup").
- Secret classification is a name convention (SecretNames); replace with explicit secret
  metadata when the settings model gains it (for example security.jwt.secret in M10).
- An empty variable value counts as absent: required values fail, defaults apply. A process
  variable defined as empty is still not replaced by .env.
- Default application name (no Settings or no app.name) is the application class's simple name;
  the specification defines no framework default. Default port 8080 follows the spec examples.
- Not implemented in M2 (spec items for later): envLong, envDuration, .env.local and
  .env.<environment> variants, custom typed settings binding (CONFIGURATION.md §12, needs
  M3/M4), `dhole config` CLI (M8). LoadedConfiguration.report() already renders the masked form.
- dhole-di is standalone: not connected to Dhole.run(), no public binding API yet. Public
  settings.bind/provide (DI.md §7, §9) needs config/DI integration via the module mechanism;
  Settings/Environment are not injectable yet.
- Not implemented in M3 (spec items for later): REQUEST scope and scope validation (HTTP),
  Provider<T> lazy access (COMPONENT_MODEL.md §30, mainly for request scope), Optional<T> and
  List<T> injection (§39, §40), generic bindings (§21), named bindings (§22), test overrides
  replace() (§47, M11), Startable/Stoppable callbacks (§32), component origins other than
  APPLICATION/FACTORY (§4), diagnostic codes (DHOLE-DI-xxx).
- Package-private constructors are not injectable until generated factories (M4) can call them
  from the same package.
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

### Owner decisions for M4 — Metadata Compiler

```text
- javac integration: standard annotation processor (no annotations in user code), no class
  loading, no bytecode parsing in v1; diagnostics through Messager; index written with Filer.
- Metadata: versioned text index META-INF/dhole/components.idx ("dhole-metadata 1"), grammar in
  METADATA_COMPILER.md §34.2; internal reader in dhole-di; no dhole-di -> dhole-compiler dependency.
- Runtime reflection only to invoke the recorded constructor; no generated factories in v1
  (package-private constructors stay unsupported, intentional v1 limitation).
- Application root: -Adhole.application=<FQCN> (from dhole.toml [build] main); absent = library
  compilation (no index); never inferred from Dhole.run calls.
- Index every concrete class of the application root as a known provider; constructor problems
  are data, not build errors. Build-time graph validation waits for structural roots (M5+).
  (METADATA_COMPILER.md §34.1)
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
7f19906 docs(status): mark M2 complete locally   (local clean build + GitHub Actions "build" run 36240156613, success)
0f83bd4 docs(status): mark M3 complete locally   (local clean build + GitHub Actions "build" run 36241951070, success)
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
M4 — Metadata Compiler
```

Read before M4:

```text
docs/METADATA_COMPILER.md
docs/COMPONENT_MODEL.md (sections 49-50, 63-65)
docs/IMPLEMENTATION_ROADMAP.md (section 8)
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
