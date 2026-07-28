# VanillaPoints

VanillaPoints is a Paper plugin for survival servers that want useful saved coordinates without teleportation. Players can save spawn, multiple homes and public warps, click coordinates to copy them, and still travel through the world normally.

Version `1.4.0` targets Paper 26.2 and adds incremental SQL persistence, safer database startup, complete bundled translations, smaller runtime-library-based packaging and improved internal service separation. It also includes the full selected feature set: named homes, point info cards, warp metadata, async YAML/SQLite/MySQL storage, PlaceholderAPI support, public Bukkit API/events, localization, cooldowns and safer command UX.

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
| Server | Paper `26.2` |
| Java | `25+` |
| Optional | PlaceholderAPI `2.11+` |
| Build | Maven wrapper included |

## Installation

1. Build with JDK 25+ using `./mvnw clean package`, or download the release jar.
2. Put `target/vanillapoints-1.4.0.jar` into `plugins/`.
3. Start the server once. Paper downloads the declared SQL runtime libraries and generates config/message files, so the first startup requires Maven Central access.
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

## Storage

SQLite is the default backend and stores data in `plugins/VanillaPoints/storage.db`. YAML remains supported through `storage.backend: yaml` and writes `plugins/VanillaPoints/data.yml` with safe temporary writes and `data.yml.bak` backups. MySQL uses the same point model and async save queue. If `storage.migrate-yaml-on-first-run` is enabled and SQL storage is empty, existing YAML data is imported once.

Named homes are stored under each player UUID. Existing old-format single homes are loaded as the `default` home automatically.

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

The API package is `dev.vaniley.vanillapoints.api`. It includes spawn, named home and warp getters/mutators plus immutable `PointInfo` DTOs. Mutation methods fire cancellable Bukkit events and must be called on the server main thread.

## Build And Verify

```bash
scripts/check-message-keys.sh
./mvnw clean package
```

The plugin jar is written to `target/vanillapoints-1.4.0.jar`.

## Test Server Checklist

- `/sethome`, `/home`, `/sethome mine`, `/home mine`, `/homes`, `/delhome mine`.
- Home limit enforcement with no permission, operator status, `vanillapoints.homes.vip`, and `vanillapoints.homes.premium`.
- Tab completion for `/home <tab>`, `/delhome <tab>` and `/warp <tab>`.
- `/warp <name>` and `/home <name>` info-card fields with `info-card.*` toggles.
- Restart persistence on YAML, and optionally SQLite/MySQL if those backends are selected.
- PlaceholderAPI startup with and without PlaceholderAPI installed.
