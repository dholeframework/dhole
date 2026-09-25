# Repository Structure

## Objetivo

Este repositório é o monorepo inicial do Java Rest Framework.

```text
java-rest-framework/
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

Runtime oficial do JRF.

## `tools/`

Compiler, build system e CLI.

## `integrations/`

Integrações oficiais independentes do core.

`jrf-tuprel` será a integração oficial com Tuprel.

## `examples/`

Aplicações usadas como specification tests e demonstrações.

## Regra importante

Ainda não existe build file definitivo neste scaffold.

Não adicionar `pom.xml`, `build.gradle` ou outro build root até o documento `IMPLEMENTATION_ROADMAP.md` fechar a estratégia de implementação interna.

O facto de aplicações JRF não precisarem de `pom.xml` não obriga o próprio repositório do framework a reinventar todo o bootstrap no primeiro commit. Essa decisão será tomada separadamente.
