# Observability

## 1. Objetivo

Observability é parte da arquitetura de produção do JRF.

O framework deve permitir compreender:

```text
what is happening
where it is slow
why it failed
whether it is healthy
```

sem acoplar aplicações a um vendor específico.

---

## 2. Pilares

```text
Logs
Metrics
Traces
Health
Diagnostics
```

---

## 3. Módulo

Planeado:

```text
jrf-observability
```

Core fornece hooks mínimos; o módulo oferece funcionalidades completas.

---

## 4. Logging

API:

```java
log.info("Order created {}", order.id());
log.warn("Payment retry {}", payment.id());
log.error("Payment failed", error);
```

Internamente pode usar facade compatível.

---

## 5. Structured Logging

Production deve suportar formato estruturado.

Exemplo:

```json
{
  "level": "INFO",
  "event": "http.request",
  "requestId": "abc",
  "method": "GET",
  "route": "/users/{id}",
  "status": 200,
  "durationMs": 12
}
```

---

## 6. Development Logs

Mais legíveis para humanos:

```text
GET /users/42  200  12ms
```

---

## 7. Sensitive Data

Nunca logar automaticamente:

```text
Authorization header
cookies de sessão
password
JWT
API keys
database password
full payment data
```

Redaction deve existir.

---

## 8. Request ID

Gerado cedo no lifecycle.

Disponível em:

```text
logs
errors
traces
response header optional
```

---

## 9. Correlation

Outbound calls podem propagar correlation headers conforme policy.

Não confiar cegamente em IDs recebidos externamente.

---

## 10. Metrics

Built-ins planeadas:

```text
http_requests_total
http_request_duration
http_active_requests
http_errors_total

validation_failures_total
auth_failures_total

db_queries_total
db_query_duration

app_startup_duration
reload_duration
```

Nomes finais podem seguir standards de integração.

---

## 11. Labels

Evitar cardinality explosiva.

Bom:

```text
route="/users/{id}"
status="200"
method="GET"
```

Mau:

```text
path="/users/928374928374"
userId="928374928374"
```

---

## 12. Tracing

Hooks para distributed tracing.

Span principal:

```text
HTTP GET /users/{id}
```

Children:

```text
UserService
DB query
Outbound HTTP
Cache
```

Não criar span para cada método Java automaticamente.

---

## 13. Standards

A arquitetura deve permitir integração com standards como OpenTelemetry através de adapters/plugins.

O core não depende de vendor específico.

---

## 14. Health

Endpoint:

```text
/health
```

pode responder se processo está vivo.

---

## 15. Readiness

Endpoint:

```text
/ready
```

indica se aplicação está pronta para tráfego.

Pode considerar:

```text
database required
critical plugins
initialization completed
```

---

## 16. Liveness vs Readiness

Não são iguais.

```text
liveness:
process should stay running?

readiness:
should traffic be sent here?
```

Database temporariamente indisponível pode afetar readiness sem exigir matar o processo, dependendo da policy.

---

## 17. Health Checks

SPI:

```java
public interface HealthCheck {

    HealthResult check();
}
```

Plugins podem contribuir:

```text
database
redis
kafka
mail
```

---

## 18. Health Output

Exemplo:

```json
{
  "status": "UP",
  "checks": {
    "database": "UP",
    "redis": "UP"
  }
}
```

Production pode ocultar detalhes sensíveis.

---

## 19. Startup Diagnostics

Development:

```text
Configuration     12ms
Metadata           5ms
DI Graph           7ms
Database          18ms
HTTP              22ms

Ready in 64ms
```

---

## 20. Module Timings

Cada módulo pode fornecer:

```text
configure
validate
start
stop
```

timings para diagnóstico.

---

## 21. Database Observability

Integração deve medir:

```text
query duration
pool usage
timeouts
errors
```

SQL logging é development-only/configurable.

---

## 22. Slow Queries

Possibilidade:

```text
query > threshold
    -> warning
```

Sem alterar query automaticamente.

---

## 23. HTTP Client Observability

Outbound:

```text
method
host
status
duration
timeout
retry
```

Sem logar full sensitive URL/query quando não seguro.

---

## 24. Error Correlation

Unexpected error:

```text
errorId
requestId
traceId
```

Cliente pode receber:

```json
{
  "error": {
    "code": "INTERNAL_ERROR",
    "requestId": "..."
  }
}
```

Logs têm detalhes completos.

---

## 25. `jrf doctor`

Observability/diagnostics alimentam:

```bash
jrf doctor
```

Checks:

```text
config
database
plugin health
security settings
metadata compatibility
dependency graph
```

---

## 26. `jrf info`

```bash
jrf info
```

Pode mostrar:

```text
Application
Version
JRF version
Environment
Modules
Plugins
Java version
```

---

## 27. Runtime Diagnostics

Futuro:

```bash
jrf inspect
```

pode consultar processo local/management endpoint.

Não entra no kernel inicial.

---

## 28. Production Logging Configuration

`Settings.java`:

```java
settings.logging(logging -> logging
    .level(INFO)
    .format(JSON)
);
```

Development:

```java
format(PRETTY)
```

---

## 29. Sampling

Tracing em alto volume pode usar sampling.

Policy configurável.

---

## 30. Exporters

Adapters/plugins podem exportar:

```text
OTLP
Prometheus
vendor-specific targets
```

Core continua independente.

---

## 31. Performance

Observability desligada ou mínima não deve impor overhead elevado.

Instrumentation deve ser desenhada para paths críticos.

---

## 32. Dev Mode Integration

Reload event:

```text
source change detected
compile duration
metadata duration
restart duration
```

Ajuda a otimizar `jrf dev`.

---

## 33. Plugin Observability

Plugin pode:

```text
register health check
register metrics
create trace spans
log through JRF facade
```

Sem substituir global logging silenciosamente.

---

## 34. Audit Logging

Audit é diferente de operational logging.

Exemplo:

```text
user X deleted order Y
```

Deve existir como módulo/plugin/application feature específica.

Não misturar automaticamente com debug logs.

---

## 35. Security

Observability endpoints podem conter informação sensível.

Production deve suportar:

```text
disabled
internal-only
authenticated
restricted network
```

---

## 36. Invariants

1. request ID existe cedo;
2. logs não expõem secrets;
3. route templates são usados em metrics;
4. core não depende de vendor;
5. health e readiness são conceitos distintos;
6. tracing é contextual, não method-by-method automático;
7. observability deve funcionar com plugins;
8. production output é estruturável;
9. errors são correlacionáveis;
10. instrumentation não deve quebrar application behavior.

---

## 37. Resumo

O JRF deve tornar aplicações fáceis de operar, não apenas fáceis de escrever.

> **If production cannot explain what the application is doing, the framework is incomplete.**
