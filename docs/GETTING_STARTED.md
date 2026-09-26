# Getting Started

## Install

1. Install a JDK 21 or newer (a JRE is not enough: Dhole compiles your application with `javac`).
2. Install Dhole: unzip `dhole-<version>.zip` and add its `bin/` directory to `PATH`.
3. Check the installation:

```bash
dhole version
```

The `dhole` command finds Java through `JAVA_HOME`, or `java` on `PATH`.

## Create and run an application

```bash
dhole new hello
cd hello
dhole dev
```

`dhole dev` compiles the application, starts it and restarts it when files in `src/main/java`, `src/main/resources`, `dhole.toml` or `.env` change. A change that does not compile is reported and the previous version keeps running. Type `r` and Enter to restart, `q` and Enter to quit.

Open `http://localhost:8080/hello`, edit `src/main/java/hello/controllers/HelloController.java` and reload.

## Commands

```text
dhole new <name>     create a project
dhole dev            run with automatic restart
dhole run            build and run the application
dhole build          build build/distributions/<name>/ (bin/ scripts and lib/ JARs)
dhole test           run the tests in src/test/java
dhole routes         list the typed routes
dhole config         show the configuration values read (secrets masked); 'dhole config check' validates
dhole doctor         check Java, dhole.toml, dhole.lock, metadata, configuration and .env (read-only)
```

## Project files

- `dhole.toml` — project manifest: name, Java version, Dhole version, modules, application class.
- `dhole.lock` — the exact artifacts and SHA-256 checksums used by the build; created by the first build. Commit it, never edit it by hand, and review its changes like dependency changes. After changing `[dependencies]`, update it explicitly with `dhole build --update-lock`.
- `.env` — local values and secrets; never commit it (the generated `.gitignore` excludes it). `.env.example` lists the variables and is committed.

## Deploying

`dhole build` produces `build/distributions/<name>/`. Copy that directory to the server and start `bin/<name>` (or `bin/<name>.cmd` on Windows), or run `java -jar lib/<name>.jar`. The server needs a Java 21+ runtime; it does not need a JDK or Dhole.
