# Configuration

## 1. Princípio

A configuração da aplicação é Java.

O Dhole não introduz uma linguagem paralela de configuração para runtime.

```text
Application configuration  -> Java
Secrets / environment      -> environment variables / .env
Project/build metadata     -> dhole.toml
```

---

## 2. `Settings.java`

Local convencional:

```text
src/main/java/<root-package>/config/Settings.java
```

Exemplo conceptual:

```java
package shop.config;

import org.dhole.config.SettingsBuilder;

import static org.dhole.env.Env.*;

public final class Settings {

    public static void configure(SettingsBuilder settings) {
        settings.app(app -> app
            .name(env("APP_NAME", "Shop"))
            .environment(env("APP_ENV", "development"))
            .port(envInt("APP_PORT", 8080))
        );

        settings.database(database -> database
            .url(env("DATABASE_URL"))
        );

        settings.security(security -> security
            .jwt(jwt -> jwt
                .secret(env("JWT_SECRET"))
            )
        );
    }
}
```

A API exata poderá evoluir, mas a regra não muda: configuração é Java.

Decisão (M2) — localização de `Settings`:

- a partir da classe da aplicação (ex.: `com.example.shop.App`), procura-se exatamente `com.example.shop.config.Settings`, com o class loader da classe da aplicação;
- sem scanning, sem `ServiceLoader`, sem pesquisa noutros packages;
- ausência da classe não é erro: aplicam-se environment e defaults;
- se existir, exige `public static void configure(SettingsBuilder settings)`; qualquer outra forma é `ConfigurationException`;
- o M4 pode substituir internamente esta localização por metadata sem mudar a convenção do código da aplicação.

---

## 3. `.env`

Em desenvolvimento:

```env
APP_NAME=Shop
APP_ENV=development
APP_PORT=8080

DATABASE_URL=postgresql://localhost:5432/shop
DB_USER=postgres
DB_PASSWORD=secret

JWT_SECRET=local-secret
```

O ficheiro `.env` deve estar no `.gitignore`.

---

## 4. Prioridade das fontes

Ordem proposta:

```text
1. explicit runtime environment
2. process/system environment
3. .env.<environment>.local
4. .env.local
5. .env.<environment>
6. .env
7. defaults declared in Settings.java
```

Para a v0.1 de implementação, o conjunto pode ser reduzido. A regra fundamental é que variáveis externas têm prioridade sobre defaults.

Conjunto implementado no M2:

```text
1. process environment
2. .env            (não usado em production)
3. defaults declared in Settings.java
```

---

## 5. Production

Production não deve depender de um `.env` presente no servidor.

A mesma função:

```java
env("JWT_SECRET")
```

pode ler o valor fornecido pelo ambiente de execução.

Exemplos:

```text
Docker
Kubernetes
systemd
cloud platform
CI/CD
secret manager adapter
```

Decisão (M2) — restrições básicas de production (`APP_ENV=production`):

- ficheiros `.env` são ignorados por completo (não são lidos);
- o process environment é a fonte externa autoritativa;
- defaults continuam permitidos para settings não secretos;
- settings secretos não podem resolver a partir de defaults e têm de vir do process environment;
- a violação é `ConfigurationException`, sem expor o valor.

---

## 6. Variáveis obrigatórias

```java
env("JWT_SECRET")
```

sem default significa obrigatório.

Se faltar:

```text
Configuration Error

Missing required environment variable:
JWT_SECRET

Required by:
Security settings

Application startup aborted.
```

---

## 7. Tipos

Helpers:

```java
env("APP_NAME")
envInt("APP_PORT", 8080)
envLong("MAX_UPLOAD_BYTES")
envBool("APP_DEBUG", false)
envDuration("REQUEST_TIMEOUT", Duration.ofSeconds(30))
```

Conversões inválidas falham no startup.

---

## 8. Secrets

Nunca imprimir valor real de uma configuração marcada/identificada como secret.

CLI:

```bash
dhole config
```

Saída:

```text
APP_NAME       Shop
APP_PORT       8080
DB_PASSWORD    ********
JWT_SECRET     ********
```

---

## 9. Segurança contra hard-coded credentials

O tooling poderá detetar padrões suspeitos:

```java
.password("super-secret")
```

e emitir warning.

Em modo strict/production, certas configurações poderão exigir fonte externa.

---

## 10. Environment

Valores oficiais iniciais:

```text
development
test
production
```

Custom environments poderão existir mais tarde.

No M2, `APP_ENV` aceita exatamente estes três valores (sem aliases como `prod` ou `local`); qualquer outro valor é `ConfigurationException`. Sem `APP_ENV`, o environment é `development` (ver exemplo da secção 2).

---

## 11. Environment-specific configuration

A configuração Java pode reagir ao environment:

```java
if (environment.isProduction()) {
    settings.debug(false);
}
```

O framework não deve exigir inheritance entre `DevelopmentSettings` e `ProductionSettings`.

Composição é preferida.

---

## 12. Configuração customizada da aplicação

Uma aplicação pode declarar settings próprios:

```java
public record PaymentSettings(
    String apiKey,
    String webhookSecret
) {}
```

O config system deve permitir binding type-safe para esse tipo.

---

## 13. Validação

Toda configuração deve ser validada antes de abrir o servidor HTTP.

Exemplo:

```text
Configuration Error

database.pool.max must be greater than database.pool.min

min = 20
max = 10
```

---

## 14. `dhole config`

Comandos previstos:

```bash
dhole config
dhole config check
dhole config get app.port
```

Secrets continuam mascarados.

---

## 15. O que `Settings.java` não é

Não é:

- service locator;
- lugar para lógica de negócio;
- lugar para executar migrations;
- lugar para abrir sockets manualmente;
- substituto do dependency injection.

É a declaração da configuração da aplicação.
