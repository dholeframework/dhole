# Parameter Binding

## 1. Objetivo

O Parameter Binding define como dados HTTP são transformados em parâmetros Java sem depender de annotations como:

```text
@PathVariable
@RequestParam
@RequestHeader
@RequestBody
@CookieValue
```

O sistema deve ser simples nos casos comuns e explícito quando existe ambiguidade.

---

## 2. Sources

O Dhole reconhece conceptualmente:

```text
PATH
QUERY
HEADER
COOKIE
BODY
AUTH
REQUEST
FILE
CONTEXT
```

---

## 3. Path Parameters

Route:

```java
routes.get("/users/{id}", this::find);
```

Handler:

```java
User find(long id)
```

Binding:

```text
{id} -> id
```

O tipo `long` define conversão.

---

## 4. Path Type Conversion

Built-ins:

```text
String
byte/Byte
short/Short
int/Integer
long/Long
float/Float
double/Double
boolean/Boolean
UUID
enum
selected time types
```

Falha:

```text
GET /users/abc
```

para:

```java
User find(long id)
```

gera:

```text
400 Bad Request
```

---

## 5. Query Parameters

Para poucos valores, o Dhole pode suportar wrappers explícitos:

```java
List<User> search(
    Query<String> q,
    Query<Integer> page
)
```

Mas isto pode tornar handlers verbosos.

Por isso a forma recomendada para filtros é object binding.

---

## 6. Query Object

```java
public record UserFilter(
    String search,
    Integer page,
    Boolean active
) {}
```

Routing:

```java
routes.get("/users", this::list);
```

Handler:

```java
List<User> list(Query<UserFilter> query) {
    return users.search(query.value());
}
```

Forma simplificada futura pode permitir:

```java
List<User> list(UserFilter filter)
```

somente quando source é não ambígua.

---

## 7. Body

Structured input:

```java
User create(Body<CreateUser> body)
```

seria totalmente explícito, mas demasiado cerimonial para casos comuns.

A experiência desejada é:

```java
User create(CreateUser input)
```

em routes que aceitam body.

O Metadata Compiler pode inferir BODY quando:

- method permite body;
- existe um único structured unbound parameter;
- não existe conflito com path/query types;
- route metadata torna a decisão inequívoca.

---

## 8. Ambiguity Rule

O Dhole nunca deve adivinhar silenciosamente quando duas sources são plausíveis.

Exemplo:

```java
User find(String value)
```

em:

```text
GET /users
```

Sem informação adicional:

```text
Binding Error

Cannot determine source for parameter `value`.

Possible sources:
QUERY
HEADER
```

O developer usa wrapper explícito.

---

## 9. Explicit Binding Types

Tipos conceptuais:

```java
Path<T>
Query<T>
Header<T>
Cookie<T>
Body<T>
Auth<T>
FilePart
Request
RequestContext
```

Exemplo:

```java
User find(Path<Long> id)
```

ou:

```java
List<User> search(Query<String> q)
```

A API final deve minimizar wrappers nos casos óbvios.

---

## 10. Header

```java
Response locale(Header<String> acceptLanguage) {
}
```

Pode existir lookup por conventional name ou constructor API explícita.

Para headers incomuns, nome precisa ser configurado.

---

## 11. Authentication

```java
User profile(Auth<User> auth) {
    return auth.user();
}
```

A presença de `Auth<User>` significa:

```text
authenticated identity required
```

Mas route security continua a fonte principal da política.

---

## 12. Raw Request

Quando necessário:

```java
Response inspect(Request request) {
}
```

Sem annotations.

---

## 13. RequestContext

Infrastructure code pode pedir:

```java
RequestContext context
```

Business handlers devem usá-lo apenas quando necessário.

---

## 14. Cookies

Wrapper:

```java
Cookie<String>
```

ou API pelo `Request`.

Não inferir arbitrary String como cookie.

---

## 15. Files

Upload:

```java
Response upload(UploadedFile file) {
}
```

ou:

```java
Upload<CreateAvatar> input
```

para multipart complexo.

---

## 16. Multiple Files

```java
List<UploadedFile>
```

pode representar multi-part repeated field quando explicitamente mapeado.

---

## 17. Body + Files

Multipart request pode utilizar record:

```java
public record ProfileUpload(
    String name,
    UploadedFile avatar
) {}
```

O multipart binder preenche o record.

---

## 18. Optional Query

```java
Query<Optional<String>> search
```

pode ser suportado, mas APIs devem evitar nesting feio.

Alternativa:

```java
Query<String> search
```

onde `query.optional()` devolve optional.

A ergonomia final será testada.

---

## 19. Defaults

Defaults pertencem ao input type ou binding config.

Exemplo:

```java
public record Paging(
    Integer page,
    Integer size
) {
    public Paging {
        page = page == null ? 1 : page;
        size = size == null ? 20 : size;
    }
}
```

O framework não deve esconder demasiados defaults.

---

## 20. Collections

Query:

```text
?tag=java&tag=backend
```

pode bindar:

```java
List<String>
```

---

## 21. Enums

```text
?status=ACTIVE
```

para:

```java
Status
```

Parsing inválido:

```text
400 Bad Request
```

---

## 22. Dates

Parsing deve seguir formato oficial/configurado.

Exemplo:

```text
2026-09-25
```

para:

```java
LocalDate
```

Formatos custom podem usar converter explícito.

---

## 23. Custom Converters

```java
binding.register(
    Money.class,
    Money::parse
);
```

Plugins podem contribuir converters.

---

## 24. Binding vs Validation

Binding responde:

```text
Can this HTTP value become this Java type?
```

Validation responde:

```text
Is this Java value acceptable for this input?
```

Exemplo:

```text
age=abc
    -> binding failure -> 400

age=15 with min 18
    -> validation failure -> 422
```

---

## 25. Error Format

```json
{
  "error": {
    "code": "INVALID_PARAMETER",
    "message": "The request contains an invalid parameter.",
    "details": {
      "parameter": "id",
      "source": "PATH",
      "expected": "long",
      "value": "abc"
    },
    "requestId": "..."
  }
}
```

Sensitive header/body values devem ser omitidos.

---

## 26. Metadata

Metadata Compiler gera:

```text
handler:
  UserController.find

parameters:
  - name: id
    source: PATH
    type: long
```

Para body:

```text
  - name: input
    source: BODY
    type: CreateUser
```

---

## 27. Build-time Validation

O compiler verifica:

```text
missing path variables
unused path variables
unsupported conversion
multiple BODY parameters
ambiguous parameter source
unsupported multipart shape
```

---

## 28. One Body Rule

Por defeito:

```text
one logical request body per handler
```

Structured records resolvem múltiplos campos.

---

## 29. GET Bodies

O Dhole não deve usar body em GET como convenção normal.

Se tecnicamente permitido, deve exigir API explícita.

---

## 30. DELETE Bodies

Podem existir, mas não são inferidos agressivamente.

Preferir explicit binding se necessário.

---

## 31. Parameter Name Preservation

O build system garante metadata suficiente para não depender acidentalmente de runtime compiler flags.

---

## 32. Extensibility

Plugins podem adicionar sources especializadas:

```text
Tenant<T>
Locale
Session<T>
Principal<T>
```

através de ParameterResolver SPI.

---

## 33. Resolver Contract

Conceptual:

```java
public interface ParameterResolver {

    boolean supports(ParameterMetadata parameter);

    Object resolve(
        ParameterMetadata parameter,
        RequestContext context
    );
}
```

Built-ins têm prioridade definida.

---

## 34. Resolver Conflicts

Dois resolvers que afirmam suportar o mesmo parâmetro:

```text
Binding Configuration Error
```

salvo priority explícita.

---

## 35. Performance

Resolution decisions devem ser pré-computadas em route registration/build-time.

Não iterar todos os resolvers para cada parâmetro em todos os requests quando evitável.

---

## 36. Invariants

1. path binding é pelo nome da route variable;
2. ambiguous source é erro;
3. body binding ocorre antes de validation;
4. binding failure = 400;
5. validation failure = 422;
6. multiple logical bodies não são default;
7. custom resolvers usam SPI;
8. sensitive values não aparecem em diagnostics;
9. route parameter plans são pré-computados;
10. annotations não são mecanismo obrigatório.

---

## 37. Resumo

A experiência desejada continua simples:

```java
User find(long id)

User create(CreateUser input)

User profile(Auth<User> auth)
```

Quando o caso deixa de ser óbvio, o developer pode tornar a source explícita.

> **Infer when certain; require clarity when ambiguous.**

---

## 38. Decisões M6

Registo de handlers tipados: `routes.get("/users/{id}").to(this::find)` (ver ROUTING.md §15). A forma `routes.get(path, handler)` continua a ser o handler de `Request` cru.

Classificação conservadora de sources, decidida em build-time e registada em `routes.idx`:

1. parâmetro do tipo `Request` → `REQUEST`;
2. `Path<T>`, `Query<T>`, `Header<T>`, `Body<T>` → source explícita (nunca reinferida); `Path<T>` exige placeholder com o mesmo nome;
3. nome igual a um placeholder da rota → `PATH` (tipo escalar);
4. um único parâmetro estruturado restante em `POST`/`PUT`/`PATCH`, sem `Body<?>` explícito → `BODY`;
5. qualquer outro caso é erro de build: escalar não ligado, segundo parâmetro estruturado, body inferido em `GET`/`DELETE`, source ambígua. `QUERY` nunca é inferido: usar `Query<T>`.

Placeholders sem parâmetro correspondente são erro de build.

`Header<T>` usa o nome do parâmetro em kebab-case como nome do header (`acceptLanguage` → `Accept-Language`).

Conversões escalares (PATH/QUERY/HEADER): `String`, primitivos e wrappers, `UUID`, enums (nome exato), `LocalDate`, `LocalDateTime`, `Instant` (ISO-8601). Falha de conversão, parâmetro em falta (`Query`/`Header` pedido com `value()`), body em falta ou inválido → `400`; `Content-Type` do body não suportado → `415`; `Accept` sem formato suportado → `406`.

Os wrappers expõem `value()` (falha com `400` quando ausente) e `optional()`.
