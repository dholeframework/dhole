# Core Architecture

## 1. Objetivo

Este documento define a arquitetura interna base do Dhole Framework.

A finalidade é estabelecer, antes da implementação, como o framework deve:

- iniciar;
- descobrir a aplicação;
- carregar configuração;
- carregar plugins;
- construir metadata;
- construir o dependency graph;
- inicializar módulos;
- registar controllers e routes;
- iniciar infraestrutura;
- receber requests;
- executar shutdown;
- funcionar em development mode.

Este documento é a planta principal do `dhole-core`.

---

## 2. Regra central

A arquitetura deve permitir:

```text
simple outside
structured inside
```

O developer vê:

```java
public class App {

    public static void main(String[] args) {
        Dhole.run(App.class);
    }
}
```

Por dentro, o framework executa um lifecycle explícito e previsível.

---

## 3. Visão geral

```text
Dhole.run(App.class)
        │
        ▼
Bootstrap
        │
        ▼
Project / Runtime Environment
        │
        ▼
Configuration
        │
        ▼
Metadata
        │
        ▼
Plugins
        │
        ▼
Dependency Graph
        │
        ▼
ApplicationContext
        │
        ▼
Modules
        │
        ▼
Routes / HTTP
        │
        ▼
Application Ready
```

---

## 4. Componentes principais

A arquitetura inicial terá estes conceitos:

```text
Dhole
Application
ApplicationBuilder
ApplicationContext
Bootstrap
Environment
Settings
Module
ModuleRegistry
PluginRegistry
ComponentRegistry
MetadataRegistry
DependencyContainer
LifecycleManager
ShutdownManager
```

Nem todos precisam de ser classes públicas.

---

# 5. `Dhole`

`Dhole` é a porta de entrada pública.

Exemplo:

```java
Dhole.run(App.class);
```

Responsabilidade:

```text
receber application root
        ↓
criar bootstrap
        ↓
construir Application
        ↓
executar lifecycle
```

`Dhole` não deve conter toda a lógica do framework.

API conceptual:

```java
public final class Dhole {

    public static void run(Class<?> applicationClass) {
        Application application =
            Bootstrap.create(applicationClass)
                .build();

        application.start();
    }
}
```

---

# 6. `Application`

`Application` representa uma instância Dhole em execução.

Responsabilidades:

- guardar estado do lifecycle;
- possuir `ApplicationContext`;
- iniciar módulos;
- iniciar server;
- controlar shutdown;
- expor informações da aplicação.

API conceptual:

```java
public interface Application {

    void start();

    void stop();

    ApplicationContext context();

    ApplicationState state();
}
```

Estados:

```text
CREATED
STARTING
RUNNING
STOPPING
STOPPED
FAILED
```

Transições inválidas devem ser impedidas.

---

# 7. `ApplicationBuilder`

O bootstrap usa um builder interno para montar a aplicação.

Exemplo conceptual:

```java
Application application = ApplicationBuilder.create()
    .root(App.class)
    .environment(environment)
    .settings(settings)
    .metadata(metadata)
    .plugins(plugins)
    .modules(modules)
    .build();
```

O developer comum não precisa usar `ApplicationBuilder`.

Pode existir uma API pública avançada futuramente para embedding/testing.

---

# 8. `Bootstrap`

`Bootstrap` coordena a preparação da aplicação.

Pipeline oficial:

```text
1. identify application root
2. load project/runtime information
3. load environment
4. load Settings.java
5. load generated metadata
6. discover plugins
7. configure plugins
8. register framework modules
9. build component registry
10. build dependency graph
11. validate configuration
12. validate routes
13. initialize ApplicationContext
14. initialize modules
15. return Application
```

O bootstrap não inicia imediatamente o HTTP server até a aplicação estar validada.

---

# 9. Application root

Ao executar:

```java
Dhole.run(App.class);
```

o package de `App` define por defeito o application root.

Exemplo:

```java
package com.acme.shop;
```

Root:

```text
com.acme.shop
```

Isso ajuda build-time discovery sem scan indiscriminado de todo classpath.

---

# 10. `Environment`

Representa ambiente runtime.

Exemplo conceptual:

```java
public interface Environment {

    String name();

    boolean isDevelopment();

    boolean isTest();

    boolean isProduction();

    Optional<String> get(String key);
}
```

Sources:

```text
process environment
system properties when supported
.env files in development
CLI overrides
test overrides
```

Secrets não são propriedades do `ApplicationContext` expostas indiscriminadamente.

---

# 11. `Settings`

`Settings.java` configura a aplicação.

Durante bootstrap:

```text
Environment
    ↓
Settings loader
    ↓
SettingsBuilder
    ↓
validated configuration objects
```

A configuração resultante deve tornar-se essencialmente imutável após startup.

Exemplo:

```java
AppSettings appSettings =
    context.settings().app();
```

Mudanças runtime não devem alterar configuração arbitrariamente.

Em dev mode, alteração de configuração provoca restart do context.

---

# 12. Generated Metadata

O Dhole não deve depender principalmente de reflection/classpath scanning em runtime.

O build gera metadata.

Possíveis artefactos internos:

```text
META-INF/dhole/application.idx
META-INF/dhole/components.idx
META-INF/dhole/routes.idx
META-INF/dhole/validation.idx
META-INF/dhole/serialization.idx
META-INF/dhole/plugins.idx
```

O formato concreto pode ser binário ou textual.

Não faz parte da API pública.

---

# 13. `MetadataRegistry`

O runtime carrega metadata através de uma abstração:

```java
public interface MetadataRegistry {

    List<ComponentMetadata> components();

    List<RouteMetadata> routes();

    List<PluginMetadata> plugins();

    Optional<TypeMetadata> type(Class<?> type);
}
```

Objetivos:

- evitar repeated reflection;
- acelerar startup;
- permitir diagnostics;
- detetar problemas em build-time;
- suportar `dhole routes`;
- suportar serialization/validation.

---

# 14. Component Metadata

Exemplo conceptual:

```text
Component
  type: UserController
  kind: CONTROLLER

Constructor
  UserController(UserService)

Dependencies
  UserService
```

Outro:

```text
Component
  type: UserService
  kind: SERVICE

Constructor
  UserService(UserRepository)
```

Não é necessário que o developer declare explicitamente `kind: SERVICE`.

O build inferirá significado a partir do graph/convenções.

---

# 15. `ComponentRegistry`

Contém os componentes conhecidos da aplicação.

Responsabilidades:

- registar component definitions;
- registar factories;
- registar plugin components;
- resolver overrides;
- expor informação ao DI container.

Não é um service locator público.

---

# 16. Dependency Graph

Antes de construir objetos:

```text
ComponentRegistry
      ↓
DependencyGraphBuilder
      ↓
validated graph
```

Exemplo:

```text
UserController
      │
      ▼
UserService
      │
      ▼
UserRepository
      │
      ▼
Database
```

Se `Database` não existir:

```text
Dependency Error

Unable to build dependency graph.

UserController
  -> UserService
      -> UserRepository
          -> Database

No provider found for Database.
```

---

# 17. Ambiguous dependencies

Exemplo:

```java
interface PaymentGateway {}
```

Implementações:

```text
StripePaymentGateway
PaypalPaymentGateway
```

Sem binding:

```text
Dependency Error

Multiple providers found for PaymentGateway:

- StripePaymentGateway
- PaypalPaymentGateway

Declare an explicit binding in Settings.
```

---

# 18. Cycles

```text
A -> B -> C -> A
```

é erro.

O Dhole não deve resolver ciclos através de proxies invisíveis por defeito.

Erro:

```text
Circular Dependency

A
 -> B
    -> C
       -> A
```

---

# 19. `DependencyContainer`

Depois de validado o graph:

```text
DependencyGraph
      ↓
DependencyContainer
```

Responsabilidades:

- criar instances;
- aplicar scopes;
- gerir lifecycle dos componentes;
- resolver factories;
- controlar request scope.

API interna conceptual:

```java
<T> T resolve(Class<T> type);
```

A aplicação normal não deve chamar diretamente o container.

---

# 20. Scopes

Scopes base:

```text
singleton
request
prototype
```

### Singleton

Uma instance por `ApplicationContext`.

### Request

Uma instance por HTTP request.

### Prototype

Nova instance por resolução.

O default de services será definido de forma conservadora para evitar estado partilhado acidental.

---

# 21. `ApplicationContext`

É o runtime context principal.

Contém acesso controlado a:

```text
settings
environment
metadata
dependencies
modules
plugins
events/lifecycle
```

API conceptual:

```java
public interface ApplicationContext {

    Environment environment();

    SettingsRegistry settings();

    ModuleRegistry modules();

    PluginRegistry plugins();

    Lifecycle lifecycle();
}
```

O `ApplicationContext` não deve tornar-se um "global bag".

---

# 22. Context hierarchy

Development/restart pode beneficiar de contexts separados:

```text
LauncherContext
      │
      └── ApplicationContext
```

`LauncherContext` pode sobreviver a restarts.

`ApplicationContext` é recriado.

Isso permite:

```text
dhole dev process
     │
     ├── app context #1
     │      ↓ stop
     │
     ├── app context #2
     │      ↓ stop
     │
     └── app context #3
```

Sem reiniciar toda a CLI.

---

# 23. Modules

Um `Module` representa uma capacidade do framework.

Exemplos:

```text
WebModule
ValidationModule
SerializationModule
SecurityModule
DatabaseModule
TestingModule
```

Contrato conceptual:

```java
public interface DholeModule {

    void configure(ModuleContext context);

    void start(ApplicationContext context);

    void stop(ApplicationContext context);
}
```

O contrato definitivo poderá separar interfaces.

---

# 24. Core vs modules

`dhole-core` conhece:

```text
module lifecycle
component registry
application lifecycle
metadata
environment abstractions
```

Não deve conhecer diretamente:

```text
HTTP server
PostgreSQL
Tuprel
JWT
Redis
Kafka
```

Esses pertencem a módulos/plugins.

---

# 25. Module ordering

Dependências entre módulos devem ser explícitas.

Exemplo:

```text
WebModule
    depends on:
      SerializationModule
```

```text
SecurityWebModule
    depends on:
      WebModule
      SecurityModule
```

Topological sorting determina startup order.

Ciclo entre módulos é erro.

---

# 26. Module lifecycle

```text
register
    ↓
configure
    ↓
validate
    ↓
start
    ↓
running
    ↓
stop
```

Start order:

```text
dependency first
dependent later
```

Stop order:

```text
dependent first
dependency later
```

---

# 27. Plugins vs Modules

A distinção:

```text
Module
    internal/framework capability

Plugin
    extension package distributed independently
```

Um plugin normalmente contribui módulos/bindings/settings.

Exemplo:

```text
dhole-tuprel plugin
      ↓
TuprelDatabaseModule
```

Não duplicar lifecycle desnecessariamente.

---

# 28. Plugin loading

Bootstrap:

```text
dhole.toml
    ↓
resolved plugin dependencies
    ↓
plugin metadata
    ↓
PluginRegistry
    ↓
configure
```

Plugins devem ser conhecidos antes do dependency graph final.

Porque podem contribuir bindings:

```java
context.bind(Database.class)
    .to(TuprelDatabase.class);
```

---

# 29. Validation Registry

Durante bootstrap:

```text
built-in validators
      +
application validators
      +
plugin validators
      ↓
ValidationRegistry
```

O registry é estabilizado antes da aplicação ficar `RUNNING`.

---

# 30. Serialization Registry

```text
JSON serializer
      +
custom serializers
      +
plugin serializers
      ↓
SerializationRegistry
```

Também deve estar pronto antes de abrir o HTTP server.

---

# 31. Routing initialization

Fluxo:

```text
Controller metadata
      ↓
instantiate controllers through DI
      ↓
controller.routes(router)
      ↓
RouteRegistry
      ↓
conflict validation
      ↓
HTTP handler tree
```

Importante:

`routes()` não deve executar lógica de negócio.

É registration-time only.

---

# 32. `Router`

`Router` é uma API pública controlada.

Internamente cria `RouteDefinition`.

Exemplo:

```java
routes.get("/users/{id}", this::find);
```

torna-se aproximadamente:

```text
RouteDefinition
  method: GET
  path: /users/{id}
  handler: UserController.find
  parameters:
    id -> PATH -> long
  response:
    User
```

---

# 33. Request pipeline

Depois do startup:

```text
HTTP request
    ↓
server adapter
    ↓
request normalization
    ↓
request ID
    ↓
route match
    ↓
middleware chain
    ↓
security
    ↓
parameter binding
    ↓
deserialization
    ↓
validation
    ↓
controller handler
    ↓
response mapping
    ↓
serialization
    ↓
HTTP response
```

Exceptions:

```text
exception
    ↓
ErrorHandlerRegistry
    ↓
error response
```

---

# 34. Request Context

Cada request recebe um contexto próprio.

Exemplo conceptual:

```java
public interface RequestContext {

    String requestId();

    Request request();

    Route route();

    ApplicationContext application();
}
```

Request-scoped dependencies são armazenadas aqui.

---

# 35. Middleware chain

Modelo:

```text
Middleware A
   ↓
Middleware B
   ↓
Security
   ↓
Handler
```

Cada middleware recebe:

```java
Response handle(
    Request request,
    Next next
);
```

A implementação interna pode usar abstrações mais ricas, mas a ordem deve ser previsível.

---

# 36. Error boundary

O request pipeline deve ter um boundary externo:

```text
try request pipeline
catch Throwable
    ↓
error registry
    ↓
safe HTTP response
```

Erros fatais da JVM não devem ser tratados como business errors arbitrariamente.

---

# 37. Server abstraction

`dhole-web` não deve depender publicamente de um servidor concreto.

SPI:

```java
public interface HttpServer {

    void start(HttpHandler handler);

    void stop();
}
```

Adapters futuros podem variar.

A aplicação continua igual.

---

# 38. Startup readiness

O HTTP server só deve anunciar `ready` depois de:

```text
configuration valid
DI graph valid
plugins valid
database required connections available
routes registered
server listening
startup hooks completed
```

Health e readiness são conceitos diferentes.

---

# 39. Lifecycle events

Eventos internos possíveis:

```text
ApplicationStarting
ApplicationConfigured
ApplicationStarted
ApplicationReady
ApplicationStopping
ApplicationStopped
```

Plugins e módulos podem ter hooks controlados.

Não transformar tudo num event bus global.

---

# 40. Startup hooks

API futura controlada:

```java
public class Warmup implements StartupTask {

    public void run(ApplicationContext context) {
        // ...
    }
}
```

Falha pode impedir readiness dependendo da policy.

---

# 41. Shutdown hook

Dhole registra shutdown hook da JVM.

Fluxo:

```text
SIGTERM / shutdown
      ↓
Application.stop()
      ↓
state = STOPPING
      ↓
stop accepting requests
      ↓
drain in-flight requests
      ↓
stop jobs/workers
      ↓
stop plugins
      ↓
stop modules (reverse order)
      ↓
close DI-managed resources
      ↓
state = STOPPED
```

---

# 42. Graceful shutdown timeout

Config:

```java
settings.lifecycle(lifecycle -> lifecycle
    .shutdownTimeout(Duration.ofSeconds(30))
);
```

Ao exceder:

```text
remaining work is terminated according to policy
```

Nunca esperar indefinidamente.

---

# 43. Managed resources

Objetos com lifecycle podem ser geridos.

Exemplo:

```java
public interface ManagedResource {

    void start();

    void close();
}
```

O Dhole deve integrar adequadamente `AutoCloseable`.

Exemplo:

```java
class DatabasePool implements AutoCloseable
```

será fechado no shutdown quando criado pelo container.

---

# 44. Failure during startup

Se módulo #5 falha depois de quatro módulos iniciarem:

```text
start module 1 ✓
start module 2 ✓
start module 3 ✓
start module 4 ✓
start module 5 ✗
```

Dhole executa rollback:

```text
stop module 4
stop module 3
stop module 2
stop module 1
```

A aplicação termina como:

```text
FAILED
```

---

# 45. Development architecture

`dhole dev` utiliza:

```text
CLI Process
   │
   ├── FileWatcher
   ├── Compiler
   ├── MetadataGenerator
   └── ApplicationLauncher
           │
           └── ApplicationContext
```

Alteração:

```text
file changed
    ↓
compile
    ↓
metadata
    ↓
old ApplicationContext.stop()
    ↓
new ApplicationContext.start()
```

---

# 46. Classloading

A implementação de dev mode deve permitir descarregar application classes quando necessário.

Arquitetura possível:

```text
Dhole CLI / DevTools ClassLoader
            │
            └── Application ClassLoader
                    │
                    ├── app classes
                    └── application dependencies
```

No restart:

```text
old Application ClassLoader discarded
new Application ClassLoader created
```

A arquitetura final deve minimizar classloader leaks.

---

# 47. Production classloading

Production não precisa da complexidade de reload.

Pode usar classloading normal.

DevTools não deve estar presente por defeito na distribuição production.

---

# 48. Thread ownership

Cada módulo que cria threads deve:

- nomeá-las;
- registá-las;
- pará-las;
- não deixar threads orphaned no restart.

Isto é especialmente importante para:

```text
database pools
HTTP server
schedulers
jobs
watchers
plugin clients
```

---

# 49. Runtime concurrency

O framework poderá usar virtual threads onde adequado.

A arquitetura pública não assume que cada módulo usa o mesmo executor.

O Dhole pode disponibilizar um `TaskExecutor` gerido.

---

# 50. Observability hooks

Core deve ter hooks desde cedo para:

```text
startup timings
module startup timings
request lifecycle
errors
dependency diagnostics
reload timings
```

Mesmo antes do módulo completo de observability existir.

---

# 51. Internal events vs application events

Separar:

```text
framework lifecycle events
```

de:

```text
domain/application events
```

Não usar o mesmo mecanismo indiscriminadamente.

---

# 52. Public API boundaries

Packages conceptuais:

```text
org.dhole.api.*
org.dhole.spi.*
org.dhole.internal.*
```

ou equivalente.

Regra:

```text
org.dhole.internal.*
```

não possui compatibility guarantees.

Plugins oficiais e community plugins devem usar API/SPI pública.

---

# 53. Compatibility

O framework deve versionar:

```text
public API
plugin API
metadata format
CLI behavior
```

Metadata format pode ter version field:

```text
metadataVersion = 1
```

Build e runtime incompatíveis devem falhar claramente.

---

# 54. Startup diagnostics

Em development:

```text
Dhole Startup

Environment        development
Components         32
Dependencies       47
Plugins             3
Routes             18

Configuration       12 ms
Metadata             5 ms
DI graph             8 ms
Plugins             21 ms
HTTP server         37 ms

Ready in 103 ms
```

Valores são apenas exemplo; não são metas oficiais.

---

# 55. Production diagnostics

Mais discretos:

```text
Application started
Environment: production
Version: 1.4.2
```

Detalhes podem ser estruturados nos logs.

---

# 56. `dhole doctor` integration

Core architecture expõe diagnostics para:

```bash
dhole doctor
```

Checks:

```text
metadata compatibility
dependency graph
plugin compatibility
settings validation
route conflicts
runtime requirements
```

---

# 57. Testing architecture

Tests podem criar `ApplicationContext` sem abrir porta real.

Exemplo conceptual:

```java
TestApplication app =
    DholeTest.start(App.class);
```

HTTP tests podem usar transport in-memory quando possível.

Isso acelera testes.

---

# 58. Embedded mode

Possibilidade futura:

```java
Application app = Dhole.application(App.class)
    .port(0)
    .build();

app.start();
```

Útil para:

- integration tests;
- tooling;
- embedding.

Não é prioridade da v1 pública.

---

# 59. Core package proposal

Estrutura interna inicial:

```text
dhole-core/
└── src/main/java/org/dhole/
    ├── Dhole.java
    ├── application/
    │   ├── Application.java
    │   ├── DefaultApplication.java
    │   ├── ApplicationBuilder.java
    │   ├── ApplicationContext.java
    │   └── ApplicationState.java
    │
    ├── bootstrap/
    │   ├── Bootstrap.java
    │   ├── BootstrapContext.java
    │   └── BootstrapException.java
    │
    ├── environment/
    │   ├── Environment.java
    │   └── EnvironmentLoader.java
    │
    ├── metadata/
    │   ├── MetadataRegistry.java
    │   └── ...
    │
    ├── components/
    │   ├── ComponentRegistry.java
    │   └── ...
    │
    ├── lifecycle/
    │   ├── LifecycleManager.java
    │   ├── StartupTask.java
    │   └── ...
    │
    └── modules/
        ├── DholeModule.java
        ├── ModuleRegistry.java
        └── ...
```

Esta estrutura é conceptual e pode evoluir durante a implementação.

---

# 60. Full bootstrap sequence

Versão consolidada:

```text
Dhole.run(App.class)
        ↓
Bootstrap.create()
        ↓
resolve root package
        ↓
load runtime/project metadata
        ↓
load Environment
        ↓
load Settings
        ↓
load generated Metadata
        ↓
discover declared Plugins
        ↓
plugins.configure()
        ↓
register Dhole Modules
        ↓
build ComponentRegistry
        ↓
build DependencyGraph
        ↓
validate dependency graph
        ↓
initialize ValidationRegistry
        ↓
initialize SerializationRegistry
        ↓
validate configuration
        ↓
create ApplicationContext
        ↓
configure Modules
        ↓
initialize database/infrastructure
        ↓
instantiate Controllers
        ↓
register Routes
        ↓
validate Routes
        ↓
start Plugins / Modules
        ↓
start HTTP Server
        ↓
run startup hooks
        ↓
state = RUNNING
        ↓
Application Ready
```

---

# 61. Full request sequence

```text
socket/request
    ↓
HttpServer
    ↓
RequestFactory
    ↓
RequestContext
    ↓
RequestId
    ↓
Router.match()
    ↓
Middleware chain
    ↓
Security
    ↓
ParameterBinder
    ↓
Serializer.deserialize()
    ↓
Validator.validate()
    ↓
Dependency request scope
    ↓
Controller method
    ↓
Return value
    ↓
ResponseMapper
    ↓
Serializer.serialize()
    ↓
HTTP response
    ↓
request scope cleanup
```

---

# 62. Full shutdown sequence

```text
shutdown signal
    ↓
state = STOPPING
    ↓
server stops accepting requests
    ↓
drain in-flight requests
    ↓
stop background workers
    ↓
plugin stop hooks
    ↓
module stop hooks (reverse order)
    ↓
close managed resources
    ↓
close ApplicationContext
    ↓
state = STOPPED
```

---

# 63. Architectural invariants

Estas regras não devem ser quebradas sem nova decisão formal:

1. `dhole-core` não depende de Tuprel.
2. `dhole-core` não depende de servidor HTTP concreto.
3. `dhole-core` não depende de JSON library concreta.
4. plugin configuration ocorre antes do dependency graph final.
5. HTTP server abre apenas após validation de startup.
6. routes são explicitamente registadas.
7. runtime scanning indiscriminado não é mecanismo principal.
8. ApplicationContext é recriável em development.
9. shutdown é gracioso por defeito.
10. internal APIs não são plugin APIs.
11. development tooling não entra obrigatoriamente em production.
12. failures de bootstrap devem ser acionáveis e legíveis.
13. framework automation deve ser inspecionável.
14. dependency cycles são erros.
15. startup parcial deve ser revertido quando possível.

---

# 64. Decisões ainda abertas

A Core Architecture ainda precisa de documentos detalhados para:

- metadata compiler;
- component discovery;
- module API final;
- scopes final;
- HTTP server adapter;
- request parameter binding;
- startup task API;
- application event system;
- concurrency model;
- observability;
- generated code strategy.

Estas decisões não impedem a arquitetura v0.1.

---

# 65. Próximos documentos recomendados

A partir desta arquitetura, os próximos documentos naturais são:

```text
METADATA_COMPILER.md       ✓
COMPONENT_MODEL.md          ✓
MODULE_SYSTEM.md          ✓
REQUEST_LIFECYCLE.md      ✓
OBSERVABILITY.md          ✓
CONCURRENCY.md            ✓
```

---

## 66. Resumo

O Dhole é simples na superfície porque a complexidade fica organizada internamente.

A arquitetura base é:

```text
Build-time intelligence
        +
Small runtime core
        +
Explicit lifecycle
        +
Modular capabilities
        +
Inspectable automation
```

Este princípio deve orientar toda a implementação do framework.


---

## 67. Documentos derivados já definidos

A arquitetura de metadata e o component model são detalhados em:

```text
METADATA_COMPILER.md
COMPONENT_MODEL.md
```

Estes dois documentos devem ser considerados extensões normativas desta Core Architecture para build-time discovery e dependency injection.


---

## 68. Request and Runtime Architecture Documents

Os seguintes documentos completam o bloco runtime da v0.1:

```text
MODULE_SYSTEM.md
REQUEST_LIFECYCLE.md
PARAMETER_BINDING.md
CONCURRENCY.md
OBSERVABILITY.md
```

Devem ser considerados normativos para módulos, request processing, binding, concorrência e operação em produção.
