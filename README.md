# VanillaPoints

VanillaPoints is a lightweight Paper plugin for private survival servers. It lets players save and view coordinates for spawn, homes and warps without teleportation, preserving vanilla-style navigation.

## Requirements

- Paper API 1.21+
- Java 21+
- Maven for building from source

## Features

- No teleportation, only coordinate display.
- Click displayed coordinates to copy them to clipboard.
- YAML storage in `data.yml`.
- Optional SQLite and MySQL storage backends.
- Async persistence queue for command/API mutations.
- Safer data saves using `data.yml.tmp` and `data.yml.bak`.
- Public Bukkit `ServicesManager` API and cancellable point events.
- Configurable messages with bundled English and Russian translations.
- Configurable copy format.
- Data validation when loading saved points.
- Admin reload command.

## Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/spawn` | Show spawn coordinates. | none |
| `/setspawn` | Save the spawn point at your current location. | `vanillapoints.setspawn` |
| `/home` | Show your home coordinates. | none |
| `/sethome` | Save your home at your current location. | none |
| `/warp [name]` | List warps without a name, or show coordinates for one warp. | none |
| `/warps` | List available warps. | none |
| `/setwarp <name>` | Save a warp at your current location. | `vanillapoints.setwarp` |
| `/delwarp <name>` | Delete a warp. | `vanillapoints.delwarp` |
| `/vp reload` | Reload config, messages and data. | `vanillapoints.reload` |

Warp names may contain only Latin letters, numbers, underscores and hyphens, up to 32 characters.

## Configuration

`config.yml`:

```yml
settings:
  language: en
  save-immediately: true
  normalize-to-block: true
  copy-format: '{x} {y} {z}'

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
```

Available bundled languages:

- `en` uses `messages.yml`.
- `ru` uses `messages_ru.yml`.

`copy-format` supports `{x}`, `{y}`, `{z}`, `{world}` and `{warp}` where applicable.

`settings.save-immediately` queues writes asynchronously after point changes. When disabled, pending data is flushed during `/vp reload` and plugin shutdown.

`storage.backend` defaults to `yaml` for compatibility. Switching to `sqlite` or `mysql` keeps point data cached in memory for fast commands and persists snapshots asynchronously. If `storage.migrate-yaml-on-first-run` is enabled and the selected SQL backend is empty, existing `data.yml` points are imported once and the YAML file is left in place as a backup.

## Public API

Other plugins can access VanillaPoints through Bukkit services:

```java
RegisteredServiceProvider<VanillaPointsAPI> provider = Bukkit.getServicesManager()
        .getRegistration(VanillaPointsAPI.class);
VanillaPointsAPI api = provider == null ? null : provider.getProvider();
```

The public API package is `dev.vaniley.vanillapoints.api`. Mutation methods and `Location`-returning methods must be called on the server main thread. Mutation methods fire cancellable Bukkit events from `dev.vaniley.vanillapoints.api.event`; `PointInfo` methods return immutable DTOs for safer reads.

## Installation

1. Build or download the plugin `.jar`.
2. Place it in the server `plugins` folder.
3. Restart the server.
4. Edit `plugins/VanillaPoints/config.yml` and `messages*.yml` if needed.
5. Use `/vp reload` after config/message changes.
