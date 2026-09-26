# Implementation Roadmap

## 1. Objetivo

Este documento define a ordem oficial de implementação do Dhole Framework.

A finalidade é impedir que o projeto cresça de forma desorganizada ou que funcionalidades avançadas sejam desenvolvidas antes das fundações necessárias.

O roadmap segue esta regra:

> **Build the smallest reliable core first, then add capabilities in dependency order.**

A implementação deve avançar por milestones verificáveis.

Nenhuma fase deve ser considerada concluída apenas porque "o código existe".

Cada milestone termina com:

- código funcional;
- testes;
- documentação mínima atualizada;
- exemplo executável;
- critérios de aceitação cumpridos.

---

# 2. Princípios de implementação

Durante todo o desenvolvimento:

1. não implementar funcionalidades que não estejam justificadas pela especificação;
2. não criar abstrações para problemas que ainda não existem;
3. preferir código simples antes de otimizações prematuras;
4. manter `dhole-core` pequeno;
5. evitar dependências circulares entre módulos;
6. preservar fronteiras entre API, SPI e internals;
7. escrever testes desde o primeiro módulo;
8. manter exemplos reais sempre atualizados;
9. falhar cedo com mensagens legíveis;
10. manter compatibility com Java normal e bibliotecas Java externas;
11. não depender de annotations como mecanismo central;
12. não utilizar reflection pesada como base da arquitetura;
13. manter startup e shutdown previsíveis;
14. não acoplar o Dhole ao Tuprel no core;
15. cada milestone deve produzir algo demonstrável.

---

# 3. Visão geral dos milestones

```text
M0  Repository Foundation
M1  Core Runtime
M2  Configuration
M3  Component Model + Dependency Injection
M4  Metadata Compiler
M5  HTTP + Routing
M6  Serialization + Parameter Binding
M7  Validation + Error Handling
M8  CLI + Build + Dev Mode
M9  Database SPI + Tuprel Integration
M10 Security
M11 Testing Framework
M12 Observability
M13 Plugin Runtime
M14 Production Hardening
M15 Dhole v0.1 Developer Preview
```

A ordem pode sofrer pequenos ajustes durante implementação, mas dependências arquiteturais não devem ser ignoradas.

---

# 4. M0 — Repository Foundation

## Objetivo

Preparar o monorepo para desenvolvimento real.

## Módulos envolvidos

```text
root
modules/
tools/
integrations/
examples/
docs/
```

## Tarefas

### 4.1 Confirmar estrutura

```text
dhole/
├── docs/
├── modules/
├── tools/
├── integrations/
└── examples/
```

### 4.2 Definir package namespace

Namespace oficial (domínio `dhole.org`):

```text
org.dhole.*
```

Exemplos:

```text
org.dhole.application
org.dhole.di
org.dhole.http
org.dhole.validation
```

Group/package namespace: `org.dhole`.

### 4.3 Escolher bootstrap de build interno

O Dhole promete que aplicações Dhole não precisarão de `pom.xml`.

Isso não significa que o próprio repositório Dhole precisa reinventar o build system antes de existir.

Para o bootstrap inicial do framework, escolher uma ferramenta Java madura apenas para construir o próprio Dhole.

A decisão deve ser documentada separadamente.

O futuro:

```text
developer application
    ↓
dhole build
```

não depende necessariamente do build usado internamente para desenvolver o framework.

### 4.4 CI inicial

Criar pipeline para:

```text
compile
unit tests
format/check
```

### 4.5 Code conventions

Definir:

```text
Java version
formatting
package naming
test naming
visibility rules
nullability conventions
```

## Critério de conclusão

```text
✓ repository compiles
✓ test command works
✓ CI runs
✓ modules can depend on each other intentionally
✓ hello unit test passes
```

---

# 5. M1 — Core Runtime

## Objetivo

Criar o menor runtime capaz de iniciar e parar uma aplicação Dhole.

## Módulo principal

```text
modules/dhole-core
```

## Primeiras classes

```text
org.dhole.Dhole

org.dhole.application.Application
org.dhole.application.DefaultApplication
org.dhole.application.ApplicationBuilder
org.dhole.application.ApplicationContext
org.dhole.application.DefaultApplicationContext
org.dhole.application.ApplicationState

org.dhole.bootstrap.Bootstrap
org.dhole.bootstrap.BootstrapContext
org.dhole.bootstrap.BootstrapException

org.dhole.lifecycle.LifecycleManager
org.dhole.lifecycle.LifecycleException
org.dhole.lifecycle.StartupTask
```

## Primeira API pública

```java
public final class Dhole {

    public static void run(Class<?> applicationClass) {
        // bootstrap
    }
}
```

## Primeira aplicação demonstrável

```java
package example.hello;

import org.dhole.Dhole;

public class App {

    public static void main(String[] args) {
        Dhole.run(App.class);
    }
}
```

Neste milestone ainda não existe servidor HTTP.

Saída:

```text
Dhole

Application starting...
Application ready.
Application stopped.
```

## Implementar estados

```text
CREATED
STARTING
RUNNING
STOPPING
STOPPED
FAILED
```

## Implementar shutdown hook

Quando a JVM termina:

```text
Application.stop()
```

é chamado.

## Testes obrigatórios

```text
application starts
application changes state correctly
application cannot start twice
application stops
shutdown hook is safe in every state reachable in M1
```

## Testes condicionais

Obrigatórios apenas quando existir uma operação de startup realmente falível:

```text
startup failure -> FAILED
shutdown after partial startup
```

O M1 não contém nenhuma operação de startup falível nem mecanismo público para registar trabalho de startup.

Não se introduz uma fonte de falha sintética apenas para exercitar `FAILED`.

Estes testes tornam-se obrigatórios no primeiro milestone que introduzir uma operação de startup realmente falível (não necessariamente o M4).

## Critério de conclusão

```text
✓ Dhole.run() works
✓ lifecycle works
✓ shutdown works (shutdown hook safe)
✓ no HTTP/database/config yet
✓ core has no dependency on optional modules
```

---

# 6. M2 — Configuration

## Objetivo

Implementar:

```text
Settings.java
Environment
.env
typed settings
configuration validation
```

## Módulo

```text
modules/dhole-config
```

## Classes iniciais

```text
org.dhole.config.Environment
org.dhole.config.DefaultEnvironment
org.dhole.config.EnvironmentLoader

org.dhole.config.SettingsBuilder
org.dhole.config.SettingsRegistry
org.dhole.config.ConfigurationException

org.dhole.env.Env
org.dhole.env.DotEnvLoader
```

## API inicial

```java
env("APP_NAME")
env("APP_NAME", "My App")
envInt("APP_PORT", 8080)
envBool("APP_DEBUG", false)
```

## Ordem de loading inicial

```text
process environment
    ↓
.env
    ↓
defaults
```

Suporte a variantes avançadas de `.env` pode vir depois.

## Segurança

Nunca imprimir secrets.

Testar:

```text
JWT_SECRET
DB_PASSWORD
API_KEY
```

como masked values.

## Primeiro `Settings.java`

```java
public final class Settings {

    public static void configure(SettingsBuilder settings) {
        settings.app(app -> app
            .name(env("APP_NAME", "Hello"))
            .port(envInt("APP_PORT", 8080))
        );
    }
}
```

## CLI ainda não é necessária

Tests chamam loader diretamente.

## Testes obrigatórios

```text
.env load
environment override
default value
required value missing
invalid integer
invalid boolean
secret masking
production restrictions basic
```

## Critério de conclusão

O M2 entrega o subsistema de configuração como módulo standalone (`dhole-config`), testado diretamente:

```text
✓ typed configuration can be loaded and validated
✓ .env and process environment precedence works
✓ missing required configuration fails configuration loading
✓ malformed typed configuration fails configuration loading
✓ secret values remain masked (.env works locally; secrets are not printed)
✓ production restrictions are enforced
✓ dhole-config remains independently testable
✓ dhole-core remains independent of dhole-config
```

## Integração no startup (adiada)

O M2 não liga `dhole-config` a `Dhole.run()`.

`dhole-core` não depende de `dhole-config`, e não se introduz SPI temporária, `ServiceLoader` nem entry point alternativo.

A integração ocorre quando existir o mecanismo real de módulos/ativação (ainda sem milestone atribuído). Nesse momento tornam-se obrigatórios:

```text
configuration validation happens before RUNNING
configuration failure -> FAILED
shutdown after such a failed startup is safe
```

---

# 7. M3 — Component Model + Dependency Injection

## Objetivo

Permitir plain Java constructor injection sem annotations.

## Módulo

```text
modules/dhole-di
```

## Classes principais

```text
org.dhole.di.ComponentDefinition
org.dhole.di.ComponentRegistry
org.dhole.di.ComponentOrigin
org.dhole.di.ComponentScope

org.dhole.di.DependencyGraph
org.dhole.di.DependencyGraphBuilder
org.dhole.di.DependencyNode

org.dhole.di.DependencyContainer
org.dhole.di.DefaultDependencyContainer

org.dhole.di.Binding
org.dhole.di.BindingRegistry
org.dhole.di.Provider
org.dhole.di.Factory

org.dhole.di.DependencyException
org.dhole.di.CircularDependencyException
org.dhole.di.AmbiguousDependencyException
```

## Primeira demonstração

```java
public class UserRepository {
}
```

```java
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }
}
```

Resolver:

```java
UserService service =
    container.resolve(UserService.class);
```

## Regras obrigatórias

### Constructor injection only

Sem field injection.

### Multiple constructors

Erro sem provider explícito.

### Circular dependency

Erro.

### Ambiguous implementation

Erro.

### Explicit binding

```java
bind(PaymentGateway.class)
    .to(StripePaymentGateway.class);
```

## Scopes

Implementar inicialmente:

```text
SINGLETON
PROTOTYPE
```

Adicionar `REQUEST` quando HTTP existir.

## Resource ownership

Integrar:

```java
AutoCloseable
```

## Testes obrigatórios

```text
simple dependency
nested dependencies
singleton reuse
prototype recreation
circular dependency
ambiguous interface
explicit binding
factory binding
AutoCloseable cleanup
startup rollback
```

## Critério de conclusão

```text
✓ constructor injection works
✓ no @Autowired/@Inject
✓ dependency graph is inspectable
✓ cycles fail early
✓ container cleanup works
```

---

# 8. M4 — Metadata Compiler

## Objetivo

Mover descoberta e validação estrutural para build-time.

## Ferramenta

```text
tools/dhole-compiler
```

## Subsystems

```text
discovery
components
dependencies
routing
validation
serialization
plugins
diagnostics
generation
incremental
```

## Primeira implementação

Não tentar resolver tudo imediatamente.

### M4.1 Component metadata

Gerar metadata para:

```text
type
constructor
dependencies
```

### M4.2 Application root

Partir de:

```java
Dhole.run(App.class)
```

e root package.

### M4.3 Generated index

Primeira versão pode gerar:

```text
META-INF/dhole/components.idx
```

ou generated Java.

Escolher a opção mais simples.

### M4.4 Diagnostics

Criar estrutura:

```text
Diagnostic
DiagnosticCode
DiagnosticSeverity
SourceLocation
```

Primeiros codes:

```text
DHOLE-DI-001 missing dependency
DHOLE-DI-002 ambiguous dependency
DHOLE-DI-003 circular dependency
```

## Não implementar ainda

```text
full route analysis
full validation metadata
full serializer generation
IDE plugin
```

Esses entram progressivamente.

## Testes

Criar compile-testing fixtures:

```text
valid project
missing dependency
ambiguous dependency
multiple constructors
```

## Critério de conclusão

```text
✓ build can generate component metadata
✓ runtime can consume metadata
✓ DI no longer needs broad runtime scanning
✓ source diagnostics contain file/line when possible
```

---

# 9. M5 — HTTP + Routing

## Objetivo

Fazer:

```text
GET /hello
```

funcionar.

## Módulos

```text
modules/dhole-http
modules/dhole-routing
modules/dhole-web
```

## HTTP abstractions

```text
HttpServer
HttpHandler
Request
Response
HttpMethod
Headers
HttpStatus
```

## Routing

```text
Router
RouteDefinition
RouteRegistry
RouteMatcher
RouteGroup
```

## Controller

```java
public abstract class Controller {

    public abstract void routes(Router routes);
}
```

## Primeiro endpoint

```java
public class HelloController extends Controller {

    public void routes(Router routes) {
        routes.get("/hello", request -> "Hello World");
    }
}
```

## Primeira execução

```bash
dhole dev
```

ainda não existe.

Nesta fase executar através de test/runtime launcher.

Resultado:

```text
GET /hello
200 OK
Hello World
```

## Route conflicts

Detetar:

```text
GET /users/{id}
GET /users/{id}
```

antes de readiness.

## Groups

Implementar:

```java
routes.group("/api", api -> {
});
```

## Middleware básico

Implementar chain antes de security.

## Request scope

Adicionar:

```text
REQUEST
```

ao DI.

## Testes obrigatórios

```text
GET
POST
PUT
PATCH
DELETE

404
405
route parameters
route conflict
route groups
middleware order
request scope cleanup
```

## Critério de conclusão

```text
✓ hello-api serves HTTP
✓ routing works without annotations
✓ middleware works
✓ request scope works
```

---

# 10. M6 — Serialization + Parameter Binding

## Objetivo

Permitir:

```java
User create(CreateUser input)
```

com JSON.

## Módulos

```text
dhole-serialization
dhole-json
dhole-web
```

## Serialization core

Criar:

```text
Serializer
SerializerRegistry
MediaType
TypeRef<T>
SerializationException
```

## JSON

Primeira implementação pode usar uma biblioteca Java madura através do adapter oficial.

Não é necessário escrever parser JSON próprio.

## Binding

Criar:

```text
ParameterBinder
BindingPlan
ParameterSource
ParameterResolver
ConversionService
```

## Primeiro body binding

Request:

```json
{
  "name": "Mamadu",
  "email": "mamadu@example.com"
}
```

Handler:

```java
User create(CreateUser input)
```

## Path binding

```text
/users/{id}
```

```java
User find(long id)
```

## Query binding

Começar explícito se necessário.

Não sacrificar clareza apenas para remover wrappers.

## Metadata Compiler

Nesta fase expandir para:

```text
route handler
parameter names
parameter sources
response type
```

## Testes obrigatórios

```text
JSON record
JSON list
path long conversion
invalid path type -> 400
body JSON -> record
invalid JSON -> 400
unsupported content type -> 415
unsupported accept -> 406
ambiguous binding -> build/startup error
```

## Critério de conclusão

```text
✓ JSON request/response works
✓ path binding works
✓ no @RequestBody
✓ no @PathVariable
✓ handler signatures remain simple
```

---

# 11. M7 — Validation + Error Handling

## Objetivo

Completar a primeira experiência de API real.

## Módulos

```text
dhole-validation
dhole-web
```

## Validation

Implementar:

```text
Validatable
Rules<T>
Rule<T>
ValidationResult
ValidationError
Validator
ValidationRegistry
```

## API

```java
.field(CreateUser::email)
    .required()
    .email()
```

Primeiro conjunto de rules:

```text
required
notBlank
minLength
maxLength
email
min
max
positive
notEmpty
```

## Error handling

Criar:

```text
AppException
ErrorResponse
ErrorHandler
ErrorHandlerRegistry
```

## Status

```text
binding -> 400
validation -> 422
not found -> 404
unauthorized -> 401
forbidden -> 403
unexpected -> 500
```

## Request ID

Adicionar antes do error boundary.

## Testes obrigatórios

```text
valid input
multiple invalid fields
nested validation basic
custom rule
binding error != validation error
not found
unexpected error in development
unexpected error in production
request ID propagation
```

## Critério de conclusão

A seguinte API funciona completamente:

```java
User create(CreateUser input) {
    return users.create(input);
}
```

com:

```text
JSON
binding
validation
controller
serialization
errors
```

---

# 12. M8 — CLI + Build + Dev Mode

## Objetivo

Transformar o runtime num framework realmente utilizável.

## Ferramentas

```text
tools/dhole-cli
tools/dhole-build
modules/dhole-devtools
```

## Primeiros comandos

```bash
dhole new
dhole run
dhole build
dhole test
dhole routes
dhole config
dhole doctor
```

Depois:

```bash
dhole add
dhole remove
dhole dependencies
```

## `dhole new`

Gerar:

```text
App.java
Settings.java
.env
.env.example
.gitignore
dhole.toml
```

## `dhole run`

```text
resolve
compile
metadata
launch
```

## `dhole dev`

```text
watch
compile changed sources
regenerate metadata
fast restart
```

## Fast Restart v1

Não implementar class mutation avançada.

Fluxo:

```text
change
 ↓
compile
 ↓
metadata
 ↓
stop ApplicationContext
 ↓
new ApplicationContext
 ↓
ready
```

## `dhole routes`

Ler metadata e mostrar tabela.

## `dhole doctor`

Primeiros checks:

```text
Java version
manifest
configuration
metadata
dependency graph
.env Git status
```

## Critério de conclusão

Um novo developer consegue:

```bash
dhole new hello
cd hello
dhole dev
```

editar controller e ver restart automático.

Este é um dos milestones mais importantes do projeto.

---

# 13. M9 — Database SPI + Tuprel Integration

## Objetivo

Integrar persistência sem acoplar core ao ORM.

## Módulos

```text
modules/dhole-database
integrations/dhole-tuprel
```

## Database SPI

Criar conceitos:

```text
Database
TransactionManager
DatabaseHealth
MigrationRunner
DatabaseModule
```

Não duplicar API do Tuprel desnecessariamente.

## Tuprel adapter

```text
Dhole Database SPI
    ↓
dhole-tuprel
    ↓
Tuprel
```

## Configuração

```java
settings.database(database -> database
    .url(env("DATABASE_URL"))
);
```

## Transactions

```java
database.transaction(() -> {
});
```

## CLI

```bash
dhole db migrate
dhole db rollback
dhole db status
```

## Bookstore

Nesta fase `bookstore-api` começa a utilizar database real.

Criar inicialmente:

```text
User
Book
Author
Order
```

## Testes

```text
connection
transaction commit
transaction rollback
migration
startup database failure
shutdown pool
dev restart no connection leak
```

## Critério de conclusão

```text
✓ Tuprel works inside Dhole
✓ Tuprel still works independently
✓ dhole-core does not depend on Tuprel
✓ transactions work explicitly
```

---

# 14. M10 — Security

## Objetivo

Criar autenticação/autorização suficiente para APIs reais.

## Módulo

```text
dhole-security
```

## Implementar primeiro

```text
password hashing
authentication context
JWT
route auth
roles
permissions
CORS
basic security headers
```

## API

```java
routes.get("/me", this::profile)
    .auth();
```

```java
routes.delete("/users/{id}", this::delete)
    .auth()
    .role("ADMIN");
```

Handler:

```java
User profile(Auth<User> auth) {
    return auth.user();
}
```

## Login

Não criar framework de identidade completo.

Fornecer primitives.

## Testes

```text
valid JWT
expired JWT
invalid signature
route without auth
role denied
permission denied
CORS
password verify
secret missing
```

## Critério de conclusão

`bookstore-api` possui:

```text
register
login
/me
admin route
```

---

# 15. M11 — Testing Framework

## Objetivo

Tornar testes uma feature central.

## Módulo

```text
dhole-testing
```

## Criar

```text
ApiTest
TestApplication
TestClient
TestResponse
BindingOverride
DatabaseAssertions
```

## API

```java
class UserApiTest extends ApiTest {

    @Test
    void createsUser() {
        post("/users", json(
            "name", "Mamadu"
        ))
        .expectStatus(201);
    }
}
```

## In-memory transport

Quando possível, usar a mesma request pipeline sem abrir porta real.

## Dependency replacement

```java
replace(PaymentGateway.class)
    .with(FakePaymentGateway.class);
```

## Authentication helper

```java
asUser(user)
    .get("/me")
    .expectStatus(200);
```

## Database isolation

Integrar com adapter.

## Critério de conclusão

```text
✓ hello-api tests
✓ bookstore API tests
✓ dependency overrides
✓ security tests
✓ database isolation
```

---

# 16. M12 — Observability

## Objetivo

Tornar Dhole operável em produção.

## Módulo

```text
dhole-observability
```

## Implementar

```text
request ID
structured logging
request metrics
startup timings
health
readiness
basic tracing hooks
database timing hooks
```

## Endpoints

```text
/health
/ready
```

Configurable.

## Vendor independence

Criar SPIs.

Não acoplar diretamente a vendor.

## OpenTelemetry

Pode entrar através de adapter/plugin.

## Critério de conclusão

Uma aplicação production consegue responder:

```text
is it alive?
is it ready?
which request failed?
which route is slow?
```

---

# 17. M13 — Plugin Runtime

## Objetivo

Abrir o ecossistema Dhole sem abrir internals.

## Módulos

```text
dhole-plugin-api
dhole-plugin-runtime
```

## Implementar

```text
PluginMetadata
DholePlugin
PluginContext
PluginRegistry
compatibility validation
plugin lifecycle
```

## Extensions iniciais

Plugins podem contribuir:

```text
bindings
modules
settings
serializers
validators
health checks
CLI commands
database adapters
```

## Primeiro plugin oficial completo

```text
dhole-tuprel
```

deve migrar para o contract oficial se ainda usar integração provisória.

## Test harness

Criar:

```text
PluginTest
```

## Critério de conclusão

Um plugin externo pequeno consegue:

```text
register service
register config
register health check
start
stop
```

sem aceder `org.dhole.internal.*`.

---

# 18. M14 — Production Hardening

## Objetivo

Preparar Developer Preview com comportamento previsível.

## Áreas

### 18.1 Graceful shutdown

Testar:

```text
active requests
database pool
plugins
executors
```

### 18.2 Timeouts

Default razoável para:

```text
HTTP
outbound HTTP
database
shutdown
```

### 18.3 Resource limits

```text
request size
uploads
headers
connections
```

### 18.4 Performance

Benchmarks:

```text
startup
request throughput
memory
DI resolution
serialization
metadata load
dev restart
```

### 18.5 Leak tests

Especialmente:

```text
dhole dev restart loops
classloaders
threads
connections
```

### 18.6 Security review

Verificar:

```text
secret exposure
error pages
headers
CORS
JWT
path normalization
uploads
dependency downloads
```

### 18.7 Compatibility matrix

Definir Java versions suportadas.

---

# 19. M15 — Dhole v0.1 Developer Preview

## Objetivo

Primeiro release utilizável por developers externos.

Não chamar `1.0`.

Exemplo:

```text
Dhole 0.1.0 Developer Preview
```

## Funcionalidades obrigatórias

```text
✓ project creation
✓ configuration
✓ .env
✓ dependency injection
✓ metadata compiler
✓ HTTP
✓ routing
✓ middleware
✓ serialization
✓ JSON
✓ parameter binding
✓ validation
✓ error handling
✓ CLI
✓ build
✓ dev mode
✓ fast restart
✓ database SPI
✓ Tuprel integration
✓ transactions
✓ migrations basic
✓ JWT auth
✓ authorization
✓ testing
✓ logging
✓ health/readiness
✓ plugin foundation
```

## Pode ficar para depois

```text
WebSockets
GraphQL
gRPC
distributed jobs
full scheduler
mail
storage
advanced cache
native image
IDE plugin
plugin marketplace
advanced OpenAPI UI
reactive-first APIs
```

---

# 20. Hello API progression

O exemplo `hello-api` deve evoluir com os milestones.

## M1

```text
application starts
```

## M5

```text
GET /hello
```

## M6

```text
POST /hello
JSON request/response
```

## M7

```text
validation/errors
```

## M8

```bash
dhole dev
```

## M11

```text
ApiTest
```

`hello-api` nunca deve tornar-se complexo.

---

# 21. Bookstore API progression

`bookstore-api` é a aplicação real de referência.

## Primeiras features

```text
Users
Books
Authors
Orders
Auth
```

## M5

Controllers e routing.

## M6

DTOs e serialization.

## M7

Validation.

## M9

Persistence com Tuprel.

## M10

Authentication e authorization.

## M11

End-to-end testing.

## M12

Health e observability.

---

# 22. Definition of Done por feature

Nenhuma feature é concluída sem:

```text
implementation
unit tests
integration tests when applicable
error cases
shutdown/resource behavior
docs update
example usage
public API review
```

---

# 23. API Review Gate

Antes de tornar uma API pública:

Perguntar:

```text
Does this need to be public?
Can it be smaller?
Can Java already express this?
Does it require an annotation unnecessarily?
Does it hide important behavior?
Can it be tested easily?
Can plugins use a narrower SPI?
Will we regret supporting this API?
```

---

# 24. Compatibility Discipline

Antes de `1.0`, mudanças breaking são permitidas.

Mas não devem ser arbitrárias.

Desde `0.1`:

```text
document breaking changes
maintain migration notes
avoid needless renames
```

---

# 25. Package Boundaries

Direção pretendida:

```text
org.dhole.api.*
org.dhole.spi.*
org.dhole.internal.*
```

A organização real pode usar packages mais naturais por módulo, mas o conceito permanece:

```text
public API
stable SPI
internal implementation
```

Nunca documentar `internal` como API suportada.

---

# 26. Testing Strategy

Cada módulo deve conter:

```text
unit tests
module integration tests
```

Além disso:

```text
examples/
```

funcionam como integration/specification tests.

CI final:

```text
compile framework
unit tests
integration tests
build hello-api
run hello-api tests
build bookstore-api
run bookstore-api tests
```

---

# 27. Benchmark Strategy

Não otimizar antes de medir.

Criar benchmarks para:

```text
metadata loading
DI graph build
component creation
route matching
binding
serialization
validation
startup
dev restart
```

Guardar resultados entre releases.

---

# 28. Documentation Strategy

Enquanto projeto interno:

```text
Portuguese documentation
English public names
```

Antes de abrir à comunidade internacional:

```text
docs translated to English
Portuguese docs optionally retained separately
```

Código sempre usa nomes em inglês.

---

# 29. Commit Strategy

Commits pequenos e focados.

Exemplos:

```text
feat(core): add application lifecycle
feat(config): load environment variables
feat(di): detect circular dependencies
feat(web): add GET route registration
test(validation): add nested rules coverage
docs(core): document startup rollback
```

---

# 30. Branch Strategy

Para equipa pequena/inicial:

```text
main
feature/*
fix/*
```

Evitar Git Flow complexo no início.

`main` deve permanecer compilável.

---

# 31. Release Strategy

Inicialmente:

```text
0.0.x internal
0.1.0 developer preview
0.2.0
0.3.0
...
1.0.0 stable
```

Não prometer estabilidade `1.0` antes da API provar-se em aplicações reais.

---

# 32. Dependency Policy

Adicionar dependency apenas quando:

```text
solves a real problem
mature enough
maintained
license acceptable
does not expose unnecessary API coupling
```

Encapsular bibliotecas externas atrás de APIs Dhole quando apropriado.

---

# 33. What Not to Build First

Não implementar antes da base:

```text
plugin marketplace
GraphQL
WebSockets
gRPC
distributed queues
native compiler
cloud platform
IDE
AI integration
full admin dashboard
```

Estas features podem ser excelentes no futuro, mas não definem o sucesso inicial do Dhole.

---

# 34. First Real Target

O primeiro grande objetivo técnico do projeto é atingir isto:

```bash
dhole new bookstore
cd bookstore
dhole dev
```

e permitir:

```java
public class BookController extends Controller {

    private final BookService books;

    public BookController(BookService books) {
        this.books = books;
    }

    public void routes(Router routes) {
        routes.get("/books", this::list);
        routes.get("/books/{id}", this::find);
        routes.post("/books", this::create);
    }

    List<BookResponse> list() {
        return books.all();
    }

    BookResponse find(long id) {
        return books.find(id);
    }

    BookResponse create(CreateBook input) {
        return books.create(input);
    }
}
```

sem:

```text
@RestController
@RequestMapping
@GetMapping
@PostMapping
@PathVariable
@RequestBody
@Autowired
@Service
@Repository
```

com:

```text
configuration
JSON
validation
database
errors
testing
dev reload
```

funcionais.

Quando isso estiver sólido, o Dhole já terá identidade real.

---

# 35. Recommended Implementation Order

Ordem prática resumida:

```text
1.  dhole-core
2.  dhole-config
3.  dhole-di
4.  dhole-compiler — component metadata
5.  dhole-http
6.  dhole-routing
7.  dhole-web
8.  dhole-serialization
9.  dhole-json
10. parameter binding
11. dhole-validation
12. error handling
13. dhole-cli
14. dhole-build
15. dhole-devtools
16. dhole-database
17. dhole-tuprel
18. dhole-security
19. dhole-testing
20. dhole-observability
21. dhole-plugin-api
22. dhole-plugin-runtime
23. production hardening
```

---

# 36. Immediate Next Step

O primeiro código real deve começar em:

```text
modules/dhole-core
```

Primeiras classes:

```text
Dhole.java
Application.java
DefaultApplication.java
ApplicationState.java
ApplicationContext.java
DefaultApplicationContext.java
Bootstrap.java
LifecycleManager.java
```

Primeiro teste:

```text
starts application
transitions CREATED -> STARTING -> RUNNING
stops RUNNING -> STOPPING -> STOPPED
```

Primeiro objetivo executável:

```java
public class App {

    public static void main(String[] args) {
        Dhole.run(App.class);
    }
}
```

sem HTTP, database, DI ou plugins.

Apenas lifecycle correto.

---

# 37. Final Rule

Ao longo de toda a implementação:

> **Do not build the impressive part before the dependable part.**

O Dhole deve ganhar poder por camadas, mantendo cada camada simples, testável e compreensível.
