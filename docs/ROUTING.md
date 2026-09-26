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
