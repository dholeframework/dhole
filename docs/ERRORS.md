# Errors

## 1. Filosofia

Errors devem ser previsíveis para o developer e consistentes para o cliente.

---

## 2. Categorias

Inicialmente:

```text
BadRequest
Unauthorized
Forbidden
NotFound
Conflict
ValidationError
RateLimitError
InternalError
```

---

## 3. Throwing framework errors

```java
throw Errors.notFound("User");
```

ou:

```java
return users.find(id)
    .orElseThrow(() -> Errors.notFound("User"));
```

Resposta:

```json
{
  "error": {
    "code": "NOT_FOUND",
    "message": "User not found."
  }
}
```

---

## 4. Domain exceptions

A aplicação pode criar erros próprios:

```java
public class InsufficientBalance extends AppException {

    public InsufficientBalance() {
        super("INSUFFICIENT_BALANCE", 409, "Insufficient balance.");
    }
}
```

A API final pode substituir status inteiro por tipo `HttpStatus`.

---

## 5. Unexpected errors

Em development:

- log completo;
- stack trace local;
- debug page/console útil.

Em production:

- cliente recebe mensagem segura;
- stack trace não é exposto;
- erro recebe correlation/request ID;
- detalhes completos ficam nos logs.

---

## 6. Error handlers

Customização:

```java
errors.handle(InventoryUnavailable.class, error ->
    Response.conflict(Map.of(
        "code", "INVENTORY_UNAVAILABLE"
    ))
);
```

---

## 7. Error envelope

Formato base:

```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable message",
    "details": {},
    "requestId": "..."
  }
}
```

`details` é opcional.

---

## 8. Validation errors

Validation adiciona `fields`.

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid request.",
    "fields": {
      "email": ["Invalid email."]
    },
    "requestId": "..."
  }
}
```

---

## 9. Logging

Cada unexpected error deve registar:

- request ID;
- method;
- route;
- timestamp;
- exception;
- stack trace;
- relevant structured context.

Nunca passwords/tokens.

---

## 10. Startup errors

Startup errors não usam o envelope HTTP.

Devem ser orientados ao developer:

```text
Routing Error

Duplicate route:
POST /users

Handlers:
UserController.create
LegacyController.create
```

---

## 11. Error codes

Códigos são estáveis e machine-readable.

Exemplos:

```text
NOT_FOUND
VALIDATION_ERROR
UNAUTHORIZED
FORBIDDEN
CONFIGURATION_ERROR
DEPENDENCY_ERROR
DATABASE_ERROR
```

Mensagens podem mudar/localizar. Códigos devem ser tratados como contrato.
