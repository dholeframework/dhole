# Dependency Injection

## 1. Filosofia

Dependency Injection no JRF deve parecer construção normal de objetos Java.

Mecanismo principal:

> **Constructor injection.**

---

## 2. Exemplo

```java
public class UserController extends Controller {

    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }
}
```

```java
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }
}
```

O framework resolve:

```text
UserController
    ↓
UserService
    ↓
UserRepository
```

Sem:

```text
@Autowired
@Inject
@Service
@Repository
@Component
```

---

## 3. Descoberta

Controllers e componentes estruturais são conhecidos por metadata.

Plain services entram no grafo porque são requeridos por construtores.

O container não precisa de instanciar todas as classes do classpath.

---

## 4. Build-time graph

Sempre que possível, o dependency graph é analisado durante build.

Problemas possíveis:

- dependência inexistente;
- múltiplas implementações sem decisão;
- ciclo;
- constructor não utilizável.

Devem falhar antes do servidor iniciar.

---

## 5. Missing dependency

```text
Dependency Error

Could not construct UserService.

Required:
UserRepository

Dependency path:
UserController
  -> UserService
      -> UserRepository
```

---

## 6. Interfaces

```java
public interface PaymentGateway {
    PaymentResult charge(Money amount);
}
```

```java
public class StripePaymentGateway implements PaymentGateway {
    // ...
}
```

Quando há apenas uma implementação registada, o container pode resolvê-la.

Quando há várias, é necessário binding explícito.

---

## 7. Explicit bindings

Em `Settings.java` ou módulo dedicado:

```java
settings.bind(PaymentGateway.class)
    .to(StripePaymentGateway.class);
```

Test:

```java
settings.bind(PaymentGateway.class)
    .to(FakePaymentGateway.class);
```

---

## 8. Scopes

Scopes iniciais previstos:

```text
singleton
request
prototype
```

Default de plain services deve ser decidido durante implementação. A especificação recomenda evitar implicitamente estado mutável partilhado.

---

## 9. Factories

Para dependências externas:

```java
settings.provide(HttpClient.class, context -> {
    return HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
});
```

Factories devem ser exceção, não regra.

---

## 10. Circular dependencies

Ciclos são erros.

```text
A -> B -> C -> A
```

O framework não deve esconder o problema com proxies automáticos.

---

## 11. Optional dependencies

Devem ser explícitas:

```java
Optional<Metrics> metrics
```

ou através de API própria quando necessário.

Evitar null injection.

---

## 12. Lifecycle

Recursos que precisam de startup/shutdown podem implementar contratos específicos:

```java
public interface Startable {
    void start();
}
```

```java
public interface Stoppable {
    void stop();
}
```

A API definitiva poderá ser refinada.

---

## 13. DI não é service locator

Evitar:

```java
Jrf.get(UserService.class)
```

na lógica normal.

O service locator pode existir internamente/tooling, mas não deve ser a forma recomendada de desenvolver.


---

## 14. Component Model

As regras completas de descoberta de componentes, scopes, ownership, lifecycle, bindings, factories e generated component factories estão definidas em:

```text
COMPONENT_MODEL.md
```

Este documento (`DI.md`) define a experiência pública de dependency injection; `COMPONENT_MODEL.md` define a arquitetura que a suporta.


---

## 15. Module Contributions

Bindings fornecidos por módulos e plugins entram no dependency graph durante a fase de configuração definida em:

```text
MODULE_SYSTEM.md
```
