# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Mercantile is a Minecraft 1.21.1 Fabric mod — a villager and trade overhaul. Java 21, Fabric Loader 0.16.10, Loom 1.9. The full feature spec lives in `SPEC.md`. Sprint and backlog tracking are in `.plan/`.

## Build Commands

```bash
./gradlew build          # compile + test + jar
./gradlew test           # JUnit tests only
./gradlew runGametest    # Fabric gametests (headless server)
./gradlew runClient      # launch dev client
./gradlew runServer      # launch dev server
./gradlew genSources     # decompile MC sources for IDE nav
```

A `Makefile` wraps these (`make build`, `make test`, `make run-client`, etc.) and adds `make release BUMP=patch|minor|major`.

Run a single JUnit test: `./gradlew test --tests "com.rfizzle.mercantile.SomeTest"`

## Source Layout

Loom's `splitEnvironmentSourceSets()` is enabled — three source sets:

| Source set | Root | Purpose |
|---|---|---|
| `main` | `src/main/java` | Server + common logic. Entrypoint: `Mercantile.java` |
| `client` | `src/client/java` | Client-only code. Entrypoint: `MercantileClient.java` |
| `gametest` | `src/gametest/java` | Fabric gametests (run with `runGametest`). Has `main` on its classpath but is NOT included in the jar. |

JUnit tests go in the standard `src/test/java` directory. The test classpath includes `fabric-loader-junit` but excludes `fabric-api` — tests that need Fabric APIs must use gametests instead.

## Key Conventions

- **Mod ID:** `mercantile` — use `Mercantile.id("path")` to create `ResourceLocation`s.
- **Mappings:** Official Mojang mappings (not Yarn). Use Mojang class/method names everywhere.
- **Asset philosophy:** Vanilla-first. All sounds, particles, and icons use existing vanilla assets. Villager pickup items use player heads with pre-existing skin textures (no custom textures). The only custom texture planned is the sentry pylon block model.
- **Mixin config:** `mercantile.mixins.json` in `src/main/resources`. Mixin package: `com.rfizzle.mercantile.mixin`.

## Compat Integrations

The mod has optional integrations (all `modCompileOnly` — not bundled):

- **Mod Menu** — config screen entry via `ModMenuIntegration`
- **Cloth Config** — settings GUI builder
- **Jade / WTHIT** — tooltip overlays
- **EMI / REI / JEI** — recipe viewer support

Compat classes live under `com.rfizzle.mercantile.compat.<modid>`.

## Version Scheme

Version is computed from git tags at build time (`build.gradle` `computeModVersion()`). Base version is in `gradle.properties` as `mod_version`. Tagged commits produce clean versions; post-tag commits append `+<commits>.g<sha>`.
