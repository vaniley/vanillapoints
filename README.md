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
- Safer data saves using `data.yml.tmp` and `data.yml.bak`.
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
```

Available bundled languages:

- `en` uses `messages.yml`.
- `ru` uses `messages_ru.yml`.

`copy-format` supports `{x}`, `{y}`, `{z}`, `{world}` and `{warp}` where applicable.

## Installation

1. Build or download the plugin `.jar`.
2. Place it in the server `plugins` folder.
3. Restart the server.
4. Edit `plugins/VanillaPoints/config.yml` and `messages*.yml` if needed.
5. Use `/vp reload` after config/message changes.
