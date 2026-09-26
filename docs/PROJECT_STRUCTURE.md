# Project Structure

## 1. Objetivo

A estrutura de projeto deve ser previsível sem ser rígida.

O Dhole define convenções, mas não obriga todas as pastas a existir.

---

## 2. Estrutura recomendada

```text
my-app/
├── src/
│   ├── main/
│   │   └── java/
│   │       └── com/example/app/
│   │           ├── App.java
│   │           ├── config/
│   │           │   └── Settings.java
│   │           ├── controllers/
│   │           ├── services/
│   │           ├── repositories/
│   │           ├── models/
│   │           ├── middleware/
│   │           ├── jobs/
│   │           ├── events/
│   │           ├── plugins/
│   │           └── modules/
│   └── test/
│       └── java/
├── database/
│   ├── migrations/
│   └── seeds/
├── storage/
├── .env
├── .env.example
├── .gitignore
├── dhole.toml
└── dhole.lock
```

---

## 3. `App.java`

É o entry point.

```java
package com.example.app;

import org.dhole.Dhole;

public class App {

    public static void main(String[] args) {
        Dhole.run(App.class);
    }
}
```

A classe passada a `Dhole.run` define o root package da aplicação por defeito.

---

## 4. `config/`

Contém configuração em Java.

```text
config/
└── Settings.java
```

Pode crescer:

```text
config/
├── Settings.java
├── DatabaseSettings.java
├── SecuritySettings.java
└── MailSettings.java
```

A aplicação pequena deve poder manter tudo em `Settings.java`.

---

## 5. `controllers/`

Classes que recebem requests HTTP e devolvem respostas.

```text
controllers/
├── UserController.java
├── AuthController.java
└── OrderController.java
```

Controllers devem ser finos. Lógica de negócio pertence normalmente aos services/domain.

---

## 6. `services/`

Lógica de aplicação e orquestração.

```text
services/
├── UserService.java
├── PaymentService.java
└── OrderService.java
```

Services são plain Java classes.

---

## 7. `repositories/`

Acesso a persistência quando a aplicação utiliza repository pattern.

```text
repositories/
├── UserRepository.java
└── OrderRepository.java
```

O Dhole não obriga repository pattern em todos os projetos.

---

## 8. `models/`

Tipos de domínio/persistência.

```text
models/
├── User.java
├── Order.java
└── Product.java
```

A definição concreta depende da camada de persistência.

---

## 9. `middleware/`

Cross-cutting request processing.

```text
middleware/
├── RequestLogger.java
└── TenantResolver.java
```

Security middleware oficial pode viver nos próprios módulos Dhole.

---

## 10. `jobs/`

Background jobs.

```text
jobs/
├── SendWelcomeEmail.java
└── GenerateReport.java
```

Não faz parte do kernel mínimo da primeira implementação, mas a estrutura é reservada.

---

## 11. `events/`

Eventos e listeners.

```text
events/
├── UserCreated.java
└── UserCreatedListener.java
```

---

## 12. `modules/`

Usado por aplicações maiores para organizar funcionalidades verticalmente.

Exemplo:

```text
modules/
├── users/
│   ├── UserController.java
│   ├── UserService.java
│   └── User.java
└── orders/
    ├── OrderController.java
    ├── OrderService.java
    └── Order.java
```

O Dhole deve suportar tanto organização por camada como organização por feature.

---

## 13. `database/`

Artefactos da base de dados:

```text
database/
├── migrations/
└── seeds/
```

Nunca conter secrets.

---

## 14. `storage/`

Ficheiros locais da aplicação em desenvolvimento ou runtime:

```text
storage/
├── logs/
├── cache/
└── uploads/
```

Não deve ser confundido com código fonte.

---

## 15. `.env`

Secrets e valores locais.

Nunca deve ser commitado.

---

## 16. `.env.example`

Contrato de variáveis necessárias:

```env
APP_ENV=development
APP_PORT=8080

DB_HOST=localhost
DB_PORT=5432
DB_NAME=
DB_USER=
DB_PASSWORD=

JWT_SECRET=
```

Pode ser commitado.

---

## 17. `dhole.toml`

Manifesto do projeto.

Não é configuração runtime.

Responsabilidades:

- metadata;
- Java target;
- módulos Dhole;
- dependências externas;
- build;
- repositories.

---

## 18. `dhole.lock`

Resultado determinístico da resolução de dependências.

Deve ser commitado em aplicações.

---

## 19. Descoberta de componentes

O Dhole evita depender exclusivamente de runtime classpath scanning.

O build system cria metadata sobre componentes conhecidos.

Regras iniciais:

- subclasses de `Controller` são controllers;
- implementações de `Middleware` podem ser registadas;
- services são descobertos através do dependency graph;
- listeners/jobs usam contratos específicos quando estes módulos existirem.

Diretórios ajudam organização, mas o tipo Java é a fonte real de significado.

---

## 20. Estrutura mínima válida

```text
hello/
├── src/main/java/hello/
│   ├── App.java
│   └── controllers/
│       └── HelloController.java
└── dhole.toml
```

O Dhole não deve obrigar ficheiros vazios.


---

## 21. `plugins/`

Opcional para plugins privados ou extensões específicas da aplicação.

```text
plugins/
└── InternalAuditPlugin.java
```

A maioria das aplicações não precisa desta pasta.

Plugins externos continuam dependências declaradas no `dhole.toml`.
