# HTTP

## 1. Objetivo

A camada HTTP deve ser simples para casos comuns e permitir acesso de baixo nível quando necessário.

---

## 2. Server

A aplicação deve iniciar através de:

```bash
dhole dev
```

ou:

```bash
dhole run
```

Configuração:

```java
settings.http(http -> http
    .host("0.0.0.0")
    .port(envInt("APP_PORT", 8080))
);
```

---

## 3. Request

O Dhole oferece um tipo `Request` para acesso explícito:

```java
Response inspect(Request request) {
    String agent = request.header("User-Agent");
    String ip = request.ip();

    return Response.ok();
}
```

---

## 4. Automatic binding

Quando seguro e não ambíguo, parâmetros Java são associados ao request.

Exemplo:

```java
User find(long id)
```

com rota:

```text
/users/{id}
```

---

## 5. Body binding

```java
record CreateUser(
    String name,
    String email
) {}
```

```java
User create(CreateUser input)
```

JSON:

```json
{
  "name": "Mamadu",
  "email": "mamadu@example.com"
}
```

O framework converte automaticamente.

---

## 6. Responses

Retorno normal:

```java
User find(long id)
```

é serializado.

Lista:

```java
List<User> list()
```

é serializada.

Resposta explícita:

```java
return Response
    .created(user)
    .header("X-Resource", user.id().toString());
```

---

## 7. Status codes

Helpers previstos:

```java
Response.ok(body)
Response.created(body)
Response.noContent()
Response.badRequest(body)
Response.unauthorized(body)
Response.forbidden(body)
Response.notFound(body)
```

Handlers normais não precisam devolver `Response` se o status puder ser inferido/configurado por routing.

---

## 8. JSON

JSON é o formato de API default do módulo web.

O serializer deve:

- suportar records;
- suportar POJOs compatíveis;
- respeitar tipos;
- tratar datas de forma consistente;
- permitir custom serializers;
- evitar exposição acidental de campos sensíveis quando metadata de domínio os marcar.

A biblioteca interna concreta não faz parte do contrato público.

---

## 9. Content negotiation

v1 deve suportar pelo menos:

```text
application/json
text/plain
application/octet-stream
```

Outros media types podem ser adicionados por módulos.

---

## 10. Uploads

Upload deve ter limites configuráveis.

Exemplo conceptual:

```java
UploadedFile file
```

O framework deve proteger contra uploads ilimitados por defeito.

---

## 11. Timeouts

Devem existir defaults e configuração para:

- request timeout;
- idle timeout;
- header read timeout;
- connection timeout em client.

---

## 12. Request IDs

Cada request deve poder receber um ID de correlação.

O ID deve aparecer em logs e, opcionalmente, response headers.

---

## 13. Virtual threads

A implementação poderá usar virtual threads para simplificar concorrência mantendo estilo síncrono.

A API pública não deve acoplar a aplicação a um modelo interno específico de servidor.

---

## 14. HTTP Client

Um módulo cliente pode oferecer:

```java
User user = http
    .get("https://example.com/users/1")
    .as(User.class);
```

Com:

- timeouts;
- headers;
- JSON;
- retries explícitos;
- tracing hooks.

Não deve fazer retries invisíveis para operações não idempotentes.


---

## 15. Integração com Serialization

HTTP não implementa JSON diretamente.

Pipeline:

```text
Request body
    ↓
Content-Type
    ↓
Serialization registry
    ↓
Java type
    ↓
Validation
    ↓
Handler
```

Response:

```text
Handler result
    ↓
Content negotiation
    ↓
Serialization registry
    ↓
HTTP body
```

Consultar `SERIALIZATION.md`.

---

## 16. Integração com Validation

Deserialization e validation são fases diferentes.

```text
invalid JSON
    -> 400 Bad Request

valid JSON + invalid field values
    -> 422 Unprocessable Content
```

Consultar `VALIDATION.md`.


---

## 17. Request Lifecycle

A sequência completa desde a receção do request até cleanup está definida em:

```text
REQUEST_LIFECYCLE.md
```

Binding de path/query/header/body é definido em:

```text
PARAMETER_BINDING.md
```
