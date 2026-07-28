package dev.vaniley.vanillapoints;

import dev.vaniley.vanillapoints.api.PointMetadata;
import dev.vaniley.vanillapoints.api.VanillaPointsAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class VanillaPoints extends JavaPlugin implements CommandExecutor, TabCompleter {
    private static final String PERMISSION_SETSPAWN = "vanillapoints.setspawn";
    private static final String PERMISSION_SETWARP = "vanillapoints.setwarp";
    private static final String PERMISSION_DELWARP = "vanillapoints.delwarp";
    private static final String PERMISSION_RELOAD = "vanillapoints.reload";
    private static final String PERMISSION_BYPASS_CONFIRM = "vanillapoints.bypass.confirm";
    private static final String PERMISSION_ADMIN = "vanillapoints.admin";
    private static final String PERMISSION_WARP_PRIVATE = "vanillapoints.warp.private";
    private static final String DEFAULT_COPY_FORMAT = "{x} {y} {z}";

    private final Map<UUID, PendingWarpDeletion> pendingWarpDeletions = new HashMap<>();

    private MessageService messages;
    private PointStorage storage;
    private AsyncSaveService saveService;
    private PointService points;
    private VanillaPointsApiProvider apiProvider;
    private PlaceholderApiIntegration placeholderApiIntegration;
    private UpdateChecker updateChecker;
    private CommandLimitService commandLimits;
    private FeedbackService feedback;
    private boolean saveImmediately;
    private boolean normalizeToBlock;
    private boolean spawnPerWorld;
    private String copyFormat;

    MessageService messages() {
        return messages;
    }

    void clearPlayerState(UUID playerId) {
        if (commandLimits != null) {
            commandLimits.clear(playerId);
        }
        pendingWarpDeletions.remove(playerId);
    }

    void notifyUpdate(Player player) {
        if (updateChecker != null) {
            updateChecker.notifyIfAdmin(player);
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            loadPluginState();
        } catch (StorageException exception) {
            getLogger().log(Level.SEVERE, "VanillaPoints could not initialize its storage and will be disabled. "
                    + "Check storage.* in config.yml and the database connection.", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        registerCommand("setspawn");
        registerCommand("spawn");
        registerCommand("sethome");
        registerCommand("home");
        registerCommand("homes");
        registerCommand("delhome");
        registerCommand("setwarp");
        registerCommand("warp");
        registerCommand("warps");
        registerCommand("delwarp");
        registerCommand("vanillapoints");

        getServer().getPluginManager().registerEvents(new PlayerStateListener(this), this);

        updateChecker = new UpdateChecker(this);
        updateChecker.checkAsync();

        initMetrics();
    }

    private void initMetrics() {
        int serviceId = getConfig().getInt("metrics.service-id", 0);
        if (!getConfig().getBoolean("metrics.enabled", false) || serviceId <= 0) {
            return;
        }
        try {
            new org.bstats.bukkit.Metrics(this, serviceId);
        } catch (Throwable throwable) {
            getLogger().log(Level.FINE, "Could not start bStats metrics: " + throwable.getMessage());
        }
    }

    @Override
    public void onDisable() {
        unregisterPlaceholderApi();
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
        unregisterPlaceholderApi();
        reloadConfig();

        saveImmediately = getConfig().getBoolean("settings.save-immediately", true);
        normalizeToBlock = getConfig().getBoolean("settings.normalize-to-block", true);
        spawnPerWorld = getConfig().getBoolean("spawn.per-world", false);
        copyFormat = getConfig().getString("settings.copy-format", DEFAULT_COPY_FORMAT);
        if (copyFormat == null || copyFormat.isBlank()) {
            copyFormat = DEFAULT_COPY_FORMAT;
        }

        messages = new MessageService(this);
        messages.load();
        commandLimits = new CommandLimitService(this, messages);
        feedback = new FeedbackService(this);

        PointStorage replacementStorage = PointStorageFactory.create(this);
        closeStorageOnly();
        storage = replacementStorage;
        saveService = new AsyncSaveService(this, storage, messages);
        points = new PointService(this, storage, saveService, normalizeToBlock, saveImmediately);
        registerApi();
        registerPlaceholderApi();
        pendingWarpDeletions.clear();
    }

    private void registerPlaceholderApi() {
        if (!getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }

        placeholderApiIntegration = new PlaceholderApiIntegration(this, points);
        if (!placeholderApiIntegration.register()) {
            placeholderApiIntegration = null;
            getLogger().warning("Could not register PlaceholderAPI expansion.");
        }
    }

    private void unregisterPlaceholderApi() {
        if (placeholderApiIntegration == null) {
            return;
        }

        placeholderApiIntegration.unregister();
        placeholderApiIntegration = null;
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
            sender.sendMessage(messages.component(sender, "console-error"));
            return true;
        }

        switch (commandName) {
            case "setspawn":
                return setSpawn(player);
            case "spawn":
                return showSpawn(player);
            case "sethome":
                return setHome(player, args);
            case "home":
                return showHome(player, args);
            case "homes":
                return listHomes(player);
            case "delhome":
                return deleteHome(player, args);
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

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "homes":
                return adminListHomes(sender, args);
            case "delhome":
                return adminDeleteHome(sender, args);
            case "purge":
                return adminPurge(sender, args);
            case "import":
                return adminImport(sender, args);
            default:
                sender.sendMessage(messages.component(sender, "plugin-usage"));
                return true;
        }
    }

    private boolean adminListHomes(CommandSender sender, String[] args) {
        if (!ensurePermission(sender, PERMISSION_ADMIN)) {
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage(messages.component(sender, "admin-homes-usage"));
            return true;
        }

        OfflinePlayer target = resolveOfflinePlayer(args[1]);
        Map<String, StoredPoint> homes = storage.homes(target.getUniqueId());
        if (homes.isEmpty()) {
            sender.sendMessage(messages.component(sender, "admin-homes-empty", Map.of("player", args[1])));
            return true;
        }

        sender.sendMessage(messages.component(sender, "admin-homes-header", Map.of(
                "player", args[1],
                "count", String.valueOf(homes.size())
        )));
        homes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sender.sendMessage(messages.component(sender, "homes-line", Map.of(
                        "home", entry.getKey(),
                        "world", entry.getValue().worldName(),
                        "x", String.valueOf(entry.getValue().blockX()),
                        "y", String.valueOf(entry.getValue().blockY()),
                        "z", String.valueOf(entry.getValue().blockZ())
                ))));
        return true;
    }

    private boolean adminDeleteHome(CommandSender sender, String[] args) {
        if (!ensurePermission(sender, PERMISSION_ADMIN)) {
            return true;
        }
        if (args.length != 3) {
            sender.sendMessage(messages.component(sender, "admin-delhome-usage"));
            return true;
        }
        String homeName = PointStorage.normalizeHomeName(args[2]);
        if (!isValidHomeName(homeName)) {
            sender.sendMessage(messages.component(sender, "invalid-home-name"));
            return true;
        }

        OfflinePlayer target = resolveOfflinePlayer(args[1]);
        PointMutationResult result = points.deleteHome(sender, target.getUniqueId(), homeName);
        if (result == PointMutationResult.NOT_FOUND) {
            sender.sendMessage(messages.component(sender, "admin-homes-empty", Map.of("player", args[1])));
            return true;
        }
        if (result != PointMutationResult.SUCCESS) {
            sender.sendMessage(messages.component(sender, "data-save-error"));
            return true;
        }

        sender.sendMessage(messages.component(sender, "admin-delhome-done", Map.of(
                "player", args[1],
                "home", homeName
        )));
        return true;
    }

    private boolean adminPurge(CommandSender sender, String[] args) {
        if (!ensurePermission(sender, PERMISSION_ADMIN)) {
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage(messages.component(sender, "admin-purge-usage"));
            return true;
        }

        OfflinePlayer target = resolveOfflinePlayer(args[1]);
        Map<String, StoredPoint> homes = storage.homes(target.getUniqueId());
        if (homes.isEmpty()) {
            sender.sendMessage(messages.component(sender, "admin-homes-empty", Map.of("player", args[1])));
            return true;
        }

        int removed = 0;
        for (String homeName : homes.keySet()) {
            if (points.deleteHome(sender, target.getUniqueId(), homeName) == PointMutationResult.SUCCESS) {
                removed++;
            }
        }
        sender.sendMessage(messages.component(sender, "admin-purge-done", Map.of(
                "player", args[1],
                "count", String.valueOf(removed)
        )));
        return true;
    }

    private boolean adminImport(CommandSender sender, String[] args) {
        if (!ensurePermission(sender, PERMISSION_ADMIN)) {
            return true;
        }
        if (args.length != 2 || !args[1].equalsIgnoreCase("essentials")) {
            sender.sendMessage(messages.component(sender, "admin-import-usage"));
            return true;
        }

        EssentialsImporter.Result result = new EssentialsImporter(this, storage).importData();
        if (result.homes() > 0 || result.warps() > 0) {
            persistNow();
        }
        sender.sendMessage(messages.component(sender, "admin-import-done", Map.of(
                "homes", String.valueOf(result.homes()),
                "warps", String.valueOf(result.warps())
        )));
        return true;
    }

    private void persistNow() {
        if (saveService != null && saveService.flush(10_000L)) {
            saveService.saveNow();
        }
    }

    private OfflinePlayer resolveOfflinePlayer(String name) {
        Player online = getServer().getPlayerExact(name);
        return online != null ? online : getServer().getOfflinePlayer(name);
    }

    private boolean reloadPlugin(CommandSender sender) {
        if (!ensurePermission(sender, PERMISSION_RELOAD)) {
            return true;
        }
        if (sender instanceof Player player && !checkCommandLimits(player, "vanillapoints")) {
            return true;
        }

        if (!flushBeforeReload()) {
            sender.sendMessage(messages.component(sender, "data-save-error"));
            return true;
        }

        loadPluginState();
        sender.sendMessage(messages.component(sender, "reload-complete"));
        return true;
    }

    private boolean showHelp(CommandSender sender, String[] args) {
        if (args.length > 2) {
            sender.sendMessage(messages.component(sender, "plugin-usage"));
            return true;
        }

        int page = 1;
        if (args.length == 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException exception) {
                sender.sendMessage(messages.component(sender, "help-invalid-page"));
                return true;
            }
        }

        List<HelpEntry> visibleEntries = visibleHelpEntries(sender);
        if (visibleEntries.isEmpty()) {
            sender.sendMessage(messages.component(sender, "help-no-commands"));
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
        sender.sendMessage(messages.component(sender, "help-header", pagePlaceholders));
        for (HelpEntry entry : visibleEntries.subList(fromIndex, toIndex)) {
            sender.sendMessage(messages.component(sender, "help-line", Map.of(
                    "command", entry.command(),
                    "description", messages.text(sender, entry.descriptionKey())
            )));
        }
        sender.sendMessage(helpFooter(sender, currentPage, pages));
        return true;
    }

    private Component helpFooter(CommandSender sender, int page, int pages) {
        Component footer = messages.component(sender, "help-footer", Map.of(
                "page", String.valueOf(page),
                "pages", String.valueOf(pages)
        ));
        if (pages <= 1) {
            return footer;
        }

        Component result = footer;
        if (page > 1) {
            result = result.append(Component.text(" ")).append(helpPageButton(sender, "help-prev", page - 1));
        }
        if (page < pages) {
            result = result.append(Component.text(" ")).append(helpPageButton(sender, "help-next", page + 1));
        }
        return result;
    }

    private Component helpPageButton(CommandSender sender, String key, int page) {
        return messages.component(sender, key)
                .clickEvent(ClickEvent.runCommand("/vp help " + page))
                .hoverEvent(HoverEvent.showText(messages.component(sender, "help-page-hover", Map.of("page", String.valueOf(page)))));
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
                new HelpEntry("/sethome [name]", "help-desc-sethome", null),
                new HelpEntry("/home [name]", "help-desc-home", null),
                new HelpEntry("/homes", "help-desc-homes", null),
                new HelpEntry("/delhome <name>", "help-desc-delhome", null),
                new HelpEntry("/warp [name]", "help-desc-warp", null),
                new HelpEntry("/warps", "help-desc-warps", null),
                new HelpEntry("/setspawn", "help-desc-setspawn", PERMISSION_SETSPAWN),
                new HelpEntry("/setwarp <name> [--icon <material>] [--category <name>] [--private] [description...]", "help-desc-setwarp", PERMISSION_SETWARP),
                new HelpEntry("/delwarp <name>", "help-desc-delwarp", PERMISSION_DELWARP),
                new HelpEntry("/vp help [page]", "help-desc-vp-help", null),
                new HelpEntry("/vp reload", "help-desc-vp-reload", PERMISSION_RELOAD),
                new HelpEntry("/vp homes <player>", "help-desc-vp-homes", PERMISSION_ADMIN),
                new HelpEntry("/vp delhome <player> <name>", "help-desc-vp-delhome", PERMISSION_ADMIN),
                new HelpEntry("/vp purge <player>", "help-desc-vp-purge", PERMISSION_ADMIN),
                new HelpEntry("/vp import essentials", "help-desc-vp-import", PERMISSION_ADMIN)
        );
    }

    private boolean setSpawn(Player player) {
        if (!ensurePermission(player, PERMISSION_SETSPAWN)) {
            return true;
        }
        if (!checkCommandLimits(player, "setspawn")) {
            return true;
        }

        String spawnWorld = spawnPerWorld ? player.getWorld().getName() : null;
        PointMutationResult result = points.setSpawn(player, spawnWorld, player.getLocation());
        if (!handleMutationResult(player, result, null)) {
            return true;
        }
        player.sendMessage(messages.component(player, "spawn-set"));
        playFeedback(player, "spawn-set");
        return true;
    }

    private boolean showSpawn(Player player) {
        if (!checkCommandLimits(player, "spawn")) {
            return true;
        }

        Optional<StoredPoint> stored = spawnPerWorld
                ? storage.spawn(player.getWorld().getName()).or(storage::spawn)
                : storage.spawn();
        StoredPoint spawn = stored.orElseGet(() -> StoredPoint.fromLocation(player.getWorld().getSpawnLocation(), true));

        sendPointMessage(player, "spawn-location", spawn, Map.of());
        return true;
    }

    private boolean setHome(Player player, String[] args) {
        if (args.length > 1) {
            player.sendMessage(messages.component(player, "sethome-usage"));
            return true;
        }
        String homeName = args.length == 0 ? PointStorage.DEFAULT_HOME_NAME : PointStorage.normalizeHomeName(args[0]);
        if (!isValidHomeName(homeName)) {
            player.sendMessage(messages.component(player, "invalid-home-name"));
            return true;
        }
        if (!checkCommandLimits(player, "sethome")) {
            return true;
        }
        int limit = homeLimit(player);
        if (storage.home(player.getUniqueId(), homeName).isEmpty() && storage.homeNames(player.getUniqueId()).size() >= limit) {
            player.sendMessage(messages.component(player, "home-limit-reached", Map.of("limit", homeLimitText(player, limit))));
            return true;
        }

        PointMutationResult result = points.setHome(
                player,
                player.getUniqueId(),
                homeName,
                player.getLocation(),
                new PointMetadata("", "", player.getName(), Instant.now().getEpochSecond())
        );
        if (!handleMutationResult(player, result, null)) {
            return true;
        }
        player.sendMessage(messages.component(player, "home-set", Map.of("home", homeName)));
        playFeedback(player, "home-set");
        return true;
    }

    private boolean showHome(Player player, String[] args) {
        if (args.length > 1) {
            player.sendMessage(messages.component(player, "home-usage"));
            return true;
        }
        if (!checkCommandLimits(player, "home")) {
            return true;
        }

        UUID playerId = player.getUniqueId();
        String resolvedName;
        Optional<StoredPoint> home;
        if (args.length == 0) {
            String fallback = resolveDefaultHomeName(playerId);
            if (fallback == null) {
                player.sendMessage(messages.component(player, "home-not-set", Map.of("home", PointStorage.DEFAULT_HOME_NAME)));
                return true;
            }
            resolvedName = fallback;
            home = storage.home(playerId, resolvedName);
        } else {
            resolvedName = PointStorage.normalizeHomeName(args[0]);
            if (!isValidHomeName(resolvedName)) {
                player.sendMessage(messages.component(player, "invalid-home-name"));
                return true;
            }
            home = storage.home(playerId, resolvedName);
            if (home.isEmpty()) {
                player.sendMessage(messages.component(player, "home-not-set", Map.of("home", resolvedName)));
                return true;
            }
        }

        sendPointMessage(player, "home-location", home.get(), Map.of("home", resolvedName));
        sendInfoCard(player, home.get(), Map.of("point", resolvedName));
        return true;
    }

    private String resolveDefaultHomeName(UUID playerId) {
        Optional<StoredPoint> defaultHome = storage.home(playerId, PointStorage.DEFAULT_HOME_NAME);
        if (defaultHome.isPresent()) {
            return PointStorage.DEFAULT_HOME_NAME;
        }

        Map<String, StoredPoint> playerHomes = storage.homes(playerId);
        if (playerHomes.isEmpty()) {
            return null;
        }

        return playerHomes.entrySet().stream()
                .min(Comparator.<Map.Entry<String, StoredPoint>>comparingLong(entry -> entry.getValue().createdAt())
                        .thenComparing(Map.Entry::getKey, Comparator.naturalOrder()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private boolean listHomes(Player player) {
        if (!checkCommandLimits(player, "homes")) {
            return true;
        }

        Map<String, StoredPoint> homes = storage.homes(player.getUniqueId());
        int limit = homeLimit(player);
        if (homes.isEmpty()) {
            player.sendMessage(messages.component(player, "no-homes", Map.of("limit", homeLimitText(player, limit))));
            return true;
        }

        player.sendMessage(messages.component(player, "homes-header", Map.of(
                "used", String.valueOf(homes.size()),
                "limit", homeLimitText(player, limit)
        )));
        homes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> player.sendMessage(buildHomeLine(player, entry.getKey(), entry.getValue())));
        return true;
    }

    private Component buildHomeLine(Player player, String homeName, StoredPoint point) {
        Map<String, String> placeholders = Map.of(
                "home", homeName,
                "world", point.worldName(),
                "x", String.valueOf(point.blockX()),
                "y", String.valueOf(point.blockY()),
                "z", String.valueOf(point.blockZ())
        );
        return LegacyComponentSerializer.legacyAmpersand()
                .deserialize(messages.text(player, "homes-line", placeholders))
                .clickEvent(ClickEvent.copyToClipboard(applyPlaceholders(copyFormat, placeholders)))
                .hoverEvent(HoverEvent.showText(messages.component(player, "coordinates-hover-text")));
    }

    private boolean deleteHome(Player player, String[] args) {
        if (args.length != 1) {
            player.sendMessage(messages.component(player, "delhome-usage"));
            return true;
        }
        String homeName = PointStorage.normalizeHomeName(args[0]);
        if (!isValidHomeName(homeName)) {
            player.sendMessage(messages.component(player, "invalid-home-name"));
            return true;
        }
        if (!checkCommandLimits(player, "delhome")) {
            return true;
        }

        PointMutationResult result = points.deleteHome(player, player.getUniqueId(), homeName);
        if (result == PointMutationResult.NOT_FOUND) {
            player.sendMessage(messages.component(player, "home-not-set", Map.of("home", homeName)));
            return true;
        }
        if (!handleMutationResult(player, result, homeName)) {
            return true;
        }

        player.sendMessage(messages.component(player, "home-deleted", Map.of("home", homeName)));
        return true;
    }

    private boolean setWarp(Player player, String[] args) {
        if (!ensurePermission(player, PERMISSION_SETWARP)) {
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(messages.component(player, "setwarp-usage"));
            return true;
        }
        if (!isValidWarpName(args[0])) {
            player.sendMessage(messages.component(player, "invalid-warp-name"));
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
                new PointMetadata(input.description(), input.icon(), input.category(), input.publicVisible(), player.getName(), Instant.now().getEpochSecond())
        );
        if (!handleMutationResult(player, result, warpName)) {
            return true;
        }
        player.sendMessage(messages.component(player, "warp-set", Map.of("warp", warpName)));
        playFeedback(player, "warp-set");
        return true;
    }

    private WarpInput parseWarpInput(Player player, String[] args) {
        String icon = "";
        String category = "";
        boolean publicVisible = true;
        List<String> descriptionParts = new ArrayList<>();
        for (int index = 1; index < args.length; index++) {
            String argument = args[index];
            if (argument.equalsIgnoreCase("--icon")) {
                if (index + 1 >= args.length) {
                    player.sendMessage(messages.component(player, "setwarp-usage"));
                    return null;
                }

                String rawIcon = args[++index];
                Optional<String> validatedIcon = validateWarpIcon(rawIcon);
                if (validatedIcon.isEmpty()) {
                    player.sendMessage(messages.component(player, "invalid-warp-icon", Map.of("icon", rawIcon)));
                    return null;
                }
                icon = validatedIcon.get();
            } else if (argument.equalsIgnoreCase("--category")) {
                if (index + 1 >= args.length) {
                    player.sendMessage(messages.component(player, "setwarp-usage"));
                    return null;
                }
                String rawCategory = args[++index];
                if (!PointStorage.isValidWarpName(rawCategory)) {
                    player.sendMessage(messages.component(player, "invalid-warp-category", Map.of("category", rawCategory)));
                    return null;
                }
                category = rawCategory.toLowerCase(Locale.ROOT);
            } else if (argument.equalsIgnoreCase("--private")) {
                publicVisible = false;
            } else if (argument.equalsIgnoreCase("--public")) {
                publicVisible = true;
            } else {
                descriptionParts.add(argument);
            }
        }

        return new WarpInput(String.join(" ", descriptionParts).trim(), icon, category, publicVisible);
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
            player.sendMessage(messages.component(player, "warp-usage"));
            return true;
        }
        if (!isValidWarpName(args[0])) {
            player.sendMessage(messages.component(player, "invalid-warp-name"));
            return true;
        }
        if (!checkCommandLimits(player, "warp")) {
            return true;
        }

        String warpName = PointStorage.normalizeWarpName(args[0]);
        Optional<StoredPoint> warp = storage.warp(warpName);
        if (warp.isEmpty() || !canSeeWarp(player, warp.get())) {
            player.sendMessage(messages.component(player, "warp-not-set", Map.of("warp", warpName)));
            return true;
        }

        StoredPoint point = warp.get();
        sendPointMessage(player, "warp-location", point, Map.of("warp", warpName));
        sendInfoCard(player, point, Map.of("point", warpName));
        if (!point.description().isBlank()) {
            player.sendMessage(messages.component(player, "warp-description", Map.of("description", point.description())));
        }
        if (!point.category().isBlank()) {
            player.sendMessage(messages.component(player, "warp-category", Map.of("category", point.category())));
        }
        if (!point.publicVisible()) {
            player.sendMessage(messages.component(player, "warp-private-flag"));
        }
        return true;
    }

    private boolean deleteWarp(Player player, String[] args) {
        if (!ensurePermission(player, PERMISSION_DELWARP)) {
            return true;
        }

        removeExpiredPendingDeletions();
        if (args.length != 1 && args.length != 2) {
            player.sendMessage(messages.component(player, "delwarp-usage"));
            return true;
        }
        if (!isValidWarpName(args[0])) {
            player.sendMessage(messages.component(player, "invalid-warp-name"));
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

            player.sendMessage(messages.component(player, "delwarp-usage"));
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
            player.sendMessage(messages.component(player, "warp-not-set", Map.of("warp", warpName)));
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
            player.sendMessage(messages.component(player, "warp-delete-confirm-missing", Map.of("warp", warpName)));
            return true;
        }
        if (pendingDeletion.expiresAtMillis() <= System.currentTimeMillis()) {
            pendingWarpDeletions.remove(player.getUniqueId());
            player.sendMessage(messages.component(player, "warp-delete-confirm-expired", Map.of("warp", warpName)));
            return true;
        }

        pendingWarpDeletions.remove(player.getUniqueId());
        return deleteWarpNow(player, warpName);
    }

    private boolean cancelWarpDeletion(Player player, String warpName) {
        PendingWarpDeletion pendingDeletion = pendingWarpDeletions.get(player.getUniqueId());
        if (pendingDeletion == null || !pendingDeletion.warpName().equals(warpName)) {
            player.sendMessage(messages.component(player, "warp-delete-confirm-missing", Map.of("warp", warpName)));
            return true;
        }

        pendingWarpDeletions.remove(player.getUniqueId());
        player.sendMessage(messages.component(player, "warp-delete-cancelled", Map.of("warp", warpName)));
        return true;
    }

    private boolean deleteWarpNow(Player player, String warpName) {
        PointMutationResult result = points.deleteWarp(player, warpName);
        if (result == PointMutationResult.NOT_FOUND) {
            player.sendMessage(messages.component(player, "warp-not-set", Map.of("warp", warpName)));
            return true;
        }
        if (!handleMutationResult(player, result, warpName)) {
            return true;
        }

        player.sendMessage(messages.component(player, "warp-deleted", Map.of("warp", warpName)));
        return true;
    }

    private void sendWarpDeletionPrompt(Player player, String warpName, long ttlMillis) {
        Component prompt = messages.component(player, "warp-delete-confirm-prompt", Map.of(
                "warp", warpName,
                "time", formatDuration(ttlMillis)
        ));
        Component confirm = messages.component(player, "warp-delete-confirm-button")
                .clickEvent(ClickEvent.runCommand("/delwarp " + warpName + " confirm"))
                .hoverEvent(HoverEvent.showText(messages.component(player, "warp-delete-confirm-hover")));
        Component cancel = messages.component(player, "warp-delete-cancel-button")
                .clickEvent(ClickEvent.runCommand("/delwarp " + warpName + " cancel"))
                .hoverEvent(HoverEvent.showText(messages.component(player, "warp-delete-cancel-hover")));

        player.sendMessage(prompt.append(Component.text(" ")).append(confirm).append(Component.text(" ")).append(cancel));
    }

    private boolean listWarps(Player player, String commandName) {
        if (!checkCommandLimits(player, commandName)) {
            return true;
        }
        return sendWarpsList(player);
    }

    private boolean sendWarpsList(CommandSender sender) {
        List<String> visible = storage.warpNames().stream()
                .filter(name -> {
                    Optional<StoredPoint> warp = storage.warp(name);
                    return warp.isPresent() && canSeeWarp(sender, warp.get());
                })
                .map(this::warpDisplayName)
                .collect(Collectors.toCollection(ArrayList::new));
        if (visible.isEmpty()) {
            sender.sendMessage(messages.component(sender, "no-warps"));
            return true;
        }

        sender.sendMessage(messages.component(sender, "available-warps", Map.of("warps", String.join(", ", visible))));
        return true;
    }

    private String warpDisplayName(String name) {
        String category = storage.warp(name).map(StoredPoint::category).orElse("");
        return category.isBlank() ? name : name + " [" + category + "]";
    }

    private boolean canSeeWarp(CommandSender sender, StoredPoint warp) {
        if (warp.publicVisible()) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (player.hasPermission(PERMISSION_WARP_PRIVATE) || player.hasPermission(PERMISSION_ADMIN)) {
            return true;
        }
        return !warp.createdBy().isBlank() && warp.createdBy().equalsIgnoreCase(player.getName());
    }

    private void sendPointMessage(Player player, String messageKey, StoredPoint point, Map<String, String> extraPlaceholders) {
        Map<String, String> placeholders = new HashMap<>(extraPlaceholders);
        placeholders.put("x", String.valueOf(point.blockX()));
        placeholders.put("y", String.valueOf(point.blockY()));
        placeholders.put("z", String.valueOf(point.blockZ()));
        placeholders.put("world", point.worldName());

        Component message = LegacyComponentSerializer.legacyAmpersand()
                .deserialize(messages.text(player, messageKey, placeholders))
                .clickEvent(ClickEvent.copyToClipboard(applyPlaceholders(copyFormat, placeholders)))
                .hoverEvent(HoverEvent.showText(messages.component(player, "coordinates-hover-text")));

        player.sendMessage(message);
    }

    private void sendInfoCard(Player player, StoredPoint point, Map<String, String> extraPlaceholders) {
        if (!getConfig().getBoolean("info-card.enabled", true)) {
            return;
        }

        Map<String, String> placeholders = pointPlaceholders(player, point, extraPlaceholders);
        for (String line : infoCardLines()) {
            String normalizedLine = line.toLowerCase(Locale.ROOT).trim();
            if (shouldShowInfoCardLine(normalizedLine, point)) {
                player.sendMessage(messages.component(player, "info-card-" + normalizedLine, placeholders));
            }
        }
    }

    private List<String> infoCardLines() {
        List<String> lines = getConfig().getStringList("info-card.lines");
        return lines.isEmpty() ? List.of("header", "coordinates", "biome", "time", "weather", "creator", "age") : lines;
    }

    private boolean shouldShowInfoCardLine(String line, StoredPoint point) {
        return switch (line) {
            case "header", "coordinates" -> true;
            case "biome" -> getConfig().getBoolean("info-card.show-biome", true);
            case "time" -> getConfig().getBoolean("info-card.show-time", true);
            case "weather" -> getConfig().getBoolean("info-card.show-weather", true);
            case "creator" -> getConfig().getBoolean("info-card.show-creator", true) && !point.createdBy().isBlank();
            case "age" -> getConfig().getBoolean("info-card.show-age", true) && point.createdAt() > 0L;
            default -> false;
        };
    }

    private Map<String, String> pointPlaceholders(Player player, StoredPoint point, Map<String, String> extraPlaceholders) {
        Map<String, String> placeholders = new HashMap<>(extraPlaceholders);
        placeholders.put("x", String.valueOf(point.blockX()));
        placeholders.put("y", String.valueOf(point.blockY()));
        placeholders.put("z", String.valueOf(point.blockZ()));
        placeholders.put("world", point.worldName());
        placeholders.put("biome", pointBiome(player, point));
        placeholders.put("time", pointTime(player, point));
        placeholders.put("weather", pointWeather(player, point));
        placeholders.put("creator", point.createdBy());
        placeholders.put("age", pointAge(player, point));
        return placeholders;
    }

    private String pointBiome(Player player, StoredPoint point) {
        World world = getServer().getWorld(point.worldName());
        if (world == null) {
            return messages.text(player, "info-card-unknown");
        }
        Biome biome = world.getBiome(point.blockX(), point.blockY(), point.blockZ());
        return prettifyKey(biome.key().value());
    }

    private String prettifyKey(String value) {
        String[] words = value.replace('_', ' ').trim().split(" ");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.toString();
    }

    private String pointTime(Player player, StoredPoint point) {
        World world = getServer().getWorld(point.worldName());
        if (world == null) {
            return messages.text(player, "info-card-unknown");
        }
        long ticks = world.getTime();
        long hours = (ticks / 1000 + 6) % 24;
        long minutes = (ticks % 1000) * 60 / 1000;
        return String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
    }

    private String pointWeather(Player player, StoredPoint point) {
        World world = getServer().getWorld(point.worldName());
        if (world == null) {
            return messages.text(player, "info-card-unknown");
        }
        if (world.hasStorm()) {
            return world.isThundering() ? messages.text(player, "info-card-weather-thunder") : messages.text(player, "info-card-weather-rain");
        }
        return messages.text(player, "info-card-weather-clear");
    }

    private String pointAge(Player player, StoredPoint point) {
        if (point.createdAt() <= 0L) {
            return messages.text(player, "info-card-unknown");
        }
        return formatDuration(Duration.between(Instant.ofEpochSecond(point.createdAt()), Instant.now()).toMillis());
    }

    private void playFeedback(Player player, String eventKey) {
        feedback.play(player, eventKey);
    }

    private boolean checkCommandLimits(Player player, String commandName) {
        return commandLimits.check(player, commandName);
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
        java.util.OptionalLong parsed = Durations.parse(value);
        if (parsed.isEmpty()) {
            if (value != null && !String.valueOf(value).isBlank()) {
                getLogger().warning("Invalid duration value '" + value + "', using default " + formatDuration(defaultMillis) + ".");
            }
            return defaultMillis;
        }
        return parsed.getAsLong();
    }

    private String formatDuration(long millis) {
        return Durations.format(millis);
    }

    private void removeExpiredPendingDeletions() {
        long now = System.currentTimeMillis();
        pendingWarpDeletions.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() <= now);
    }

    private boolean isValidWarpName(String name) {
        return PointStorage.isValidWarpName(name);
    }

    private boolean isValidHomeName(String name) {
        return PointStorage.isValidHomeName(name);
    }

    private int homeLimit(Player player) {
        if (getConfig().getBoolean("homes.operator-unlimited", true) && player.isOp()) {
            return Integer.MAX_VALUE;
        }
        int limit = Math.max(1, getConfig().getInt("homes.default-limit", 1));
        if (getConfig().isConfigurationSection("homes.limits-by-permission")) {
            for (String permission : getConfig().getConfigurationSection("homes.limits-by-permission").getKeys(false)) {
                if (player.hasPermission(permission)) {
                    limit = Math.max(limit, getConfig().getInt("homes.limits-by-permission." + permission, limit));
                }
            }
        }
        return limit;
    }

    private String homeLimitText(Player player, int limit) {
        return limit == Integer.MAX_VALUE ? messages.text(player, "home-limit-unlimited") : String.valueOf(limit);
    }

    private boolean ensurePermission(CommandSender sender, String permission) {
        if (!(sender instanceof Player) || sender.hasPermission(permission)) {
            return true;
        }

        sender.sendMessage(messages.component(sender, "no-permission"));
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
            player.sendMessage(messages.component(player, "point-change-cancelled", placeholders));
            return false;
        }
        if (result == PointMutationResult.INVALID) {
            player.sendMessage(messages.component(player, "data-save-error"));
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
            return completeWarpNames(sender, args);
        }
        if (commandName.equals("home") || commandName.equals("delhome")) {
            return completeHomeNames(sender, args);
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
            if (sender.hasPermission(PERMISSION_ADMIN)) {
                completions.add("homes");
                completions.add("delhome");
                completions.add("purge");
                completions.add("import");
            }
            return filterByPrefix(completions, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("import") && sender.hasPermission(PERMISSION_ADMIN)) {
            return filterByPrefix(List.of("essentials"), args[1]);
        }
        return List.of();
    }

    private List<String> completeSetWarp(String[] args) {
        if (args.length < 2) {
            return List.of();
        }

        String previous = args[args.length - 2];
        if (previous.equalsIgnoreCase("--icon")) {
            return filterByPrefix(itemMaterialNames(), args[args.length - 1]);
        }
        if (previous.equalsIgnoreCase("--category")) {
            return List.of();
        }
        return filterByPrefix(List.of("--icon", "--category", "--private", "--public"), args[args.length - 1]);
    }

    private List<String> itemMaterialNames() {
        return Stream.of(Material.values())
                .filter(material -> material.isItem() && !material.isLegacy() && !material.isAir())
                .map(material -> material.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<String> completeWarpNames(CommandSender sender, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> names = storage.warpNames().stream()
                .filter(name -> storage.warp(name).map(warp -> canSeeWarp(sender, warp)).orElse(false))
                .collect(Collectors.toCollection(ArrayList::new));
        return filterByPrefix(names, args[0]);
    }

    private List<String> completeHomeNames(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) {
            return List.of();
        }
        return filterByPrefix(new ArrayList<>(storage.homeNames(player.getUniqueId())), args[0]);
    }

    private List<String> completeDeleteWarp(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_DELWARP)) {
            return List.of();
        }

        if (args.length == 1) {
            return completeWarpNames(sender, args);
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

    private record WarpInput(String description, String icon, String category, boolean publicVisible) {
    }
}
