# CLI

## 1. Visão

A CLI é parte central da experiência JRF.

Nome provisório:

```text
jrf
```

---

## 2. Criação

```bash
jrf new shop
```

Opções futuras:

```bash
jrf new shop --api
jrf new shop --database postgres
```

A geração inicial deve continuar pequena.

---

## 3. Development

```bash
jrf dev
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
jrf run
```

Executa a aplicação sem tooling extra de development.

---

## 5. Build

```bash
jrf build
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
jrf test
```

---

## 7. Dependency management

```bash
jrf add redis
jrf add org.jsoup:jsoup:VERSION
jrf remove redis
jrf update
jrf dependencies
```

---

## 8. Code generators

Geradores não são obrigatórios, mas aceleram tarefas:

```bash
jrf make controller User
jrf make service User
jrf make model User
jrf make middleware AuthLog
jrf make job SendWelcomeEmail
jrf make test UserApi
```

Código gerado deve ser simples e editável.

---

## 9. Routes

```bash
jrf routes
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
jrf config
jrf config check
```

Secrets mascarados.

---

## 11. Database

```bash
jrf db migrate
jrf db rollback
jrf db status
jrf db seed
jrf db reset
```

Comandos destrutivos devem ter proteção.

---

## 12. Doctor

```bash
jrf doctor
```

Exemplo:

```text
JRF Doctor

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
jrf clean
```

Remove outputs do build/cache seguro.

Nunca apagar dados de aplicação.

---

## 14. Info

```bash
jrf info
```

```text
JRF        0.1.0
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
jrf db reset
```

deve informar claramente que dados serão removidos.

Em production, exigir flag explícita ou recusar por defeito.
