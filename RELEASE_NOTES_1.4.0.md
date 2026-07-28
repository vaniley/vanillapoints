# VanillaPoints 1.4.0 — Paper 26.2

## GitHub release description

VanillaPoints 1.4.0 is the Paper 26.2 maintenance and performance release. It keeps the plugin's no-teleport survival gameplay while making persistence, startup and distribution safer and lighter.

### Highlights

- Added official Paper 26.2 support and a Java 25 target.
- Reworked SQLite/MySQL saves to update only changed rows instead of deleting and recreating the entire points table.
- Added SQLite persistence and incremental-write integration tests.
- Database initialization now fails fast, closes partially opened pools and disables the plugin with a clear error instead of continuing in an unsafe state.
- Added configurable SQL connection and validation timeouts.
- Reduced the plugin JAR from about 19 MB to about 156 KB by using Paper's runtime library loader for SQLite JDBC, MySQL Connector/J and HikariCP.
- Completed all bundled locale files: English, Russian, Ukrainian, Spanish, German, French, Simplified Chinese, Japanese, Portuguese and Polish.
- Updated PlaceholderAPI and update-checker metadata access for the Paper 26.2 API.
- Extracted command-limit and audiovisual-feedback logic into dedicated services.
- Made bStats configuration honest and explicit: metrics stay disabled until a valid registered service ID is configured.
- Expanded automated verification to 19 passing tests.

### Requirements

- Paper 26.2
- Java 25 or newer
- Internet access on the first server startup so Paper can download declared runtime libraries from Maven Central
- PlaceholderAPI 2.11+ is optional

### Upgrade notes

1. Back up the `plugins/VanillaPoints` directory and database.
2. Replace the previous JAR with `vanillapoints-1.4.0.jar`.
3. Start the server and allow Paper to download the declared libraries.
4. Review the new `storage.connection-timeout-ms`, `storage.validation-timeout-ms` and `metrics.service-id` options.
5. Existing YAML, SQLite and MySQL data remains compatible.

## Modrinth version description

### VanillaPoints 1.4.0 for Paper 26.2

A lightweight coordinates-only homes, spawn and warps plugin for survival servers—no teleportation shortcuts.

**New in 1.4.0:**

- Paper 26.2 and Java 25 support
- Incremental SQLite/MySQL writes for safer and faster saves
- Fail-fast database startup with configurable timeouts
- Tiny ~156 KB plugin JAR; Paper loads SQL libraries at runtime
- Complete translations for all 10 bundled languages
- Updated Paper API usage and cleaner internal architecture
- 19 passing automated tests, including real SQLite persistence checks

Named homes, permission limits, public/private categorized warps, clickable coordinates, info cards, cooldowns, PlaceholderAPI, EssentialsX import and the public Bukkit API are all included.

**Required:** Paper 26.2, Java 25+. The first startup needs access to Maven Central for runtime libraries. PlaceholderAPI is optional.
