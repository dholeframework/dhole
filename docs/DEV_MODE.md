# Dev Mode

## 1. Estado

`dhole dev` é parte oficial da experiência Dhole v0.1.

O módulo responsável é:

```text
dhole-devtools
```

---

## 2. Objetivo

O developer executa:

```bash
dhole dev
```

e recebe um ambiente de desenvolvimento integrado:

- compilation;
- source watching;
- fast restart;
- readable errors;
- route/config diagnostics;
- development logging;
- automatic metadata regeneration.

---

## 3. Startup

```text
dhole dev
    ↓
load project
    ↓
resolve dependencies
    ↓
compile
    ↓
generate metadata
    ↓
start application
    ↓
start file watcher
    ↓
ready
```

---

## 4. Example output

```text
Dhole Dev

Application   shop
Java          21
Environment   development
Server        http://localhost:8080

✓ Dependencies resolved
✓ Sources compiled
✓ Metadata generated
✓ Application started
✓ Watching source files
```

---

## 5. Change detection

O watcher observa inicialmente:

```text
src/main/java/
src/main/resources/
dhole.toml
.env
```

Algumas alterações têm comportamentos diferentes.

---

## 6. Change classes

### Source change

```text
UserService.java
```

Ação:

```text
incremental compile
metadata refresh if needed
fast restart
```

### Static/resource change

Quando aplicável:

```text
reload resource
```

ou restart mínimo.

### Dependency change

```text
dhole.toml
```

Ação:

```text
resolve dependencies
rebuild dependency graph
compile
restart
```

### Environment/config change

```text
.env
Settings.java
```

Ação:

```text
reload configuration
validate
restart
```

---

## 7. Development errors

Se a compilação falhar:

```text
Build Error

UserService.java:42

incompatible types:
String cannot be converted to long

Application remains stopped at previous safe state.
Waiting for changes...
```

O processo `dhole dev` continua vivo.

Após correção:

```text
✓ Compiled
✓ Application restarted
```

---

## 8. Browser/API workflow

A v0.1 não exige browser auto-refresh.

Ferramentas futuras podem oferecer:

- browser refresh;
- API client integration;
- WebSocket dev channel;
- IDE diagnostics.

---

## 9. State

A primeira implementação não promete preservar memória da aplicação durante reload.

O comportamento correto é:

> alteração de código pode reiniciar o application context.

Estado durável deve viver em infraestrutura apropriada.

---

## 10. Database safety

`dhole dev` nunca executa automaticamente migrations destrutivas sem política explícita.

Pode detetar:

```text
pending migrations
```

e informar.

---

## 11. Test integration

Modo futuro:

```bash
dhole dev --test
```

ou watch tests separado.

Não faz parte do kernel inicial.

---

## 12. Logs

Development output deve incluir:

- startup;
- requests opcionais;
- SQL opcional;
- reload events;
- errors;
- timings úteis.

Sem ruído excessivo.

---

## 13. Diagnostics

Dentro de dev mode:

```text
r  restart
c  config
u  routes
q  quit
```

Pode ser explorado numa versão futura.

---

## 14. Regra central

> **`dhole dev` deve transformar editar-compilar-executar num ciclo quase imediato, sem comprometer previsibilidade.**
