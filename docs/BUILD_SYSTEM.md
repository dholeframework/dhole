# Build System

## 1. Objetivo

O Dhole terá um build/dependency experience próprio para que o utilizador comum não precise de escrever `pom.xml` ou `build.gradle`.

Isso não significa abandonar o ecossistema Java.

---

## 2. Arquivos

```text
dhole.toml
dhole.lock
```

---

## 3. `dhole.toml`

Exemplo:

```toml
[project]
name = "shop"
version = "0.1.0"
java = "21"

[dhole]
version = "0.1.0"

[dependencies]
web = "0.1.0"
database = "0.1.0"
security = "0.1.0"

[external-dependencies]
jsoup = "org.jsoup:jsoup:VERSION"

[build]
main = "shop.App"
```

O formato exato pode mudar, mas responsabilidades permanecem.

---

## 4. `dhole.lock`

Guarda versões e checksums resolvidos.

Objetivos:

- builds reproduzíveis;
- CI consistente;
- mesma versão em diferentes máquinas;
- debugging de dependency resolution.

Deve ser commitado em aplicações.

---

## 5. Maven ecosystem compatibility

O Dhole deve conseguir resolver artifacts publicados em repositórios Java.

CLI:

```bash
dhole add org.jsoup:jsoup:VERSION
```

O programador não precisa criar `pom.xml`.

Internamente o build system pode reutilizar bibliotecas/protocolos existentes de resolução. O contrato público continua Dhole.

---

## 6. Transitive dependencies

O resolver deve:

- resolver transitivas;
- detetar conflitos;
- produzir dependency graph;
- respeitar lock;
- permitir exclusions quando necessário;
- fornecer mensagens legíveis.

---

## 7. Dependency graph

```bash
dhole dependencies
```

Exemplo:

```text
shop
├── dhole-web 0.1.0
│   └── ...
├── dhole-database 0.1.0
└── org.jsoup:jsoup ...
```

---

## 8. Conflict diagnostics

Erro desejado:

```text
Dependency Conflict

library-a requires foo >= 2.0 < 3.0
library-b requires foo >= 3.1

No compatible version exists.
```

Não mostrar apenas stack trace interno do resolver.

---

## 9. Compile pipeline

```text
Read manifest
    ↓
Resolve dependencies
    ↓
Validate source layout
    ↓
Compile Java
    ↓
Analyze Dhole components
    ↓
Generate Dhole metadata
    ↓
Compile generated sources if needed
    ↓
Run checks
    ↓
Package
```

---

## 10. Metadata generation

O Dhole compiler/tooling gera metadata para:

- controllers;
- routes;
- constructor dependency graph;
- validation metadata;
- serialization metadata;
- plugin metadata;
- configuration;
- startup checks.

Objetivo:

- reduzir reflection;
- falhar cedo;
- acelerar startup;
- melhorar CLI/introspection.

Generated output não deve ser editado manualmente.

---

## 11. Build outputs

```text
build/
├── classes/
├── generated/
├── reports/
└── distributions/
```

O formato final de distribuição será decidido na implementação.

---

## 12. Packaging

v1 pode produzir:

```text
executable jar
```

ou distribuição equivalente com launcher.

Comando:

```bash
dhole build
```

A experiência deve ser simples independentemente do mecanismo interno.

---

## 13. Development mode

```bash
dhole dev
```

O watcher deteta alterações, recompila incrementalmente e reinicia/recarrega o mínimo necessário.

A primeira implementação usa fast restart como estratégia oficial de hot reload.

Detalhes:

- `DEV_MODE.md`
- `HOT_RELOAD.md`

Correctness é mais importante do que "hot reload mágico".

---

## 14. Cache

Dependências e outputs podem ser cacheados.

Local previsto:

```text
~/.dhole/
```

e:

```text
project/.dhole/
```

O layout exato é interno.

---

## 15. Offline

Quando dependências já estão em cache:

```bash
dhole build --offline
```

pode ser suportado.

---

## 16. Repositories

Maven Central ou repositórios compatíveis devem poder ser configurados.

Private repositories devem suportar credentials via environment/secrets, nunca hard-coded em `dhole.toml`.

---

## 17. Plugins

Plugin system não entra no primeiro kernel.

Quando existir, deve ter:

- API versionada;
- sandbox/permissions quando aplicável;
- dependency isolation;
- lifecycle claro.

---

## 18. Escape hatch

Projetos avançados podem precisar integração com tooling externo.

O Dhole deve considerar export/interop futura, mas o fluxo oficial permanece:

```bash
dhole build
```

---

## 19. Regra essencial

> **No pom.xml for the normal Dhole developer; no abandonment of the Java ecosystem.**


---

## 20. Metadata Compiler

A fase:

```text
Analyze Dhole components
Generate Dhole metadata
```

é detalhada em:

```text
METADATA_COMPILER.md
```

O build system é responsável por invocar esta fase e integrar os seus diagnostics no output de `dhole build` e `dhole dev`.

---

## 21. Decisões M8

### Resolução de dependências

- No M8 só são resolvíveis artefactos da distribuição Dhole instalada: módulos oficiais e as suas dependências de terceiros empacotadas (por exemplo Jackson para `dhole-json`). Não há acesso à rede: uma aplicação que use apenas capacidades empacotadas compila offline.
- `[dependencies]` usa nomes de módulos oficiais (`web = "0.1.0"`); a versão tem de ser a da distribuição instalada. `[external-dependencies]` e repositórios Maven produzem um erro claro de "ainda não suportado".
- Fora do M8: repositórios remotos, travessia de POMs, mediação de versões, precedência de repositórios, snapshots, exclusions, classifiers, mirrors, autenticação e transitivas arbitrárias. Pertencem a um milestone próprio de dependency management.

### `dhole.lock` v1

```text
dhole-lock 1
dhole <version>

artifact <group>:<name>:<version>
scope runtime|test
sha256 <hex>
```

- Regista os artefactos exatos selecionados para a aplicação (runtime e ferramentas de teste), ordenados por scope e coordenadas; SHA-256 sobre os bytes reais; sem caminhos da máquina. O JAR da própria aplicação não é entrada do lock.
- Versão (qual artefacto foi selecionado) e checksum (se os bytes coincidem) são verificações distintas: bytes diferentes para a mesma identidade falham o build; artefacto em falta falha o build; o lock nunca é reescrito silenciosamente.
- O primeiro build gera o lock; os seguintes consomem-no e verificam-no. Uma mudança legítima da seleção exige um fluxo explícito (`--update-lock`). Uma distribuição instalada incompatível com o lock produz um diagnóstico acionável, nunca substituição silenciosa.
- É estado gerado: commitado, não editado à mão, revisto como uma alteração de dependências. Requisitos futuros incompatíveis introduzem `dhole-lock 2` em vez de mudar a semântica da versão 1.

### Empacotamento

`dhole build` produz uma distribuição com JARs separados (sem fat JAR):

```text
build/distributions/<name>/
├── bin/<name>, bin/<name>.cmd
└── lib/<name>.jar + JARs de runtime do lock
```

- `lib/<name>.jar`: classes, recursos e metadata gerada da aplicação; `Main-Class` de `[build] main`; `Class-Path` para os JARs irmãos. `java -jar lib/<name>.jar` funciona; os scripts são o mecanismo preferido.
- Os JARs de runtime são copiados byte a byte dos artefactos verificados do lock; nada é desempacotado nem reempacotado; sem caminhos absolutos, fontes ou diretórios temporários.
- O JAR da aplicação é reprodutível (ordem de entradas determinística, timestamps normalizados).
- A máquina de deployment precisa apenas de um Java runtime compatível; `javac` só é exigido em desenvolvimento.
- Fora do M8: fat JAR, Docker, native image, imagens jlink, instaladores, WAR.

### Motor de build

O `dhole build` compila com a API `javax.tools` do JDK (in-process), invoca o metadata compiler com `-Adhole.application` a partir de `[build] main` e escreve `modules.idx` (MODULE_SYSTEM.md §29). Gradle é apenas a ferramenta interna deste repositório; não é contrato do utilizador.
