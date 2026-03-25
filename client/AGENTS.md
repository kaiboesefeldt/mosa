# Client Agent Guide

## Scope
- This file applies to everything under `client/`.
- Follow the repository root [`AGENTS.md`](/home/kai/Projects/straightway/mosa/AGENTS.md) in addition to this file.

## JS Test Startup
- Use `./gradlew :client:jsBrowserTest` for the real browser-based client test run.
- Use `./gradlew :client:jsTest` when you explicitly want the aggregate JS test task.
- Prefer running commands from the repository root.

## Important Pitfall
- `./gradlew :client:jsTest --tests ...` does not work in this project. Gradle fails during task configuration with `Unknown command-line option '--tests'`.
- If you need to confirm the available client test tasks, run `./gradlew :client:tasks --all`.
- If you need a narrower run, verify first which concrete client task supports filtering before assuming standard JVM `--tests` behavior.

## Environment Notes
- Client browser tests run through Karma with Firefox Headless.
- `prepareFirefoxEnvironment` is wired into `KotlinJsTest` tasks automatically by Gradle configuration, so use the Gradle tasks above instead of trying to start Karma manually.
