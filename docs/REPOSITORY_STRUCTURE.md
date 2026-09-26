# Repository Structure

## Objetivo

Este repositório é o monorepo inicial do Dhole Framework.

```text
dhole/
├── README.md
├── docs/
├── modules/
├── tools/
├── integrations/
├── examples/
├── scripts/
└── .github/
```

## `docs/`

Contém a Specification v0.1.

A documentação em português é temporária; nomes públicos, ficheiros, packages, classes e APIs permanecem em inglês.

## `modules/`

Runtime oficial do Dhole.

## `tools/`

Compiler, build system e CLI.

## `integrations/`

Integrações oficiais independentes do core.

`dhole-tuprel` será a integração oficial com Tuprel.

## `examples/`

Aplicações usadas como specification tests e demonstrações.

## Build interno do repositório

O próprio repositório do Dhole é construído atualmente com **Gradle (Kotlin DSL)**, através do Gradle Wrapper incluído no repositório.

```text
settings.gradle.kts        lista de projetos
build-logic/               convention plugin partilhado (org.dhole.java-conventions)
gradle/                    wrapper e version catalog
<projeto>/build.gradle.kts dependências de cada projeto
```

Esta é uma decisão de implementação/bootstrap, usada apenas para desenvolver o Dhole.

Não altera o objetivo do produto: aplicações Dhole serão construídas e executadas com:

```bash
dhole build
dhole run
dhole dev
```

sem que o developer da aplicação tenha de manter ficheiros de build Maven ou Gradle.

Detalhes e comandos: `docs/DEVELOPMENT.md`.
