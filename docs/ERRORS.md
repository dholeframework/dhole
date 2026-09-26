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

Validation adiciona `fields`. O formato canónico dos erros de campo é o de VALIDATION.md §12: cada erro é um objeto com `code` estável e `message`.

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "The request contains invalid fields.",
    "fields": {
      "email": [
        {
          "code": "INVALID_EMAIL",
          "message": "Must be a valid email address."
        }
      ]
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

---

## 12. Decisões M7

API pública (`org.dhole.web`): `AppException` (code estável, `HttpStatus`, mensagem segura), `Errors` (`notFound(resource)`, `badRequest(message)`, `unauthorized(message)`, `forbidden(message)`, `conflict(message)`), `ErrorResponse` (envelope), `ErrorHandler<E>` (`Response handle(E error)`).

O `ErrorHandlerRegistry` é interno. A superfície pública de registo de handlers customizados fica adiada até existir composição por settings/módulos; não existe API temporária de registo.

Mapeamento central (determinístico; handlers específicos antes do fallback):

```text
BindingException                 -> 400 INVALID_PARAMETER (parâmetros) / BAD_REQUEST (body)
validation failure               -> 422 VALIDATION_ERROR (com fields)
AppException                     -> o seu HttpStatus e code (NOT_FOUND 404, BAD_REQUEST 400,
                                    UNAUTHORIZED 401, FORBIDDEN 403, CONFLICT 409, ...)
rota inexistente / método        -> 404 NOT_FOUND / 405 METHOD_NOT_ALLOWED (Allow)
Accept / Content-Type            -> 406 NOT_ACCEPTABLE / 415 UNSUPPORTED_MEDIA_TYPE
qualquer outra exceção           -> 500 INTERNAL_ERROR
```

Todos os erros do pipeline usam o envelope da secção 7 em `application/json`, com `requestId`; `details` e `fields` são omitidos quando vazios.

`500` em production: mensagem genérica segura, sem stack trace nem nomes de classes. Em development: `details` pode conter apenas o tipo e a mensagem da exceção; o stack trace completo vai para stderr com o request ID, nunca para o body. O modo development/production é, no M7, uma opção interna de composição do web runtime; passará a derivar do `Environment` real quando existir integração com Settings.

Rejeições de protocolo feitas pelo adapter antes do pipeline (`413`, `501`, query malformada) acontecem antes do stage de request ID e mantêm corpo de texto simples.
