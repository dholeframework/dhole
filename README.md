# Dhole Framework — Specification v0.1

> **Modern Java backend development, simplified.**

> **Estado:** Draft oficial de arquitetura  
> **Idioma desta versão:** Português  
> **Nomes técnicos:** Inglês  
> **Website:** dhole.org  
> **CLI:** `dhole`  
> **Java namespace:** `org.dhole`

## 1. Objetivo

O Dhole é um framework completo para desenvolvimento backend em Java, pensado para oferecer uma experiência simples, produtiva e coerente sem retirar ao programador o poder da linguagem Java.

A ambição do projeto é aproximar a experiência de desenvolvimento de frameworks como Django da realidade Java:

- configuração central e legível;
- convenções fortes;
- pouca configuração repetitiva;
- suporte nativo a `.env`;
- routing simples;
- dependency injection sem annotations;
- validação integrada;
- segurança integrada;
- persistência integrada por adaptadores;
- testes de primeira classe;
- CLI própria;
- build system próprio;
- acesso ao ecossistema Java sem exigir `pom.xml` ao utilizador.

O Dhole **não pretende ser uma versão reduzida do Spring**. A proposta é repensar a experiência de desenvolvimento backend Java a partir de princípios mais simples.

---

## 2. Filosofia central

A regra principal é:

> **O programador deve escrever a lógica da aplicação. O framework deve remover o código cerimonial.**

O Dhole prefere Java normal a mecanismos mágicos.

```java
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    public User find(long id) {
        return users.find(id)
            .orElseThrow(() -> Errors.notFound("User"));
    }
}
```

Não são necessárias annotations como:

```text
@Autowired
@Service
@Repository
@Component
```

---

## 3. Pilares da v0.1

1. **Plain Java first**
2. **Convention over configuration**
3. **No annotation soup**
4. **Constructor injection**
5. **Configuration as Java**
6. **Secrets outside source code**
7. **Progressive complexity**
8. **Type safety**
9. **Excellent developer experience**
10. **Production-ready foundations**
11. **Modular architecture**
12. **Transparent framework behavior**
13. **Java ecosystem compatibility**
14. **No mandatory pom.xml**

---

## 4. Experiência mínima desejada

Criar um projeto:

```bash
dhole new shop
cd shop
dhole dev
```

Resultado esperado:

```text
Dhole 0.1

Application   shop
Environment   development
Server        http://localhost:8080

✓ Configuration loaded
✓ Dependency graph built
✓ Routes registered
✓ Application started
```

---

## 5. Estrutura inicial

```text
shop/
├── src/
│   ├── main/
│   │   └── java/
│   │       └── shop/
│   │           ├── App.java
│   │           ├── config/
│   │           │   └── Settings.java
│   │           ├── controllers/
│   │           ├── services/
│   │           ├── repositories/
│   │           ├── models/
│   │           ├── middleware/
│   │           ├── jobs/
│   │           └── events/
│   └── test/
├── database/
│   ├── migrations/
│   └── seeds/
├── storage/
├── .env
├── .env.example
├── .gitignore
├── dhole.toml
└── dhole.lock
```

Pastas vazias não precisam de existir. O projeto começa pequeno e cresce quando necessário.

---

## 6. Aplicação mínima

```java
package shop;

import org.dhole.Dhole;

public class App {

    public static void main(String[] args) {
        Dhole.run(App.class);
    }
}
```

Controller:

```java
package shop.controllers;

import org.dhole.web.Controller;
import org.dhole.web.Router;

public class HelloController extends Controller {

    @Override
    public void routes(Router routes) {
        routes.get("/hello", request -> "Hello World");
    }
}
```

O framework descobre controllers por metadata gerada durante o build, sem annotations de registo.

---

## 7. Lifecycle

A arquitetura interna detalhada está em `docs/CORE_ARCHITECTURE.md`.

```text
dhole dev / dhole run
        │
        ▼
Read dhole.toml
        │
        ▼
Resolve dependencies
        │
        ▼
Load environment
        │
        ▼
Load Settings.java
        │
        ▼
Compile / load application metadata
        │
        ▼
Build dependency graph
        │
        ▼
Validate configuration
        │
        ▼
Register controllers and routes
        │
        ▼
Start infrastructure modules
        │
        ▼
Start HTTP server
        │
        ▼
Application ready
```

O lifecycle deve ser observável através da CLI e dos logs. Automação é permitida; comportamento inexplicável não é.

---

## 8. Documentos desta especificação

- [FRAMEWORK_VISION.md](docs/FRAMEWORK_VISION.md)
- [CORE_ARCHITECTURE.md](docs/CORE_ARCHITECTURE.md)
- [METADATA_COMPILER.md](docs/METADATA_COMPILER.md)
- [COMPONENT_MODEL.md](docs/COMPONENT_MODEL.md)
- [MODULE_SYSTEM.md](docs/MODULE_SYSTEM.md)
- [REQUEST_LIFECYCLE.md](docs/REQUEST_LIFECYCLE.md)
- [PARAMETER_BINDING.md](docs/PARAMETER_BINDING.md)
- [CONCURRENCY.md](docs/CONCURRENCY.md)
- [OBSERVABILITY.md](docs/OBSERVABILITY.md)
- [DESIGN_PRINCIPLES.md](docs/DESIGN_PRINCIPLES.md)
- [PROJECT_STRUCTURE.md](docs/PROJECT_STRUCTURE.md)
- [REPOSITORY_STRUCTURE.md](docs/REPOSITORY_STRUCTURE.md)
- [CONFIGURATION.md](docs/CONFIGURATION.md)
- [ROUTING.md](docs/ROUTING.md)
- [DI.md](docs/DI.md)
- [HTTP.md](docs/HTTP.md)
- [VALIDATION.md](docs/VALIDATION.md)
- [SERIALIZATION.md](docs/SERIALIZATION.md)
- [DEV_MODE.md](docs/DEV_MODE.md)
- [HOT_RELOAD.md](docs/HOT_RELOAD.md)
- [PLUGIN_SYSTEM.md](docs/PLUGIN_SYSTEM.md)
- [ERRORS.md](docs/ERRORS.md)
- [SECURITY.md](docs/SECURITY.md)
- [DATABASE.md](docs/DATABASE.md)
- [TESTING.md](docs/TESTING.md)
- [CLI.md](docs/CLI.md)
- [BUILD_SYSTEM.md](docs/BUILD_SYSTEM.md)

---


## 8.1 Arquitetura modular inicial

```text
dhole-core
dhole-config
dhole-di
dhole-http
dhole-routing
dhole-web
dhole-validation
dhole-serialization
dhole-json
dhole-security
dhole-database
dhole-tuprel
dhole-plugin-api
dhole-plugin-runtime
dhole-devtools
dhole-testing
dhole-build
dhole-cli
```

Nem todos são dependências obrigatórias da aplicação. A divisão representa responsabilidades internas e fronteiras arquiteturais.

---

## 9. Decisões já assumidas na v0.1

- Java é a linguagem da aplicação e da configuração.
- `.env` é suportado nativamente para desenvolvimento local.
- Variáveis do sistema têm prioridade sobre `.env`.
- `Settings.java` é a configuração principal da aplicação.
- `dhole.toml` descreve projeto, build e dependências; não substitui `Settings.java`.
- `dhole.lock` fixa versões resolvidas.
- O utilizador não precisa de `pom.xml`.
- Dependências do ecossistema Maven podem continuar a ser utilizadas.
- Dependency injection usa construtores.
- Componentes não precisam de annotations de registo.
- Controllers utilizam routing programático.
- O framework gera metadata em build-time sempre que isso reduzir reflection e erros em runtime.
- Componentes são descobertos por estrutura, reachability ou providers explícitos; não por annotations obrigatórias.
- O Metadata Compiler não substitui `javac`; complementa o build com análise e metadata Dhole.
- Constructor injection é a forma oficial de DI; múltiplos constructors ambíguos causam erro.
- Generated factories e metadata podem substituir reflection em caminhos críticos.
- O request lifecycle é uma pipeline explícita: routing, middleware, security, binding, serialization, validation, handler e response.
- Parameter binding infere apenas quando a source é inequívoca; casos ambíguos exigem binding explícito.
- O Dhole é synchronous-first e pode usar virtual threads/managed concurrency sem obrigar reactive programming.
- Observability é parte da arquitetura de produção, com logs, metrics, traces, health e readiness.
- Capabilities internas compõem-se através do Module System com dependency ordering e graceful shutdown.
- `ApplicationContext` é recriável em development e representa o runtime da aplicação.
- `dhole-core` coordena lifecycle e metadata, mas não depende de servidor HTTP, JSON library ou ORM concretos.
- Plugins configuram extensões antes da construção final do dependency graph.
- Startup parcial deve ser revertido quando uma fase crítica falha.
- Validation é parte oficial da v1 e usa Rules<T> com referências type-safe sempre que possível.
- Serialization possui contratos próprios e JSON oficial, sem acoplar HTTP a uma biblioteca concreta.
- `dhole dev` fornece source watching e fast restart como primeira implementação de hot reload.
- O Plugin System é parte da arquitetura desde v0.1, com `dhole-plugin-api` separado do runtime.
- Persistência é modular.
- Tuprel será o adaptador ORM oficial, mas não ficará acoplado ao `dhole-core`.

---

## 10. Questões ainda abertas

Estas decisões serão tratadas em versões seguintes da especificação:

- versão mínima definitiva de Java antes do primeiro release;
- formato final de migrations;
- registry próprio versus resolução exclusiva de repositórios Java existentes;
- estratégia de native image, se existir;
- WebSockets, GraphQL e gRPC.

---

## 11. Objetivo da primeira versão pública

A primeira versão pública não precisa de suportar todo o backend moderno.

Ela deve fazer muito bem:

```text
configuration
.env
dependency injection
HTTP
routing
controllers
JSON
validation
serialization
errors
dev mode / fast restart
plugin foundations
database integration
transactions
authentication
authorization
testing
CLI
build
```

A prioridade é qualidade da experiência, não quantidade de funcionalidades.

---

## 12. Definição de sucesso

A v1 será considerada bem-sucedida quando um programador Java conseguir:

```bash
dhole new api
dhole dev
```

e construir uma API real com autenticação, base de dados, validação e testes sem precisar de dominar Maven, dezenas de annotations ou configuração extensa do framework.

A sensação desejada é:

> **Continua a ser Java. Só desapareceu o trabalho que não devia ser necessário.**
