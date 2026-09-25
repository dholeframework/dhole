# CLAUDE.md

# Project Operating Rules

## Project

Java Rest Framework (JRF)

This repository is developed from the architecture and implementation specifications stored in `docs/`.

The repository is the source of truth. Never rely on previous chat context when the repository contains the relevant decision.

---

## 1. Required Reading Before Any Change

Before modifying code:

1. Read `README.md`.
2. Read `docs/IMPLEMENTATION_ROADMAP.md`.
3. Read `docs/IMPLEMENTATION_STATUS.md`.
4. Read the specification document related to the module being changed.
5. Inspect the current Git status and recent commits.
6. Confirm the current milestone and current slice.
7. Do not begin implementation until the existing architecture is understood.

For example:

- Core work -> `docs/CORE_ARCHITECTURE.md`
- Dependency Injection -> `docs/DI.md` and `docs/COMPONENT_MODEL.md`
- Metadata -> `docs/METADATA_COMPILER.md`
- HTTP -> `docs/HTTP.md` and `docs/REQUEST_LIFECYCLE.md`
- Routing -> `docs/ROUTING.md`
- Binding -> `docs/PARAMETER_BINDING.md`
- Validation -> `docs/VALIDATION.md`
- Serialization -> `docs/SERIALIZATION.md`
- Modules -> `docs/MODULE_SYSTEM.md`
- Plugins -> `docs/PLUGIN_SYSTEM.md`
- Concurrency -> `docs/CONCURRENCY.md`
- Observability -> `docs/OBSERVABILITY.md`
- Database -> `docs/DATABASE.md`
- Security -> `docs/SECURITY.md`
- Testing -> `docs/TESTING.md`
- CLI -> `docs/CLI.md`
- Build -> `docs/BUILD_SYSTEM.md`
- Dev Mode -> `docs/DEV_MODE.md` and `docs/HOT_RELOAD.md`

---

## 2. One Agent at a Time

Only one coding agent may modify the working tree at a time.

Never assume another agent is working in parallel.

Before starting:

```bash
git status
```

The preferred starting state is:

```text
working tree clean
```

When continuing work from another coding session:

1. inspect the latest commit;
2. inspect `docs/IMPLEMENTATION_STATUS.md`;
3. inspect the relevant tests;
4. continue from the repository state;
5. do not reconstruct previous work from memory.

---

## 3. Handoff Rule

A handoff is valid only after the current slice is complete.

Before handing the repository to another agent:

```text
implementation complete
tests executed
relevant checks executed
status document updated
commit created
working tree clean
```

Do not intentionally leave partially edited files for another agent to guess how to finish.

If work cannot be completed safely, do not create a misleading completion commit. Document the blocker clearly in `docs/IMPLEMENTATION_STATUS.md`.

---

## 4. Git Identity and Attribution

Never change the repository Git author identity.

Use the Git identity already configured by the repository owner.

Do not add AI/tool attribution to commits.

Forbidden commit trailers or metadata include examples such as:

```text
Co-authored-by: Claude ...
Co-authored-by: Codex ...
Co-authored-by: ChatGPT ...
Generated-by: ...
AI-assisted-by: ...
```

Do not add bot identities as commit authors or co-authors.

Do not override:

```text
user.name
user.email
GIT_AUTHOR_NAME
GIT_AUTHOR_EMAIL
GIT_COMMITTER_NAME
GIT_COMMITTER_EMAIL
```

unless the repository owner explicitly requests it.

Commit authorship must remain the repository owner's configured Git identity.

---

## 5. Branch Naming

Branch names must describe the engineering work only.

Good:

```text
feature/application-lifecycle
feature/component-registry
feature/http-routing
feature/validation-engine
fix/shutdown-order
refactor/dependency-graph
```

Forbidden:

```text
claude/*
codex/*
chatgpt/*
ai/*
agent/*
bot/*
generated/*
```

Do not include the coding tool or model name in a branch name.

For the initial development phase, prefer small feature branches only when necessary.

---

## 6. Commit Messages

All commit messages must be written in English.

Use clear conventional-style commit messages.

Examples:

```text
feat(core): add application lifecycle
feat(di): build dependency graph
feat(web): register GET routes
feat(validation): add email rule
fix(core): close resources after startup failure
test(di): cover circular dependency detection
docs(core): document lifecycle invariant
refactor(web): simplify route registration
```

Never mention the coding agent, model, AI assistance, token limits, prompts, or chat sessions in commit messages.

Avoid vague commits such as:

```text
updates
changes
work done
framework changes
```

Each commit should represent one coherent engineering change.

---

## 7. Language Policy

### Source code

All source code must be written in English.

This includes:

- package names;
- class names;
- interface names;
- enum names;
- record names;
- method names;
- variable names;
- constants;
- test names;
- exception names;
- log messages;
- CLI output;
- error codes;
- code comments;
- Javadocs;
- generated code;
- configuration keys;
- technical strings intended for developers.

Good:

```java
public final class ApplicationContext {
}
```

Bad:

```java
public final class ContextoAplicacao {
}
```

Good comment:

```java
// Close resources in reverse dependency order.
```

Bad comment:

```java
// Fechar recursos na ordem inversa.
```

### Git

The following must also be in English:

- commit messages;
- branch names;
- tags where descriptive text is used;
- pull request titles;
- pull request technical descriptions;
- issue titles created for implementation work.

### Specification documentation

The current architecture/specification documents may remain in Portuguese during the v0.1 design phase.

Public names, filenames, packages, classes, commands and APIs remain in English.

Do not translate the existing specification unless explicitly requested.

---

## 8. Architecture Authority

The architecture documents are authoritative.

Do not change an architecture document merely because the implementation took a different direction.

If implementation conflicts with the specification:

1. stop;
2. describe the conflict;
3. explain the implementation alternative;
4. do not silently rewrite the specification;
5. wait for an architectural decision when the difference is significant.

Implementation follows specification, not the other way around.

---

## 9. Current Milestone Only

Follow `docs/IMPLEMENTATION_ROADMAP.md`.

Do not automatically start the next milestone.

Example:

If the current work is:

```text
M1 — Core Runtime
```

do not implement:

```text
HTTP
Database
Security
Plugin Runtime
```

unless required by the current milestone or explicitly requested.

Avoid speculative framework features.

---

## 10. Small Slices

Break milestones into small, verifiable slices.

Example for Core Runtime:

```text
Slice 1 — ApplicationState
Slice 2 — Application interface
Slice 3 — DefaultApplication lifecycle
Slice 4 — ApplicationContext
Slice 5 — Bootstrap
Slice 6 — shutdown handling
```

Complete and verify one slice before expanding unnecessarily.

---

## 11. Testing Rule

Every implemented behavior requires tests when reasonably testable.

Before committing:

1. run tests for the changed module;
2. run broader relevant tests when module boundaries are affected;
3. inspect failures instead of bypassing them;
4. never delete or weaken a valid test just to make the build pass.

Do not mark a slice complete while relevant tests are failing.

---

## 12. No Fake Completion

Never claim:

```text
done
complete
production-ready
fully implemented
```

unless the repository state supports that claim.

If some checks were not executed, record that fact.

If a test cannot run, record:

```text
NOT EXECUTED
```

and the reason.

---

## 13. No Hidden Architectural Expansion

Do not introduce large new abstractions, dependencies, annotations, proxies or runtime mechanisms without justification.

In particular:

- do not introduce annotation-driven DI;
- do not introduce annotation-driven routing as the main API;
- do not couple `jrf-core` to Tuprel;
- do not couple `jrf-core` to a concrete HTTP server;
- do not couple `jrf-http` to a concrete JSON library;
- do not make runtime classpath scanning the primary discovery mechanism;
- do not add invisible AOP/proxy behavior as a shortcut;
- do not replace explicit transaction APIs with hidden proxies.

---

## 14. Dependency Policy

Before adding an external dependency:

1. determine whether Java/JRF already provides the required capability;
2. verify that the dependency solves a real current problem;
3. keep it behind a JRF abstraction when appropriate;
4. avoid leaking third-party types into stable public APIs without deliberate approval.

Do not add dependencies for convenience alone.

---

## 15. Public API Discipline

Before adding a public API ask:

```text
Does this need to be public?
Can the API be smaller?
Can normal Java express this already?
Will plugins need this?
Can this be changed later without breaking users?
```

Prefer package-private/internal implementation until a public contract is necessary.

---

## 16. Internal API Rule

Internal implementation must not accidentally become plugin API.

Plugins should depend only on stable public API/SPI.

Avoid documenting or exposing `internal` types as supported extension points.

---

## 17. Documentation Updates

When implementation changes a documented status or completes a roadmap slice:

Update:

```text
docs/IMPLEMENTATION_STATUS.md
```

Update architectural documents only when an architectural decision has actually changed and the repository owner has approved that change.

---

## 18. Implementation Status

`docs/IMPLEMENTATION_STATUS.md` is the handoff document between coding sessions.

It must record:

```text
current milestone
current slice
completed work
remaining work
tests executed
known issues
last verified commit
next recommended action
```

Keep it concise and factual.

---

## 19. Working Tree Before Commit

Inspect:

```bash
git status
git diff
git diff --staged
```

Do not commit:

- temporary files;
- IDE metadata;
- secrets;
- `.env`;
- build output;
- scratch files;
- unrelated changes.

---

## 20. Commit Gate

A normal implementation commit requires:

```text
✓ current slice is coherent
✓ relevant tests pass
✓ no known accidental changes
✓ status document updated when needed
✓ commit message is English
✓ commit contains no AI attribution
```

After commit:

```bash
git status
```

Preferred result:

```text
nothing to commit, working tree clean
```

---

## 21. Handoff Summary

When finishing a coding session, report only factual repository state:

```text
Implemented:
- ...

Tests:
- ...

Commit:
<hash> <message>

Working tree:
clean

Next:
- ...
```

Do not put this session summary into Git history unless it belongs in `IMPLEMENTATION_STATUS.md`.

---

## 22. Security

Never commit:

```text
passwords
API keys
tokens
private keys
database credentials
real .env files
```

Do not print secrets in tests, logs or diagnostics.

---

## 23. Destructive Actions

Do not:

```text
reset --hard
clean untracked files
force push
rewrite history
delete branches
remove large sections of code
```

unless explicitly requested or clearly required by an approved workflow.

---

## 24. Final Principle

The repository must remain understandable without knowing which coding tool worked on it.

The engineering history should describe:

```text
what changed
why it changed
how it was verified
```

not which tool produced the change.
