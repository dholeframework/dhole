# Testing

## 1. Princípio

Testing deve ser uma funcionalidade de primeira classe.

Uma aplicação JRF não deve precisar montar manualmente todo o framework para testes comuns.

---

## 2. Tipos de testes

```text
unit
application
HTTP/API
database
integration
```

---

## 3. Unit tests

Plain Java.

```java
class PriceServiceTest {

    @Test
    void calculatesTotal() {
        var service = new PriceService();

        assertEquals(
            Money.of("20.00"),
            service.total(...)
        );
    }
}
```

JRF não interfere.

---

## 4. API tests

```java
class UserApiTest extends ApiTest {

    @Test
    void createsUser() {
        post("/users", json(
            "name", "Mamadu",
            "email", "mamadu@example.com"
        ))
        .expectStatus(201)
        .expectJson("name", "Mamadu");
    }
}
```

---

## 5. Authentication helpers

```java
asUser(user)
    .get("/me")
    .expectStatus(200);
```

Roles/permissions podem ser simuladas sem gerar tokens manualmente, quando o teste não pretende testar o mecanismo de token.

---

## 6. Database assertions

```java
database()
    .table("users")
    .where("email", "mamadu@example.com")
    .exists();
```

---

## 7. Isolation

Por defeito, database tests devem ter isolamento.

Estratégias:

- transaction + rollback;
- clean schema;
- isolated database/container.

A implementação concreta depende do adapter.

---

## 8. Dependency replacement

```java
replace(PaymentGateway.class)
    .with(new FakePaymentGateway());
```

Permite testar sem APIs reais.

---

## 9. Mail fake

```java
mail().fake();

post("/users", ...);

mail()
    .assertSent("welcome", user.email());
```

Funcionalidade futura quando mail existir.

---

## 10. Queue fake

```java
jobs().fake();

post("/reports", ...);

jobs()
    .assertDispatched(GenerateReport.class);
```

---

## 11. Clock

O framework deve facilitar substituição do relógio:

```java
time().freeze(Instant.parse("2026-01-01T10:00:00Z"));
```

Evita testes frágeis.

---

## 12. Test environment

`APP_ENV=test`.

Configuração de test não deve utilizar serviços reais por acidente sem opt-in explícito.

---

## 13. CLI

```bash
jrf test
```

Filtros previstos:

```bash
jrf test UserApiTest
jrf test --unit
jrf test --integration
```

---

## 14. Parallel tests

Suporte futuro deve considerar isolamento de:

- database;
- filesystem;
- ports;
- shared state.

Não ativar paralelismo agressivo se comprometer determinismo.

---

## 15. Framework self-tests

O próprio JRF deve ter:

- unit tests;
- compatibility tests;
- generated project tests;
- CLI tests;
- integration tests;
- HTTP compliance tests;
- dependency resolution tests.
