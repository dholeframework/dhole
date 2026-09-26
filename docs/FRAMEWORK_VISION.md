# Framework Vision

## 1. Visão

O Dhole Framework pretende tornar o desenvolvimento backend em Java simples o suficiente para um iniciante começar rapidamente e poderoso o suficiente para uma aplicação crescer sem obrigar a migração para outro framework.

O Dhole inspira-se na produtividade de frameworks "batteries included", mas mantém Java como linguagem principal em todas as camadas da aplicação.

Não pretende esconder Java. Pretende remover cerimónia.

---

## 2. Problema que o Dhole procura resolver

O desenvolvimento backend em Java pode exigir que o programador compreenda demasiados conceitos antes de criar funcionalidades simples:

- build tools;
- XML ou DSLs externas;
- dependency management;
- component scanning;
- annotations de routing;
- annotations de DI;
- annotations de validação;
- configuration binding;
- proxies;
- diferentes abstrações para HTTP;
- múltiplas bibliotecas antes de uma aplicação simples ficar pronta.

O problema não é falta de poder. Java possui um ecossistema extremamente poderoso.

O problema que o Dhole ataca é a **fricção entre esse poder e a experiência do programador**.

---

## 3. Proposta

O Dhole oferece uma plataforma backend integrada:

```text
Dhole
├── Core
├── Configuration
├── Dependency Injection
├── HTTP
├── Routing
├── Serialization
├── Validation
├── Plugin API
├── DevTools
├── Error Handling
├── Security
├── Database Integration
├── Testing
├── CLI
└── Build System
```

Funcionalidades posteriores podem incluir:

```text
Jobs
Scheduler
Events
Cache
Mail
Storage
WebSockets
Observability
Messaging
```

---

## 4. Público-alvo

### 4.1 Iniciantes em backend Java

Devem conseguir criar uma API sem estudar primeiro um grande ecossistema de annotations e build configuration.

### 4.2 Programadores Java experientes

Devem continuar a ter:

- type safety;
- debugging normal;
- profiling;
- bibliotecas Java;
- acesso a níveis baixos quando necessário;
- arquitetura modular;
- comportamento previsível.

### 4.3 Equipas

Devem beneficiar de:

- estrutura consistente;
- menos decisões repetitivas;
- convenções;
- tooling;
- testes integrados;
- segurança por defeito;
- observabilidade futura.

---

## 5. Proposta de valor

O Dhole não tenta competir através da frase "faz tudo automaticamente".

A proposta é:

> **Automatizar o repetitivo, manter explícito o importante.**

Exemplo:

```java
public class UserController extends Controller {

    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    public void routes(Router r) {
        r.get("/users/{id}", this::find);
    }

    User find(long id) {
        return users.find(id);
    }
}
```

O código deixa claro:

- qual controller existe;
- qual dependência utiliza;
- qual rota existe;
- qual método trata a rota.

O framework trata:

- criação da instância;
- binding do `id`;
- execução;
- serialização;
- tratamento de erros.

---

## 6. Lifecycle oficial v0.1

### 6.1 Build/development bootstrap

```text
CLI invoked
   ↓
Project manifest loaded
   ↓
Dependency graph resolved
   ↓
Source code compiled
   ↓
Dhole metadata generated
   ↓
Application launched
```

### 6.2 Runtime bootstrap

```text
Application starts
   ↓
Environment loaded
   ↓
Settings loaded
   ↓
Plugins discovered/configured
   ↓
Configuration validated
   ↓
Serialization/validation registries initialized
   ↓
DI container initialized
   ↓
Framework modules initialized
   ↓
Controllers registered
   ↓
Routes registered
   ↓
Infrastructure started
   ↓
Plugins started
   ↓
HTTP server started
   ↓
Ready hooks executed
   ↓
Application ready
   ↓
DevTools watcher active (development only)
```

### 6.3 Shutdown

```text
Shutdown requested
   ↓
Stop accepting new requests
   ↓
Wait for in-flight work
   ↓
Stop jobs/workers
   ↓
Close infrastructure
   ↓
Execute shutdown hooks
   ↓
Exit
```

O shutdown deve ser gracioso por defeito.

---

## 7. Progressive complexity

O framework não deve forçar arquitetura empresarial para um Hello World.

Aplicação pequena:

```text
src/main/java/app/
├── App.java
└── controllers/
    └── HelloController.java
```

Aplicação maior:

```text
src/main/java/app/
├── App.java
├── config/
├── controllers/
├── services/
├── repositories/
├── models/
├── security/
├── middleware/
├── jobs/
├── events/
└── modules/
```

A arquitetura cresce conforme a necessidade.

---

## 8. Relação com Java

O Dhole considera Java um ativo, não um problema.

Por isso preserva:

- classes;
- interfaces;
- records;
- generics;
- exceptions;
- concurrency;
- virtual threads quando apropriado;
- standard library;
- ferramentas de profiling;
- debugging;
- interoperabilidade com `.jar`;
- bibliotecas existentes.

---

## 9. Relação com Tuprel

Tuprel é um projeto independente de ORM/persistência.

O Dhole terá uma integração oficial com Tuprel, mas respeitará esta separação:

```text
Dhole Core
   │
   ├── Database SPI
   │       │
   │       ├── Tuprel Adapter (official)
   │       └── Other adapters (possible)
   │
   └── No direct Tuprel dependency
```

Consequências:

- Tuprel pode ser utilizado sem Dhole.
- Dhole pode teoricamente suportar outra persistência.
- evolução de um projeto não obriga acoplamento interno do outro.

---

## 10. Critério de design

Sempre que houver duas alternativas, o projeto deve perguntar:

1. Qual é mais simples de aprender?
2. Qual produz código mais legível?
3. Qual mantém melhor type safety?
4. Qual falha mais cedo?
5. Qual cria menos comportamento invisível?
6. Qual escala sem obrigar reescrita?
7. Qual permite debugging normal?
8. Qual introduz menos conceitos próprios?
9. Qual aproveita melhor Java?
10. Qual mantém o framework sustentável?

---

## 11. Não objetivos

Na v0.1/v1, o Dhole não precisa de ser:

- framework de frontend;
- framework mobile;
- service mesh;
- plataforma cloud;
- framework de machine learning;
- engine de workflow distribuído;
- implementação própria de base de dados;
- substituto da linguagem Java;
- substituto de todo o ecossistema Java.

O foco é backend Java.


---

## 12. Arquitetura interna

O lifecycle, bootstrap, `Application`, `ApplicationContext`, metadata, module system e shutdown são definidos em:

```text
CORE_ARCHITECTURE.md
```

Este documento deve ser tratado como a referência principal para a arquitetura interna do runtime Dhole.
