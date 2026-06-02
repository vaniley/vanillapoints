package dev.vaniley.vanillapoints;

import dev.vaniley.vanillapoints.api.PointMetadata;
import dev.vaniley.vanillapoints.api.VanillaPointsAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

public final class VanillaPoints extends JavaPlugin implements CommandExecutor, TabCompleter {
    private static final String PERMISSION_SETSPAWN = "vanillapoints.setspawn";
    private static final String PERMISSION_SETWARP = "vanillapoints.setwarp";
    private static final String PERMISSION_DELWARP = "vanillapoints.delwarp";
    private static final String PERMISSION_RELOAD = "vanillapoints.reload";
    private static final String PERMISSION_BYPASS_COOLDOWN = "vanillapoints.bypass.cooldown";
    private static final String PERMISSION_BYPASS_CONFIRM = "vanillapoints.bypass.confirm";
    private static final String DEFAULT_COPY_FORMAT = "{x} {y} {z}";

    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, Deque<Long>> commandWindows = new HashMap<>();
    private final Map<UUID, PendingWarpDeletion> pendingWarpDeletions = new HashMap<>();

    private MessageService messages;
    private PointStorage storage;
    private AsyncSaveService saveService;
    private PointService points;
    private VanillaPointsApiProvider apiProvider;
    private boolean saveImmediately;
    private boolean normalizeToBlock;
    private String copyFormat;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadPluginState();

        registerCommand("setspawn");
        registerCommand("spawn");
        registerCommand("sethome");
        registerCommand("home");
        registerCommand("setwarp");
        registerCommand("warp");
        registerCommand("warps");
        registerCommand("delwarp");
        registerCommand("vanillapoints");
    }

    @Override
    public void onDisable() {
        unregisterApi();
        flushAndCloseStorage();
    }

    private void registerCommand(String name) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Command is missing from plugin.yml: " + name);
            return;
        }

        command.setExecutor(this);
        command.setTabCompleter(this);
    }

    private void loadPluginState() {
        reloadConfig();

        saveImmediately = getConfig().getBoolean("settings.save-immediately", true);
        normalizeToBlock = getConfig().getBoolean("settings.normalize-to-block", true);
        copyFormat = getConfig().getString("settings.copy-format", DEFAULT_COPY_FORMAT);
        if (copyFormat == null || copyFormat.isBlank()) {
            copyFormat = DEFAULT_COPY_FORMAT;
        }

        messages = new MessageService(this);
        messages.load();

        closeStorageOnly();
        storage = PointStorageFactory.create(this);
        saveService = new AsyncSaveService(this, storage, messages);
        points = new PointService(this, storage, saveService, normalizeToBlock, saveImmediately);
        registerApi();
        pendingWarpDeletions.clear();
    }

    private void registerApi() {
        unregisterApi();
        apiProvider = new VanillaPointsApiProvider(this, points);
        getServer().getServicesManager().register(VanillaPointsAPI.class, apiProvider, this, ServicePriority.Normal);
    }

    private void unregisterApi() {
        if (apiProvider != null) {
            getServer().getServicesManager().unregister(VanillaPointsAPI.class, apiProvider);
            apiProvider = null;
        }
    }

    void reloadFromApi() {
        if (!getServer().isPrimaryThread()) {
            getServer().getScheduler().runTask(this, this::reloadFromApi);
            return;
        }

        if (!flushBeforeReload()) {
            return;
        }
        loadPluginState();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);

        if (commandName.equals("vanillapoints")) {
            return handlePluginCommand(sender, args);
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.component("console-error"));
            return true;
        }

        switch (commandName) {
            case "setspawn":
                return setSpawn(player);
            case "spawn":
                return showSpawn(player);
            case "sethome":
                return setHome(player);
            case "home":
                return showHome(player);
            case "setwarp":
                return setWarp(player, args);
            case "warp":
                return showWarp(player, args);
            case "warps":
                return listWarps(player, "warps");
            case "delwarp":
                return deleteWarp(player, args);
            default:
                return false;
        }
    }

    private boolean handlePluginCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return showHelp(sender, new String[]{"help"});
        }

        if (args[0].equalsIgnoreCase("help")) {
            return showHelp(sender, args);
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            return reloadPlugin(sender);
        }

        sender.sendMessage(messages.component("plugin-usage"));
        return true;
    }

    private boolean reloadPlugin(CommandSender sender) {
        if (!ensurePermission(sender, PERMISSION_RELOAD)) {
            return true;
        }
        if (sender instanceof Player player && !checkCommandLimits(player, "vanillapoints")) {
            return true;
        }

        if (!flushBeforeReload()) {
            sender.sendMessage(messages.component("data-save-error"));
            return true;
        }

        loadPluginState();
        sender.sendMessage(messages.component("reload-complete"));
        return true;
    }

    private boolean showHelp(CommandSender sender, String[] args) {
        if (args.length > 2) {
            sender.sendMessage(messages.component("plugin-usage"));
            return true;
        }

        int page = 1;
        if (args.length == 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException exception) {
                sender.sendMessage(messages.component("help-invalid-page"));
                return true;
            }
        }

        List<HelpEntry> visibleEntries = visibleHelpEntries(sender);
        if (visibleEntries.isEmpty()) {
            sender.sendMessage(messages.component("help-no-commands"));
            return true;
        }

        int perPage = Math.max(1, getConfig().getInt("help.per-page", 7));
        int pages = Math.max(1, (visibleEntries.size() + perPage - 1) / perPage);
        int currentPage = Math.min(Math.max(1, page), pages);
        int fromIndex = (currentPage - 1) * perPage;
        int toIndex = Math.min(fromIndex + perPage, visibleEntries.size());

        Map<String, String> pagePlaceholders = Map.of(
                "page", String.valueOf(currentPage),
                "pages", String.valueOf(pages)
        );
        sender.sendMessage(messages.component("help-header", pagePlaceholders));
        for (HelpEntry entry : visibleEntries.subList(fromIndex, toIndex)) {
            sender.sendMessage(messages.component("help-line", Map.of(
                    "command", entry.command(),
                    "description", messages.text(entry.descriptionKey())
            )));
        }
        sender.sendMessage(helpFooter(currentPage, pages));
        return true;
    }

    private Component helpFooter(int page, int pages) {
        Component footer = messages.component("help-footer", Map.of(
                "page", String.valueOf(page),
                "pages", String.valueOf(pages)
        ));
        if (pages <= 1) {
            return footer;
        }

        Component result = footer;
        if (page > 1) {
            result = result.append(Component.text(" ")).append(helpPageButton("help-prev", page - 1));
        }
        if (page < pages) {
            result = result.append(Component.text(" ")).append(helpPageButton("help-next", page + 1));
        }
        return result;
    }

    private Component helpPageButton(String key, int page) {
        return messages.component(key)
                .clickEvent(ClickEvent.runCommand("/vp help " + page))
                .hoverEvent(HoverEvent.showText(messages.component("help-page-hover", Map.of("page", String.valueOf(page)))));
    }

    private List<HelpEntry> visibleHelpEntries(CommandSender sender) {
        boolean showHidden = getConfig().getBoolean("help.show-hidden-commands", false);
        return helpEntries().stream()
                .filter(entry -> showHidden || canSeeHelpEntry(sender, entry))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private boolean canSeeHelpEntry(CommandSender sender, HelpEntry entry) {
        if (!(sender instanceof Player)) {
            return true;
        }
        return entry.permission() == null || sender.hasPermission(entry.permission());
    }

    private List<HelpEntry> helpEntries() {
        return List.of(
                new HelpEntry("/spawn", "help-desc-spawn", null),
                new HelpEntry("/sethome", "help-desc-sethome", null),
                new HelpEntry("/home", "help-desc-home", null),
                new HelpEntry("/warp [name]", "help-desc-warp", null),
                new HelpEntry("/warps", "help-desc-warps", null),
                new HelpEntry("/setspawn", "help-desc-setspawn", PERMISSION_SETSPAWN),
                new HelpEntry("/setwarp <name> [--icon <material>] [description...]", "help-desc-setwarp", PERMISSION_SETWARP),
                new HelpEntry("/delwarp <name>", "help-desc-delwarp", PERMISSION_DELWARP),
                new HelpEntry("/vp help [page]", "help-desc-vp-help", null),
                new HelpEntry("/vp reload", "help-desc-vp-reload", PERMISSION_RELOAD)
        );
    }

    private boolean setSpawn(Player player) {
        if (!ensurePermission(player, PERMISSION_SETSPAWN)) {
            return true;
        }
        if (!checkCommandLimits(player, "setspawn")) {
            return true;
        }

        PointMutationResult result = points.setSpawn(player, player.getLocation());
        if (!handleMutationResult(player, result, null)) {
            return true;
        }
        player.sendMessage(messages.component("spawn-set"));
        return true;
    }

    private boolean showSpawn(Player player) {
        if (!checkCommandLimits(player, "spawn")) {
            return true;
        }

        StoredPoint spawn = storage.spawn()
                .orElseGet(() -> StoredPoint.fromLocation(player.getWorld().getSpawnLocation(), true));

        sendPointMessage(player, "spawn-location", spawn, Map.of());
        return true;
    }

    private boolean setHome(Player player) {
        if (!checkCommandLimits(player, "sethome")) {
            return true;
        }

        PointMutationResult result = points.setHome(player, player.getUniqueId(), player.getLocation());
        if (!handleMutationResult(player, result, null)) {
            return true;
        }
        player.sendMessage(messages.component("home-set"));
        playFeedback(player, "home-set");
        return true;
    }

    private boolean showHome(Player player) {
        if (!checkCommandLimits(player, "home")) {
            return true;
        }

        Optional<StoredPoint> home = storage.home(player.getUniqueId());
        if (home.isEmpty()) {
            player.sendMessage(messages.component("home-not-set"));
            return true;
        }

        sendPointMessage(player, "home-location", home.get(), Map.of());
        return true;
    }

    private boolean setWarp(Player player, String[] args) {
        if (!ensurePermission(player, PERMISSION_SETWARP)) {
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(messages.component("setwarp-usage"));
            return true;
        }
        if (!isValidWarpName(args[0])) {
            player.sendMessage(messages.component("invalid-warp-name"));
            return true;
        }

        WarpInput input = parseWarpInput(player, args);
        if (input == null) {
            return true;
        }
        if (!checkCommandLimits(player, "setwarp")) {
            return true;
        }

        String warpName = PointStorage.normalizeWarpName(args[0]);
        PointMutationResult result = points.setWarp(
                player,
                warpName,
                player.getLocation(),
                new PointMetadata(input.description(), input.icon(), player.getName(), Instant.now().getEpochSecond())
        );
        if (!handleMutationResult(player, result, warpName)) {
            return true;
        }
        player.sendMessage(messages.component("warp-set", Map.of("warp", warpName)));
        playFeedback(player, "warp-set");
        return true;
    }

    private WarpInput parseWarpInput(Player player, String[] args) {
        String icon = "";
        List<String> descriptionParts = new ArrayList<>();
        for (int index = 1; index < args.length; index++) {
            String argument = args[index];
            if (argument.equalsIgnoreCase("--icon")) {
                if (index + 1 >= args.length) {
                    player.sendMessage(messages.component("setwarp-usage"));
                    return null;
                }

                String rawIcon = args[++index];
                Optional<String> validatedIcon = validateWarpIcon(rawIcon);
                if (validatedIcon.isEmpty()) {
                    player.sendMessage(messages.component("invalid-warp-icon", Map.of("icon", rawIcon)));
                    return null;
                }
                icon = validatedIcon.get();
            } else {
                descriptionParts.add(argument);
            }
        }

        return new WarpInput(String.join(" ", descriptionParts).trim(), icon);
    }

    private Optional<String> validateWarpIcon(String icon) {
        Material material = Material.matchMaterial(icon);
        if (material == null || material.isLegacy() || material.isAir() || !material.isItem()) {
            return Optional.empty();
        }
        return Optional.of(material.name());
    }

    private boolean showWarp(Player player, String[] args) {
        if (args.length == 0) {
            if (!checkCommandLimits(player, "warp")) {
                return true;
            }
            return sendWarpsList(player);
        }

        if (args.length != 1) {
            player.sendMessage(messages.component("warp-usage"));
            return true;
        }
        if (!isValidWarpName(args[0])) {
            player.sendMessage(messages.component("invalid-warp-name"));
            return true;
        }
        if (!checkCommandLimits(player, "warp")) {
            return true;
        }

        String warpName = PointStorage.normalizeWarpName(args[0]);
        Optional<StoredPoint> warp = storage.warp(warpName);
        if (warp.isEmpty()) {
            player.sendMessage(messages.component("warp-not-set", Map.of("warp", warpName)));
            return true;
        }

        StoredPoint point = warp.get();
        sendPointMessage(player, "warp-location", point, Map.of("warp", warpName));
        if (!point.description().isBlank()) {
            player.sendMessage(messages.component("warp-description", Map.of("description", point.description())));
        }
        return true;
    }

    private boolean deleteWarp(Player player, String[] args) {
        if (!ensurePermission(player, PERMISSION_DELWARP)) {
            return true;
        }

        removeExpiredPendingDeletions();
        if (args.length != 1 && args.length != 2) {
            player.sendMessage(messages.component("delwarp-usage"));
            return true;
        }
        if (!isValidWarpName(args[0])) {
            player.sendMessage(messages.component("invalid-warp-name"));
            return true;
        }

        String warpName = PointStorage.normalizeWarpName(args[0]);
        if (args.length == 2) {
            if (args[1].equalsIgnoreCase("confirm")) {
                return confirmWarpDeletion(player, warpName);
            }
            if (args[1].equalsIgnoreCase("cancel")) {
                return cancelWarpDeletion(player, warpName);
            }

            player.sendMessage(messages.component("delwarp-usage"));
            return true;
        }

        if (!checkCommandLimits(player, "delwarp")) {
            return true;
        }
        if (!getConfig().getBoolean("safety.confirm-deletions", true)
                || hasConfiguredPermission(player, "safety.bypass-permission", PERMISSION_BYPASS_CONFIRM)) {
            return deleteWarpNow(player, warpName);
        }
        if (storage.warp(warpName).isEmpty()) {
            player.sendMessage(messages.component("warp-not-set", Map.of("warp", warpName)));
            return true;
        }

        long ttlMillis = Math.max(1000L, configDurationMillis("safety.confirm-ttl", "30s"));
        pendingWarpDeletions.put(player.getUniqueId(), new PendingWarpDeletion(warpName, System.currentTimeMillis() + ttlMillis));
        sendWarpDeletionPrompt(player, warpName, ttlMillis);
        return true;
    }

    private boolean confirmWarpDeletion(Player player, String warpName) {
        PendingWarpDeletion pendingDeletion = pendingWarpDeletions.get(player.getUniqueId());
        if (pendingDeletion == null || !pendingDeletion.warpName().equals(warpName)) {
            player.sendMessage(messages.component("warp-delete-confirm-missing", Map.of("warp", warpName)));
            return true;
        }
        if (pendingDeletion.expiresAtMillis() <= System.currentTimeMillis()) {
            pendingWarpDeletions.remove(player.getUniqueId());
            player.sendMessage(messages.component("warp-delete-confirm-expired", Map.of("warp", warpName)));
            return true;
        }

        pendingWarpDeletions.remove(player.getUniqueId());
        return deleteWarpNow(player, warpName);
    }

    private boolean cancelWarpDeletion(Player player, String warpName) {
        PendingWarpDeletion pendingDeletion = pendingWarpDeletions.get(player.getUniqueId());
        if (pendingDeletion == null || !pendingDeletion.warpName().equals(warpName)) {
            player.sendMessage(messages.component("warp-delete-confirm-missing", Map.of("warp", warpName)));
            return true;
        }

        pendingWarpDeletions.remove(player.getUniqueId());
        player.sendMessage(messages.component("warp-delete-cancelled", Map.of("warp", warpName)));
        return true;
    }

    private boolean deleteWarpNow(Player player, String warpName) {
        PointMutationResult result = points.deleteWarp(player, warpName);
        if (result == PointMutationResult.NOT_FOUND) {
            player.sendMessage(messages.component("warp-not-set", Map.of("warp", warpName)));
            return true;
        }
        if (!handleMutationResult(player, result, warpName)) {
            return true;
        }

        player.sendMessage(messages.component("warp-deleted", Map.of("warp", warpName)));
        return true;
    }

    private void sendWarpDeletionPrompt(Player player, String warpName, long ttlMillis) {
        Component prompt = messages.component("warp-delete-confirm-prompt", Map.of(
                "warp", warpName,
                "time", formatDuration(ttlMillis)
        ));
        Component confirm = messages.component("warp-delete-confirm-button")
                .clickEvent(ClickEvent.runCommand("/delwarp " + warpName + " confirm"))
                .hoverEvent(HoverEvent.showText(messages.component("warp-delete-confirm-hover")));
        Component cancel = messages.component("warp-delete-cancel-button")
                .clickEvent(ClickEvent.runCommand("/delwarp " + warpName + " cancel"))
                .hoverEvent(HoverEvent.showText(messages.component("warp-delete-cancel-hover")));

        player.sendMessage(prompt.append(Component.text(" ")).append(confirm).append(Component.text(" ")).append(cancel));
    }

    private boolean listWarps(Player player, String commandName) {
        if (!checkCommandLimits(player, commandName)) {
            return true;
        }
        return sendWarpsList(player);
    }

    private boolean sendWarpsList(CommandSender sender) {
        Set<String> warpNames = storage.warpNames();
        if (warpNames.isEmpty()) {
            sender.sendMessage(messages.component("no-warps"));
            return true;
        }

        sender.sendMessage(messages.component("available-warps", Map.of("warps", String.join(", ", warpNames))));
        return true;
    }

    private void sendPointMessage(Player player, String messageKey, StoredPoint point, Map<String, String> extraPlaceholders) {
        Map<String, String> placeholders = new HashMap<>(extraPlaceholders);
        placeholders.put("x", String.valueOf(point.blockX()));
        placeholders.put("y", String.valueOf(point.blockY()));
        placeholders.put("z", String.valueOf(point.blockZ()));
        placeholders.put("world", point.worldName());

        Component message = LegacyComponentSerializer.legacyAmpersand()
                .deserialize(messages.text(messageKey, placeholders))
                .clickEvent(ClickEvent.copyToClipboard(applyPlaceholders(copyFormat, placeholders)))
                .hoverEvent(HoverEvent.showText(messages.component("coordinates-hover-text")));

        player.sendMessage(message);
    }

    private void playFeedback(Player player, String eventKey) {
        String eventPath = "feedback.events." + eventKey;
        if (getConfig().getBoolean("feedback.sounds", true)) {
            playConfiguredSound(player, eventPath);
        }
        if (getConfig().getBoolean("feedback.particles", true)) {
            spawnConfiguredParticle(player, eventPath);
        }
    }

    private void playConfiguredSound(Player player, String eventPath) {
        String soundName = getConfig().getString(eventPath + ".sound", "");
        if (soundName == null || soundName.isBlank()) {
            return;
        }

        NamespacedKey key = soundKey(soundName);
        Sound sound = key == null ? null : Registry.SOUNDS.get(key);
        if (sound == null) {
            getLogger().warning("Invalid feedback sound '" + soundName + "' at " + eventPath + ".sound");
            return;
        }

        float volume = (float) Math.max(0.0D, getConfig().getDouble(eventPath + ".volume", 1.0D));
        float pitch = (float) Math.max(0.01D, getConfig().getDouble(eventPath + ".pitch", 1.0D));
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    private NamespacedKey soundKey(String soundName) {
        String normalized = soundName.trim().toLowerCase(Locale.ROOT).replace('_', '.');
        try {
            if (normalized.contains(":")) {
                return NamespacedKey.fromString(normalized);
            }
            return NamespacedKey.minecraft(normalized);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void spawnConfiguredParticle(Player player, String eventPath) {
        String particleName = getConfig().getString(eventPath + ".particle", "");
        if (particleName == null || particleName.isBlank()) {
            return;
        }

        try {
            Particle particle = Particle.valueOf(particleName.toUpperCase(Locale.ROOT));
            if (particle.getDataType() != Void.class) {
                getLogger().warning("Feedback particle '" + particleName + "' at " + eventPath
                        + ".particle requires data and was skipped.");
                return;
            }

            int count = Math.max(0, getConfig().getInt(eventPath + ".count", 8));
            if (count == 0) {
                return;
            }

            Location location = player.getLocation().add(0.0D, 1.0D, 0.0D);
            player.spawnParticle(particle, location, count, 0.35D, 0.45D, 0.35D);
        } catch (IllegalArgumentException exception) {
            getLogger().warning("Invalid feedback particle '" + particleName + "' at " + eventPath + ".particle");
        }
    }

    private boolean checkCommandLimits(Player player, String commandName) {
        if (hasConfiguredPermission(player, "cooldowns.bypass-permission", PERMISSION_BYPASS_COOLDOWN)) {
            return true;
        }

        long now = System.currentTimeMillis();
        if (!checkRateLimit(player, now)) {
            return false;
        }

        long cooldownMillis = cooldownMillis(commandName);
        if (cooldownMillis > 0L) {
            Long lastUsedAt = cooldowns
                    .getOrDefault(player.getUniqueId(), Map.of())
                    .get(commandName);
            if (lastUsedAt != null) {
                long remainingMillis = cooldownMillis - (now - lastUsedAt);
                if (remainingMillis > 0L) {
                    player.sendMessage(messages.component("cooldown-active", Map.of("time", formatDuration(remainingMillis))));
                    return false;
                }
            }
        }

        recordCommandUsage(player.getUniqueId(), commandName, now, cooldownMillis);
        return true;
    }

    private boolean checkRateLimit(Player player, long now) {
        long windowMillis = configDurationMillis("rate-limit.window", "60s");
        int maxCommands = getConfig().getInt("rate-limit.max-commands", 30);
        if (windowMillis <= 0L || maxCommands <= 0) {
            return true;
        }

        Deque<Long> window = commandWindows.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayDeque<>());
        pruneCommandWindow(window, now, windowMillis);
        if (window.size() >= maxCommands) {
            Long oldestCommandAt = window.peekFirst();
            long remainingMillis = oldestCommandAt == null ? windowMillis : oldestCommandAt + windowMillis - now;
            player.sendMessage(messages.component("rate-limit-active", Map.of("time", formatDuration(Math.max(1L, remainingMillis)))));
            return false;
        }
        return true;
    }

    private void pruneCommandWindow(Deque<Long> window, long now, long windowMillis) {
        while (!window.isEmpty() && now - window.peekFirst() >= windowMillis) {
            window.removeFirst();
        }
    }

    private void recordCommandUsage(UUID playerId, String commandName, long now, long cooldownMillis) {
        long windowMillis = configDurationMillis("rate-limit.window", "60s");
        int maxCommands = getConfig().getInt("rate-limit.max-commands", 30);
        if (windowMillis > 0L && maxCommands > 0) {
            commandWindows.computeIfAbsent(playerId, ignored -> new ArrayDeque<>()).addLast(now);
        }
        if (cooldownMillis > 0L) {
            cooldowns.computeIfAbsent(playerId, ignored -> new HashMap<>()).put(commandName, now);
        }
        pruneCommandLimits(playerId, now);
    }

    private void pruneCommandLimits(UUID playerId, long now) {
        long windowMillis = configDurationMillis("rate-limit.window", "60s");
        Deque<Long> window = commandWindows.get(playerId);
        if (window != null) {
            pruneCommandWindow(window, now, windowMillis);
            if (window.isEmpty()) {
                commandWindows.remove(playerId);
            }
        }

        Map<String, Long> playerCooldowns = cooldowns.get(playerId);
        if (playerCooldowns != null) {
            playerCooldowns.entrySet().removeIf(entry -> now - entry.getValue() >= cooldownMillis(entry.getKey()));
            if (playerCooldowns.isEmpty()) {
                cooldowns.remove(playerId);
            }
        }
    }

    private long cooldownMillis(String commandName) {
        long defaultMillis = configDurationMillis("cooldowns.default", "2s");
        return configDurationMillis("cooldowns.per-command." + commandName, defaultMillis);
    }

    private long configDurationMillis(String path, String defaultValue) {
        return configDurationMillis(path, parseDurationMillis(defaultValue, 0L));
    }

    private long configDurationMillis(String path, long defaultMillis) {
        Object value = getConfig().get(path);
        if (value == null) {
            return defaultMillis;
        }
        return parseDurationMillis(value, defaultMillis);
    }

    private long parseDurationMillis(Object value, long defaultMillis) {
        if (value instanceof Number number) {
            return Math.max(0L, Math.round(number.doubleValue() * 1000.0D));
        }

        String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if (text.isBlank()) {
            return defaultMillis;
        }

        long multiplier = 1000L;
        String numberText = text;
        if (text.endsWith("ms")) {
            multiplier = 1L;
            numberText = text.substring(0, text.length() - 2);
        } else if (text.endsWith("s")) {
            multiplier = 1000L;
            numberText = text.substring(0, text.length() - 1);
        } else if (text.endsWith("m")) {
            multiplier = 60_000L;
            numberText = text.substring(0, text.length() - 1);
        } else if (text.endsWith("h")) {
            multiplier = 3_600_000L;
            numberText = text.substring(0, text.length() - 1);
        }

        try {
            double amount = Double.parseDouble(numberText.trim());
            return Math.max(0L, (long) Math.ceil(amount * multiplier));
        } catch (NumberFormatException exception) {
            getLogger().warning("Invalid duration value '" + value + "', using default " + formatDuration(defaultMillis) + ".");
            return defaultMillis;
        }
    }

    private String formatDuration(long millis) {
        if (millis < 1000L) {
            return "1s";
        }

        long seconds = (millis + 999L) / 1000L;
        if (seconds < 60L) {
            return seconds + "s";
        }

        long minutes = seconds / 60L;
        long remainingSeconds = seconds % 60L;
        if (remainingSeconds == 0L) {
            return minutes + "m";
        }
        return minutes + "m " + remainingSeconds + "s";
    }

    private void removeExpiredPendingDeletions() {
        long now = System.currentTimeMillis();
        pendingWarpDeletions.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() <= now);
    }

    private boolean isValidWarpName(String name) {
        return PointStorage.isValidWarpName(name);
    }

    private boolean ensurePermission(CommandSender sender, String permission) {
        if (!(sender instanceof Player) || sender.hasPermission(permission)) {
            return true;
        }

        sender.sendMessage(messages.component("no-permission"));
        return false;
    }

    private boolean hasConfiguredPermission(Player player, String path, String defaultPermission) {
        String permission = getConfig().getString(path, defaultPermission);
        return permission != null && !permission.isBlank() && player.hasPermission(permission);
    }

    private String applyPlaceholders(String text, Map<String, String> placeholders) {
        String result = text;
        for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
            result = result.replace("{" + placeholder.getKey() + "}", placeholder.getValue());
        }
        return result;
    }

    private boolean handleMutationResult(Player player, PointMutationResult result, String pointName) {
        if (result == PointMutationResult.SUCCESS) {
            return true;
        }
        if (result == PointMutationResult.CANCELLED) {
            Map<String, String> placeholders = pointName == null ? Map.of() : Map.of("point", pointName);
            player.sendMessage(messages.component("point-change-cancelled", placeholders));
            return false;
        }
        if (result == PointMutationResult.INVALID) {
            player.sendMessage(messages.component("data-save-error"));
            return false;
        }
        return false;
    }

    private boolean flushBeforeReload() {
        return saveService == null || (saveService.flush(10_000L) && saveService.saveNow());
    }

    private void flushAndCloseStorage() {
        if (saveService != null) {
            if (saveService.flush(10_000L)) {
                saveService.saveNow();
            }
            saveService = null;
        } else if (storage != null) {
            try {
                storage.save(storage.snapshot());
            } catch (StorageException exception) {
                getLogger().log(Level.SEVERE, "Could not save VanillaPoints data on shutdown.", exception);
            }
        }
        closeStorageOnly();
    }

    private void closeStorageOnly() {
        if (storage == null) {
            return;
        }
        try {
            storage.close();
        } catch (StorageException exception) {
            getLogger().log(Level.SEVERE, "Could not close VanillaPoints storage.", exception);
        } finally {
            storage = null;
            points = null;
            saveService = null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);

        if (commandName.equals("vanillapoints")) {
            return completePluginCommand(sender, args);
        }
        if (commandName.equals("setwarp")) {
            return completeSetWarp(args);
        }
        if (commandName.equals("warp")) {
            return completeWarpNames(args);
        }
        if (commandName.equals("delwarp")) {
            return completeDeleteWarp(sender, args);
        }

        return List.of();
    }

    private List<String> completePluginCommand(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("help");
            if (sender.hasPermission(PERMISSION_RELOAD)) {
                completions.add("reload");
            }
            return filterByPrefix(completions, args[0]);
        }
        return List.of();
    }

    private List<String> completeSetWarp(String[] args) {
        if (args.length == 2 && "--icon".startsWith(args[1].toLowerCase(Locale.ROOT))) {
            return List.of("--icon");
        }
        return List.of();
    }

    private List<String> completeWarpNames(String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        return filterByPrefix(new ArrayList<>(storage.warpNames()), args[0]);
    }

    private List<String> completeDeleteWarp(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_DELWARP)) {
            return List.of();
        }

        if (args.length == 1) {
            return completeWarpNames(args);
        }
        if (args.length == 2 && sender instanceof Player player && isValidWarpName(args[0])) {
            PendingWarpDeletion pendingDeletion = pendingWarpDeletions.get(player.getUniqueId());
            if (pendingDeletion != null && pendingDeletion.warpName().equals(PointStorage.normalizeWarpName(args[0]))
                    && pendingDeletion.expiresAtMillis() > System.currentTimeMillis()) {
                return filterByPrefix(List.of("confirm", "cancel"), args[1]);
            }
        }
        return List.of();
    }

    private List<String> filterByPrefix(List<String> values, String prefix) {
        String normalizedPrefix = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private record PendingWarpDeletion(String warpName, long expiresAtMillis) {
    }

    private record HelpEntry(String command, String descriptionKey, String permission) {
    }

    private record WarpInput(String description, String icon) {
    }
}
