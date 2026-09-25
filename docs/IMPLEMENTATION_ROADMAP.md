# Implementation Roadmap

## 1. Objetivo

Este documento define a ordem oficial de implementação do Java Rest Framework (JRF).

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
4. manter `jrf-core` pequeno;
5. evitar dependências circulares entre módulos;
6. preservar fronteiras entre API, SPI e internals;
7. escrever testes desde o primeiro módulo;
8. manter exemplos reais sempre atualizados;
9. falhar cedo com mensagens legíveis;
10. manter compatibility com Java normal e bibliotecas Java externas;
11. não depender de annotations como mecanismo central;
12. não utilizar reflection pesada como base da arquitetura;
13. manter startup e shutdown previsíveis;
14. não acoplar o JRF ao Tuprel no core;
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
M15 JRF v0.1 Developer Preview
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
java-rest-framework/
├── docs/
├── modules/
├── tools/
├── integrations/
└── examples/
```

### 4.2 Definir package namespace temporário

Enquanto o domínio oficial não estiver decidido, usar:

```text
jrf.*
```

Exemplos:

```text
jrf.application
jrf.di
jrf.http
jrf.validation
```

Não publicar artefactos externos antes de decidir group/package namespace definitivo.

### 4.3 Escolher bootstrap de build interno

O JRF promete que aplicações JRF não precisarão de `pom.xml`.

Isso não significa que o próprio repositório JRF precisa reinventar o build system antes de existir.

Para o bootstrap inicial do framework, escolher uma ferramenta Java madura apenas para construir o próprio JRF.

A decisão deve ser documentada separadamente.

O futuro:

```text
developer application
    ↓
jrf build
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

Criar o menor runtime capaz de iniciar e parar uma aplicação JRF.

## Módulo principal

```text
modules/jrf-core
```

## Primeiras classes

```text
jrf.Jrf

jrf.application.Application
jrf.application.DefaultApplication
jrf.application.ApplicationBuilder
jrf.application.ApplicationContext
jrf.application.DefaultApplicationContext
jrf.application.ApplicationState

jrf.bootstrap.Bootstrap
jrf.bootstrap.BootstrapContext
jrf.bootstrap.BootstrapException

jrf.lifecycle.LifecycleManager
jrf.lifecycle.LifecycleException
jrf.lifecycle.StartupTask
```

## Primeira API pública

```java
public final class Jrf {

    public static void run(Class<?> applicationClass) {
        // bootstrap
    }
}
```

## Primeira aplicação demonstrável

```java
package example.hello;

import jrf.Jrf;

public class App {

    public static void main(String[] args) {
        Jrf.run(App.class);
    }
}
```

Neste milestone ainda não existe servidor HTTP.

Saída:

```text
JRF

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
startup failure -> FAILED
shutdown after partial startup
```

## Critério de conclusão

```text
✓ Jrf.run() works
✓ lifecycle works
✓ shutdown works
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
modules/jrf-config
```

## Classes iniciais

```text
jrf.config.Environment
jrf.config.DefaultEnvironment
jrf.config.EnvironmentLoader

jrf.config.SettingsBuilder
jrf.config.SettingsRegistry
jrf.config.ConfigurationException

jrf.env.Env
jrf.env.DotEnvLoader
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

```text
✓ application starts with typed config
✓ missing required config fails before RUNNING
✓ .env works locally
✓ secrets are not printed
```

---

# 7. M3 — Component Model + Dependency Injection

## Objetivo

Permitir plain Java constructor injection sem annotations.

## Módulo

```text
modules/jrf-di
```

## Classes principais

```text
jrf.di.ComponentDefinition
jrf.di.ComponentRegistry
jrf.di.ComponentOrigin
jrf.di.ComponentScope

jrf.di.DependencyGraph
jrf.di.DependencyGraphBuilder
jrf.di.DependencyNode

jrf.di.DependencyContainer
jrf.di.DefaultDependencyContainer

jrf.di.Binding
jrf.di.BindingRegistry
jrf.di.Provider
jrf.di.Factory

jrf.di.DependencyException
jrf.di.CircularDependencyException
jrf.di.AmbiguousDependencyException
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
tools/jrf-compiler
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
Jrf.run(App.class)
```

e root package.

### M4.3 Generated index

Primeira versão pode gerar:

```text
META-INF/jrf/components.idx
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
JRF-DI-001 missing dependency
JRF-DI-002 ambiguous dependency
JRF-DI-003 circular dependency
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
modules/jrf-http
modules/jrf-routing
modules/jrf-web
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
jrf dev
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
jrf-serialization
jrf-json
jrf-web
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
jrf-validation
jrf-web
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
tools/jrf-cli
tools/jrf-build
modules/jrf-devtools
```

## Primeiros comandos

```bash
jrf new
jrf run
jrf build
jrf test
jrf routes
jrf config
jrf doctor
```

Depois:

```bash
jrf add
jrf remove
jrf dependencies
```

## `jrf new`

Gerar:

```text
App.java
Settings.java
.env
.env.example
.gitignore
jrf.toml
```

## `jrf run`

```text
resolve
compile
metadata
launch
```

## `jrf dev`

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

## `jrf routes`

Ler metadata e mostrar tabela.

## `jrf doctor`

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
jrf new hello
cd hello
jrf dev
```

editar controller e ver restart automático.

Este é um dos milestones mais importantes do projeto.

---

# 13. M9 — Database SPI + Tuprel Integration

## Objetivo

Integrar persistência sem acoplar core ao ORM.

## Módulos

```text
modules/jrf-database
integrations/jrf-tuprel
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
JRF Database SPI
    ↓
jrf-tuprel
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
jrf db migrate
jrf db rollback
jrf db status
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
✓ Tuprel works inside JRF
✓ Tuprel still works independently
✓ jrf-core does not depend on Tuprel
✓ transactions work explicitly
```

---

# 14. M10 — Security

## Objetivo

Criar autenticação/autorização suficiente para APIs reais.

## Módulo

```text
jrf-security
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
jrf-testing
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

Tornar JRF operável em produção.

## Módulo

```text
jrf-observability
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

Abrir o ecossistema JRF sem abrir internals.

## Módulos

```text
jrf-plugin-api
jrf-plugin-runtime
```

## Implementar

```text
PluginMetadata
JrfPlugin
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
jrf-tuprel
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

sem aceder `jrf.internal.*`.

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
jrf dev restart loops
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

# 19. M15 — JRF v0.1 Developer Preview

## Objetivo

Primeiro release utilizável por developers externos.

Não chamar `1.0`.

Exemplo:

```text
JRF 0.1.0 Developer Preview
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
jrf dev
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
jrf.api.*
jrf.spi.*
jrf.internal.*
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

Encapsular bibliotecas externas atrás de APIs JRF quando apropriado.

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

Estas features podem ser excelentes no futuro, mas não definem o sucesso inicial do JRF.

---

# 34. First Real Target

O primeiro grande objetivo técnico do projeto é atingir isto:

```bash
jrf new bookstore
cd bookstore
jrf dev
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

Quando isso estiver sólido, o JRF já terá identidade real.

---

# 35. Recommended Implementation Order

Ordem prática resumida:

```text
1.  jrf-core
2.  jrf-config
3.  jrf-di
4.  jrf-compiler — component metadata
5.  jrf-http
6.  jrf-routing
7.  jrf-web
8.  jrf-serialization
9.  jrf-json
10. parameter binding
11. jrf-validation
12. error handling
13. jrf-cli
14. jrf-build
15. jrf-devtools
16. jrf-database
17. jrf-tuprel
18. jrf-security
19. jrf-testing
20. jrf-observability
21. jrf-plugin-api
22. jrf-plugin-runtime
23. production hardening
```

---

# 36. Immediate Next Step

O primeiro código real deve começar em:

```text
modules/jrf-core
```

Primeiras classes:

```text
Jrf.java
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
        Jrf.run(App.class);
    }
}
```

sem HTTP, database, DI ou plugins.

Apenas lifecycle correto.

---

# 37. Final Rule

Ao longo de toda a implementação:

> **Do not build the impressive part before the dependable part.**

O JRF deve ganhar poder por camadas, mantendo cada camada simples, testável e compreensível.
