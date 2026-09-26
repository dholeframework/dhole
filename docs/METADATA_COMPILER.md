# Metadata Compiler

## 1. Objetivo

O Metadata Compiler é uma das peças centrais do Dhole Framework.

O seu objetivo é transferir o máximo possível de descoberta, validação e geração de informação do runtime para build-time.

A ideia central é:

```text
understand the application during build
instead of discovering everything during startup
```

Isto permite:

- menos reflection;
- menos classpath scanning;
- startup mais rápido;
- mensagens de erro melhores;
- dependency graph validado cedo;
- routes conhecidas antecipadamente;
- validation metadata conhecida antecipadamente;
- serialization metadata conhecida antecipadamente;
- melhor suporte a CLI e IDE;
- comportamento mais previsível.

---

# 2. Responsabilidades

O Metadata Compiler deve analisar:

```text
application root
controllers
routes
constructors
dependencies
components
validation rules
serialization types
plugins
modules
configuration bindings
```

e gerar metadata consumível pelo runtime.

---

# 3. Não é um novo compilador Java

O Dhole não substitui `javac`.

Pipeline:

```text
Java source
    ↓
javac
    ↓
Dhole metadata analysis
    ↓
generated metadata
```

ou, quando tecnicamente útil:

```text
Java source
    ↓
Dhole compiler integration
    ↓
javac + metadata generation
```

A integração concreta pode usar:

- annotation processing sem exigir annotations;
- compiler APIs;
- bytecode analysis;
- generated source;
- build-time indexing.

A implementação final será escolhida por simplicidade, performance e compatibilidade.

---

# 4. Regra arquitetural

O developer não deve precisar escrever código extra apenas para alimentar o Metadata Compiler.

Exemplo:

```java
public class UserController extends Controller {

    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    public void routes(Router routes) {
        routes.get("/users/{id}", this::find);
    }

    User find(long id) {
        return users.find(id);
    }
}
```

O build deve conseguir inferir:

```text
component:
  UserController

kind:
  controller

constructor:
  UserController(UserService)

dependencies:
  UserService

route:
  GET /users/{id}

handler:
  UserController.find(long)

response:
  User
```

---

# 5. Application root

A análise começa a partir da classe passada a:

```java
Dhole.run(App.class);
```

Exemplo:

```java
package com.acme.shop;
```

Root package:

```text
com.acme.shop
```

Por defeito, apenas esse namespace e subpackages são considerados application code.

Isso evita analisar indiscriminadamente todo o classpath.

---

# 6. Compilation Units

O Metadata Compiler analisa tipos relevantes dentro do application root.

Tipos possíveis:

```text
class
interface
record
enum
sealed class
sealed interface
```

O objetivo não é indexar tudo.

Apenas informação necessária ao framework.

---

# 7. Controller discovery

Regra oficial inicial:

```text
subclass of Controller
    => controller candidate
```

Exemplo:

```java
public class UserController extends Controller {
}
```

Não precisa:

```java
@Controller
```

Nem:

```java
@RestController
```

---

# 8. Service discovery

Services não precisam implementar `Service`.

Um service torna-se parte do graph quando:

```text
a known component depends on it
```

Exemplo:

```java
UserController(UserService users)
```

Então:

```text
UserService
```

entra no dependency graph.

Se `UserService` precisa de:

```java
UserService(UserRepository repository)
```

`UserRepository` também entra.

---

# 9. Reachability model

O Metadata Compiler usa reachability.

Exemplo:

```text
Controller
    ↓
Service
    ↓
Repository
    ↓
Database
```

Classes nunca utilizadas não precisam ser componentes Dhole.

Isto reduz:

- metadata;
- startup;
- ambiguidade;
- component scanning.

---

# 10. Explicit components

Alguns componentes precisam existir mesmo sem serem alcançados diretamente.

Exemplos futuros:

```text
middleware
startup task
job handler
event listener
plugin extension
scheduled task
```

Esses tipos usam contratos Dhole específicos:

```java
implements Middleware
```

ou:

```java
implements StartupTask
```

Não precisam de annotations.

---

# 11. Constructor analysis

Regra inicial:

### Um único construtor

Se existe apenas um construtor utilizável:

```java
public UserService(UserRepository users) {
}
```

é o dependency constructor.

### Zero construtores declarados

Java cria constructor sem argumentos.

Pode ser usado.

### Vários construtores

Quando há mais de um:

```java
public UserService() {}

public UserService(UserRepository users) {}
```

o Dhole não deve adivinhar silenciosamente.

Erro:

```text
Component Error

UserService declares multiple constructors.

Dhole cannot determine which constructor should be used.

Declare one injectable constructor or configure a provider.
```

Não criar `@Inject` como solução principal.

---

# 12. Dependency metadata

Exemplo gerado:

```text
ComponentMetadata:
  type = com.acme.UserService

  constructor:
    parameters:
      - type = com.acme.UserRepository
      - type = org.dhole.mail.Mail
```

O runtime usa isso para construir o graph.

---

# 13. Interface resolution

Quando um constructor recebe:

```java
PaymentGateway
```

o compiler procura providers conhecidos.

### Um provider

```text
StripePaymentGateway
```

resolve automaticamente.

### Mais de um

```text
StripePaymentGateway
PaypalPaymentGateway
```

gera ambiguity.

A binding definida em settings/plugin resolve:

```java
bind(PaymentGateway.class)
    .to(StripePaymentGateway.class);
```

---

# 14. Generic dependencies

O metadata model precisa preservar generics quando relevantes.

Exemplo:

```java
Repository<User>
```

não deve virar simplesmente:

```text
Repository
```

quando o tipo genérico influencia a resolução.

Representação conceptual:

```text
TypeRef:
  raw = Repository
  args:
    - User
```

---

# 15. Route metadata

Controllers registam routes em Java.

O compiler deve conseguir associar method references quando possível.

Exemplo:

```java
routes.get("/users/{id}", this::find);
```

Handler:

```java
User find(long id)
```

Metadata:

```text
method: GET
path: /users/{id}
controller: UserController
handler: find
parameters:
  - id
    source: PATH
    type: long
response:
  type: User
```

---

# 16. Route analysis strategy

Nem todo routing Java é trivial de analisar estaticamente.

Por isso existem dois níveis.

### Static route metadata

Quando o compiler consegue compreender:

```java
routes.get("/users/{id}", this::find);
```

gera metadata completa.

### Runtime registration metadata

Quando route registration contém lógica dinâmica:

```java
for (...) {
    routes.get(...);
}
```

o framework pode executar `routes()` em registration-time.

A regra é:

> metadata compiler otimiza o previsível sem proibir Java válido.

---

# 17. Dynamic routing restrictions

Rotas altamente dinâmicas dificultam:

- OpenAPI;
- `dhole routes`;
- build-time validation;
- conflict detection.

Por isso a documentação recomendará rotas estáticas/convention-driven para aplicações normais.

Dynamic routing continua escape hatch.

---

# 18. Parameter binding metadata

Para:

```text
GET /users/{id}
```

e:

```java
User find(long id)
```

metadata:

```text
parameter:
  name: id
  type: long
  source: PATH
```

O compiler deve validar:

```text
path variable exists
parameter exists
conversion supported
```

---

# 19. Parameter names

O build deve preservar nomes de parâmetros necessários ao framework.

O Dhole build system deve configurar Java compilation de forma apropriada.

Se o mecanismo final não puder confiar em parameter names, metadata gerada resolve isso.

---

# 20. Request body metadata

Exemplo:

```java
User create(CreateUser input)
```

num `POST /users`.

Se não existir outra source explícita e o tipo for structured input:

```text
source = BODY
```

Esta convenção precisa ser determinística.

---

# 21. Binding ambiguity

Exemplo:

```java
User search(String value)
```

em:

```text
GET /users
```

O compiler não deve decidir arbitrariamente se `value` é query, header ou body.

Erro ou exigência de API explícita.

Exemplo futuro:

```java
Query<String> value
```

ou configuração no Router.

---

# 22. Validation metadata

Para:

```java
public record CreateUser(...) implements Validatable
```

o Metadata Compiler regista:

```text
type = CreateUser
validation = available
rulesProvider = CreateUser.rules
```

Também pode validar:

```text
field references
rule/type compatibility
nested validation availability
```

---

# 23. Serialization metadata

Para:

```java
record UserResponse(
    long id,
    String name
) {}
```

metadata pode incluir:

```text
components:
  id -> long
  name -> String
constructor:
  canonical record constructor
```

Isso reduz reflection durante serialization.

---

# 24. Record support

Records são particularmente adequados para metadata.

O compiler conhece:

```text
record components
types
canonical constructor
accessors
```

Sem getters/setters artificiais.

---

# 25. Class serialization metadata

Para classes normais, metadata pode representar:

```text
constructors
readable properties
writable properties
access strategy
```

A política exata será documentada em Serialization.

---

# 26. Configuration metadata

Settings custom:

```java
record PaymentSettings(
    String apiKey,
    String endpoint
) {}
```

Metadata pode suportar:

```text
type-safe configuration binding
CLI config diagnostics
secret masking metadata
```

---

# 27. Plugin metadata

Um plugin deve publicar metadata:

```text
plugin id
plugin version
Dhole compatibility
entry point
provided modules
provided settings
capabilities
```

O build valida compatibilidade antes do runtime.

---

# 28. Module metadata

Módulos podem declarar:

```text
id
dependencies
optional dependencies
startup order constraints
```

Exemplo:

```text
security-web
depends:
  - web
  - security
```

---

# 29. Generated metadata location

Formato conceptual:

```text
META-INF/dhole/
```

Possíveis ficheiros:

```text
application.idx
components.idx
routes.idx
types.idx
validation.idx
serialization.idx
plugins.idx
modules.idx
```

O número real de ficheiros pode mudar.

---

# 30. Metadata format

Requisitos:

- versionado;
- rápido de ler;
- determinístico;
- fácil de invalidar;
- possível de debugar;
- compatível com incremental builds.

Exemplo conceptual:

```text
metadataVersion: 1
frameworkVersion: 0.1.0
```

---

# 31. Text vs binary

A v0.1 não obriga um formato.

Possibilidades:

```text
JSON
CBOR
custom binary
generated Java classes
```

Generated Java possui vantagens:

- type safety;
- zero parser;
- JIT friendly.

Mas pode aumentar output.

A decisão deve ser baseada em benchmark e simplicidade.

---

# 32. Generated Java strategy

Exemplo:

```java
final class UserController_DholeMetadata {

    static final ComponentDefinition DEFINITION = ...;
}
```

Benefícios:

- compilador Java valida output;
- runtime simples;
- menos parsing.

Riscos:

- generated source volume;
- compile cycles;
- debugging visual.

---

# 33. Bytecode analysis strategy

Outra opção:

```text
compile Java
    ↓
analyze class files
    ↓
generate indexes
```

Benefícios:

- trabalha com bytecode real;
- não depende tanto do source compiler AST.

Riscos:

- method references/routes podem ser mais difíceis;
- tooling mais complexo.

---

# 34. Recommended initial implementation

Para a primeira implementação:

```text
javac integration
+
generated metadata index
+
limited bytecode inspection when necessary
```

Não construir um compiler frontend Java próprio.

## 34.1 Decisões M4 (v1)

Integração com `javac`:

- annotation processor standard (`javax.annotation.processing`), sem annotations no código da aplicação;
- observa tipos estruturalmente (`Element`, `TypeElement`, `TypeMirror`); não carrega classes da aplicação; sem bytecode parsing na v1;
- não reivindica annotations (outros processors continuam a funcionar) e não depende da ordem de execução;
- diagnostics via `Messager` (file/line preservados);
- escreve o índice com `Filer` na última round.

Application root:

- opção do processor `-Adhole.application=<fully-qualified class>`, preenchida pelo build a partir de `dhole.toml` `[build] main`;
- root package = package dessa classe; apenas esse package e subpackages entram no índice;
- sem a opção: compilação de biblioteca, sem índice e sem erro;
- não se infere o root a partir de chamadas `Dhole.run(...)`.

Descoberta e validação:

- o índice contém todas as classes concretas (classes e records) do application root presentes na compilação, como providers conhecidos;
- problemas de constructor são registados como dados, não como erros de build (DTOs e utilitários não são componentes);
- a validação de graph em build-time requer roots estruturais (Controller, M5+) e bindings conhecidos em build-time; no M4 não existem, pelo que DHOLE-DI-001/002/003 são reportados pelo runtime DI com a localização registada no índice.

Runtime:

- `dhole-di` contém um leitor interno do formato; `dhole-di` não depende de `dhole-compiler`;
- reflection apenas para invocar o constructor já registado; sem análise estrutural em runtime para tipos indexados;
- tipos fora do índice (bibliotecas) usam o fallback reflexivo controlado do M3, com semântica idêntica;
- sem generated factories na v1: constructors package-private continuam não suportados (limitação intencional da v1, não regra permanente).

## 34.2 Formato `META-INF/dhole/components.idx` v1

UTF-8, linhas terminadas em `\n`, sem timestamps, paths absolutos ou valores de configuração.

```text
dhole-metadata 1

component <binary-name>
constructor [<type> ...]
unusable <reason> [<argument> ...]
supertype <binary-name>
source <relative-path>:<line>
```

Regras:

- primeira linha exatamente `dhole-metadata 1`; outra versão falha com erro de compatibilidade;
- um bloco por componente, separado por linha vazia, ordenado por `<binary-name>`;
- dentro do bloco, pela ordem: `component`, `constructor` (se existir exatamente um constructor público elegível), `unusable` (se o tipo não é utilizável), `supertype` (zero ou mais, ordenados), `source` (opcional);
- `<type>` usa o formato de `Class.forName` (binary names, primitives como `int`, arrays como `[Ljava.lang.String;`);
- `<reason>`: `not-public`, `not-static-nested`, `no-public-constructor`, `multiple-public-constructors <count>`, `parameterized-dependency <index>`;
- `supertype` lista os supertypes transitivos (erasure), excluindo `java.lang.Object`;
- `source` usa o path relativo ao package (`com/acme/shop/UserService.java`) e a linha do constructor (ou da classe);
- linhas desconhecidas ou blocos inválidos falham como metadata malformada.

Exemplo:

```text
dhole-metadata 1

component com.acme.shop.UserRepository
constructor
source com/acme/shop/UserRepository.java:3

component com.acme.shop.UserService
constructor com.acme.shop.UserRepository
source com/acme/shop/UserService.java:7
```

---

# 35. Incremental compilation

O compiler precisa saber:

```text
what changed
what metadata depends on it
```

Exemplo:

```text
CreateUser.java changed
```

Pode invalidar:

```text
validation metadata
serialization metadata
controllers using CreateUser
OpenAPI metadata
```

---

# 36. Dependency graph invalidation

Se constructor muda:

```java
UserService(UserRepository)
```

para:

```java
UserService(
    UserRepository,
    Mail
)
```

reconstruir dependency graph relevante.

---

# 37. Route invalidation

Se controller muda:

```text
/users/{id}
```

para:

```text
/customers/{id}
```

regenerar route metadata.

---

# 38. Dev mode integration

```text
file watcher
    ↓
changed source set
    ↓
incremental Java compile
    ↓
metadata compile
    ↓
validation
    ↓
restart application context
```

Metadata compilation faz parte do fast restart.

---

# 39. Build failures

Erros de metadata devem ser tratados como build errors.

Exemplo:

```text
Routing Error

Route:
GET /users/{id}

Handler:
UserController.find(String userId)

Path variable `{id}` has no matching handler parameter.

Expected a parameter named `id`.
```

---

# 40. Error location

Sempre que possível:

```text
file
line
column
symbol
```

Exemplo:

```text
src/main/java/shop/controllers/UserController.java:18
```

Para integração futura com IDE.

---

# 41. Diagnostics codes

Erros Dhole podem ter códigos estáveis:

```text
DHOLE-DI-001
DHOLE-ROUTE-003
DHOLE-VAL-002
DHOLE-SER-004
```

Isso ajuda:

- documentação;
- search;
- IDE;
- CI;
- support.

---

# 42. Warnings

Nem tudo é erro.

Exemplo:

```text
Dhole Warning DHOLE-WEB-011

Route returns persistence entity User directly.

Consider returning a response DTO to avoid accidental data exposure.
```

Warnings devem ser úteis e não excessivos.

---

# 43. Strict mode

Possibilidade:

```toml
[build]
warningsAsErrors = true
```

ou:

```bash
dhole build --strict
```

---

# 44. CLI usage

Metadata alimenta:

```bash
dhole routes
dhole dependencies
dhole config
dhole doctor
dhole info
```

Sem precisar iniciar a aplicação em todos os casos.

---

# 45. OpenAPI future

O mesmo metadata model pode permitir:

```text
route
request body
response type
validation rules
security requirements
```

gerar OpenAPI futuramente.

Não criar um metadata model exclusivo para OpenAPI.

---

# 46. IDE tooling future

Metadata Compiler pode alimentar:

```text
route navigation
dependency graph visualization
config completion
validation diagnostics
plugin diagnostics
```

---

# 47. Native compatibility

Menos reflection e metadata explícita melhoram compatibilidade futura com native compilation.

Isso é benefício secundário, não requisito da v0.1.

---

# 48. Reflection policy

Reflection não é proibida.

Regra:

> use reflection where it is the simplest safe tool, but do not make runtime reflection the architecture.

O framework deve preferir metadata quando já conhece a informação no build.

---

# 49. Runtime fallback

Para bibliotecas externas ou dynamic cases:

```text
metadata unavailable
    ↓
controlled reflection fallback
```

Deve ser explícito e diagnosticável.

---

# 50. Metadata cache

Build output pode guardar cache.

Key conceptual:

```text
source hash
compiler version
Dhole metadata version
relevant dependency versions
```

Mudança relevante invalida cache.

---

# 51. Determinism

O mesmo source + mesmas dependencies deve gerar metadata equivalente.

Isto é requisito para:

- reproducible builds;
- caching;
- debugging.

---

# 52. Security

Metadata nunca deve incluir secret values.

Pode incluir apenas:

```text
required env key names
setting structure
secret classification
```

Nunca:

```text
JWT secret actual value
database password
API keys
```

---

# 53. Metadata Compiler modules

Estrutura conceptual:

```text
dhole-compiler/
├── discovery/
├── components/
├── dependencies/
├── routing/
├── validation/
├── serialization/
├── plugins/
├── modules/
├── diagnostics/
├── generation/
└── incremental/
```

---

# 54. Compiler phases

```text
Phase 1  Discover
Phase 2  Analyze types
Phase 3  Build component graph
Phase 4  Analyze routes
Phase 5  Analyze validation
Phase 6  Analyze serialization
Phase 7  Analyze plugins/modules
Phase 8  Cross-validation
Phase 9  Generate metadata
Phase 10 Emit diagnostics
```

---

# 55. Cross-validation

Exemplos:

### Route + serialization

```text
handler returns type
but no serializer exists
```

### Route + validation

```text
body type has invalid rule declaration
```

### DI + plugins

```text
plugin provides conflicting binding
```

### Module graph

```text
module cycle
```

---

# 56. Compiler output contract

Runtime deve confiar que metadata já foi validada.

Mas ainda deve defender-se contra:

```text
metadata corruption
version incompatibility
missing generated files
```

---

# 57. Metadata version mismatch

Erro:

```text
Metadata Compatibility Error

Application metadata version: 3
Runtime supports: 2

Rebuild the application using a compatible Dhole build tool.
```

---

# 58. Development inspectability

Comando futuro:

```bash
dhole metadata
```

Pode mostrar:

```text
Components  37
Routes      18
Validators   7
Serializers 12
Plugins      3
```

Modo verbose:

```bash
dhole metadata --component UserController
```

---

# 59. Architectural invariants

1. metadata generation acontece antes do runtime normal;
2. metadata não contém secrets;
3. metadata é versionada;
4. runtime scanning indiscriminado não é fallback padrão;
5. compiler diagnostics devem apontar para código da aplicação;
6. dynamic Java continua possível, mas perde algumas otimizações;
7. Metadata Compiler não substitui `javac`;
8. plugins participam do compile/build contract quando necessário;
9. output deve ser determinístico;
10. runtime deve conseguir verificar compatibility.

---

# 60. Resumo

O Metadata Compiler é o mecanismo que permite ao Dhole combinar:

```text
plain Java
+
minimal annotations
+
fast startup
+
good diagnostics
+
low reflection
```

sem obrigar o developer a escrever configuração repetitiva.

A filosofia é:

> **Se o framework consegue compreender algo durante o build, não deve esperar pelo runtime para descobrir.**
