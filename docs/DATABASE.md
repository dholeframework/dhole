# Database

## 1. Filosofia

Database support é parte central de backend, mas o JRF não deve acoplar o core a um ORM específico.

---

## 2. Arquitetura

```text
Application
    ↓
JRF Database API / SPI
    ↓
Official Tuprel Adapter
    ↓
Tuprel
    ↓
Database Driver
    ↓
PostgreSQL / MySQL / ...
```

Tuprel é o adaptador ORM oficial planeado.

---

## 3. Independência de Tuprel

Regras:

- `jrf-core` não depende de Tuprel;
- integração vive num módulo separado;
- Tuprel continua utilizável fora de JRF;
- o framework pode suportar adapters adicionais.

---

## 4. Configuration

```java
settings.database(database -> database
    .url(env("DATABASE_URL"))
    .pool(pool -> pool
        .min(2)
        .max(20)
    )
);
```

Ou settings específicos do adapter oficial.

---

## 5. Connections

O framework gere:

- datasource;
- pool lifecycle;
- health;
- startup/shutdown;
- integration com transactions.

A aplicação não deve abrir connections manualmente para tarefas normais.

---

## 6. Transactions

API explícita:

```java
database.transaction(() -> {
    accounts.debit(source, amount);
    accounts.credit(target, amount);
});
```

Com retorno:

```java
Order order = database.transaction(() -> {
    return orders.create(input);
});
```

---

## 7. Nested transactions

Comportamento de nesting/savepoints deve ser explicitamente documentado antes de v1.

Não assumir semanticamente que todo nesting cria nova transaction.

---

## 8. Migrations

CLI:

```bash
jrf db migrate
jrf db rollback
jrf db status
```

Directório:

```text
database/migrations/
```

A geração automática através de Tuprel pode existir, mas migrations devem continuar inspecionáveis.

---

## 9. Seeds

```bash
jrf db seed
```

Seeds são Java:

```java
public class DevelopmentSeeder implements Seeder {

    public void run(Database db) {
        // ...
    }
}
```

---

## 10. Development reset

Comandos destrutivos devem exigir clareza:

```bash
jrf db reset
```

Em production, bloqueado por defeito.

---

## 11. Query visibility

O framework deve oferecer logging de queries em development:

```text
SQL  12ms  select ... from users ...
```

Nunca logar valores sensíveis sem política clara.

---

## 12. N+1 and performance

Integração com Tuprel poderá produzir warnings de desenvolvimento.

Esses warnings não devem mudar queries automaticamente de forma imprevisível.

---

## 13. Database errors

Constraints conhecidas podem ser traduzidas:

```text
unique violation -> Conflict
foreign key violation -> Conflict/validation context
```

Mas o erro original deve permanecer acessível nos logs.

---

## 14. Test database

Testing deve permitir:

- transaction-per-test;
- rollback;
- test containers/adapters;
- seeds;
- database assertions.

---

## 15. Primeira prioridade

Na primeira implementação oficial:

1. PostgreSQL;
2. Tuprel adapter;
3. transactions;
4. migrations;
5. testing support.

Outros databases depois.
