# VanillaPoints: TODO по выбранным улучшениям

Документ собран из `improve.html` для поэтапной реализации выбранных идей: `1.1`, `1.2`, `2.3`, `3.2`, `3.3`, `3.4`, `3.5`, `4.1`, `4.2`, `4.3`, `5.1`, `6.1`, `6.3`, `7.1`.

## Рекомендуемый порядок

### Phase 1: быстрые UX-улучшения

- [x] `3.3` Алиасы команд и smart tab-complete
- [x] `3.5` Звуки и частицы при установке точек
- [x] `6.3` Цветные координаты `x/y/z`
- [x] `3.4` Команда `/vp help` с пагинацией
- [x] `5.1` Кулдауны и rate-limit
- [x] `3.2` Подтверждение удаления варпа
- [x] `1.2` Описание и иконка у варпа

### Phase 2: расширение игрового UX

- [x] `1.1` Несколько домов на игрока
- [x] `2.3` Информация о точке: биом, время, погода

### Phase 3: инфраструктура и API

- [x] `4.2` Асинхронная запись данных
- [x] `4.3` Публичный API для других плагинов
- [x] `4.1` SQLite / MySQL backend

### Phase 4: локализация и интеграции

- [ ] `6.1` Больше встроенных языков
- [ ] `7.1` PlaceholderAPI: плейсхолдеры координат

---

## `1.1` Несколько домов на игрока

**Тип:** Feature, UX  
**Сложность:** `3 / 5`, примерно `6-8 ч`

### Цель

Сейчас у игрока есть только один дом через `/sethome`. Нужно добавить именованные дома с лимитом по правам, чтобы survival-сервер мог поддерживать несколько баз, ферм и точек интереса у одного игрока.

### Команды

```yaml
/sethome                # основной дом
/sethome <name>         # именованный дом
/home                   # показать основной дом
/home <name>            # показать координаты дома по имени
/homes                  # список домов игрока
/delhome <name>         # удалить дом
```

### Конфиг

```yaml
homes:
  default-limit: 3
  limits-by-permission:
    vanillapoints.homes.vip: 5
    vanillapoints.homes.premium: 10
```

### TODO

- [x] Спроектировать модель хранения нескольких homes на `UUID` игрока.
- [x] Добавить лимит по умолчанию и лимиты через permissions.
- [x] Реализовать `/sethome <name>` и сохранить совместимость с `/sethome` без аргументов.
- [x] Реализовать `/home <name>` как вывод координат, без телепортации.
- [x] Реализовать `/homes` со списком, количеством `used/limit` и миром.
- [x] Реализовать `/delhome <name>`.
- [x] Добавить tab-complete по именам домов игрока.
- [x] Добавить сообщения об ошибках: лимит превышен, дом не найден, имя занято.

### Acceptance Criteria

- [x] Старое поведение `/sethome` и `/home` не ломается.
- [x] Игрок не может создать больше домов, чем разрешено его правами.
- [x] `/homes` показывает корректный список только домов текущего игрока.
- [x] Удаление дома обновляет хранилище и tab-complete.

---

## `1.2` Описание и иконка у варпа

**Тип:** Feature, UX  
**Сложность:** `2 / 5`, примерно `3 ч`

### Цель

Сейчас варпы имеют только имя и координаты. Нужно добавить метаданные `description` и `icon`, чтобы варпы можно было лучше отображать в списке, GUI или hover-подсказках.

### Формат данных

```yaml
warps:
  shop:
    world: overworld
    x: 102
    y: 64
    z: -218
    yaw: 90.0
    pitch: 0.0
    description: "Главный торговый квартал"
    icon: GOLD_INGOT
    created-by: "admin"
    created-at: 1717200000
```

### TODO

- [ ] Расширить модель варпа полями `description`, `icon`, `createdBy`, `createdAt`.
- [ ] Обновить чтение и запись `data.yml`.
- [ ] Добавить безопасные default-значения для старых варпов.
- [ ] Расширить `/setwarp` аргументом описания или отдельной командой редактирования.
- [ ] Валидировать `icon` как Minecraft material.
- [ ] Выводить описание в `/warp` или `/warps` через hover/chat.

### Acceptance Criteria

- [ ] Старые варпы без metadata продолжают загружаться.
- [ ] Невалидная иконка не ломает загрузку данных.
- [ ] Описание отображается в сообщении или hover-подсказке.

---

## `2.3` Информация о точке: биом, время, погода

**Тип:** Feature, UX  
**Сложность:** `3 / 5`, примерно `5 ч`

### Цель

При просмотре точки через `/home` или `/warp` добавить info-card: координаты, мир, биом, время суток, погода, автор и возраст точки.

### Конфиг

```yaml
info-card:
  show-biome: true
  show-time: true
  show-weather: true
  show-creator: true
  show-age: true
  custom-fields:
    treasury: vault.balance
```

### TODO

- [x] Добавить секцию `info-card` в config.
- [x] Реализовать сбор biome/time/weather для сохраненной локации.
- [x] Добавить creator/created-at для homes и warps, если данных ещё нет.
- [x] Сформировать hover-card или расширенный chat-output.
- [x] Учитывать выключенные поля из конфига.
- [x] Не выполнять тяжёлые операции на main thread, если появятся внешние custom-fields.

### Acceptance Criteria

- [x] `/home` и `/warp <name>` показывают расширенную информацию при включенном `info-card`.
- [x] Отключенные поля не попадают в сообщение.
- [x] Отсутствующие metadata не вызывают ошибки.

---

## `3.2` Подтверждение удаления варпа

**Тип:** UX, Safety  
**Сложность:** `2 / 5`, примерно `3 ч`

### Цель

Команда `/delwarp shop` сейчас удаляет варп мгновенно. Нужно добавить подтверждение с TTL, чтобы снизить риск случайного удаления.

### Конфиг

```yaml
safety:
  confirm-deletions: true
  confirm-ttl: 30s
  bypass-permission: vanillapoints.bypass.confirm
```

### TODO

- [ ] Добавить pending-confirmation store по игроку и имени варпа.
- [ ] На первый `/delwarp <name>` показывать clickable confirm/cancel.
- [ ] На подтверждение удалять варп только если TTL не истек.
- [ ] Добавить bypass permission для админов.
- [ ] Добавить очистку истекших подтверждений.
- [ ] Локализовать сообщения confirm/cancel/expired.

### Acceptance Criteria

- [ ] При включенном `confirm-deletions` первый вызов не удаляет варп.
- [ ] Подтверждение работает только для того игрока, который инициировал удаление.
- [ ] Истекшее подтверждение не удаляет варп.
- [ ] Bypass permission сохраняет быстрое удаление.

---

## `3.3` Алиасы команд и smart tab-complete

**Тип:** UX  
**Сложность:** `1 / 5`, примерно `1 ч`

### Цель

Добавить короткие алиасы команд и улучшить tab-complete, чтобы игрокам не приходилось вводить длинные команды полностью.

### Алиасы

```yaml
commands:
  sethome:
    aliases: [sh, setmyhome]
  home:
    aliases: [h, myhome]
  spawn:
    aliases: [s]
  warp:
    aliases: [w]
```

### TODO

- [ ] Добавить aliases в `plugin.yml`.
- [ ] Проверить конфликты с существующими командами сервера.
- [ ] Добавить tab-complete для `/warp` по именам варпов.
- [ ] Добавить tab-complete для `/home` по именам домов после реализации `1.1`.
- [ ] При наличии статистики использования сортировать варпы по популярности.

### Acceptance Criteria

- [ ] `/h`, `/s`, `/w`, `/sh` вызывают ожидаемые команды.
- [ ] Tab-complete показывает доступные игроку варианты.
- [ ] Приватные или недоступные варпы не раскрываются через completion.

---

## `3.4` Команда `/vp help` с пагинацией

**Тип:** UX, Docs  
**Сложность:** `2 / 5`, примерно `3 ч`

### Цель

Добавить встроенную справку по командам с пагинацией, группами и фильтрацией по правам.

### Конфиг

```yaml
help:
  per-page: 7
  show-hidden-commands: false
  groups:
    player: [home, sethome, warp, warps, spawn]
    admin: [setspawn, setwarp, delwarp, vp]
```

### TODO

- [ ] Добавить `/vp help [page]`.
- [ ] Сгенерировать список команд из registry или вручную описать команды.
- [ ] Добавить пагинацию и clickable next/prev.
- [ ] Фильтровать команды по permissions.
- [ ] Разделить player/admin команды.
- [ ] Добавить локализуемые descriptions.

### Acceptance Criteria

- [ ] `/vp help` показывает первую страницу справки.
- [ ] `/vp help 2` показывает корректную страницу.
- [ ] Игрок не видит admin-команды без прав, если `show-hidden-commands: false`.

---

## `3.5` Звуки и частицы при установке

**Тип:** UX, Feedback  
**Сложность:** `1 / 5`, примерно `1 ч`

### Цель

Добавить легкий feedback при `/sethome` и `/setwarp`: звук и частицы. Всё должно отключаться через конфиг.

### Конфиг

```yaml
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
      particle: END_ROD
```

### TODO

- [ ] Добавить config-секцию `feedback`.
- [ ] Реализовать проигрывание sound per event.
- [ ] Реализовать spawn particles per event.
- [ ] Валидировать имена sound/particle с fallback.
- [ ] Учитывать глобальные флаги `sounds` и `particles`.

### Acceptance Criteria

- [ ] При успешном `/sethome` проигрывается настроенный feedback.
- [ ] При `feedback.sounds: false` звуки не проигрываются.
- [ ] При `feedback.particles: false` частицы не создаются.
- [ ] Невалидная настройка не ломает команду.

---

## `4.1` SQLite / MySQL backend

**Тип:** Arch, Perf  
**Сложность:** `5 / 5`, примерно `16-24 ч`

### Цель

YAML-хранилище подходит для маленьких серверов, но плохо масштабируется. Нужно добавить storage abstraction и реализации SQLite/MySQL, оставив YAML как поддерживаемый backend.

### Конфиг

```yaml
storage:
  backend: sqlite # yaml | sqlite | mysql
  sqlite:
    file: storage.db
  mysql:
    host: localhost
    port: 3306
    database: vanillapoints
    username: root
    password: ${MYSQL_PASS}
    pool-size: 8
    use-ssl: true
```

### TODO

- [x] Выделить `PointStorage` interface.
- [x] Перенести текущую YAML-логику в `YamlPointStorage`.
- [x] Реализовать `SqlitePointStorage`.
- [x] Реализовать `MysqlPointStorage` с connection pool.
- [x] Добавить schema migrations.
- [x] Добавить выбор backend через config.
- [x] Добавить безопасную миграцию YAML -> SQLite/MySQL.
- [x] Обеспечить async I/O и thread-safety.

### Acceptance Criteria

- [x] Backend `yaml` работает как раньше.
- [x] Backend `sqlite` сохраняет homes/warps/spawn между рестартами.
- [x] Backend `mysql` работает с несколькими подключениями через pool.
- [x] Ошибка подключения к SQL не приводит к тихой потере данных.

---

## `4.2` Асинхронная запись данных

**Тип:** Perf, I/O  
**Сложность:** `2 / 5`, примерно `3 ч`

### Цель

Вынести запись данных из main thread, чтобы `saveDataIfNeeded` не вызывал tick freeze на больших файлах или медленных дисках.

### TODO

- [x] Найти все синхронные вызовы сохранения данных.
- [x] Добавить async save через `BukkitScheduler.runTaskAsynchronously`.
- [x] Сделать snapshot данных перед записью, чтобы не читать Bukkit API off-thread.
- [x] Синхронизировать доступ к storage или использовать immutable DTO.
- [x] Вернуть сообщение об ошибке игроку через main thread.
- [x] Добавить shutdown flush при отключении плагина.

### Acceptance Criteria

- [x] Команды не блокируют main thread при записи.
- [x] Ошибки записи логируются и не теряются.
- [x] При выключении сервера pending writes сохраняются.
- [x] Bukkit API не вызывается из async thread.

---

## `4.3` Публичный API для других плагинов

**Тип:** Arch, Public API  
**Сложность:** `3 / 5`, примерно `5-6 ч`

### Цель

Предоставить официальный API, чтобы другие плагины не лезли в internals через reflection. API регистрируется через Bukkit `ServicesManager`.

### API sketch

```java
public interface VanillaPointsAPI {
  Optional<Location> getHome(UUID player);
  boolean setHome(UUID player, Location loc);
  Optional<Location> getWarp(String name);
  boolean setWarp(String name, Location loc);
  boolean deleteWarp(String name);
  Collection<String> listWarps();
  void reload();
}
```

### Events

```java
class WarpSetEvent extends Event {}
class HomeSetEvent extends Event {}
class HomeDeletedEvent extends Event {}
```

### TODO

- [x] Создать public API package.
- [x] Определить stable interfaces и DTO.
- [x] Зарегистрировать API через `ServicesManager`.
- [x] Добавить events для set/delete операций.
- [x] Документировать thread-safety API.
- [x] Не раскрывать mutable internal storage наружу.

### Acceptance Criteria

- [x] Другой плагин может получить `VanillaPointsAPI` через `ServicesManager`.
- [x] API возвращает корректные homes/warps.
- [x] Events вызываются при изменениях через команды и API.

---

## `5.1` Кулдауны и rate-limit

**Тип:** Security, Perf  
**Сложность:** `2 / 5`, примерно `3 ч`

### Цель

Защитить чат, команды и диск от спама. Добавить cooldown per-command и общий rate-limit.

### Конфиг

```yaml
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
```

### TODO

- [ ] Добавить cooldown tracker по `UUID + command`.
- [ ] Добавить общий sliding/fixed window rate-limit по игроку.
- [ ] Добавить bypass permission.
- [ ] Добавить сообщения с оставшимся временем.
- [ ] Не применять cooldown к ошибкам permission, если это нежелательно.
- [ ] Очистить старые entries, чтобы map не росла бесконечно.

### Acceptance Criteria

- [ ] Повтор команды до истечения cooldown блокируется.
- [ ] Игрок с bypass permission не ограничивается.
- [ ] Rate-limit блокирует массовый spam команд.
- [ ] Сообщение показывает понятное оставшееся время.

---

## `6.1` Больше встроенных языков

**Тип:** i18n, Content  
**Сложность:** `4 / 5`, переводы долгие, код примерно `3 ч`

### Цель

Расширить встроенную локализацию: сейчас есть только `en` и `ru`. Добавить больше language files и workflow для поддержки переводов.

### Ресурсы

```text
messages.yml          # en, default
messages_ru.yml       # русский
messages_uk.yml       # українська
messages_es.yml       # español
messages_de.yml       # deutsch
messages_fr.yml       # français
messages_zh.yml       # 简体中文
messages_ja.yml       # 日本語
messages_pt.yml       # português
messages_pl.yml       # polski
```

### Конфиг

```yaml
settings:
  language: en
  per-player-permissions: true
```

### TODO

- [ ] Проверить текущий механизм загрузки `messages*.yml`.
- [ ] Добавить fallback на default language.
- [ ] Добавить новые language files.
- [ ] Добавить проверку полноты ключей между локалями.
- [ ] Поддержать language через permission `vanillapoints.lang.<code>`.
- [ ] Подготовить workflow для обновления переводов.

### Acceptance Criteria

- [ ] Все локали содержат одинаковый набор ключей.
- [ ] При отсутствующем ключе используется fallback.
- [ ] Глобальный `settings.language` меняет язык сообщений.
- [ ] Permission-based language работает, если включен `per-player-permissions`.

---

## `6.3` Цветные координаты: x/y/z разными цветами

**Тип:** UX, Format  
**Сложность:** `1 / 5`, примерно `30 мин`

### Цель

Улучшить читаемость координат в чате: разрешить отдельное форматирование `x`, `y`, `z`, включая legacy colors и MiniMessage gradient после внедрения MiniMessage.

### Пример messages.yml

```yaml
warp-location: "&fWarp &a{warp}&f: &c{x} &a{y} &9{z} &7({world})"
```

### TODO

- [ ] Проверить, что placeholders `{x}`, `{y}`, `{z}` не экранируют цветовые коды вокруг себя.
- [ ] Обновить default messages для цветного вывода координат.
- [ ] Подготовить MiniMessage-вариант после перехода на MiniMessage.
- [ ] Проверить copy-to-clipboard, если координаты используются внутри clickable-компонента.

### Acceptance Criteria

- [ ] Координаты отображаются разными цветами в `/home` и `/warp`.
- [ ] Форматирование не ломает копирование координат.
- [ ] Старые messages продолжают работать.

---

## `7.1` PlaceholderAPI: плейсхолдеры координат

**Тип:** Integration, PAPI  
**Сложность:** `3 / 5`, примерно `5 ч`

### Цель

Добавить PlaceholderAPI provider, чтобы координаты VanillaPoints можно было использовать в scoreboard, tab, chat-плагинах и других интеграциях.

### Плейсхолдеры

```text
%vanillapoints_home_x%
%vanillapoints_home_y%
%vanillapoints_home_z%
%vanillapoints_home_world%
%vanillapoints_home_set%
%vanillapoints_warp_shop_x%
%vanillapoints_warp_count%
%vanillapoints_warp_list%
%vanillapoints_distance_home%
%vanillapoints_bearing_home%
```

### TODO

- [ ] Добавить optional dependency на PlaceholderAPI.
- [ ] Реализовать expansion/provider.
- [ ] Зарегистрировать provider только если PlaceholderAPI установлен.
- [ ] Добавить placeholders для home coordinates.
- [ ] Добавить placeholders для warp coordinates.
- [ ] Добавить count/list placeholders.
- [ ] Добавить distance/bearing placeholders.
- [ ] Обработать missing home/warp через configurable empty value.

### Acceptance Criteria

- [ ] Плагин запускается без PlaceholderAPI.
- [ ] При наличии PlaceholderAPI expansion регистрируется автоматически.
- [ ] Все documented placeholders возвращают корректные значения.
- [ ] Missing values возвращают безопасную строку, а не exception.
