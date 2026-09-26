# Security

## 1. Princípio

Security faz parte do framework base de backend, não é uma extensão tardia.

O Dhole deve oferecer defaults seguros sem esconder decisões importantes.

---

## 2. Áreas

A v1 deve cobrir:

- password hashing;
- authentication;
- authorization;
- JWT;
- session/cookie support quando aplicável;
- CORS;
- CSRF para auth baseada em cookie;
- security headers;
- rate limiting hooks;
- secrets;
- secure error handling.

---

## 3. Configuration

```java
settings.security(security -> security
    .jwt(jwt -> jwt
        .secret(env("JWT_SECRET"))
        .expiresIn(Duration.ofHours(2))
    )
);
```

---

## 4. Authentication

Rota protegida:

```java
routes.get("/me", this::profile)
    .auth();
```

Handler:

```java
User profile(Auth<User> auth) {
    return auth.user();
}
```

O tipo `Auth<User>` representa contexto autenticado.

---

## 5. Authorization

```java
routes.delete("/users/{id}", this::delete)
    .auth()
    .role("ADMIN");
```

Permissões:

```java
routes.post("/refunds/{id}", this::refund)
    .auth()
    .permission("payments.refund");
```

A política exata pode ser extensível.

---

## 6. Passwords

O framework deve fornecer uma API:

```java
String hash = passwords.hash(rawPassword);

boolean valid = passwords.verify(
    rawPassword,
    storedHash
);
```

O algoritmo concreto é uma decisão de implementação e deve permitir atualização ao longo do tempo.

Nunca fornecer encryption reversível como mecanismo normal de password storage.

---

## 7. JWT

API deve tratar:

- signing;
- verification;
- expiration;
- issuer/audience opcionais;
- key rotation futura;
- claims controladas.

Segredos nunca entram no source code.

---

## 8. CORS

Default restritivo.

Development pode permitir configuração fácil:

```java
security.cors(cors -> cors
    .allowOrigin(env("FRONTEND_URL"))
);
```

`*` com credentials não deve ser permitido.

---

## 9. CSRF

Para cookies/sessions com autenticação automática pelo browser, proteção CSRF deve existir por defeito ou ser exigida explicitamente conforme o modo.

JWT em headers não deve ser confundido automaticamente com auth por cookie.

---

## 10. Security headers

O módulo web/security deve poder aplicar defaults como:

```text
X-Content-Type-Options
Referrer-Policy
Content-Security-Policy (quando configurável/aplicável)
Strict-Transport-Security (production HTTPS)
```

Sem prometer headers inadequados ao contexto.

---

## 11. Rate limiting

API prevista:

```java
routes.post("/auth/login", this::login)
    .rateLimit("login");
```

Policy em settings:

```java
security.rateLimit("login", limit -> limit
    .requests(10)
    .per(Duration.ofMinutes(1))
);
```

---

## 12. Secrets

`dhole config` mascara:

```text
JWT_SECRET  ********
```

Logs também.

---

## 13. Production checks

`dhole doctor` deve alertar:

```text
debug enabled
weak/default secret
.env tracked by Git
CORS wildcard
HTTP without trusted proxy configuration
missing production database configuration
```

---

## 14. Security escape hatch

Developers avançados podem substituir providers, mas bypasses devem ser explícitos e visíveis.

---

## 15. Scope da v0.1

Security v0.1 não tentará implementar:

- full OAuth provider;
- identity platform;
- SSO enterprise completo;
- secrets manager próprio.

Pode integrar ferramentas externas futuramente.
