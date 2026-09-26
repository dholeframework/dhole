# Dhole Modules

Esta pasta contém os módulos oficiais de runtime do Dhole Framework.

Estrutura inicial:

- `dhole-core` — application lifecycle, bootstrap e contratos centrais.
- `dhole-config` — environment e configuração.
- `dhole-di` — dependency graph e container.
- `dhole-http` — abstrações HTTP.
- `dhole-routing` — router e route definitions.
- `dhole-web` — integração HTTP + routing + controllers.
- `dhole-validation` — validation engine.
- `dhole-serialization` — contratos de serialization.
- `dhole-json` — implementação JSON oficial.
- `dhole-security` — authentication/authorization/security.
- `dhole-database` — database SPI.
- `dhole-observability` — logs, metrics, tracing e health.
- `dhole-plugin-api` — API pública para plugins.
- `dhole-plugin-runtime` — runtime dos plugins.
- `dhole-devtools` — `dhole dev`, watching e fast restart.
- `dhole-testing` — testing utilities.
