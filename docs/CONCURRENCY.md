# Concurrency

## 1. Objetivo

O JRF deve oferecer um modelo de concorrência simples para o developer e seguro para produção.

A filosofia é:

```text
normal synchronous Java code
+
managed concurrency
+
explicit async/background work
```

---

## 2. Virtual Threads

A implementação web deve poder utilizar virtual threads para request handling quando a versão Java suportada o permitir.

Objetivo:

```text
simple blocking style
+
high concurrency
```

Sem obrigar programação reactive para APIs comuns.

---

## 3. Public API Independence

A API pública não deve depender diretamente de uma implementação específica de server/executor.

O developer escreve:

```java
User find(long id) {
    return users.find(id);
}
```

não:

```java
Mono<User>
Future<User>
FrameworkPromise<User>
```

como requisito geral.

---

## 4. Request Concurrency

Cada request é tratado de forma independente.

Request-scoped state não deve ser partilhado entre requests.

Singletons precisam ser thread-safe.

---

## 5. Singleton Safety

Exemplo perigoso:

```java
public class CounterService {

    private int count;

    public int next() {
        return ++count;
    }
}
```

JRF não torna isso seguro automaticamente.

O tooling pode alertar alguns padrões, mas thread safety continua responsabilidade do código.

---

## 6. Managed Executor

Framework pode fornecer:

```java
TaskExecutor
```

para tarefas concorrentes controladas.

Exemplo conceptual:

```java
tasks.run(() -> refreshCache());
```

Essas tasks são conhecidas pelo lifecycle.

---

## 7. Avoid Raw Thread Creation

Desencorajar:

```java
new Thread(...).start();
```

em components.

Razões:

- shutdown;
- hot reload;
- tracing;
- error handling;
- resource leaks.

---

## 8. Background Jobs

Trabalho durável não deve usar simplesmente executor em memória.

Exemplo:

```java
jobs.dispatch(new GenerateInvoice(orderId));
```

é apropriado para jobs que precisam:

- retry;
- persistence;
- monitoring.

---

## 9. Request-local Parallelism

Possível API futura baseada em structured concurrency:

```java
var result = tasks.scope(scope -> {
    var user = scope.fork(() -> users.find(id));
    var orders = scope.fork(() -> orders.forUser(id));

    scope.join();

    return new Profile(
        user.get(),
        orders.get()
    );
});
```

A API final dependerá da versão Java suportada.

---

## 10. Cancellation

Cancellation deve ser propagada quando seguro.

Possíveis triggers:

```text
request timeout
client disconnect
application shutdown
task scope failure
```

---

## 11. Cancellation Safety

Não cancelar arbitrariamente operações que podem deixar estado inconsistente.

Exemplo:

```text
bank transfer database transaction
```

deve confiar na transaction semantics.

---

## 12. Timeouts

Timeouts devem existir em boundaries:

```text
HTTP request
database query
outbound HTTP
cache
queue
external service
```

Evitar um único timeout mágico para tudo.

---

## 13. Outbound HTTP

Configuração:

```java
httpClient
    .connectTimeout(...)
    .requestTimeout(...)
```

Retries são explícitos.

---

## 14. Database

Database adapter deve:

- usar pool gerido;
- integrar cancellation quando driver suporta;
- fechar resources;
- expor query timeout.

---

## 15. Backpressure

Para streaming/queues, o framework deverá oferecer mecanismos próprios.

Não ignorar produtor mais rápido que consumidor.

Isto é uma capacidade posterior.

---

## 16. Thread Ownership

Todo thread/executor criado por módulo deve ter owner.

Exemplo:

```text
SchedulerModule owns scheduler executor
JobsModule owns worker executor
HttpModule owns request executor
```

Owner deve parar recursos no shutdown.

---

## 17. Thread Naming

Threads/platform helpers devem ter nomes diagnósticos:

```text
jrf-scheduler-1
jrf-jobs-3
```

Virtual threads podem seguir naming/tracing equivalente quando útil.

---

## 18. Shutdown

Fluxo:

```text
stop accepting new work
    ↓
signal cancellation where allowed
    ↓
wait for grace period
    ↓
close executors
    ↓
force according to policy
```

---

## 19. Dev Restart

Antes de novo `ApplicationContext`:

```text
all application-owned executors must stop
```

Caso contrário ocorre classloader leak.

---

## 20. Context Propagation

Informação como:

```text
request ID
trace context
security identity
tenant
```

pode precisar de propagation para managed tasks.

JRF não deve depender exclusivamente de `ThreadLocal` sem estratégia clara.

---

## 21. ThreadLocal

Pode ser usado internamente com disciplina.

Regras:

- cleanup obrigatório;
- não usar como hidden global state;
- cuidado com task handoff;
- considerar scoped values quando a versão Java suportada justificar.

---

## 22. Structured Concurrency

O design deve manter espaço para structured concurrency.

Não criar APIs próprias incompatíveis desnecessariamente.

---

## 23. Reactive Integration

Bibliotecas reactive poderão ser usadas através de adapters/plugins.

JRF core não será reactive-first.

---

## 24. CompletableFuture

Pode ser suportado como retorno quando integração exigir:

```java
CompletableFuture<User>
```

Mas não deve tornar-se o tipo obrigatório para handlers concorrentes.

---

## 25. Async Handler

Possibilidade futura:

```java
CompletionStage<User> find(long id)
```

O ResponseMapper deverá saber aguardar/adaptar.

---

## 26. Streaming Handler

Tipos futuros:

```text
StreamResponse
ServerSentEvents
Publisher<T>
```

serão tratados por módulos específicos.

---

## 27. Resource Limits

Production defaults devem considerar:

```text
max connections
max request size
queue sizes
worker concurrency
job concurrency
```

Nunca assumir recursos infinitos.

---

## 28. Load Shedding

Futuro:

```text
too many requests/work
    -> reject early
```

melhor do que colapsar processo.

---

## 29. Concurrency Metrics

Observability deve poder medir:

```text
active requests
queued work
running jobs
executor saturation
timeouts
cancellations
```

---

## 30. Error Handling

Exceptions de tasks geridas não devem desaparecer.

Precisam:

```text
logging
metrics
error handler
job failure state
```

---

## 31. Testing

Testing deve permitir executors determinísticos ou controláveis quando possível.

Scheduler/test clock também ajuda.

---

## 32. Invariants

1. JRF não exige reactive programming;
2. request handlers podem escrever Java síncrono normal;
3. framework-owned concurrency é gerida pelo lifecycle;
4. raw threads são desencorajadas;
5. cancellation não compromete consistência;
6. timeouts existem nos boundaries;
7. application executors param no restart;
8. singleton thread safety não é automática;
9. context propagation é explícita/controlada;
10. concurrency internals não vazam para a API sem necessidade.

---

## 33. Resumo

O objetivo é aproveitar o Java moderno sem transformar código simples numa cadeia de abstrações assíncronas.

> **Concurrency should scale the application, not complicate every method.**
