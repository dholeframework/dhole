# Plugin System

## 1. Estado

O Plugin System é parte oficial da arquitetura Dhole v0.1.

A API deve ser desenhada desde o início, mesmo que plugins públicos de terceiros sejam ativados apenas após estabilização suficiente.

Módulos:

```text
dhole-plugin-api
dhole-plugin-runtime
```

---

## 2. Objetivo

Permitir extensões como:

```text
dhole-redis
dhole-openapi
dhole-kafka
dhole-s3
dhole-mail
dhole-tuprel
community integrations
company-specific integrations
```

sem alterar `dhole-core`.

---

## 3. Princípio

Plugins usam APIs públicas e SPIs estáveis.

Não recebem acesso irrestrito a internals.

---

## 4. Interface conceptual

```java
public interface DholePlugin {

    default void configure(PluginContext context) {}

    default void validate(PluginContext context) {}

    default void start(ApplicationContext context) {}

    default void stop(ApplicationContext context) {}
}
```

A interface definitiva pode separar lifecycle contracts, mas estas quatro fases são oficiais no desenho.

---

## 5. Lifecycle

```text
discover
   ↓
configure
   ↓
build/extend registrations
   ↓
validate
   ↓
application bootstrap
   ↓
start
   ↓
application running
   ↓
stop
```

---

## 6. Plugin discovery

Plugins são dependências explícitas.

Exemplo:

```toml
[dependencies]
web = "0.1.0"
tuprel = "0.1.0"
redis = "0.1.0"
```

Não existe scan aleatório da máquina.

---

## 7. Plugin Context

O plugin pode receber capacidades limitadas:

```text
configuration
dependency bindings
routing extensions
serialization registry
validation registry
health checks
CLI commands
database adapters
build metadata
```

Nem todos os plugins precisam de todas as capacidades.

---

## 8. DI integration

Exemplo:

```java
public void configure(PluginContext context) {
    context.bind(Cache.class)
        .to(RedisCache.class);
}
```

O binding entra no dependency graph antes da validação final.

---

## 9. Configuration

Plugin define settings próprios:

```java
public record RedisSettings(
    String host,
    int port
) {}
```

Integração:

```java
context.configuration()
    .register(RedisSettings.class);
```

Secrets continuam a vir do environment.

---

## 10. Routes

Plugin poderá adicionar endpoints técnicos, por exemplo:

```text
/health
/openapi
```

Isso deve ser visível em:

```bash
dhole routes
```

A origem do plugin deve aparecer.

---

## 11. CLI extensions

Plugin poderá contribuir comandos namespaced:

```bash
dhole openapi generate
dhole redis check
```

Plugins não devem poder sobrescrever comandos core silenciosamente.

---

## 12. Health

Plugin pode contribuir checks:

```text
database
redis
kafka
mail
```

Visíveis em:

```bash
dhole doctor
```

e health endpoints configurados.

---

## 13. Serialization

Plugins podem registar:

```java
context.serialization()
    .register(Money.class, serializer);
```

---

## 14. Validation

Plugins podem fornecer reusable rules:

```text
IBAN
VAT number
country-specific identifiers
```

Sem modificar `dhole-validation`.

---

## 15. Database adapters

`dhole-tuprel` é um exemplo de plugin/adaptor oficial.

```text
Dhole Database SPI
       ↑
dhole-tuprel
       ↓
Tuprel
```

---

## 16. Plugin metadata

Cada plugin deve declarar:

```text
name
version
Dhole compatibility
capabilities
dependencies
```

Formato exato será definido no build system.

---

## 17. Version compatibility

O runtime/build deve recusar combinações conhecidamente incompatíveis.

Exemplo:

```text
Plugin Compatibility Error

dhole-openapi 2.0 requires Dhole >= 1.4
Project uses Dhole 1.2
```

---

## 18. Dependency isolation

Plugins não devem criar classpath conflicts silenciosos.

O build system deve diagnosticar conflitos.

Isolamento forte por classloader poderá ser considerado, mas não é requisito inicial.

---

## 19. Security model

Plugins são código Java e, por definição, podem ter grande capacidade quando carregados no processo.

Por isso:

- instalação é explícita;
- origem e versão são visíveis;
- checksums são fixados em lockfile;
- permissions metadata pode ser estudada futuramente;
- não se deve prometer sandbox completa sem mecanismo real.

---

## 20. Official vs Community

Categorias:

```text
Dhole Core
Official Plugins
Community Plugins
Private Plugins
```

Plugins oficiais seguem compatibility testing do projeto.

---

## 21. Registry

Um registry Dhole próprio é possibilidade futura.

A v1 pode distribuir plugins através de Maven-compatible repositories enquanto a CLI oferece experiência simplificada.

---

## 22. Dev mode

Plugins devem informar necessidades de restart:

```text
restart-safe
full-restart
```

DevTools considera isso no hot reload.

---

## 23. Testing

Plugin SDK deverá oferecer test harness:

```java
class RedisPluginTest extends PluginTest {
    // ...
}
```

Objetivo:

- lifecycle;
- registration;
- config;
- compatibility.

---

## 24. Regra central

> **O core permanece pequeno. O ecossistema cresce através de contratos públicos claros.**
