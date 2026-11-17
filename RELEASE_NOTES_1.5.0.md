# VanillaPoints 1.5.0 — Paper/Folia compatibility

VanillaPoints remains a lightweight coordinates-only homes, spawn and warps plugin. No command teleports players or forces chunks to load.

## Compatibility and packaging

- Targets Paper and Folia from Minecraft `1.21.11` through `26.3`.
- Compiles against the pinned Paper `1.21.11` API revision `1.21.11-R0.1-20260511.115010-91` with Java 21 bytecode rather than requiring Java 25 bytecode for the plugin.
- Declares `api-version: '1.21.11'` and `folia-supported: true`.
- Builds on JDK 21 and 25. Modern `26.1+` servers still require Java 25; `1.21.11` servers use Java 21 or newer.
- The build helper now runs the full Maven verification lifecycle, honors `JAVA_HOME`, and refuses ambiguous jar selection instead of copying a potentially stale build.
- Pinned packaging plugins and archive timestamps improve reproducibility. CI runs tests on JDK 21/25, validates packaged metadata/bytecode, repeats each build to compare hashes, and publishes jars with SHA-256 checksums.
- Runtime SQL libraries remain server-loaded; JUnit and Mockito remain test-only. The plugin does not bundle the Paper API or SQL drivers.
- Updated SQLite JDBC to `3.54.1.0`, MySQL Connector/J to `9.7.0`, HikariCP to `6.3.3`, and shaded bStats to `3.2.1`. Descriptor library versions now share the POM properties used by tests.
- PlaceholderAPI compile baseline is `2.11.7`, the first release with Folia scheduling support. Newer `2.12.3` marks Paper support experimental; Hikari 7.x and the newer MySQL driver line are deliberately deferred. See the README dependency table for upstream evidence and compatibility trade-offs.

## Folia behavior and integrations

- Background persistence and player notifications use the appropriate Paper/Folia schedulers.
- Commands and API operations coordinate with reload through a lifecycle lock; reload requests are queued globally rather than upgrading a lock from a command/event callback.
- API providers resolve the current point service per operation, including after reload. Mutations remain synchronous server-tick operations with cancellable events; arbitrary async callers are not supported.
- Optional biome info is read only if the chunk is loaded and the caller owns the region. Other cases show unknown rather than loading chunks or reaching across regions.
- Existing message files, copy formats, permission limits, optional info-card rows and YAML/SQLite/MySQL backends remain configurable.
- Feedback accepts namespaced sound keys as well as Bukkit-style names, caches settings and bounds effect parameters. Admin player lookups use cached names/UUIDs rather than a blocking Mojang request, with localized unknown-player output.
- Location-returning API methods, including `PointInfo.toLocation()`, now throw `IllegalStateException` when called from an async worker. Use a Paper main thread or a Folia region/global tick thread; immutable `PointInfo` reads and name snapshots remain available to async callers.
- API reads return empty snapshots and mutations return `false` while a queued reload or lifecycle maintenance prevents immediate access, as well as after disable. Consumers should retry after the queued reload completes; never wait or busy-loop on a server tick thread.

## Upstream availability and verification limits

On **2026-11-18 (MSK, UTC+03:00)**, the official PaperMC API listed Paper `1.21.11` build `132` and `26.3` build `253` as stable, and Folia `1.21.11` build `14` and `26.3` build `20` as stable. Folia `26.2` build `7` was beta.

Sources:

- https://fill.papermc.io/v3/projects/paper/versions/1.21.11/builds/latest
- https://fill.papermc.io/v3/projects/paper/versions/26.3/builds/latest
- https://fill.papermc.io/v3/projects/folia/versions/1.21.11/builds/latest
- https://fill.papermc.io/v3/projects/folia/versions/26.3/builds/latest
- https://docs.papermc.io/paper/getting-started/

Upstream availability and automated build checks are not a live server certification for every release. See the README server checklist and record exact builds for deployment testing. Beta builds remain experimental.

## Upgrade

1. Stop the server and back up `plugins/VanillaPoints` and any external database.
2. Remove the previous VanillaPoints jar and install `vanillapoints-1.5.0.jar`.
3. Start with the Java version required by your server and allow access to Maven Central for declared runtime libraries on first startup.
4. Keep existing config, translation and point data files. Review optional info-card settings if cross-region biome output previously appeared available.
5. Exercise save/reload/restart and concurrent-region commands on a staging server before production.
