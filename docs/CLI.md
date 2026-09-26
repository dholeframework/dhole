# CLI

## 1. Visão

A CLI é parte central da experiência Dhole.

Nome provisório:

```text
dhole
```

---

## 2. Criação

```bash
dhole new shop
```

Opções futuras:

```bash
dhole new shop --api
dhole new shop --database postgres
```

A geração inicial deve continuar pequena.

---

## 3. Development

```bash
dhole dev
```

Responsabilidades:

- compile;
- start;
- watch;
- reload/restart;
- readable errors;
- development logs.

---

## 4. Run

```bash
dhole run
```

Executa a aplicação sem tooling extra de development.

---

## 5. Build

```bash
dhole build
```

Pipeline:

```text
manifest
dependencies
compile
metadata
tests(optional/configurable)
package
```

---

## 6. Test

```bash
dhole test
```

---

## 7. Dependency management

```bash
dhole add redis
dhole add org.jsoup:jsoup:VERSION
dhole remove redis
dhole update
dhole dependencies
```

---

## 8. Code generators

Geradores não são obrigatórios, mas aceleram tarefas:

```bash
dhole make controller User
dhole make service User
dhole make model User
dhole make middleware AuthLog
dhole make job SendWelcomeEmail
dhole make test UserApi
```

Código gerado deve ser simples e editável.

---

## 9. Routes

```bash
dhole routes
```

```text
METHOD  PATH          NAME        HANDLER
GET     /users        users.list  UserController.list
GET     /users/{id}   users.find  UserController.find
POST    /users        users.create UserController.create
```

---

## 10. Config

```bash
dhole config
dhole config check
```

Secrets mascarados.

---

## 11. Database

```bash
dhole db migrate
dhole db rollback
dhole db status
dhole db seed
dhole db reset
```

Comandos destrutivos devem ter proteção.

---

## 12. Doctor

```bash
dhole doctor
```

Exemplo:

```text
Dhole Doctor

Project
✓ manifest valid
✓ lock file valid

Java
✓ compatible runtime

Configuration
✓ required variables present
✓ .env ignored by Git

Database
✓ reachable
✓ migrations current

Security
✓ debug disabled in production
✓ JWT secret configured

No critical problems found.
```

---

## 13. Clean

```bash
dhole clean
```

Remove outputs do build/cache seguro.

Nunca apagar dados de aplicação.

---

## 14. Info

```bash
dhole info
```

```text
Dhole        0.1.0
Java       21
Project    shop
Environment development
```

---

## 15. UX

Regras:

- mensagens curtas;
- erro acionável;
- não mostrar stack trace interno da CLI por defeito;
- `--verbose` para diagnóstico;
- `--json` futuro para CI/tooling;
- exit codes coerentes.

---

## 16. Não esconder ações perigosas

Exemplo:

```bash
dhole db reset
```

deve informar claramente que dados serão removidos.

Em production, exigir flag explícita ou recusar por defeito.
