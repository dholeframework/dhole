# Serialization

## 1. Estado

Serialization é uma capacidade oficial da arquitetura Dhole v0.1.

Módulos:

```text
dhole-serialization
dhole-json
```

`dhole-serialization` define contratos.

`dhole-json` fornece a implementação JSON oficial.

---

## 2. Objetivo

Permitir que handlers trabalhem com tipos Java:

```java
User find(long id)
```

enquanto o framework trata automaticamente:

```text
Java object
    ↓
serialization
    ↓
HTTP response body
```

E no sentido inverso:

```text
HTTP JSON
    ↓
deserialization
    ↓
CreateUser
```

---

## 3. Contrato base

Interface conceptual:

```java
public interface Serializer {

    <T> T deserialize(
        Input input,
        TypeRef<T> type
    );

    Output serialize(
        Object value,
        TypeRef<?> type
    );
}
```

`TypeRef<T>` é preferido a depender apenas de `Class<T>` porque deve suportar:

```text
List<User>
Map<String, User>
Optional<User>
Page<User>
```

---

## 4. Media types

O registry de serializers associa serializers a media types.

Exemplo:

```text
application/json     -> JsonSerializer
text/plain           -> TextSerializer
application/octet-stream -> BinarySerializer
```

---

## 5. JSON como default

Para APIs Dhole:

```text
application/json
```

é o default para objetos estruturados.

Exemplo:

```java
public record UserResponse(
    long id,
    String name,
    String email
) {}
```

Response:

```json
{
  "id": 1,
  "name": "Mamadu",
  "email": "mamadu@example.com"
}
```

---

## 6. Records

Records devem ter suporte de primeira classe.

```java
public record CreateUser(
    String name,
    String email
) {}
```

Não devem exigir adapters manuais para casos normais.

---

## 7. POJOs

Classes Java normais também devem ser suportadas quando têm metadata/construção compatível.

A API oficial deve favorecer código claro e previsível.

---

## 8. Collections

Suporte mínimo:

```text
List<T>
Set<T>
Map<K, V>
Optional<T>
arrays
```

Generics devem preservar type information sempre que o pipeline Dhole a conhece.

---

## 9. Dates and time

Tipos oficiais:

```text
Instant
LocalDate
LocalDateTime
OffsetDateTime
ZonedDateTime
Duration
```

A representação default deve ser documentada e estável.

Para datas em APIs, preferir padrões ISO.

---

## 10. Enums

Default:

```java
enum Status {
    PENDING,
    APPROVED
}
```

JSON:

```json
"PENDING"
```

Custom representation deverá ser possível.

---

## 11. Null

O serializer deve distinguir:

```text
missing property
explicit null
default value
```

Semântica precisa é especialmente importante para PATCH.

---

## 12. Unknown properties

Default proposto para APIs públicas:

```text
reject unknown properties in strict request DTOs
```

ou configuração explícita por aplicação.

A decisão final do default será validada por ergonomia e compatibilidade, mas nunca deve ser silenciosamente imprevisível.

---

## 13. Sensitive data

Serialization não deve ser usada como substituto de desenho correto de DTOs.

Preferir:

```java
UserResponse
```

a serializar diretamente uma entidade com password.

Ainda assim, metadata de campos sensíveis poderá impedir exposição acidental como proteção adicional.

---

## 14. Cycles

Referências cíclicas não devem provocar recursion infinita.

Quando detetadas:

```text
Serialization Error

Circular reference detected:
User.orders[0].user -> User
```

O framework não deve inventar silenciosamente uma representação.

---

## 15. Custom serializers

```java
serialization.register(
    Money.class,
    new MoneyJsonSerializer()
);
```

Ou via plugin.

---

## 16. Adapters externos

A arquitetura permite:

```text
dhole-jackson
dhole-gson
custom serializer
```

sem acoplar `dhole-http` diretamente a uma biblioteca.

---

## 17. Content negotiation

Pipeline:

```text
Accept header
    ↓
registered serializers
    ↓
best supported media type
```

Se não houver formato suportado:

```text
406 Not Acceptable
```

Body com Content-Type não suportado:

```text
415 Unsupported Media Type
```

---

## 18. Serialization errors

Request:

```text
invalid JSON
wrong numeric type
invalid date
missing required constructor data
```

gera:

```text
400 Bad Request
```

Validation ocorre depois da deserialization.

---

## 19. Build-time metadata

Sempre que possível, o build gera metadata de serialização:

- properties;
- constructors;
- record components;
- generic types;
- custom adapters.

Objetivos:

- menos reflection;
- startup rápido;
- erros antecipados.

---

## 20. Streaming

Streaming de grandes payloads é uma capacidade futura, mas o contrato não deve impedir:

```text
streaming JSON
file streaming
server-sent events
```

---

## 21. Regra central

> **Serialization é infraestrutura. O developer continua a trabalhar com tipos Java.**
