# Design Principles

## 1. Plain Java First

O código da aplicação deve parecer Java normal.

Evitar APIs que transformem toda a aplicação numa DSL específica do framework.

Bom:

```java
public class PaymentService {

    private final PaymentGateway gateway;

    public PaymentService(PaymentGateway gateway) {
        this.gateway = gateway;
    }
}
```

Evitar como requisito:

```java
@Service
@FrameworkManaged
@AutoDiscover
public class PaymentService {}
```

---

## 2. No Annotation Soup

Annotations não são proibidas em absoluto, mas não são o mecanismo principal do Dhole.

Uma annotation só deve existir quando:

- representa metadata declarativa realmente útil;
- não existe alternativa Java mais simples;
- o comportamento continua previsível;
- não é necessária apenas para "marcar" uma classe para o container.

O framework não exige annotations para:

- controllers;
- services;
- repositories;
- dependency injection;
- routing principal;
- configuração.

---

## 3. Convention Over Configuration

Convenções eliminam decisões repetitivas.

Exemplos:

- `Settings.java` representa configuração.
- subclasses de `Controller` são candidatas a controller.
- construtores representam dependências.
- `.env` é carregado em desenvolvimento.
- JSON é a resposta default de objetos serializáveis.

Todas as convenções importantes devem ter forma explícita de override.

---

## 4. Explicit When Important

Segurança, transações e operações destrutivas devem ser claras.

Bom:

```java
database.transaction(() -> {
    accounts.debit(source, amount);
    accounts.credit(target, amount);
});
```

Evitar transações iniciadas por comportamento pouco visível.

---

## 5. Fail Early

Problemas devem ser detetados o mais cedo possível.

Prioridade:

```text
build-time
    ↓
startup
    ↓
request-time
```

Exemplos:

- dependência DI impossível: build/startup error;
- rota duplicada: build/startup error;
- variável de ambiente obrigatória ausente: startup error;
- regra de validação inválida: build error quando possível.

---

## 6. Type Safety

Evitar strings quando Java pode representar o conceito com tipos.

Preferir:

```java
Duration.ofMinutes(10)
```

a:

```java
"10m"
```

quando a API Java direta for mais segura.

Strings continuam aceitáveis onde representam naturalmente protocolos externos, rotas ou chaves.

---

## 7. Progressive Complexity

O utilizador paga a complexidade apenas quando precisa dela.

Uma aplicação simples não precisa conhecer:

- modules;
- custom scopes;
- service providers;
- lifecycle hooks avançados;
- custom serialization;
- custom transport.

Uma aplicação grande pode utilizar esses mecanismos.

---

## 8. Observable Magic

Automação interna deve ser inspecionável.

A CLI deve permitir:

```bash
dhole routes
dhole config
dhole dependencies
dhole doctor
```

Uma decisão automática não deve tornar-se impossível de explicar.

---

## 9. Secure by Default

Defaults devem favorecer segurança:

- `.env` ignorado no Git;
- debug desligado em production;
- secrets mascarados nos logs;
- password hashing com algoritmo seguro por configuração oficial;
- cookies seguros em production;
- CORS restritivo por defeito;
- stack traces não expostos ao cliente em production;
- validation ativa;
- limits HTTP razoáveis.

---

## 10. Production Is Not an Afterthought

Desde cedo o framework deve pensar em:

- graceful shutdown;
- health checks;
- structured logs;
- request IDs;
- metrics hooks;
- configuration validation;
- connection pools;
- timeouts;
- retries explícitos;
- deployment.

---

## 11. Modular Core

Nenhum módulo deve obrigar dependências desnecessárias.

Exemplo:

```text
dhole-core
   ↑
dhole-web
   ↑
dhole-security
```

Database, mail, cache e outros módulos não devem ser obrigatórios numa aplicação que não os utiliza.

---

## 12. Java Ecosystem Compatibility

O Dhole não cria uma ilha.

Deve ser possível utilizar bibliotecas Java normais:

```bash
dhole add org.jsoup:jsoup:VERSION
```

O build system resolve dependências sem exigir que o programador escreva `pom.xml`.

---

## 13. Stable Public Surface

APIs públicas devem ser pequenas e deliberadas.

Classes internas não devem ser expostas sem necessidade.

O framework deve distinguir:

```text
public API
internal API
SPI
generated API
```

---

## 14. Good Errors Are a Feature

Mensagens de erro fazem parte da experiência do framework.

Erro ruim:

```text
BeanCreationException: null
```

Erro desejado:

```text
Dependency Error

UserController requires UserService,
but Dhole could not construct UserService.

Constructor:
UserService(UserRepository users)

Missing dependency:
UserRepository

Location:
shop.services.UserService
```

---

## 15. One Obvious Way for Common Tasks

Operações frequentes devem ter uma abordagem preferida:

- config: `Settings.java`;
- secrets: environment;
- DI: constructor;
- routing: `Router`;
- errors: framework error types/handler;
- tests: `ApiTest`;
- dependencies: `dhole add`;
- build: `dhole build`.

Extensibilidade não deve destruir consistência.


---

## 16. Startup Must Be Reversible

Se o startup falhar depois de recursos já terem sido inicializados, o framework deve encerrar esses recursos na ordem inversa sempre que possível.

Exemplo:

```text
Database started
Cache started
HTTP initialization failed
```

Dhole deve tentar:

```text
stop Cache
stop Database
fail application
```

Startup parcial não deve deixar recursos vivos sem necessidade.
