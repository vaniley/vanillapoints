# VanillaPoints

VanillaPoints is a Paper plugin for survival servers that want useful coordinates without teleportation. Players can save spawn, home and warp points, click chat output to copy coordinates, and keep the game focused on walking, boats, rails, maps and Nether travel.

Version `1.2.5` is a release build with async persistence, SQL storage options, public API/events, bundled localization, PlaceholderAPI support, command safety, and polished chat UX.

## Highlights

- Coordinate display only: no teleportation commands and no vanilla progression shortcuts.
- Clickable coordinates: every point message copies a configurable coordinate string to clipboard.
- Spawn, home and named warps: simple commands for the core points most survival servers need.
- Warp metadata: `/setwarp` supports an optional Material icon and free-text description.
- Safer deletion: `/delwarp` can require clickable confirm/cancel with a TTL.
- Friendly command UX: aliases, smart tab completion, paginated `/vp help`, cooldowns and rate limits.
- Player feedback: configurable sounds and particles for successful home/warp saves.
- Durable storage: YAML by default, optional SQLite/MySQL, async saves and safe YAML backups.
- Public integration surface: Bukkit `ServicesManager` API plus cancellable point events.
- PlaceholderAPI: optional expansion for scoreboards, tabs, chat plugins and HUDs.
- Bundled languages: English, Russian, Ukrainian, Spanish, German, French, Simplified Chinese, Japanese, Portuguese and Polish.

## Requirements

| Requirement | Version |
| --- | --- |
| Server | Paper API `1.21+` |
| Java | `21+` |
| Build tool | Maven wrapper or Maven |
| Optional integration | PlaceholderAPI `2.11+` |

## Installation

1. Download or build the VanillaPoints jar.
2. Place the jar in the server `plugins` directory.
3. Restart the server.
4. Edit `plugins/VanillaPoints/config.yml` and message files if needed.
5. Run `/vp reload` after config or message changes.

## Commands

| Command | Aliases | Description | Permission |
| --- | --- | --- | --- |
| `/spawn` | `/s` | Show spawn coordinates. | none |
| `/setspawn` | none | Save spawn at your current location. | `vanillapoints.setspawn` |
| `/home` | `/h`, `/myhome` | Show your home coordinates. | none |
| `/sethome` | `/sh`, `/setmyhome` | Save your home at your current location. | none |
| `/warp` | `/w` | List all warps. | none |
| `/warp <name>` | `/w <name>` | Show one warp's coordinates and description. | none |
| `/warps` | none | List all warps. | none |
| `/setwarp <name> [--icon <material>] [description...]` | none | Save a named warp with optional metadata. | `vanillapoints.setwarp` |
| `/delwarp <name>` | none | Delete a warp, optionally after confirmation. | `vanillapoints.delwarp` |
| `/vp help [page]` | `/vanillapoints help [page]` | Show paginated command help. | none |
| `/vp reload` | `/vanillapoints reload` | Reload config, messages and storage state. | `vanillapoints.reload` |

Warp names may contain Latin letters, numbers, underscores and hyphens, up to 32 characters. Names are stored case-insensitively.

## Permissions

| Permission | Default | Purpose |
| --- | --- | --- |
| `vanillapoints.setspawn` | `op` | Allows `/setspawn`. |
| `vanillapoints.setwarp` | `op` | Allows `/setwarp`. |
| `vanillapoints.delwarp` | `op` | Allows `/delwarp`. |
| `vanillapoints.reload` | `op` | Allows `/vp reload`. |
| `vanillapoints.bypass.cooldown` | `op` | Bypasses cooldown and rate-limit checks. |
| `vanillapoints.bypass.confirm` | `op` | Bypasses warp deletion confirmation. |
| `vanillapoints.lang.<code>` | `false` | Uses a per-player language when enabled in config. |

Supported language permission codes are `en`, `ru`, `uk`, `es`, `de`, `fr`, `zh`, `ja`, `pt` and `pl`.

## Configuration

Default `config.yml`:

```yml
settings:
  # Available bundled languages: en, ru, uk, es, de, fr, zh, ja, pt, pl.
  language: en
  # When enabled, players with vanillapoints.lang.<code> receive messages in that language.
  per-player-permissions: false
  # When enabled, changes are queued for async persistence immediately after commands/API mutations.
  # When disabled, data is flushed during /vp reload and plugin shutdown.
  save-immediately: true
  normalize-to-block: true
  copy-format: '{x} {y} {z}'

placeholders:
  empty-value: ''
  warp-list-separator: ', '

storage:
  backend: yaml # yaml | sqlite | mysql
  migrate-yaml-on-first-run: true
  sqlite:
    file: storage.db
  mysql:
    host: localhost
    port: 3306
    database: vanillapoints
    username: root
    password: ''
    pool-size: 8
    use-ssl: true

feedback:
  sounds: true
  particles: true
  events:
    home-set:
      sound: ENTITY_PLAYER_LEVELUP
      volume: 0.6
      pitch: 1.4
      particle: HAPPY_VILLAGER
      count: 12
    warp-set:
      sound: BLOCK_BEACON_ACTIVATE
      volume: 0.8
      pitch: 1.0
      particle: END_ROD
      count: 16

help:
  per-page: 7
  show-hidden-commands: false

cooldowns:
  default: 2s
  per-command:
    home: 3s
    warp: 2s
    sethome: 10s
    setwarp: 10s
  bypass-permission: vanillapoints.bypass.cooldown

rate-limit:
  window: 60s
  max-commands: 30

safety:
  confirm-deletions: true
  confirm-ttl: 30s
  bypass-permission: vanillapoints.bypass.confirm
```

`settings.copy-format` supports `{x}`, `{y}`, `{z}`, `{world}` and `{warp}` where applicable. Chat messages can color coordinates independently while the clipboard output stays clean.

Duration values support `ms`, `s`, `m` and `h`, for example `500ms`, `3s`, `2m` or `1h`.

## Localization

VanillaPoints ships these message files:

| Code | File | Language |
| --- | --- | --- |
| `en` | `messages.yml` | English |
| `ru` | `messages_ru.yml` | Russian |
| `uk` | `messages_uk.yml` | Ukrainian |
| `es` | `messages_es.yml` | Spanish |
| `de` | `messages_de.yml` | German |
| `fr` | `messages_fr.yml` | French |
| `zh` | `messages_zh.yml` | Simplified Chinese |
| `ja` | `messages_ja.yml` | Japanese |
| `pt` | `messages_pt.yml` | Portuguese |
| `pl` | `messages_pl.yml` | Polish |

`settings.language` controls the global language. If `settings.per-player-permissions` is `true`, a player with `vanillapoints.lang.ru` or another language permission receives command messages in that language. Missing keys fall back to English.

To verify translation completeness while developing, run:

```bash
scripts/check-message-keys.sh
```

## PlaceholderAPI

PlaceholderAPI is optional. VanillaPoints starts normally without it and registers the `vanillapoints` expansion automatically when PlaceholderAPI is installed.

| Placeholder | Description |
| --- | --- |
| `%vanillapoints_spawn_x%` | Spawn block X. |
| `%vanillapoints_spawn_y%` | Spawn block Y. |
| `%vanillapoints_spawn_z%` | Spawn block Z. |
| `%vanillapoints_spawn_world%` | Spawn world name. |
| `%vanillapoints_spawn_set%` | `true` if a custom spawn is saved. |
| `%vanillapoints_home_x%` | Player home block X. |
| `%vanillapoints_home_y%` | Player home block Y. |
| `%vanillapoints_home_z%` | Player home block Z. |
| `%vanillapoints_home_world%` | Player home world name. |
| `%vanillapoints_home_set%` | `true` if the player has a home. |
| `%vanillapoints_warp_<name>_x%` | Warp block X. |
| `%vanillapoints_warp_<name>_y%` | Warp block Y. |
| `%vanillapoints_warp_<name>_z%` | Warp block Z. |
| `%vanillapoints_warp_<name>_world%` | Warp world name. |
| `%vanillapoints_warp_<name>_set%` | `true` if the warp exists. |
| `%vanillapoints_warp_<name>_description%` | Warp description, if set. |
| `%vanillapoints_warp_<name>_icon%` | Warp Material icon, if set. |
| `%vanillapoints_warp_count%` | Number of saved warps. |
| `%vanillapoints_warp_list%` | Configurable separator-joined warp list. |
| `%vanillapoints_distance_home%` | Player distance to home in the same world. |
| `%vanillapoints_bearing_home%` | Compass direction from player to home. |

Missing values return `placeholders.empty-value`, never an exception. Warp placeholders parse the field suffix from the right, so warp names with underscores work.

## Storage

The default YAML backend stores data in `plugins/VanillaPoints/data.yml`. Saves use a temporary file and `data.yml.bak` backup to reduce corruption risk.

SQLite and MySQL use the same in-memory command cache and persist snapshots through the async save service. If `storage.migrate-yaml-on-first-run` is enabled and the selected SQL backend is empty, existing YAML data is imported once while the YAML file remains as a backup.

The SQL schema includes point metadata columns for descriptions, icons and creator information. Upgrades add missing metadata columns automatically.

## Public API

Other plugins can access VanillaPoints through Bukkit services:

```java
RegisteredServiceProvider<VanillaPointsAPI> provider = Bukkit.getServicesManager()
        .getRegistration(VanillaPointsAPI.class);
VanillaPointsAPI api = provider == null ? null : provider.getProvider();
```

The public API package is `dev.vaniley.vanillapoints.api`. Mutation methods and `Location`-returning methods must be called on the server main thread because they interact with Bukkit worlds and events. Read-only `PointInfo` methods return immutable DTOs.

Mutation methods fire cancellable events from `dev.vaniley.vanillapoints.api.event` for spawn set, home set, warp set and warp delete operations.

## Building

Build with the Maven wrapper:

```bash
./mvnw clean package
```

The shaded plugin jar is written to `target/vanillapoints-1.2.5.jar`.

Run the standard verification used for this release:

```bash
scripts/check-message-keys.sh
./mvnw test
./mvnw package
```
