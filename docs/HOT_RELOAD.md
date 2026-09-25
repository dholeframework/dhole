# Hot Reload

## 1. Definição

No JRF, "hot reload" descreve a experiência do developer, mas a implementação inicial será deliberadamente conservadora.

A v0.1 prioriza:

```text
fast restart
```

em vez de complexos sistemas de mutation de classes em runtime.

---

## 2. Razão

Java permite estratégias avançadas de reload, mas elas podem introduzir:

- classloader leaks;
- estado inconsistente;
- diferenças entre development e production;
- debugging difícil;
- incompatibilidades com bibliotecas.

A primeira implementação deve ser correta antes de ser sofisticada.

---

## 3. Level 1 — Fast Restart

Primeira implementação oficial.

```text
source changed
    ↓
detect affected source
    ↓
incremental compilation
    ↓
regenerate affected metadata
    ↓
stop application context gracefully
    ↓
create fresh application context
    ↓
start
```

O processo da CLI permanece vivo.

---

## 4. Level 2 — Component Reload

Futuro.

Poderá substituir componentes isolados quando seguro:

```text
controller
service
route metadata
serializer metadata
```

Somente quando não alterar estruturas incompatíveis.

---

## 5. Level 3 — Advanced Reload

Possibilidade futura:

- class redefinition;
- state-preserving reload;
- JVM instrumentation.

Não faz parte da v1 obrigatória.

---

## 6. Restart target

Objetivo da implementação é minimizar:

```text
change -> ready
```

sem definir nesta fase um número artificial como requisito.

Métricas devem ser recolhidas desde o início.

---

## 7. Graceful restart

Antes de substituir o application context:

```text
stop accepting new requests
wait briefly for in-flight requests
stop old context
start new context
resume
```

O dev server poderá manter o socket/launcher quando a implementação permitir.

---

## 8. Failed reload

Se a nova versão não compilar:

```text
reload rejected
```

A CLI continua a observar ficheiros.

Se tecnicamente possível e seguro, a versão anterior pode continuar a responder. Caso contrário, mostrar claramente estado indisponível.

Nunca esconder que o código novo falhou.

---

## 9. Plugin interaction

Plugins devem declarar se participam em restart.

Exemplo:

```text
restart-safe
requires-full-restart
```

O runtime decide o nível necessário.

---

## 10. Database connections

Pools e resources precisam de shutdown correto para evitar:

```text
connection leaks
file descriptor leaks
threads orphaned
```

Dev mode será um teste importante do lifecycle do framework.

---

## 11. Generated metadata

Hot reload depende do build system.

Alteração:

```text
route
constructor
validation rule
serialization structure
```

deve atualizar metadata correspondente antes do restart.

---

## 12. Production

Hot reload/devtools não são carregados em production distributions por defeito.

`jrf-devtools` é development-only.

---

## 13. Regra central

> **Reload rápido é uma feature; runtime imprevisível é um bug.**
