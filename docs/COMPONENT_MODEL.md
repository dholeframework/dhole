# Component Model

## 1. Objetivo

O Component Model define o que é um componente Dhole, como nasce, como é descoberto, como recebe dependências, como é destruído e como se relaciona com outros tipos da aplicação.

O objetivo é permitir:

```text
plain Java classes
```

sem exigir:

```text
@Component
@Service
@Repository
@Controller
```

para registrar tudo.

---

# 2. O que é um componente

Um componente é um objeto cujo lifecycle é controlado pelo Dhole.

Exemplos:

```text
controller
service
repository
middleware
configuration provider
plugin component
module component
HTTP client wrapper
database gateway
```

Nem toda classe Java é um componente.

---

# 3. Component definition

Representação conceptual:

```text
ComponentDefinition
  type
  constructor
  dependencies
  scope
  lifecycle
  origin
  qualifiers/bindings
```

---

# 4. Component origins

Possíveis origins:

```text
APPLICATION
FRAMEWORK
PLUGIN
MODULE
TEST_OVERRIDE
FACTORY
```

Isto melhora diagnostics.

Exemplo:

```text
PaymentGateway
provided by:
StripePlugin
```

---

# 5. Discovery categories

Existem três formas principais.

## 5.1 Structural components

Descobertos pelo tipo.

Exemplo:

```java
class UserController extends Controller
```

ou:

```java
class AuditMiddleware implements Middleware
```

---

## 5.2 Reachable components

Plain Java classes necessárias por componentes conhecidos.

Exemplo:

```text
UserController
    ↓
UserService
    ↓
UserRepository
```

`UserService` e `UserRepository` não precisam implementar interface Dhole.

---

## 5.3 Explicit providers

Registrados em configuração/plugin.

```java
bind(PaymentGateway.class)
    .to(StripePaymentGateway.class);
```

ou:

```java
provide(HttpClient.class, context -> ...);
```

---

# 6. Controllers

Controller é explicitamente estrutural:

```java
public class UserController extends Controller {
}
```

O base type serve para:

- discovery;
- routing contract;
- lifecycle meaning.

Não precisa annotation.

---

# 7. Services

Não existe necessidade de:

```java
extends Service
```

ou:

```java
implements Service
```

Exemplo:

```java
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }
}
```

Se um controller precisa dele, ele é componente.

---

# 8. Repositories

Também podem ser plain Java:

```java
public class UserRepository {

    private final Database database;

    public UserRepository(Database database) {
        this.database = database;
    }
}
```

Não existe `@Repository`.

---

# 9. Domain objects

Entidades e value objects normalmente não são componentes.

Exemplo:

```java
public record Money(
    BigDecimal amount,
    Currency currency
) {}
```

O container não deve gerir isso.

---

# 10. DTOs

Records de request/response não são componentes.

```java
record CreateUser(
    String name,
    String email
) {}
```

São tipos de dados.

---

# 11. Constructor rule

A regra oficial é:

> Um componente deve possuir um único constructor elegível ou provider explícito.

Exemplo ideal:

```java
public UserService(
    UserRepository users,
    Mail mail
) {
}
```

---

# 12. Multiple constructors

Isto é ambíguo:

```java
public UserService() {}

public UserService(UserRepository users) {}
```

Sem provider explícito, build error.

Não introduzir `@Inject` como mecanismo normal para resolver.

---

# 13. Field injection

Não suportado como abordagem oficial.

Evitar:

```java
UserService service;
```

com framework a escrever o field invisivelmente.

Razões:

- dificulta testes;
- esconde dependencies;
- permite objetos parcialmente inicializados;
- complica immutability.

---

# 14. Setter injection

Também não é mecanismo principal.

Pode existir em integração muito específica, mas não como DI padrão.

---

# 15. Constructor injection benefits

```text
dependencies visible
object valid after construction
easy testing
final fields
compiler assistance
no hidden mutation
```

---

# 16. Binding model

Contrato:

```text
abstraction
    ↓
provider
```

Exemplo:

```text
PaymentGateway
    ↓
StripePaymentGateway
```

---

# 17. Default binding

Se existe exatamente uma implementação concreta elegível:

```java
PaymentGateway
    ↓
StripePaymentGateway
```

o Dhole pode inferir.

Se existirem várias:

```text
StripePaymentGateway
PaypalPaymentGateway
```

é necessário binding.

---

# 18. Binding configuration

Exemplo:

```java
settings.bind(PaymentGateway.class)
    .to(StripePaymentGateway.class);
```

A API final poderá viver num `Bindings`/`Services` section dos settings.

---

# 19. Instance binding

```java
bind(Clock.class)
    .toInstance(Clock.systemUTC());
```

Útil em infrastructure/tests.

---

# 20. Factory binding

```java
provide(HttpClient.class, context ->
    HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()
);
```

Factories são componentes de infraestrutura, não lógica de negócio.

---

# 21. Generic binding

Exemplo futuro:

```text
Repository<User>
Repository<Order>
```

O DI model precisa distinguir os dois quando generic information é relevante.

---

# 22. Qualifiers

A v0.1 evita qualifiers baseados em annotations.

Alternativas:

```text
named binding
typed wrapper
explicit provider
```

Exemplo possível:

```java
bind(Storage.class)
    .named("private")
    .to(S3PrivateStorage.class);
```

Mas named string bindings devem ser usados com moderação.

Preferir tipos dedicados quando fizer sentido.

---

# 23. Component scopes

Scopes oficiais:

```text
SINGLETON
REQUEST
PROTOTYPE
```

---

# 24. Singleton

Uma instância por `ApplicationContext`.

```text
ApplicationContext
    └── UserService instance
```

É recriada após dev-mode restart.

---

# 25. Request

Uma instância por request.

```text
Request #1 -> AuthContext #1
Request #2 -> AuthContext #2
```

Adequado para:

```text
request auth
tenant context
request-local state
```

---

# 26. Prototype

Nova instância cada vez que é resolvida.

Deve ser usado conscientemente.

---

# 27. Default scope

Proposta v0.1:

```text
application services without mutable request state
    -> SINGLETON
```

Mas o framework deve emitir guidance claro:

```text
singleton components must not store request-specific mutable state
```

Request-specific objects devem usar request scope.

---

# 28. Thread safety

Singleton não significa automaticamente thread-safe.

Documentação deve ensinar:

```java
public class CounterService {
    private int count;
}
```

como potencialmente inseguro.

O framework não deve fingir proteger estado mutável arbitrário.

---

# 29. Scope validation

Erro:

```text
Scope Error

Singleton OrderService depends directly on RequestScoped AuthContext.

Dependency path:
OrderService -> AuthContext
```

O container deve impedir captive dependencies.

---

Decisão M5 (request scope):

- `REQUEST` é implementado no DI: uma instância por request scope, reutilizada dentro do mesmo request, nunca partilhada entre requests;
- o web runtime abre um request scope por HTTP request e fecha-o sempre, com sucesso ou falha; recursos `AutoCloseable` request-scoped são fechados uma única vez, em ordem inversa;
- resolver um componente `REQUEST` fora de um request é erro;
- um `SINGLETON` que dependa de um componente `REQUEST` (diretamente ou através de `PROTOTYPE`) é `Scope Error` na validação do graph;
- não se usa `ThreadLocal`: o scope é passado explicitamente pela pipeline.

---

# 30. Provider/Lazy access

Quando necessário:

```java
Provider<AuthContext>
```

ou abstração equivalente pode resolver uma dependência request-scoped de forma explícita.

Não usar proxies invisíveis.

---

# 31. Component lifecycle

Possível lifecycle:

```text
definition created
    ↓
dependencies resolved
    ↓
instance constructed
    ↓
start callback
    ↓
available
    ↓
stop callback
    ↓
closed
```

---

# 32. Startable components

Para componentes que realmente precisam startup:

```java
public interface Startable {
    void start();
}
```

Não obrigar todos os services a ter hooks.

---

# 33. AutoCloseable

O container deve reconhecer:

```java
AutoCloseable
```

para recursos criados/possuídos por ele.

Exemplo:

```java
class DatabasePool implements AutoCloseable
```

No shutdown:

```java
close()
```

---

# 34. Ownership

Regra importante:

> O container só deve fechar recursos que possui.

Se uma instance foi fornecida externamente:

```java
toInstance(externalClient)
```

ownership deve ser explícito.

Não fechar recursos externos inesperadamente.

---

# 35. Component startup order

Determinado pelo dependency graph.

Exemplo:

```text
Database
   ↓
UserRepository
   ↓
UserService
```

Criar dependency first.

---

# 36. Shutdown order

Inverso:

```text
UserService
   ↓
UserRepository
   ↓
Database
```

Resources de nível superior param antes das dependencies.

---

# 37. Failure during construction

Se:

```text
A constructed
B constructed
C fails
```

container limpa:

```text
B
A
```

quando apropriado.

---

# 38. Circular dependency

Não permitido.

```text
A -> B -> A
```

Erro de build/startup.

Não gerar proxy mágico.

---

# 39. Optional dependency

Pode usar:

```java
Optional<Metrics> metrics
```

ou API específica.

Optional injection não deve esconder erro de configuração importante.

---

# 40. Collection injection

Possibilidade útil:

```java
List<PaymentProvider> providers
```

Dhole pode injetar todas as implementações conhecidas.

Ordering deve ser explícito/determinístico.

---

# 41. Plugin components

Plugin pode registrar componentes:

```java
context.bind(Cache.class)
    .to(RedisCache.class);
```

Origin metadata:

```text
PLUGIN: dhole-redis
```

Se houver conflito:

```text
Binding Conflict

Cache has multiple providers:

- MemoryCache [Dhole Core]
- RedisCache [dhole-redis]

Select a binding explicitly.
```

---

# 42. Framework components

Exemplos:

```text
Router
Validator
SerializerRegistry
Clock
ApplicationContext
```

Nem todos devem ser injectable publicamente.

A superfície injectable deve ser deliberada.

---

# 43. ApplicationContext injection

Evitar:

```java
public UserService(ApplicationContext context)
```

como padrão.

Isto transforma DI em service locator.

Pode ser permitido apenas para infrastructure/framework extensions.

---

# 44. RequestContext injection

Permitido onde faz sentido:

```java
public class AuditService {

    private final RequestContext request;
}
```

Mas isso transforma o componente em request-dependent.

Scope validation aplica-se.

---

# 45. Environment injection

Em vez de chamar env global em qualquer service:

```java
Environment
```

pode ser injetado em infrastructure.

Para business code, preferir typed settings.

---

# 46. Typed settings as components

Exemplo:

```java
public record PaymentSettings(
    URI endpoint,
    String apiKey
) {}
```

Pode ser injectable:

```java
public PaymentClient(
    PaymentSettings settings
) {
}
```

Secrets continuam protegidos em diagnostics.

---

# 47. Test overrides

Tests podem substituir binding:

```java
replace(PaymentGateway.class)
    .with(FakePaymentGateway.class);
```

Origin:

```text
TEST_OVERRIDE
```

Tem prioridade apenas no test context.

---

# 48. Test instance

```java
replace(Clock.class)
    .withInstance(fixedClock);
```

---

# 49. Component metadata

Exemplo:

```text
type:
  com.acme.UserService

scope:
  SINGLETON

origin:
  APPLICATION

constructor:
  UserService(UserRepository)

dependencies:
  UserRepository
```

---

# 50. Component IDs

Internamente componentes podem ter IDs determinísticos.

Exemplo conceptual:

```text
com.acme.UserService
```

Para generic/specialized bindings:

```text
Repository<User>
```

---

# 51. Component registry

Responsabilidades:

```text
register definitions
resolve bindings
validate scopes
detect duplicates
detect cycles
build graph
```

Não cria instances diretamente.

---

# 52. Dependency graph

É separado do registry.

```text
ComponentRegistry
      ↓
DependencyGraph
      ↓
DependencyContainer
```

Essa separação ajuda:

- diagnostics;
- testing;
- CLI visualization.

---

# 53. CLI dependency view

```bash
dhole dependencies
```

Pode mostrar:

```text
UserController
└── UserService
    ├── UserRepository
    │   └── Database
    └── Mail
```

---

# 54. Component inspection

Futuro:

```bash
dhole component UserService
```

Saída:

```text
Type         UserService
Scope        singleton
Origin       application
Constructor  UserService(UserRepository, Mail)

Dependencies
- UserRepository
- Mail
```

---

# 55. Visibility

Private constructors não são injectables.

Package-private/public podem ser suportados conforme module accessibility.

Dhole deve respeitar regras Java, não quebrá-las com reflection agressiva.

---

# 56. Java modules

Se JPMS for suportado futuramente, o component model deve respeitar module boundaries.

Não exigir `opens` indiscriminado.

---

# 57. Inheritance

Um component pode herdar comportamento normal Java.

Mas component discovery não deve depender de inheritance complexo salvo contratos estruturais como `Controller`.

---

# 58. Abstract classes

Não podem ser instantiated.

Podem servir como base.

Se dependency pede abstract type, precisa provider concreto.

---

# 59. Sealed types

Podem melhorar resolução quando uma interface sealed possui implementação conhecida.

Ainda assim, explicit binding pode ser preferível quando há várias.

---

# 60. Records as components

Records podem ser componentes tecnicamente, mas normalmente são data objects.

Não transformar records em services automaticamente só porque possuem constructor.

Reachability + semantics determina.

---

# 61. Static state

O Dhole deve desencorajar service state global estático.

Isto quebra:

- test isolation;
- dev restart;
- multiple application contexts.

---

# 62. Multiple application contexts

A arquitetura deve permitir mais de um context no mesmo processo para testing/tooling.

Por isso evitar globals como:

```java
static ApplicationContext INSTANCE;
```

no core.

---

# 63. Component creation performance

Como constructors/dependencies são conhecidos em build-time, o runtime pode gerar factories.

Exemplo conceptual:

```java
UserService create(Container c) {
    return new UserService(
        c.resolve(UserRepository.class)
    );
}
```

Isso reduz reflection.

---

# 64. Generated factories

Possibilidade:

```text
UserService_DholeFactory
UserController_DholeFactory
```

A decisão pertence ao Metadata Compiler.

O Component Model deve suportar isso.

---

# 65. Reflection fallback

Para third-party classes:

```text
provider/factory
```

é preferido.

Reflection fallback pode existir de forma controlada.

---

# 66. Error quality

Exemplo:

```text
Component Error DHOLE-DI-002

Cannot create OrderService.

Constructor:
OrderService(OrderRepository, PaymentGateway)

Problem:
PaymentGateway has 2 providers.

Providers:
- StripePaymentGateway
- PaypalPaymentGateway

Fix:
Declare an explicit binding in Settings.
```

---

# 67. No hidden proxy by default

O Dhole não deve criar proxies invisíveis para:

```text
transactions
security
lazy DI
scopes
```

quando uma alternativa explícita existe.

Isso diferencia o framework.

---

# 68. Transactions

Transação pertence à database API:

```java
database.transaction(() -> {
    ...
});
```

e não a proxy automática criada em volta de `UserService`.

---

# 69. Security

Security de method-level pode existir no futuro, mas não deve depender de component proxy invisível como requisito fundamental.

Route-level security permanece clara.

---

# 70. Interceptors

Se existirem futuramente:

```text
metrics
tracing
audit
```

devem ser declarados através de API explícita e observável.

Não começar com AOP geral.

---

# 71. Component model and hot reload

No fast restart:

```text
old ApplicationContext
    ↓ destroy components
new ApplicationContext
    ↓ create components
```

Nenhum singleton da aplicação deve escapar silenciosamente para o launcher context.

---

# 72. Launcher-owned services

Apenas devtools infrastructure deve sobreviver:

```text
FileWatcher
Compiler client
Dev server coordinator
```

Não application services.

---

# 73. Memory leak protection

Dev mode deve testar:

```text
component cleanup
thread cleanup
classloader cleanup
resource cleanup
```

Components que mantêm static references a application classes podem impedir unload.

Doctor/dev diagnostics poderão alertar.

---

# 74. Extensibility SPI

Plugins podem registrar component providers através de SPI pública.

Não devem manipular internals do container.

---

# 75. Component model invariants

1. constructor injection é o padrão;
2. field injection não é padrão suportado;
3. cycles são erros;
4. ambiguous binding é erro;
5. components são descobertos estruturalmente, por reachability ou explicit providers;
6. domain objects não são components automaticamente;
7. ApplicationContext não é global singleton;
8. container fecha apenas recursos que possui;
9. scopes são validados;
10. plugin bindings possuem origin identificável;
11. DI graph deve ser inspecionável;
12. proxies invisíveis não são mecanismo central;
13. startup/shutdown seguem dependency order;
14. generated factories podem substituir reflection;
15. test overrides são first-class.

---

# 76. Resumo

O Dhole Component Model pretende fazer isto:

```java
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }
}
```

ser suficiente.

Sem:

```text
@Service
@Component
@Inject
@Autowired
```

O poder vem de:

```text
build-time metadata
+
dependency graph
+
explicit bindings when necessary
+
clear lifecycle
```

e não de annotations espalhadas pela aplicação.
