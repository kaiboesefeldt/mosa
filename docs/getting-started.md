# Getting started

This guide helps you build Mosa, run its checks, and prepare a local pod. Mosa
is an experimental project under active development; a successful build does
not mean it is ready for a public deployment. See the [MVP scope](../spec/mvp.md)
for current launch requirements.

## Requirements

- JDK 25. The Gradle build uses this toolchain and compiles JVM bytecode for
  Java 24.
- Git, to clone the repository.

The Gradle wrapper downloads the configured Gradle and Node.js versions as
needed. You do not need to install Gradle or Node.js separately.

## Build and check

From the repository root, run:

```bash
./gradlew build
```

Run the full verification tasks with:

```bash
./gradlew check
```

The client is a Kotlin/JS browser application, and its browser tests use
headless Firefox. See the [client build configuration](../client/build.gradle.kts)
for details.

## Prepare a local pod

The server reads and writes runtime files in its current working directory.
Use a dedicated directory for a local instance so its generated identity and
data stay separate from the source tree. The server reads the imprint, privacy,
and terms profiles from that directory. The [serve files guide](../server/src/main/resources/doc/serve/files.md)
describes these files and their formats; the example profiles in
`server/src/main/resources/clearterms/` can help you prepare local copies.

Build the runnable server artifact from the repository root:

```bash
./gradlew :server:shadowJar
```

### Optional: import sample content

The CSV files in [`testdata/`](../testdata/) contain sample posts and replies
for exploring the local pod. Before starting the server, import them from the
data directory you will use for the pod. For example:

```bash
cd /home/me/mosa-local
cat /home/me/src/mosa/testdata/*.csv | /home/me/src/mosa/mosa import
```

Replace the paths with your own. To import only selected samples, list their
CSV files instead of using `*.csv`.
The import reports how many posts and replies it imported. It may download the
embedding model on first use; missing or invalid legal profiles can also
produce warnings as described below.

### Start the server

Start the server from that data directory using the repository's launcher:

```bash
/path/to/mosa/mosa serve --allow-http
```

For example, if the repository is at `/home/me/src/mosa` and the data directory
is `/home/me/mosa-local`, run:

```bash
cd /home/me/mosa-local
/home/me/src/mosa/mosa serve --allow-http
```

The server listens on port `8080` by default. `--allow-http` does **not** limit
plain HTTP to localhost: it disables the pod's TLS requirement, and the HTTP
connector binds to the available network interfaces by default. Use it only
when access is otherwise isolated, such as on a trusted local network. Without
this option, pod requests must use TLS (or arrive through a configured trusted
proxy that reports HTTPS). Other server options are documented in the
[serve command guide](../server/src/main/resources/doc/serve/overview.md).

If the imprint, privacy, or terms files are missing or invalid, the server
creates templates for missing files, starts, and displays a warning banner in
the client. Edit the generated templates and fill in their required values.
You can also use the example ClearTerms profiles as a starting point. Once all
three documents are valid, restart the server and the warning banner disappears.
The templates and example profiles are starting points, not ready-to-publish
legal documents; review and complete them for the operator and jurisdiction.

The first semantic search may download the embedding model. For deployment,
backup, and operator configuration, read the serving documentation before
using a pod with real data.

## Explore the code

- `client/` contains the Kotlin/JS browser client.
- `server/` contains the Ktor pod and business services.
- `shared/` contains models shared between client and server.
- [Architecture](../spec/Architecture.md) describes module boundaries and
  system design.
- [Pod API](../spec/mosa-pod-api.raml) describes the server interface.
