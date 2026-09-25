# JRF Modules

Esta pasta contém os módulos oficiais de runtime do Java Rest Framework.

Estrutura inicial:

- `jrf-core` — application lifecycle, bootstrap e contratos centrais.
- `jrf-config` — environment e configuração.
- `jrf-di` — dependency graph e container.
- `jrf-http` — abstrações HTTP.
- `jrf-routing` — router e route definitions.
- `jrf-web` — integração HTTP + routing + controllers.
- `jrf-validation` — validation engine.
- `jrf-serialization` — contratos de serialization.
- `jrf-json` — implementação JSON oficial.
- `jrf-security` — authentication/authorization/security.
- `jrf-database` — database SPI.
- `jrf-observability` — logs, metrics, tracing e health.
- `jrf-plugin-api` — API pública para plugins.
- `jrf-plugin-runtime` — runtime dos plugins.
- `jrf-devtools` — `jrf dev`, watching e fast restart.
- `jrf-testing` — testing utilities.
