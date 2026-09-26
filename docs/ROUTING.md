# Routing

## 1. Objetivo

Routing deve ser simples, explícito e independente de annotations.

---

## 2. Controller básico

```java
public class UserController extends Controller {

    @Override
    public void routes(Router routes) {
        routes.get("/users", this::list);
        routes.get("/users/{id}", this::find);
        routes.post("/users", this::create);
    }

    List<User> list() {
        return users.all();
    }

    User find(long id) {
        return users.find(id);
    }

    User create(CreateUser input) {
        return users.create(input);
    }
}
```

---

## 3. HTTP methods

API inicial:

```java
routes.get(path, handler);
routes.post(path, handler);
routes.put(path, handler);
routes.patch(path, handler);
routes.delete(path, handler);
routes.options(path, handler);
routes.head(path, handler);
```

---

## 4. Path parameters

Rota:

```java
routes.get("/users/{id}", this::find);
```

Handler:

```java
User find(long id)
```

Binding esperado:

```text
{id} -> long id
```

Falha de conversão produz `400 Bad Request`, não exception genérica.

---

## 5. Route groups

```java
routes.group("/api", api -> {
    api.get("/health", this::health);

    api.group("/users", users -> {
        users.get("/", this::list);
        users.post("/", this::create);
    });
});
```

---

## 6. Middleware

```java
routes.group("/admin", admin -> {
    admin.use(auth());
    admin.use(role("ADMIN"));

    admin.get("/users", this::listUsers);
});
```

---

## 7. Named routes

A v0.1 reserva suporte para nomes:

```java
routes.get("/users/{id}", this::find)
    .name("users.find");
```

Útil para:

- reverse routing;
- logs;
- métricas;
- documentação;
- testes.

---

## 8. Resource routes

CRUD convencional:

```java
app.resource("/users", UserController.class);
```

Mapeamento:

```text
GET     /users         -> list
GET     /users/{id}    -> find
POST    /users         -> create
PUT     /users/{id}    -> update
DELETE  /users/{id}    -> delete
```

Métodos que não existem não devem ser registados automaticamente.

---

## 9. Route conflicts

Rotas duplicadas são erro de startup/build.

```text
Routing Error

Duplicate route:
GET /users/{id}

Declared by:
UserController.find
LegacyUserController.show
```

---

## 10. Query parameters

Para poucos parâmetros, poderá existir binding direto:

```java
List<User> search(String query, int page)
```

Para filtros maiores, preferir object binding:

```java
record UserFilter(
    String query,
    Integer page,
    Boolean active
) {}
```

```java
List<User> search(UserFilter filter)
```

A API final de distinção entre body/query/path será validada em implementação para evitar ambiguidades.

---

## 11. Route metadata

O build gera metadata para permitir:

```bash
dhole routes
```

Exemplo:

```text
METHOD  PATH          HANDLER
GET     /users        UserController.list
GET     /users/{id}   UserController.find
POST    /users        UserController.create
```

---

## 12. No hidden endpoint generation

Fora de recursos explicitamente registados, um método público num controller não vira endpoint apenas por existir.

Isso evita exposição acidental.

---

## 13. Versioning

O framework não impõe uma estratégia.

Pode ser implementada:

```java
routes.group("/api/v1", v1 -> {
    // ...
});
```

Estratégias por header poderão ser adicionadas posteriormente.

---

## 14. Decisões M5

Contrato de handler do M5:

```java
@FunctionalInterface
public interface Handler {
    Object handle(Request request) throws Exception;
}
```

- `Router.get/post/put/patch/delete(String, Handler)` devolvem `RouteDefinition`.
- No M5, `Handler` significa "executar um request já encontrado pelo router". Binding de parâmetros Java e invocação de métodos arbitrários (`User find(long id)`) pertencem ao M6.
- Restrição registada para o M6: adicionar outro overload genérico de um argumento diretamente a `Router.get(String, ...)` tornaria lambdas implícitas de um argumento (`request -> "Hello World"`) potencialmente ambíguas. O M6 deve introduzir method references tipadas de forma compatível com o código-fonte existente; o desenho concreto é decidido no M6.

Regras de routing do M5:

- templates começam por `/`, sem segmentos vazios; `{name}` ocupa um segmento inteiro; nomes de parâmetros não se repetem na mesma rota; templates malformados são erro de registo;
- groups compõem prefixos sem barras duplicadas; `group("/users", users -> users.get("/", ...))` regista `/users`; groups podem ser aninhados (secção 5);
- sem normalização de barra final no request: `/hello/` não corresponde a `/hello`;
- segmentos literais têm precedência sobre parâmetros na mesma posição; o matching é determinístico e não depende da ordem de registo;
- path inexistente → `404`; path existente com método não registado → `405` com header `Allow` (métodos registados, ordem de `HttpMethod`); route matching ocorre antes do middleware;
- `HEAD` corresponde à rota `GET` do path (sem body na resposta); não há registo de rotas `HEAD`;
- rotas com o mesmo método e a mesma forma (nomes de parâmetros ignorados) são conflito antes do servidor aceitar tráfego;
- `use(middleware)` aplica-se às rotas do group (e groups aninhados) e deve ser declarado antes das rotas desse group; a ordem é: group exterior primeiro, depois ordem de `use`;
- valores de path parameters são entregues em bruto (strings, percent-decoded); conversão de tipos é M6.

---

## 15. Decisões M6 — handlers tipados

Sintaxe canónica para handlers com binding de parâmetros:

```java
routes.get("/users/{id}").to(this::find);
routes.post("/users").to(this::create);

User find(long id) { ... }
User create(CreateUser input) { ... }
```

- `get/post/put/patch/delete(String path)` devolvem um `RouteBuilder`; `to(...)` aceita `Handler0` a `Handler3` (funções Java por aridade, sem semântica HTTP) e devolve `RouteDefinition`.
- A forma de dois argumentos `routes.get(path, request -> ...)` continua a ser a API de handler de `Request` cru (M5) e não muda. `to(...)` não aceita `Handler`.
- Não existem overloads da mesma aridade ao lado de `get(String, Handler)`, pelo que não há ambiguidade com lambdas implícitas.
- Rotas tipadas exigem path conhecido em build-time (literal ou constante, incluindo prefixos literais de groups) e referência `this::metodo` do controller; caso contrário é erro de build. Rotas dinâmicas usam a API de `Request` cru.
- `RouteBuilder` sem `to(...)` é erro de startup.
- Os exemplos anteriores `routes.get("/users/{id}", this::find)` devem ler-se como `routes.get("/users/{id}").to(this::find)`.
