# VanillaPoints

VanillaPoints is a Paper/Folia plugin for survival servers that want useful saved coordinates without teleportation. Players can save spawn, multiple homes and public warps, click coordinates to copy them, and still travel through the world normally.

Version `1.5.0` targets Paper and Folia from Minecraft `1.21.11` through `26.3`, using the oldest supported Paper API and Java 21 bytecode. It keeps coordinate-only gameplay, configurable messages and presentation, async YAML/SQLite/MySQL storage, optional PlaceholderAPI support and a public Bukkit API/events.

## Why VanillaPoints

- No teleport shortcuts: commands show coordinates only.
- Multiple homes per player: `/sethome [name]`, `/home [name]`, `/homes`, `/delhome <name>`.
- Permission-based home limits: one home by default, configurable VIP/premium overrides and unlimited homes for operators.
- Rich point output: coordinates, world, biome, world time, weather, creator and age.
- Warps with metadata: optional Material icon and description through `/setwarp`.
- Click-to-copy chat: colored coordinates in chat, clean configurable clipboard text.
- Safe server operation: async saves, YAML backups, SQL backends, cooldowns and rate limits.
- Integrations: PlaceholderAPI expansion and Bukkit `ServicesManager` API.
- Bundled languages: `en`, `ru`, `uk`, `es`, `de`, `fr`, `zh`, `ja`, `pt`, `pl`.

## Requirements

| Requirement | Version |
| --- | --- |
| Server compatibility target | Paper or Folia `1.21.11`–`26.3` |
| Server Java | Java `21+` for `1.21.11`; Java `25+` for `26.1+` |
| Optional | PlaceholderAPI `2.11.7+` (first Folia-compatible release) |
| Build | JDK `21+`, Bash, `tar`, and `curl` or `wget`; Maven `3.9.11` wrapper included |

The plugin's Java 21 bytecode does not lower the server's Java requirement. Use the Java version required by the server you run. Bukkit/Spigot and versions older than `1.21.11` are not supported targets.

### Upstream availability, not runtime certification

The official PaperMC download API returned these builds when checked on **2026-11-18 (MSK, UTC+03:00)**:

| Minecraft | Paper | Folia |
| --- | --- | --- |
| `1.21.11` | `132` — stable | `14` — stable |
| `26.1.2` | `74` — stable | `8` — stable |
| `26.2` | `135` — stable | `7` — beta |
| `26.3` | `253` — stable | `20` — stable |

Sources: https://fill.papermc.io/v3/projects/paper and https://fill.papermc.io/v3/projects/folia; individual build metadata is at `https://fill.papermc.io/v3/projects/<paper|folia>/versions/<version>/builds/latest`. Java requirements: https://docs.papermc.io/paper/getting-started/.

These are upstream availability records, not evidence of a successful VanillaPoints server session on every version. CI checks compilation, unit/integration tests and packaging on JDK 21 and 25, not a live server matrix. Treat beta server builds as experimental and run the server checklist below before upgrading production.

## Installation

1. Build with JDK 21+ using `scripts/build-plugin.sh --clean`, or download the release jar.
2. Stop the server, back up existing plugin data, and put `target/vanillapoints-1.5.0.jar` into `plugins/`, removing the old VanillaPoints jar.
3. Start the server with its required Java version. Paper/Folia downloads the declared SQL runtime libraries and generates config/message files, so the first startup requires Maven Central access.
4. Edit `plugins/VanillaPoints/config.yml` if needed.
5. Use `/vp reload` after config/message changes.

## Commands

| Command | Aliases | Description | Permission |
| --- | --- | --- | --- |
| `/spawn` | `/s` | Show spawn coordinates. | none |
| `/setspawn` | none | Save spawn at your current location. | `vanillapoints.setspawn` |
| `/sethome` | `/sh`, `/setmyhome` | Save the default home. | none |
| `/sethome <name>` | `/sh <name>` | Save or update a named home. | none |
| `/home` | `/h`, `/myhome` | Show the default home info. | none |
| `/home <name>` | `/h <name>` | Show a named home info. | none |
| `/homes` | none | List your homes with `used/limit`. | none |
| `/delhome <name>` | none | Delete one of your homes. | none |
| `/warp` | `/w` | List all warps. | none |
| `/warp <name>` | `/w <name>` | Show warp coordinates, description and info card. | none |
| `/warps` | none | List all warps. | none |
| `/setwarp <name> [--icon <material>] [description...]` | none | Save a warp with optional metadata. | `vanillapoints.setwarp` |
| `/delwarp <name>` | none | Delete a warp, optionally after confirmation. | `vanillapoints.delwarp` |
| `/vp help [page]` | `/vanillapoints help [page]` | Show paginated command help. | none |
| `/vp reload` | `/vanillapoints reload` | Reload config, messages and storage. | `vanillapoints.reload` |

Home and warp names may contain Latin letters, numbers, underscores and hyphens, up to 32 characters. Names are stored case-insensitively. The old `/sethome` and `/home` behavior is preserved through the `default` home.

## Permissions

| Permission | Default | Purpose |
| --- | --- | --- |
| `vanillapoints.setspawn` | `op` | Allows `/setspawn`. |
| `vanillapoints.setwarp` | `op` | Allows `/setwarp`. |
| `vanillapoints.delwarp` | `op` | Allows `/delwarp`. |
| `vanillapoints.reload` | `op` | Allows `/vp reload`. |
| `vanillapoints.bypass.cooldown` | `op` | Bypasses cooldown and rate-limit checks. |
| `vanillapoints.bypass.confirm` | `op` | Bypasses warp deletion confirmation. |
| `vanillapoints.homes.vip` | `false` | Example 5-home limit permission. |
| `vanillapoints.homes.premium` | `false` | Example 10-home limit permission. |
| `vanillapoints.lang.<code>` | `false` | Uses a per-player language when enabled. |

## Configuration

The generated `config.yml` contains the main controls:

```yml
settings:
  language: en
  per-player-permissions: false
  save-immediately: true
  normalize-to-block: true
  copy-format: '{x} {y} {z}'

homes:
  default-limit: 1
  operator-unlimited: true
  limits-by-permission:
    vanillapoints.homes.vip: 5
    vanillapoints.homes.premium: 10

info-card:
  enabled: false
  show-biome: true
  show-time: false
  show-weather: false
  show-creator: true
  show-age: false
  lines:
    - header
    - coordinates
    - biome
    - time
    - weather
    - creator
    - age

storage:
  backend: sqlite # yaml | sqlite | mysql
  migrate-yaml-on-first-run: true
  connection-timeout-ms: 10000
  validation-timeout-ms: 5000

cooldowns:
  default: 2s
  per-command:
    home: 3s
    warp: 2s
    sethome: 10s
    setwarp: 10s

rate-limit:
  window: 60s
  max-commands: 30
```

`settings.copy-format` supports `{x}`, `{y}`, `{z}`, `{world}` and contextual placeholders such as `{warp}`. `info-card.lines` controls the order and presence of card rows; valid values are `header`, `coordinates`, `biome`, `time`, `weather`, `creator` and `age`. Duration values support `ms`, `s`, `m` and `h`.

Keep optional info cards disabled for the smallest output, or enable only the rows you need. Bundled `messages*.yml` files control translated text and formatting; missing translations fall back to the default messages. None of these options adds teleportation.

Feedback sound settings accept both Bukkit-style names such as `ENTITY_PLAYER_LEVELUP` and namespaced keys such as `minecraft:entity.player.levelup`. Feedback settings are cached on startup/reload, with particle counts and sound parameters bounded to avoid excessive effects. Admin player lookup uses cached names or explicit UUIDs instead of blocking commands on a Mojang network lookup; unknown players receive a localized message.

On Folia, biome information is queried only when the saved point's chunk is already loaded **and** the calling thread owns its region. Otherwise the card shows the configured unknown value. Coordinate lookups never force chunks to load or synchronously wait for another region.

## Storage

SQLite is the default backend and stores data in `plugins/VanillaPoints/storage.db`. YAML remains supported through `storage.backend: yaml` and writes `plugins/VanillaPoints/data.yml` with safe temporary writes and `data.yml.bak` backups. MySQL uses the same point model and async save queue. If `storage.migrate-yaml-on-first-run` is enabled and SQL storage is empty, existing YAML data is imported once.

Named homes are stored under each player UUID. Existing old-format single homes are loaded as the `default` home automatically.

EssentialsX imports parse files asynchronously into a detached snapshot, then merge on the global scheduler under the lifecycle write lock. Persistence is queued asynchronously after the merge; parsing does not block a player region tick or write directly into active storage.

## PlaceholderAPI

PlaceholderAPI is optional. VanillaPoints starts normally without it and registers the `vanillapoints` expansion automatically when PlaceholderAPI is present.

| Placeholder | Description |
| --- | --- |
| `%vanillapoints_home_x%` | Default home block X. |
| `%vanillapoints_home_y%` | Default home block Y. |
| `%vanillapoints_home_z%` | Default home block Z. |
| `%vanillapoints_home_world%` | Default home world. |
| `%vanillapoints_home_set%` | `true` if default home exists. |
| `%vanillapoints_warp_<name>_x%` | Warp block X. |
| `%vanillapoints_warp_<name>_world%` | Warp world. |
| `%vanillapoints_warp_<name>_description%` | Warp description. |
| `%vanillapoints_warp_count%` | Number of saved warps. |
| `%vanillapoints_warp_list%` | Separator-joined warp list. |
| `%vanillapoints_distance_home%` | Distance to default home in the same world. |
| `%vanillapoints_bearing_home%` | Compass direction to default home. |

Missing values return `placeholders.empty-value`, never an exception.

## Public API

Other plugins can access VanillaPoints through Bukkit services:

```java
RegisteredServiceProvider<VanillaPointsAPI> provider = Bukkit.getServicesManager()
        .getRegistration(VanillaPointsAPI.class);
VanillaPointsAPI api = provider == null ? null : provider.getProvider();
```

The API package is `dev.vaniley.vanillapoints.api`. It includes spawn, named home and warp getters/mutators plus immutable `PointInfo` DTOs.

- Mutation events are synchronous and cancellable. Mutations and `Location`-returning methods require a valid server tick context: the main thread on Paper; a region/entity tick thread or the global tick thread on Folia. Arbitrary async workers are rejected. Use the player's entity scheduler for player work and the global-region scheduler for console/global work; do not use legacy Bukkit task scheduling on Folia.
- `PointInfo` reads and name lists return immutable snapshots. The provider resolves the current point service under a lifecycle read lock for every operation, so a retained API provider does not write to storage detached by a reload.
- Commands and tab completion share the lifecycle read lock. Reload is queued on the global scheduler and takes the write lock before replacing services. A reload request returns before that queued reload finishes; do not block a tick thread waiting for it or attempt an in-place lock upgrade from an event listener.
- Saved coordinates do not grant ownership of the corresponding world region. Integrations that additionally inspect blocks, biomes or entities must schedule that work on the owning region themselves.

## Build And Verify

```bash
scripts/check-message-keys.sh
bash scripts/tests/build-plugin-test.sh
scripts/build-plugin.sh --clean
# Equivalent Maven verification, including all tests:
./mvnw --batch-mode --no-transfer-progress clean verify
```

Set `JAVA_HOME` to a full JDK installation when multiple Java versions are installed. The build helper honors it, checks for JDK 21+, runs tests without `-DskipTests`, and refuses to deploy an ambiguous/stale jar selection. To copy a successful build to a stopped local server:

```bash
JAVA_HOME=/path/to/jdk-21 scripts/build-plugin.sh --clean --plugins-dir /path/to/server/plugins
```

The plugin jar is written to `target/vanillapoints-1.5.0.jar`. Only bStats is shaded; Paper/Folia supplies the API and loads the declared SQL libraries at runtime. Mockito and JUnit are test-only dependencies.

Metrics are disabled by default and require a valid registered `metrics.service-id` when enabled. The bundled bStats `3.2.1` detects Folia and uses its own background executor instead of invoking the unsupported Bukkit scheduler. The SQL dependencies declared under `plugin.yml`'s `libraries` use Paper/Folia's library loader; they do not need to be copied into `plugins/` separately. Their versions are filtered from the same POM properties used at compile/test time, preventing descriptor drift.

### Dependency maintenance choices

Published Maven metadata and upstream notes were checked on **2026-11-18 (MSK, UTC+03:00)**:

| Dependency | Selected | Rationale |
| --- | --- | --- |
| SQLite JDBC | `3.54.1.0` | Latest published stable; native-handle, null-binding and temporary-file permission fixes. Real SQLite persistence/rollback tests run on both build JDKs. |
| MySQL Connector/J | `9.7.0` | Updated within the existing 9.x line; upstream explicitly supports MySQL `8.0+`. The newer `26.10.0` line has conflicting upstream `8.0+`/`8.4+` support statements, so this release avoids silently raising the database floor. No live MySQL validation is claimed. |
| HikariCP | `6.3.3` | Latest 6.x maintenance release, including timeout/schema/metrics-reflection fixes; avoids the 7.x behavior change in this compatibility release. |
| bStats | `3.2.1` | Latest stable; verified Folia scheduler bypass remains intact. Shaded into the plugin namespace. |
| PlaceholderAPI | `2.11.7` | Stable patch adds Folia scheduler support. Newer `2.12.3` upstream notes call Paper support experimental and recommend waiting, so the compile baseline remains `2.11.7`. Install an appropriate compatible upstream build for your actual server. |

Metadata sources: https://repo.maven.apache.org/maven2/org/xerial/sqlite-jdbc/maven-metadata.xml, https://repo.maven.apache.org/maven2/com/mysql/mysql-connector-j/maven-metadata.xml, https://repo.maven.apache.org/maven2/com/zaxxer/HikariCP/maven-metadata.xml, https://repo.maven.apache.org/maven2/org/bstats/bstats-bukkit/maven-metadata.xml and https://repo.extendedclip.com/content/repositories/placeholderapi/me/clip/placeholderapi/maven-metadata.xml.

Upstream notes: https://github.com/xerial/sqlite-jdbc/releases/tag/3.54.1.0, https://dev.mysql.com/doc/relnotes/connector-j/en/news-9-7-0.html, https://dev.mysql.com/doc/connector-j/en/connector-j-versions.html, https://github.com/brettwooldridge/HikariCP/blob/dev/CHANGES, https://github.com/PlaceholderAPI/PlaceholderAPI/releases/tag/2.11.7 and https://github.com/PlaceholderAPI/PlaceholderAPI/releases/tag/2.12.3.

The compiler uses `--release 21`. Paper API `1.21.11-R0.1-20260511.115010-91` is pinned to an immutable snapshot revision rather than a moving `SNAPSHOT`; its metadata is published at https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/1.21.11-R0.1-SNAPSHOT/maven-metadata.xml. Packaging plugins and archive timestamps are pinned. The Java 21/25 CI matrix runs full verification twice, checks matching SHA-256 hashes within each JDK job, validates plugin metadata and classfile compatibility, and uploads the jar with `SHA256SUMS`. Reproducibility is checked for the same source tree, dependencies and JDK; cross-JDK byte-for-byte identity is not assumed.

## Test Server Checklist

- `/sethome`, `/home`, `/sethome mine`, `/home mine`, `/homes`, `/delhome mine`.
- Home limit enforcement with no permission, operator status, `vanillapoints.homes.vip`, and `vanillapoints.homes.premium`.
- Tab completion for `/home <tab>`, `/delhome <tab>` and `/warp <tab>`.
- `/warp <name>` and `/home <name>` info-card fields with `info-card.*` toggles.
- Restart persistence on YAML, and optionally SQLite/MySQL if those backends are selected.
- PlaceholderAPI startup with and without PlaceholderAPI installed.
- Paper and Folia startup on `1.21.11` with Java 21 and `26.3` with Java 25; record the exact server build, not just the Minecraft version.
- On Folia, concurrent players in separate regions saving/deleting homes and warps while another player runs `/vp reload`; verify no scheduler/region ownership errors or lost saves after a clean restart.
- Enable biome info cards and query points in the caller's region, a different region and an unloaded chunk. The last two must show unknown without forcing a chunk load.
- Confirm all lookup commands still only display/copy coordinates and never teleport a player.
