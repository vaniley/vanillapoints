package dev.vaniley.vanillapoints;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class VanillaPoints extends JavaPlugin implements CommandExecutor, TabCompleter {
    private static final String PERMISSION_SETSPAWN = "vanillapoints.setspawn";
    private static final String PERMISSION_SETWARP = "vanillapoints.setwarp";
    private static final String PERMISSION_DELWARP = "vanillapoints.delwarp";
    private static final String PERMISSION_RELOAD = "vanillapoints.reload";
    private static final String DEFAULT_COPY_FORMAT = "{x} {y} {z}";

    private MessageService messages;
    private PointStorage storage;
    private boolean saveImmediately;
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
        saveData();
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
        boolean normalizeToBlock = getConfig().getBoolean("settings.normalize-to-block", true);
        copyFormat = getConfig().getString("settings.copy-format", DEFAULT_COPY_FORMAT);
        if (copyFormat == null || copyFormat.isBlank()) {
            copyFormat = DEFAULT_COPY_FORMAT;
        }

        messages = new MessageService(this);
        messages.load();

        storage = new PointStorage(this, normalizeToBlock);
        storage.load();
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
                return listWarps(player);
            case "delwarp":
                return deleteWarp(player, args);
            default:
                return false;
        }
    }

    private boolean handlePluginCommand(CommandSender sender, String[] args) {
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

        if (!saveData()) {
            sender.sendMessage(messages.component("data-save-error"));
            return true;
        }

        loadPluginState();
        sender.sendMessage(messages.component("reload-complete"));
        return true;
    }

    private boolean setSpawn(Player player) {
        if (!ensurePermission(player, PERMISSION_SETSPAWN)) {
            return true;
        }

        storage.setSpawn(player.getLocation());
        player.sendMessage(messages.component("spawn-set"));
        saveDataIfNeeded(player);
        return true;
    }

    private boolean showSpawn(Player player) {
        Optional<StoredPoint> spawn = storage.spawn();
        if (spawn.isEmpty()) {
            player.sendMessage(messages.component("spawn-not-set"));
            return true;
        }

        sendPointMessage(player, "spawn-location", spawn.get(), Map.of());
        return true;
    }

    private boolean setHome(Player player) {
        storage.setHome(player.getUniqueId(), player.getLocation());
        player.sendMessage(messages.component("home-set"));
        saveDataIfNeeded(player);
        return true;
    }

    private boolean showHome(Player player) {
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

        if (args.length != 1) {
            player.sendMessage(messages.component("setwarp-usage"));
            return true;
        }
        if (!isValidWarpName(args[0])) {
            player.sendMessage(messages.component("invalid-warp-name"));
            return true;
        }

        String warpName = PointStorage.normalizeWarpName(args[0]);
        storage.setWarp(warpName, player.getLocation());
        player.sendMessage(messages.component("warp-set", Map.of("warp", warpName)));
        saveDataIfNeeded(player);
        return true;
    }

    private boolean showWarp(Player player, String[] args) {
        if (args.length == 0) {
            return listWarps(player);
        }

        if (args.length != 1) {
            player.sendMessage(messages.component("warp-usage"));
            return true;
        }
        if (!isValidWarpName(args[0])) {
            player.sendMessage(messages.component("invalid-warp-name"));
            return true;
        }

        String warpName = PointStorage.normalizeWarpName(args[0]);
        Optional<StoredPoint> warp = storage.warp(warpName);
        if (warp.isEmpty()) {
            player.sendMessage(messages.component("warp-not-set", Map.of("warp", warpName)));
            return true;
        }

        sendPointMessage(player, "warp-location", warp.get(), Map.of("warp", warpName));
        return true;
    }

    private boolean deleteWarp(Player player, String[] args) {
        if (!ensurePermission(player, PERMISSION_DELWARP)) {
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(messages.component("delwarp-usage"));
            return true;
        }
        if (!isValidWarpName(args[0])) {
            player.sendMessage(messages.component("invalid-warp-name"));
            return true;
        }

        String warpName = PointStorage.normalizeWarpName(args[0]);
        if (!storage.deleteWarp(warpName)) {
            player.sendMessage(messages.component("warp-not-set", Map.of("warp", warpName)));
            return true;
        }

        player.sendMessage(messages.component("warp-deleted", Map.of("warp", warpName)));
        saveDataIfNeeded(player);
        return true;
    }

    private boolean listWarps(CommandSender sender) {
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

    private String applyPlaceholders(String text, Map<String, String> placeholders) {
        String result = text;
        for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
            result = result.replace("{" + placeholder.getKey() + "}", placeholder.getValue());
        }
        return result;
    }

    private void saveDataIfNeeded(Player player) {
        if (!saveImmediately) {
            return;
        }

        if (!saveData()) {
            player.sendMessage(messages.component("data-save-error"));
        }
    }

    private boolean saveData() {
        if (storage == null) {
            return true;
        }

        try {
            storage.save();
            return true;
        } catch (IOException exception) {
            getLogger().severe("Could not save data.yml: " + exception.getMessage());
            return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);

        if (commandName.equals("vanillapoints")) {
            if (args.length != 1 || !sender.hasPermission(PERMISSION_RELOAD)) {
                return List.of();
            }

            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("reload").stream()
                    .filter(subCommand -> subCommand.startsWith(prefix))
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        if (args.length != 1 || (!commandName.equals("warp") && !commandName.equals("delwarp"))) {
            return List.of();
        }
        if (commandName.equals("delwarp") && !sender.hasPermission(PERMISSION_DELWARP)) {
            return List.of();
        }

        String prefix = args[0].toLowerCase(Locale.ROOT);
        return storage.warpNames().stream()
                .filter(warp -> warp.startsWith(prefix))
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
