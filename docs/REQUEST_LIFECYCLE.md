# Request Lifecycle

## 1. Objetivo

Este documento define o caminho completo de um HTTP request dentro do Dhole.

O lifecycle deve ser:

- previsível;
- observável;
- extensível;
- eficiente;
- seguro;
- consistente.

---

## 2. Visão geral

```text
Client
  ↓
HttpServer
  ↓
Request normalization
  ↓
RequestContext
  ↓
Request ID
  ↓
Router
  ↓
Middleware
  ↓
Security
  ↓
Parameter binding
  ↓
Deserialization
  ↓
Validation
  ↓
Controller handler
  ↓
Response mapping
  ↓
Serialization
  ↓
HTTP Response
```

---

## 3. Stage 1 — Server Receive

O servidor recebe:

```text
method
URI
headers
body
remote information
```

e cria representação interna.

---

## 4. Stage 2 — Normalization

Normalização inclui:

```text
path normalization
header normalization
content length checks
body size limits
protocol validation
```

Requests inválidos podem terminar aqui.

---

## 5. Stage 3 — Request ID

Cada request recebe um identificador.

```text
requestId = generated or trusted incoming ID according to policy
```

Esse ID acompanha:

- logs;
- errors;
- traces;
- response header opcional.

---

## 6. Stage 4 — RequestContext

Criado um contexto request-scoped.

Contém:

```text
request
request ID
route metadata
auth state
request-scoped dependencies
timings
attributes
```

---

## 7. Stage 5 — Route Matching

O Router procura uma `RouteDefinition`.

Exemplo:

```text
GET /users/10
```

match:

```text
GET /users/{id}
```

Se não existir:

```text
404 Not Found
```

Se path existe mas method não:

```text
405 Method Not Allowed
```

---

## 8. Stage 6 — Middleware

Middleware global:

```text
Request ID
logging
CORS
rate limiting
```

Route/group middleware:

```text
authentication
authorization
tenant resolution
custom policies
```

---

## 9. Middleware Order

A ordem é explícita.

Exemplo:

```text
GlobalErrorBoundary
    ↓
RequestLogging
    ↓
Cors
    ↓
RateLimit
    ↓
Authentication
    ↓
Authorization
    ↓
Handler
```

---

## 10. Stage 7 — Security

Security pode:

```text
authenticate
load identity
check role
check permission
reject request
```

Falhas:

```text
401 Unauthorized
403 Forbidden
```

---

## 11. Stage 8 — Parameter Binding

O binder resolve:

```text
path
query
headers
cookies
body
auth
request context
uploaded files
```

Detalhes em:

```text
PARAMETER_BINDING.md
```

---

## 12. Stage 9 — Deserialization

Para structured body:

```text
JSON
  ↓
SerializationRegistry
  ↓
CreateUser
```

Falha de sintaxe/tipo:

```text
400 Bad Request
```

---

## 13. Stage 10 — Validation

Input construído é validado.

Falha:

```text
422 Unprocessable Content
```

Handler não executa.

---

## 14. Stage 11 — Request Scope Resolution

Request-scoped dependencies ficam disponíveis.

Exemplo:

```text
AuthContext
TenantContext
RequestLoggerContext
```

---

## 15. Stage 12 — Controller Handler

Exemplo:

```java
User create(CreateUser input) {
    return users.create(input);
}
```

Handler recebe valores já:

```text
bound
deserialized
validated
authorized
```

---

## 16. Stage 13 — Return Value Mapping

Tipos possíveis:

```text
POJO / record
List<T>
Page<T>
String
byte[]
Response
void
stream
```

São convertidos num `ResponseModel`.

---

## 17. Stage 14 — Content Negotiation

Baseado em:

```text
Accept
available serializers
handler response type
```

Sem serializer suportado:

```text
406 Not Acceptable
```

---

## 18. Stage 15 — Serialization

Exemplo:

```text
UserResponse
    ↓
JsonSerializer
    ↓
application/json
```

---

## 19. Stage 16 — Response Filters

Pode incluir:

```text
security headers
CORS response headers
compression
ETag
cache headers
request ID
```

---

## 20. Stage 17 — Response Write

Servidor envia:

```text
status
headers
body
```

---

## 21. Stage 18 — Cleanup

Mesmo quando há erro:

```text
close request scope
release temporary resources
finish tracing
record metrics
clear request-local state
```

---

## 22. Error Boundary

Existe boundary externo:

```text
try:
    request pipeline
catch:
    ErrorHandlerRegistry
```

Unexpected errors:

```text
500 Internal Server Error
```

Sem stack trace para o cliente em production.

---

## 23. Early Exit

Qualquer stage pode terminar a pipeline.

Exemplo:

```text
rate limit
    -> 429

auth failure
    -> 401

validation failure
    -> 422
```

Stages posteriores não executam.

---

## 24. Async Work

Handler não deve manter request aberto para background work desnecessário.

Exemplo:

```java
jobs.dispatch(new SendWelcomeEmail(user.id()));
return user;
```

---

## 25. Cancellation

Se cliente desconecta:

- operations cancellable podem receber sinal;
- background persistence crítica não deve ser interrompida arbitrariamente sem policy;
- resource cleanup continua obrigatório.

Detalhes em `CONCURRENCY.md`.

---

## 26. Timeouts

Timeout pode existir em:

```text
server read
request execution
upstream HTTP
database
stream
```

Timeout global não substitui timeouts específicos.

---

## 27. Request Logging

Log base:

```text
requestId
method
route
status
duration
```

Evitar logar:

```text
Authorization
passwords
tokens
raw sensitive bodies
```

---

## 28. Metrics Hooks

Possible metrics:

```text
requests total
duration
status
route
active requests
validation failures
auth failures
```

---

## 29. Tracing Hooks

Spans possíveis:

```text
HTTP request
controller
database
outbound HTTP
queue
```

Sem acoplar core a vendor específico.

---

## 30. Test Lifecycle

`ApiTest` deve executar praticamente a mesma pipeline.

Quando possível:

```text
test request
    ↓
in-memory transport
    ↓
same Router/Middleware/Binding/etc.
```

Evitar mocks que bypassam comportamento real do framework.

---

## 31. Dev Mode

Reload nunca deve deixar request scope antigo ligado ao novo `ApplicationContext`.

Durante restart:

```text
stop accepting requests
drain
destroy old context
start new context
```

---

## 32. Request Pipeline Components

Estrutura conceptual:

```text
HttpServer
RequestFactory
RequestContextFactory
Router
MiddlewareChain
SecurityPipeline
ParameterBinder
SerializerRegistry
Validator
HandlerInvoker
ResponseMapper
ErrorHandlerRegistry
```

---

## 33. Stage Timing

Development/observability pode medir:

```text
routing         0.1ms
binding         0.3ms
validation      0.2ms
handler         7.4ms
serialization   0.8ms
```

Sem overhead excessivo quando desativado.

---

## 34. Invariants

1. handler só executa depois de binding e validation;
2. request scope é sempre fechado;
3. error handling cobre toda pipeline;
4. security executa antes do handler;
5. serializer é escolhido explicitamente pelo media type;
6. request ID existe antes dos principais logs;
7. route matching é determinístico;
8. early exits não executam stages posteriores;
9. production não expõe internals;
10. pipeline continua testável sem porta real.

---

## 35. Resumo

A experiência desejada:

```java
User create(CreateUser input) {
    return users.create(input);
}
```

é possível porque o Dhole executa uma pipeline organizada antes e depois desse método.

> **Simple handler, explicit pipeline.**
