# Validation

## 1. Estado

A validação deixa de ser uma área em aberto.

Na especificação v0.1, Validation é uma capacidade oficial do JRF e fará parte da primeira versão utilizável do framework.

O módulo oficial é:

```text
jrf-validation
```

---

## 2. Objetivos

O sistema de validação deve ser:

- escrito em Java;
- type-safe sempre que possível;
- utilizável dentro e fora de HTTP;
- integrado automaticamente com request binding;
- extensível;
- independente de annotations como mecanismo principal;
- capaz de produzir erros estruturados;
- compatível com records e classes Java;
- analisável em build-time quando possível.

---

## 3. Input types

Records são a forma recomendada para inputs simples:

```java
public record CreateUser(
    String name,
    String email,
    String password
) implements Validatable {

    public static Rules<CreateUser> rules() {
        return Rules.forType(CreateUser.class)
            .field(CreateUser::name)
                .required()
                .minLength(2)
                .maxLength(100)

            .field(CreateUser::email)
                .required()
                .email()

            .field(CreateUser::password)
                .required()
                .minLength(8);
    }
}
```

A utilização de method references é preferida a nomes de fields escritos como strings.

---

## 4. Type-safe field references

Preferir:

```java
.field(CreateUser::email)
```

em vez de:

```java
.field("email")
```

Objetivos:

- refactoring seguro;
- autocomplete;
- compile-time feedback;
- menos erros de escrita;
- melhor tooling.

Quando um tipo não permitir method reference de forma prática, uma API alternativa poderá existir, mas não será a recomendação principal.

---

## 5. Validation automática em HTTP

Quando um handler recebe um tipo que possui rules:

```java
User create(CreateUser input) {
    return users.create(input);
}
```

o pipeline será:

```text
HTTP request
    ↓
deserialize body
    ↓
construct CreateUser
    ↓
validate
    ↓
valid?
 ┌──┴───┐
 no    yes
 │       │
422      handler
 │
validation error
```

O handler não é executado quando a validação falha.

---

## 6. Validation manual

O mesmo motor deve funcionar fora de HTTP:

```java
validator.validate(input);
```

Aplicações:

- jobs;
- commands;
- event consumers;
- batch processing;
- tests;
- application services.

Validation não pertence ao HTTP. HTTP apenas integra o motor.

---

## 7. Regras built-in

### String

```text
required
notBlank
minLength
maxLength
email
url
pattern
oneOf
```

### Numbers

```text
min
max
positive
positiveOrZero
negative
negativeOrZero
```

### Collections

```text
required
notEmpty
minSize
maxSize
uniqueItems
```

### Dates / time

```text
before
after
past
pastOrPresent
future
futureOrPresent
```

### Comparison

```text
sameAs
differentFrom
```

---

## 8. Optional values

A ausência e a invalidade são conceitos diferentes.

Exemplo:

```java
Integer age
```

pode ser optional se não tiver `.required()`.

```java
.field(CreateUser::age)
    .min(18)
```

significa:

```text
null        -> allowed
17          -> invalid
20          -> valid
```

---

## 9. Custom rules

O developer pode criar regras reutilizáveis:

```java
Rule<String> strongPassword = value -> {
    if (value == null || value.length() < 12) {
        return Validation.failure(
            "PASSWORD_TOO_WEAK",
            "Password must contain at least 12 characters."
        );
    }

    return Validation.success();
};
```

Uso:

```java
.field(CreateUser::password)
    .required()
    .rule(strongPassword)
```

---

## 10. Cross-field validation

Regras entre vários campos devem ser suportadas:

```java
Rules.forType(RegisterUser.class)
    .field(RegisterUser::password)
        .required()

    .field(RegisterUser::passwordConfirmation)
        .required()

    .check(input -> {
        if (!input.password().equals(input.passwordConfirmation())) {
            return Validation.failure(
                "PASSWORD_MISMATCH",
                "Passwords do not match."
            );
        }

        return Validation.success();
    });
```

---

## 11. Nested validation

Tipos compostos devem poder ser validados:

```java
public record CreateOrder(
    CustomerInput customer,
    List<OrderItemInput> items
) {}
```

Rules:

```java
.field(CreateOrder::customer)
    .required()
    .nested()

.field(CreateOrder::items)
    .required()
    .notEmpty()
    .eachNested()
```

---

## 12. Error response

Formato oficial inicial:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "The request contains invalid fields.",
    "fields": {
      "email": [
        {
          "code": "INVALID_EMAIL",
          "message": "Must be a valid email address."
        }
      ],
      "password": [
        {
          "code": "MIN_LENGTH",
          "message": "Must contain at least 8 characters."
        }
      ]
    },
    "requestId": "..."
  }
}
```

Mensagens podem ser localizadas.

Códigos devem ser estáveis.

---

## 13. HTTP status

A v0.1 adota:

```text
422 Unprocessable Content
```

para requests sintaticamente válidos que falham validation de input.

Erros de parsing/body inválido permanecem:

```text
400 Bad Request
```

---

## 14. Domain rules não são input validation

Exemplo:

```text
email mal formatado
    -> validation

password curta
    -> validation

saldo insuficiente
    -> domain rule

produto sem stock
    -> domain rule
```

O framework não deve incentivar a colocar toda lógica de negócio em validators.

---

## 15. Database constraints

Validation nunca substitui constraints da base de dados.

Exemplo:

```text
email unique
```

pode ser pré-validado para melhor UX, mas a constraint da base de dados continua autoridade final para evitar race conditions.

---

## 16. Build-time analysis

O tooling deve analisar rules quando possível:

- field reference inválida;
- validator incompatível com tipo;
- rule configuration impossível;
- nested validator ausente;
- duplicate rule IDs quando relevante.

Objetivo:

```text
fail early
```

---

## 17. Extensibilidade

Plugins podem contribuir:

- custom rules;
- localization;
- validation codecs;
- integrations.

Mas o `jrf-validation` continua independente do plugin runtime para funcionar sozinho.

---

## 18. Testing

Helpers:

```java
ValidationResult result = validator.validate(input);

assertThat(result)
    .hasError("email", "INVALID_EMAIL");
```

API final poderá variar, mas testing explícito é requisito.

---

## 19. Decisão v0.1

Validation é parte do core funcional da primeira versão pública.

Não será baseado em annotation soup.

A API recomendada será:

```text
Rules<T>
+
method references
+
automatic HTTP integration
+
manual validator access
```
