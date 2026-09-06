# Gradle Wrapper — known gap and how to close it

This repo ships `gradlew` and `gradlew.bat` (plain text shell scripts —
safe to hand-author and verified with `bash -n`) but does **not** ship
`gradle/wrapper/gradle-wrapper.jar`.

That jar is compiled Java bytecode distributed by Gradle itself. It was
not generated here because doing so requires either:

- network access to `services.gradle.org` to fetch the real jar, or
- a local Gradle installation to run `gradle wrapper` and regenerate it

and this repository was authored in a sandbox with neither. Committing a
hand-written/guessed binary in its place would risk a wrapper that looks
present but silently fails or — worse — runs something that isn't
actually Gradle. That's a worse outcome than leaving it out and saying so.

## This is already handled in CI

`.github/workflows/android-ci.yml` installs a real Gradle 8.9 (via
`gradle/actions/setup-gradle`), runs `gradle wrapper --gradle-version 8.9`
to generate a correct, checksum-verified `gradle-wrapper.jar`, then
proceeds with `./gradlew` exactly as before. No manual step needed for CI
runs.

## To get a working `./gradlew` locally (e.g. in Termux)

You have real network access there, so either:

```bash
# Option A — if you have any Gradle available locally (Android Studio ships one):
gradle wrapper --gradle-version 8.9 --distribution-type bin

# Option B — fetch the jar directly:
curl -L -o gradle/wrapper/gradle-wrapper.jar \
  https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar
```

Either regenerates/downloads the same file CI verifies against. Commit it
once generated — from then on `./gradlew` works locally without repeating
this step, and future contributors don't hit this gap.
