# Client Agent Guide

## Scope
- Applies to everything under `client/`.
- Follow root [`AGENTS.md`](/home/kai/Projects/straightway/mosa/AGENTS.md) as well.

## Test Commands
- Preferred browser test run: `./gradlew :client:jsBrowserTest`
- Aggregate JS test task: `./gradlew :client:jsTest`
- Prefer running Gradle from repo root.

## Important Pitfall
- `./gradlew :client:jsTest --tests ...` does not work in this project.
- Gradle fails during task configuration with `Unknown command-line option '--tests'`.
- If you need narrower execution, first inspect available tasks with `./gradlew :client:tasks --all` and verify which task actually supports filtering.

## Environment Notes
- Browser tests run through Karma with Firefox Headless.
- `prepareFirefoxEnvironment` is wired into `KotlinJsTest` tasks automatically.
- Use Gradle tasks above instead of starting Karma manually.
