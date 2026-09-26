# Module System

## 1. Objetivo

O Module System define como capacidades oficiais e extensões do Dhole Framework são agrupadas, registadas, ordenadas, iniciadas e encerradas.

O Dhole não deve tornar-se um monólito onde todos os recursos estão sempre carregados.

A arquitetura pretendida é:

```text
small core
    +
optional modules
    +
plugins
```

---

## 2. O que é um Module

Um `DholeModule` representa uma capacidade técnica do framework.

Exemplos:

```text
WebModule
RoutingModule
SerializationModule
ValidationModule
SecurityModule
DatabaseModule
TestingModule
ObservabilityModule
```

Um módulo pode:

- registar componentes;
- registar settings;
- registar lifecycle hooks;
- registar serializers;
- registar validators;
- contribuir health checks;
- contribuir CLI metadata;
- depender de outros módulos.

---

## 3. Module vs Plugin

```text
Module
    = capacidade interna/runtime do Dhole

Plugin
    = pacote distribuído separadamente que pode contribuir módulos
```

Exemplo:

```text
dhole-tuprel
    ↓
TuprelPlugin
    ↓
TuprelDatabaseModule
```

---

## 4. Contrato conceptual

```java
public interface DholeModule {

    String id();

    default Set<String> requires() {
        return Set.of();
    }

    default void configure(ModuleContext context) {}

    default void validate(ModuleContext context) {}

    default void start(ApplicationContext context) {}

    default void stop(ApplicationContext context) {}
}
```

A API final pode separar interfaces, mas estas fases são normativas.

---

## 5. Identidade

Cada módulo possui ID estável:

```text
core
config
serialization
validation
web
security
database
observability
```

IDs são usados em:

- dependency graph;
- diagnostics;
- plugin metadata;
- `dhole doctor`;
- compatibility checks.

---

## 6. Module Dependencies

Exemplo:

```text
web
  requires:
    - routing
    - serialization
```

```text
security-web
  requires:
    - web
    - security
```

```text
dhole-tuprel
  requires:
    - database
```

---

## 7. Ordering

O Dhole constrói um directed acyclic graph.

```text
serialization
      ↓
     web
      ↓
security-web
```

Startup:

```text
dependency first
```

Shutdown:

```text
dependent first
```

---

## 8. Cycles

Isto é inválido:

```text
module-a -> module-b -> module-a
```

Erro:

```text
Module Dependency Error

Circular dependency detected:

module-a
  -> module-b
      -> module-a
```

---

## 9. Optional Dependencies

Um módulo pode integrar-se opcionalmente:

```text
observability
  optionally integrates with:
    - database
    - web
```

A ausência de optional dependency não impede startup.

---

## 10. Registration

Modules oficiais são ativados pelo dependency graph do projeto.

Exemplo:

```toml
[dependencies]
web = "0.1.0"
security = "0.1.0"
```

O developer não deve registar manualmente todas as classes internas.

---

## 11. Module Context

Durante `configure` e `validate`, o módulo recebe uma API limitada:

```text
bindings
settings
serialization
validation
routes extensions
health checks
lifecycle
metadata
```

Não recebe internals arbitrários.

---

## 12. Configuration Phase

Exemplo:

```java
public void configure(ModuleContext context) {
    context.bind(HttpServer.class)
        .to(DefaultHttpServer.class);
}
```

Esta fase ocorre antes do dependency graph final.

---

## 13. Validation Phase

Exemplos:

```text
required setting missing
incompatible module
invalid provider
unsupported transport
```

Falha aborta startup.

---

## 14. Start Phase

Apenas recursos runtime devem iniciar aqui.

Exemplos:

```text
HTTP server
connection pool
scheduler
worker
metrics exporter
```

---

## 15. Stop Phase

Todo recurso iniciado deve ser parado.

Regra:

```text
start order  = dependency order
stop order   = reverse dependency order
```

---

## 16. Lazy Modules

A v0.1 não depende de lazy module startup como feature central.

Startup previsível é preferido.

Lazy initialization pode existir apenas onde for comprovadamente útil.

---

## 17. Module State

Estados:

```text
REGISTERED
CONFIGURED
VALIDATED
STARTING
RUNNING
STOPPING
STOPPED
FAILED
```

---

## 18. Failure Rollback

Se:

```text
serialization ✓
database ✓
web ✗
```

o lifecycle deve parar:

```text
database
serialization
```

quando aplicável.

---

## 19. Module Registry

Responsabilidades:

```text
register modules
resolve dependencies
sort
validate compatibility
expose diagnostics
```

---

## 20. Module Metadata

Exemplo:

```text
id: web
version: 0.1.0
requires:
  - routing
  - serialization
```

Pode ser gerado/publicado em metadata.

---

## 21. Compatibility

Módulos devem declarar compatibility requirements.

Exemplo:

```text
security-web 1.2
requires:
web >= 1.2 < 2.0
```

Conflito deve ser detetado no build.

---

## 22. Public Module API

A API pública de module extension deve ser pequena.

Plugins não devem depender de:

```text
DefaultApplicationContext
InternalDependencyGraph
InternalRouteTree
```

Devem usar:

```text
ModuleContext
PluginContext
public SPI
```

---

## 23. Official Modules

Primeiro conjunto planeado:

```text
dhole-core
dhole-config
dhole-di
dhole-routing
dhole-http
dhole-web
dhole-validation
dhole-serialization
dhole-json
dhole-security
dhole-database
dhole-observability
dhole-testing
```

---

## 24. Development-only Modules

Exemplo:

```text
dhole-devtools
```

Não entram em production por defeito.

---

## 25. Test-only Modules

Exemplo:

```text
dhole-testing
```

Pode contribuir:

- fake transports;
- dependency overrides;
- test lifecycle;
- database isolation.

---

## 26. Module Introspection

CLI futura:

```bash
dhole modules
```

Saída:

```text
MODULE           STATE      ORIGIN
core             running    framework
serialization    running    framework
web              running    framework
tuprel           running    plugin
```

---

## 27. Architectural Invariants

1. módulos não dependem de internals de outros módulos;
2. dependencies são explícitas;
3. cycles são erros;
4. startup e shutdown seguem dependency order;
5. dev-only modules não entram em production por defeito;
6. plugins podem contribuir modules, mas não alterar internals;
7. module IDs são estáveis;
8. module validation acontece antes de readiness;
9. recursos iniciados devem possuir shutdown;
10. o core continua pequeno.

---

## 28. Resumo

O Module System permite ao Dhole crescer sem transformar o framework num monólito.

A regra é:

> **Capabilities compose through modules; applications only load what they need.**

---

## 29. Decisões M8 — ativação de módulos

A ponte entre `Dhole.run(App.class)` e as capacidades do framework (web, ...) é metadata de ativação gerada no build, preservando o isolamento do `dhole-core`.

- Recurso versionado `META-INF/dhole/modules.idx` na aplicação, escrito pelo `dhole build` (nunca à mão), UTF-8, determinístico, sem timestamps, caminhos absolutos ou secrets.
- Cada módulo oficial transporta um descritor `META-INF/dhole/module.idx` (`dhole-module 1`) no seu JAR; o build lê os descritores dos artefactos selecionados (leitura direta de ficheiros conhecidos, não scanning) e escreve o índice da aplicação.
- Sem `ServiceLoader`, sem classpath/package scanning, sem registos globais mutáveis, sem launcher escolhido por system property, sem dependência de `dhole-core` para `dhole-web`/`routing`/`serialization`/`validation`/`json`.
- O contrato de ativação é interno ao `dhole-core` (`org.dhole.internal.module`): um activator com `start`/`stop` e um contexto de ativação mínimo. O `DholeModule` público (§4) e o Plugin System não são congelados no M8.
- O activator é a classe exata nomeada pelo índice, instanciada pelo class loader da aplicação através de um construtor público sem argumentos; sem DI reflexiva de activators. Módulos sem comportamento de runtime não têm activator.
- Bootstrap: carregar o índice pelo class loader da aplicação, validar versão e formato, validar o graph (módulo requerido em falta, ID duplicado, ciclo, activator desconhecido), ordenar por dependências (desempate determinístico por ID), iniciar por essa ordem, `RUNNING` só depois de todos iniciarem; parar em ordem inversa; falha de um activator faz rollback dos já iniciados e deixa a aplicação em `FAILED`. Nenhum servidor aceita pedidos antes de o startup terminar.
- O `WebActivator` interno do `dhole-web` reutiliza o `WebRuntime` existente (não há duas implementações) e lê a configuração pelo modelo real do M2 (Settings/Environment).
- O `dhole dev` reutiliza o mesmo contrato: parar os módulos em ordem inversa, descartar o runtime antigo, criar um novo e ler o novo `modules.idx`; o runtime antigo nunca é mutado.

Formato:

```text
dhole-modules 1

module <id>
activator <binary-name>          (opcional)
requires <id> <id> ...           (opcional)
```

Blocos ordenados por ID. `modules.idx` é só ativação de capacidades de runtime: não é `dhole.toml`, `dhole.lock`, metadata de resolução de dependências, metadata de componentes nem o formato futuro de plugins.
