package com.osiris.upgrader.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ConfigManager {
    public static final List<String> CONFIG_FILES = List.of(
            "config.yml",
            "vanilla-items.yml",
            "rewards.yml",
            "messages.yml",
            "menus.yml"
    );

    private final JavaPlugin plugin;
    private FileConfiguration config;
    private FileConfiguration items;
    private FileConfiguration rewards;
    private FileConfiguration messages;
    private FileConfiguration menus;
    private MenuLayout menuLayout = MenuLayout.fallback();
    private RouletteSettings rouletteSettings = RouletteSettings.fallback();

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("No se pudo crear la carpeta de datos.");
        }
        config = load("config.yml");
        renameLegacy("items.yml", "vanilla-items.yml");
        items = load("vanilla-items.yml");
        rewards = load("rewards.yml");
        messages = load("messages.yml");
        menus = load("menus.yml");
        menuLayout = MenuLayout.load(menus, plugin.getLogger());
        rouletteSettings = RouletteSettings.load(config, plugin);
        String menuType = menuType();
        if (!menuType.equals("nexo") && !menuType.equals("vanilla")) {
            plugin.getLogger().warning("menu-type '" + menuType + "' no es válido. Se usará vanilla.");
        }
    }

    public FileConfiguration config() {
        return config;
    }

    public FileConfiguration items() {
        return items;
    }

    public FileConfiguration rewards() {
        return rewards;
    }

    public void saveFile(String name) {
        FileConfiguration yaml = switch (name) {
            case "vanilla-items.yml" -> items;
            case "rewards.yml" -> rewards;
            default -> null;
        };
        if (!(yaml instanceof YamlConfiguration configuration)) {
            return;
        }
        try {
            configuration.save(new File(plugin.getDataFolder(), name));
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo guardar " + name, ex);
        }
    }

    public FileConfiguration messages() {
        return messages;
    }

    public FileConfiguration menus() {
        return menus;
    }

    public MenuLayout menu() {
        return menuLayout;
    }

    public RouletteSettings roulette() {
        return rouletteSettings;
    }

    public String menuType() {
        return config.getString("menu-type", "vanilla").trim().toLowerCase();
    }

    public boolean nexoMenu() {
        return "nexo".equals(menuType());
    }

    public double minChance() {
        return config.getDouble("min-chance", 1.0d);
    }

    public double maxChance() {
        return config.getDouble("max-chance", 95.0d);
    }

    public double chanceFactor() {
        return config.getDouble("chance-factor", 1.0d);
    }

    public long spinCooldownSeconds() {
        long seconds = config.getLong("spin-cooldown-seconds", 86_400L);
        if (seconds < 0L) {
            return 0L;
        }
        return Math.min(seconds, 8_760L * 3_600L);
    }

    public boolean scaleDamagedTools() {
        return config.getBoolean("scale-damaged-tools", true);
    }

    public boolean broadcastDailySpin() {
        return config.getBoolean("broadcast-daily-spin", true);
    }

    public boolean dailySpinPrefix() {
        return config.getBoolean("daily-spin-prefix", true);
    }

    public String sound(String key) {
        String fallback = switch (key) {
            case "spin-tick" -> "BLOCK_NOTE_BLOCK_PLING";
            case "win" -> "ENTITY_PLAYER_LEVELUP";
            case "fail" -> "ENTITY_VILLAGER_NO";
            case "click" -> "UI_BUTTON_CLICK";
            default -> "";
        };
        return config.getString("sounds." + key, fallback);
    }

    public int animationTicks() {
        return clamp(config.getInt("animation.duration-ticks", 70), 20, 200);
    }

    public int extraRotations() {
        return clamp(config.getInt("animation.extra-rotations", 5), 1, 12);
    }

    public int soundInterval() {
        return clamp(config.getInt("animation.tick-sound-interval", 2), 1, 10);
    }

    public long showcaseIntervalTicks() {
        double seconds = config.getDouble("roulette.showcase-interval-seconds", 3.0d);
        if (seconds < 0.5d) {
            seconds = 0.5d;
        } else if (seconds > 60.0d) {
            seconds = 60.0d;
        }
        return Math.max(10L, Math.round(seconds * 20.0d));
    }

    public Material portableMaterial() {
        return material(config, "items.portable.material", Material.ECHO_SHARD);
    }

    public int portableModelData() {
        return config.getInt("items.portable.custom-model-data", 88101);
    }

    public Material spawnerMaterial() {
        return material(config, "items.spawner.material", Material.CHEST);
    }

    public int spawnerModelData() {
        return config.getInt("items.spawner.custom-model-data", 88102);
    }

    private void renameLegacy(String from, String to) {
        File oldFile = new File(plugin.getDataFolder(), from);
        File newFile = new File(plugin.getDataFolder(), to);
        if (!oldFile.isFile() || newFile.exists()) {
            return;
        }
        if (oldFile.renameTo(newFile)) {
            plugin.getLogger().info(from + " ahora se llama " + to + ".");
        } else {
            plugin.getLogger().warning("No se pudo renombrar " + from + " a " + to + ".");
        }
    }

    private FileConfiguration load(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
        if ("menus.yml".equals(name)) {
            migrateGuideSlot(file);
        }
        if ("vanilla-items.yml".equals(name)) {
            migrateVanillaItems(file);
        }
        if ("config.yml".equals(name)) {
            migrateDailyBroadcast(file);
        }
        mergeMissing(name, file);
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo leer " + name + ". Se usan los valores internos.", ex);
        }
        if ("config.yml".equals(name)) {
            migrateConfig(yaml, file);
        }
        if ("rewards.yml".equals(name)) {
            migrateRewards(yaml, file);
        }
        if ("vanilla-items.yml".equals(name)) {
            migrateItemLimits(yaml, file);
        }
        try (InputStream stream = plugin.getResource(name)) {
            if (stream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
                yaml.setDefaults(defaults);
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "No se pudieron aplicar los valores internos de " + name, ex);
        }
        return yaml;
    }

    private void migrateGuideSlot(File file) {
        if (file == null || !file.isFile()) {
            return;
        }
        String text;
        try {
            text = java.nio.file.Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "No se pudo leer menus.yml para mover el libro.", ex);
            return;
        }
        java.util.regex.Matcher version = java.util.regex.Pattern.compile("(?m)^file-version:\\s*(\\d+)\\s*$").matcher(text);
        int current = version.find() ? Integer.parseInt(version.group(1)) : 1;
        if (current >= 4) {
            return;
        }
        String updated = text.replaceAll("(?m)^(\\s*)guide:\\s*8\\s*$", "$1guide: 0");
        updated = updated.replaceFirst("(?m)^file-version:\\s*\\d+\\s*$", "file-version: 4");
        if (updated.equals(text)) {
            return;
        }
        try {
            java.nio.file.Files.writeString(file.toPath(), updated, StandardCharsets.UTF_8);
            plugin.getLogger().info("El libro de cómo apostar pasó al primer hueco del menú.");
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "No se pudo mover el libro en menus.yml.", ex);
        }
    }

    private void mergeMissing(String name, File file) {
        if (file == null || !file.isFile()) {
            return;
        }
        YamlConfiguration defaults;
        try (InputStream stream = plugin.getResource(name)) {
            if (stream == null) {
                return;
            }
            defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "No se pudo leer el " + name + " interno.", ex);
            return;
        }
        String text;
        try {
            text = java.nio.file.Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "No se pudo leer " + name + " para añadir opciones nuevas.", ex);
            return;
        }
        YamlConfiguration live = YamlConfiguration.loadConfiguration(file);
        List<YamlMerger.Placement> placements = new ArrayList<>();
        List<String> added = new ArrayList<>();
        collectMissing(name, text, "", defaults, live, placements, added);
        if (placements.isEmpty()) {
            return;
        }
        String updated = YamlMerger.insert(text, placements);
        if (updated.equals(text.replace("\r\n", "\n"))) {
            plugin.getLogger().warning(name + ": había opciones nuevas, pero no se pudieron insertar sin reescribir el archivo.");
            return;
        }
        if (text.contains("\r\n")) {
            updated = updated.replace("\n", "\r\n");
        }
        try {
            java.nio.file.Files.writeString(file.toPath(), updated, StandardCharsets.UTF_8);
            plugin.getLogger().info(name + ": se añadieron " + added.size() + " opciones nuevas. Lo ya configurado se dejó igual.");
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "No se pudieron añadir las opciones nuevas de " + name, ex);
        }
    }

    private void collectMissing(String fileName, String text, String prefix, org.bukkit.configuration.ConfigurationSection defaults,
                                YamlConfiguration live, List<YamlMerger.Placement> placements, List<String> added) {
        for (String key : defaults.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (skipMerge(fileName, path)) {
                continue;
            }
            if (defaults.isConfigurationSection(key)) {
                if (live.isConfigurationSection(path)) {
                    collectMissing(fileName, text, path, defaults.getConfigurationSection(key), live, placements, added);
                } else if (!live.contains(path)) {
                    addPlacement(fileName, text, prefix, key, path, defaults, placements, added);
                }
            } else if (!live.contains(path)) {
                addPlacement(fileName, text, prefix, key, path, defaults, placements, added);
            }
        }
    }

    private void addPlacement(String fileName, String text, String prefix, String key, String path,
                              org.bukkit.configuration.ConfigurationSection defaults,
                              List<YamlMerger.Placement> placements, List<String> added) {
        if (!prefix.isEmpty() && !YamlMerger.canPlace(text, prefix)) {
            plugin.getLogger().warning(nameWarning(fileName, path));
            return;
        }
        int indent = prefix.isEmpty() ? 0 : YamlMerger.indentOfPath(text, prefix) + 2;
        if (indent < 0) {
            plugin.getLogger().warning(nameWarning(fileName, path));
            return;
        }
        placements.add(new YamlMerger.Placement(prefix, YamlMerger.fragment(key, snapshot(defaults, key), indent)));
        added.add(path);
    }

    private static String nameWarning(String fileName, String path) {
        return fileName + ": no se pudo insertar " + path + " sin reescribir el archivo. El valor interno se usa igual.";
    }

    private static boolean skipMerge(String fileName, String path) {
        return "rewards.yml".equals(fileName) && (path.equals("rewards") || path.startsWith("rewards."));
    }

    private static Object snapshot(org.bukkit.configuration.ConfigurationSection section, String key) {
        if (section.isConfigurationSection(key)) {
            org.bukkit.configuration.ConfigurationSection child = section.getConfigurationSection(key);
            Map<String, Object> map = new LinkedHashMap<>();
            if (child != null) {
                for (String nested : child.getKeys(false)) {
                    map.put(nested, snapshot(child, nested));
                }
            }
            return map;
        }
        if (section.isList(key)) {
            return section.getList(key);
        }
        return section.get(key);
    }

    private void migrateDailyBroadcast(File file) {
        if (file == null || !file.isFile()) {
            return;
        }
        String text;
        try {
            text = java.nio.file.Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "No se pudo leer config.yml para el aviso de giro.", ex);
            return;
        }
        StringBuilder extra = new StringBuilder();
        if (!java.util.regex.Pattern.compile("(?m)^broadcast-daily-spin\\s*:").matcher(text).find()) {
            extra.append("\n# Aviso a todos los jugadores cuando alguien termina un giro, gane o pierda.\n");
            extra.append("# El texto se edita en messages.yml, en messages.daily-spin. %player% es el nombre.\n");
            extra.append("broadcast-daily-spin: true\n");
        }
        if (!java.util.regex.Pattern.compile("(?m)^daily-spin-prefix\\s*:").matcher(text).find()) {
            extra.append("# false envía ese aviso sin el prefijo de messages.yml. Los demás mensajes no cambian.\n");
            extra.append("daily-spin-prefix: true\n");
        }
        if (extra.isEmpty()) {
            return;
        }
        if (!text.endsWith("\n")) {
            text = text + "\n";
        }
        try {
            java.nio.file.Files.writeString(file.toPath(), text + extra, StandardCharsets.UTF_8);
            plugin.getLogger().info("config.yml: se añadieron las opciones del aviso de giro diario.");
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "No se pudieron añadir las opciones del aviso de giro en config.yml.", ex);
        }
    }

    private void migrateConfig(YamlConfiguration yaml, File file) {
        int version = yaml.getInt("config-version", 1);
        if (version >= 8) {
            return;
        }
        if (version < 7 && version >= 6) {
            if (!yaml.contains("spin-cooldown-hours") && !yaml.contains("spin-cooldown-seconds")) {
                yaml.set("spin-cooldown-hours", 24);
            }
        }
        if (version < 2) {
            String menu = yaml.getString("menu-type", "nexo");
            if (menu != null && menu.equalsIgnoreCase("nexo")) {
                yaml.set("menu-type", "vanilla");
            }
            if ("BLACK_CONCRETE".equalsIgnoreCase(yaml.getString("roulette.fail-material", ""))) {
                yaml.set("roulette.fail-material", "RED_CONCRETE");
            }
            if ("GOLD_BLOCK".equalsIgnoreCase(yaml.getString("roulette.needle-tip-material", ""))) {
                yaml.set("roulette.needle-tip-material", "BLACK_CONCRETE");
            }
            if ("GOLD_BLOCK".equalsIgnoreCase(yaml.getString("roulette.hub-material", ""))) {
                yaml.set("roulette.hub-material", "OAK_PLANKS");
            }
            yaml.set("roulette.small-stands", false);
        }
        if (version < 3) {
            yaml.set("roulette.idle-palette", List.of("RED_CONCRETE", "BLACK_CONCRETE"));
        }
        if (version < 4) {
            yaml.set("roulette.needle-material", "WHITE_CONCRETE");
            yaml.set("roulette.needle-tip-material", "WHITE_CONCRETE");
        }
        if (version < 5) {
            if (!yaml.contains("roulette.showcase-interval-seconds")) {
                yaml.set("roulette.showcase-interval-seconds", 3);
            }
        }
        if (version < 7 && !yaml.contains("spin-cooldown-hours") && !yaml.contains("spin-cooldown-seconds")) {
            yaml.set("spin-cooldown-hours", 24);
        }
        if (yaml.contains("spin-cooldown-hours")) {
            long seconds = Math.round(yaml.getDouble("spin-cooldown-hours", 24.0d) * 3_600.0d);
            yaml.set("spin-cooldown-seconds", Math.max(0L, seconds));
            yaml.set("spin-cooldown-hours", null);
        } else if (!yaml.contains("spin-cooldown-seconds")) {
            yaml.set("spin-cooldown-seconds", 86_400L);
        }
        String chatPrefix = yaml.getString("chat-prefix", "");
        yaml.set("chat-prefix", null);
        if (chatPrefix != null && !chatPrefix.isBlank()) {
            movePrefixToMessages(chatPrefix);
        }
        yaml.set("config-version", 8);
        try {
            yaml.save(file);
            plugin.getLogger().info("config.yml pasó a la versión 8: el cooldown está en segundos y el prefijo solo vive en messages.yml.");
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "No se pudo guardar la migración de config.yml", ex);
        }
    }

    private void movePrefixToMessages(String prefix) {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            return;
        }
        YamlConfiguration messages = YamlConfiguration.loadConfiguration(messagesFile);
        messages.set("messages.prefix", prefix);
        try {
            messages.save(messagesFile);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "No se pudo pasar chat-prefix a messages.yml", ex);
        }
    }

    private void migrateRewards(YamlConfiguration yaml, File file) {
        int version = yaml.getInt("file-version", 1);
        if (version >= 3) {
            return;
        }
        if (version < 2) {
            ConfigurationSection rewards = yaml.getConfigurationSection("rewards");
            if (rewards != null) {
                for (String key : rewards.getKeys(false)) {
                    String path = "rewards." + key + ".display-name";
                    String next = switch (yaml.getString(path, "")) {
                        case "&6Gema de Osiris" -> "&6Gema";
                        case "&eGema superior de Osiris" -> "&eGema superior";
                        case "&bOjo de Horus" -> "&bOjo";
                        case "&6Escarabajo dorado" -> "&6Reliquia dorada";
                        default -> null;
                    };
                    if (next != null) {
                        yaml.set(path, next);
                    }
                }
            }
        }
        for (String key : List.of("gema_osiris_basica", "gema_osiris_superior", "ojo_de_horus", "escarabajo_dorado")) {
            yaml.set("rewards." + key, null);
        }
        yaml.set("file-version", 3);
        try {
            yaml.save(file);
            plugin.getLogger().info("rewards.yml pasó a la versión 3: los premios de Nexo ya no vienen registrados.");
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "No se pudo guardar la migración de rewards.yml", ex);
        }
    }

    private void migrateVanillaItems(File file) {
        if (file == null || !file.isFile()) {
            return;
        }
        String text;
        try {
            text = java.nio.file.Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "No se pudo leer vanilla-items.yml para actualizar materiales.", ex);
            return;
        }
        String updated = renameLegacyMaterials(text);
        String additions = missingVanillaEntries(updated);
        if (additions.isEmpty() && updated.equals(text)) {
            return;
        }
        if (!updated.endsWith("\n")) {
            updated = updated + "\n";
        }
        updated = updated + additions;
        try {
            java.nio.file.Files.writeString(file.toPath(), updated, StandardCharsets.UTF_8);
            plugin.getLogger().info("vanilla-items.yml: se corrigieron nombres viejos y se añadieron valores que faltaban.");
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "No se pudo actualizar vanilla-items.yml.", ex);
        }
    }

    private static String renameLegacyMaterials(String text) {
        String[][] renames = {
                {"RAW_BEEF", "BEEF"},
                {"RAW_PORKCHOP", "PORKCHOP"},
                {"RAW_CHICKEN", "CHICKEN"}
        };
        String updated = text;
        for (String[] pair : renames) {
            if (hasMaterialKey(updated, pair[0]) && !hasMaterialKey(updated, pair[1])) {
                updated = updated.replaceAll("(?m)^(\\s*)" + pair[0] + "\\s*:", "$1" + pair[1] + ":");
            }
        }
        return updated;
    }

    private String missingVanillaEntries(String serverText) {
        YamlConfiguration defaults;
        try (InputStream stream = plugin.getResource("vanilla-items.yml")) {
            if (stream == null) {
                return "";
            }
            defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "No se pudo leer la lista interna de vanilla-items.yml.", ex);
            return "";
        }
        ConfigurationSection section = defaults.getConfigurationSection("item-values.vanilla");
        if (section == null) {
            return "";
        }
        StringBuilder extra = new StringBuilder();
        for (String key : section.getKeys(false)) {
            if (hasMaterialKey(serverText, key)) {
                continue;
            }
            ConfigurationSection nested = section.getConfigurationSection(key);
            if (nested != null) {
                extra.append("    ").append(key).append(":\n");
                extra.append("      value: ").append(plainNumber(nested.getDouble("value"))).append('\n');
                if (nested.isSet("max-win")) {
                    extra.append("      max-win: ").append(nested.getInt("max-win")).append('\n');
                }
            } else {
                extra.append("    ").append(key).append(": ").append(plainNumber(section.getDouble(key))).append('\n');
            }
        }
        return extra.toString();
    }

    private static boolean hasMaterialKey(String text, String key) {
        return java.util.regex.Pattern.compile("(?m)^\\s*" + java.util.regex.Pattern.quote(key) + "\\s*:").matcher(text).find();
    }

    private static String plainNumber(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    private void migrateItemLimits(YamlConfiguration yaml, File file) {
        String path = "item-values.vanilla.TOTEM_OF_UNDYING";
        if (yaml.isConfigurationSection(path) || !yaml.isSet(path)) {
            return;
        }
        double value = yaml.getDouble(path, 1200.0d);
        yaml.set(path, null);
        yaml.set(path + ".value", value);
        yaml.set(path + ".max-win", 16);
        try {
            yaml.save(file);
            plugin.getLogger().info("vanilla-items.yml: el tótem ahora limita el premio a 16 por apuesta.");
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "No se pudo guardar el límite del tótem en vanilla-items.yml", ex);
        }
    }

    private Material material(FileConfiguration yaml, String path, Material fallback) {
        String raw = yaml.getString(path);
        if (raw == null) {
            return fallback;
        }
        Material material = Material.matchMaterial(raw);
        if (material == null || !material.isItem()) {
            plugin.getLogger().warning("Material inválido en " + path + ": " + raw);
            return fallback;
        }
        return material;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public record RouletteSettings(
            int segments,
            double radius,
            double frameGap,
            double centerRaise,
            int maxRoulettes,
            double minDistance,
            boolean smallStands,
            boolean markerVisuals,
            Material success,
            Material fail,
            Material hub,
            Material post,
            Material base,
            Material needle,
            Material needleTip,
            Material frame,
            Material frameAccent,
            List<Material> idlePalette
    ) {
        public static RouletteSettings fallback() {
            return new RouletteSettings(
                    16, 1.35d, 0.42d, 1.9d, 40, 3.5d, false, false,
                    Material.LIME_CONCRETE, Material.RED_CONCRETE, Material.OAK_PLANKS,
                    Material.OAK_FENCE, Material.OAK_PLANKS, Material.WHITE_CONCRETE,
                    Material.WHITE_CONCRETE, Material.BLACK_CONCRETE, Material.BLACK_CONCRETE,
                    List.of(Material.RED_CONCRETE, Material.BLACK_CONCRETE)
            );
        }

        public static RouletteSettings load(FileConfiguration yaml, org.bukkit.plugin.Plugin plugin) {
            RouletteSettings defaults = fallback();
            ConfigurationSection section = yaml.getConfigurationSection("roulette");
            if (section == null) {
                return defaults;
            }
            Logger logger = plugin.getLogger();
            List<Material> palette = new ArrayList<>();
            for (String raw : section.getStringList("idle-palette")) {
                Material material = Material.matchMaterial(raw);
                if (material != null && material.isItem()) {
                    palette.add(material);
                } else {
                    logger.warning("Color de ruleta inválido: " + raw);
                }
            }
            if (palette.isEmpty()) {
                palette.addAll(defaults.idlePalette);
            }
            return new RouletteSettings(
                    Math.max(8, Math.min(32, section.getInt("segments", defaults.segments))),
                    Math.max(0.8d, Math.min(3.0d, section.getDouble("radius", defaults.radius))),
                    Math.max(0.2d, Math.min(1.2d, section.getDouble("frame-gap", defaults.frameGap))),
                    Math.max(1.0d, Math.min(4.0d, section.getDouble("center-raise", defaults.centerRaise))),
                    Math.max(1, section.getInt("max-roulettes", defaults.maxRoulettes)),
                    Math.max(0.0d, section.getDouble("min-distance", defaults.minDistance)),
                    section.getBoolean("small-stands", defaults.smallStands),
                    section.getBoolean("marker-visuals", defaults.markerVisuals),
                    mat(section, "success-material", defaults.success, logger),
                    mat(section, "fail-material", defaults.fail, logger),
                    mat(section, "hub-material", defaults.hub, logger),
                    mat(section, "post-material", defaults.post, logger),
                    mat(section, "base-material", defaults.base, logger),
                    mat(section, "needle-material", defaults.needle, logger),
                    mat(section, "needle-tip-material", defaults.needleTip, logger),
                    mat(section, "frame-material", defaults.frame, logger),
                    mat(section, "frame-accent", defaults.frameAccent, logger),
                    List.copyOf(palette)
            );
        }

        private static Material mat(ConfigurationSection section, String path, Material fallback, Logger logger) {
            String raw = section.getString(path);
            if (raw == null) {
                return fallback;
            }
            Material material = Material.matchMaterial(raw);
            if (material == null || !material.isItem()) {
                logger.warning("Material inválido en roulette." + path + ": " + raw);
                return fallback;
            }
            return material;
        }
    }
}
