package com.osiris.upgrader.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class MenuLayout {
    public final int size;
    public final int origin;
    public final int target;
    public final int chance;
    public final int spin;
    public final int guide;
    public final int betInfo;
    public final int plus1;
    public final int plus15;
    public final int plus32;
    public final int x2;
    public final int x4;
    public final int x8;
    public final List<Integer> wheelSlots;
    public final Material fillerMaterial;
    public final int fillerModelData;
    public final String fillerName;
    public final List<ArtPiece> art;
    public final Icons icons;
    public final Browser browser;
    public final String title;

    public MenuLayout(
            int size,
            int origin,
            int target,
            int chance,
            int spin,
            int guide,
            int betInfo,
            int plus1,
            int plus15,
            int plus32,
            int x2,
            int x4,
            int x8,
            List<Integer> wheelSlots,
            Material fillerMaterial,
            int fillerModelData,
            String fillerName,
            List<ArtPiece> art,
            Icons icons,
            Browser browser,
            String title
    ) {
        this.size = size;
        this.origin = origin;
        this.target = target;
        this.chance = chance;
        this.spin = spin;
        this.guide = guide;
        this.betInfo = betInfo;
        this.plus1 = plus1;
        this.plus15 = plus15;
        this.plus32 = plus32;
        this.x2 = x2;
        this.x4 = x4;
        this.x8 = x8;
        this.wheelSlots = List.copyOf(wheelSlots);
        this.fillerMaterial = fillerMaterial;
        this.fillerModelData = fillerModelData;
        this.fillerName = fillerName;
        this.art = List.copyOf(art);
        this.icons = icons;
        this.browser = browser;
        this.title = title == null || title.isBlank() ? "&8Ruleta" : title;
    }

    public static MenuLayout load(FileConfiguration yaml, Logger logger) {
        MenuLayout defaults = fallback();
        ConfigurationSection upgrader = yaml.getConfigurationSection("upgrader");
        if (upgrader == null) {
            logger.warning("menus.yml no tiene la sección upgrader. Se usan los slots por defecto.");
            return defaults;
        }
        int size = normalizeSize(upgrader.getInt("size", defaults.size));
        ConfigurationSection slots = upgrader.getConfigurationSection("slots");
        int origin = slot(slots, "origin", defaults.origin, size, logger);
        int target = slot(slots, "target", defaults.target, size, logger);
        int chance = slot(slots, "chance", defaults.chance, size, logger);
        int spin = slot(slots, "spin", defaults.spin, size, logger);
        int guide = slot(slots, "guide", defaults.guide, size, logger);
        int betInfo = slot(slots, "bet-info", defaults.betInfo, size, logger);
        int plus1 = slot(slots, "plus-1", defaults.plus1, size, logger);
        int plus15 = slot(slots, "plus-15", defaults.plus15, size, logger);
        int plus32 = slot(slots, "plus-32", defaults.plus32, size, logger);
        int x2 = slot(slots, "multiplier-x2", defaults.x2, size, logger);
        int x4 = slot(slots, "multiplier-x4", defaults.x4, size, logger);
        int x8 = slot(slots, "multiplier-x8", defaults.x8, size, logger);
        List<Integer> reserved = List.of(origin, target, chance, spin, guide, betInfo, plus1, plus15, plus32, x2, x4, x8);
        List<Integer> wheel = new ArrayList<>();
        for (int candidate : upgrader.getIntegerList("wheel-slots")) {
            if (candidate < 0 || candidate >= size || reserved.contains(candidate) || wheel.contains(candidate)) {
                logger.warning("Slot de ruleta ignorado por solaparse o salir del inventario: " + candidate);
                continue;
            }
            wheel.add(candidate);
        }
        if (wheel.isEmpty()) {
            wheel.addAll(defaults.wheelSlots);
        }
        ConfigurationSection filler = upgrader.getConfigurationSection("filler");
        Material fillerMaterial = material(filler, "material", defaults.fillerMaterial, logger);
        int fillerCmd = filler == null ? 0 : filler.getInt("custom-model-data", 0);
        String fillerName = filler == null ? " " : filler.getString("name", " ");
        Icons icons = Icons.load(upgrader.getConfigurationSection("icons"), defaults.icons, logger);
        List<ArtPiece> art = new ArrayList<>();
        for (Object raw : upgrader.getMapList("nexo-art")) {
            if (!(raw instanceof java.util.Map<?, ?> map)) {
                continue;
            }
            List<Integer> pieceSlots = new ArrayList<>();
            Object slotsRaw = map.get("slots");
            if (slotsRaw instanceof List<?> list) {
                for (Object value : list) {
                    if (value instanceof Number number) {
                        int index = number.intValue();
                        if (index >= 0 && index < size) {
                            pieceSlots.add(index);
                        }
                    }
                }
            }
            if (pieceSlots.isEmpty()) {
                continue;
            }
            Material material = Material.PAPER;
            Object materialRaw = map.get("material");
            if (materialRaw instanceof String text) {
                Material parsed = Material.matchMaterial(text);
                if (parsed != null && parsed.isItem()) {
                    material = parsed;
                }
            }
            int cmd = map.get("custom-model-data") instanceof Number number ? number.intValue() : 0;
            boolean increment = Boolean.TRUE.equals(map.get("increment-model-data"));
            String nexoId = map.get("nexo-id") instanceof String text ? text : "";
            String name = map.get("name") instanceof String text ? text : " ";
            art.add(new ArtPiece(pieceSlots, material, cmd, increment, nexoId, name));
        }
        Browser browser = Browser.load(yaml.getConfigurationSection("browser"), defaults.browser, logger);
        String title = upgrader.getString("title", defaults.title);
        return new MenuLayout(size, origin, target, chance, spin, guide, betInfo, plus1, plus15, plus32, x2, x4, x8,
                wheel, fillerMaterial, fillerCmd, fillerName, art, icons, browser, title);
    }

    public static MenuLayout fallback() {
        Icons icons = new Icons(
                IconSpec.of(Material.SUNFLOWER),
                IconSpec.of(Material.CLOCK),
                IconSpec.of(Material.SUNFLOWER),
                IconSpec.of(Material.GOLD_NUGGET),
                IconSpec.of(Material.GOLD_INGOT),
                IconSpec.of(Material.GOLD_BLOCK),
                IconSpec.of(Material.LAPIS_LAZULI),
                IconSpec.of(Material.LAPIS_LAZULI),
                IconSpec.of(Material.LAPIS_LAZULI),
                IconSpec.of(Material.ECHO_SHARD),
                IconSpec.of(Material.BOOK),
                IconSpec.of(Material.NAME_TAG),
                IconSpec.of(Material.LIME_STAINED_GLASS_PANE),
                IconSpec.of(Material.RED_STAINED_GLASS_PANE),
                IconSpec.of(Material.SPECTRAL_ARROW)
        );
        Browser browser = new Browser(
                54,
                45,
                53,
                49,
                4,
                List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43),
                Material.BLACK_STAINED_GLASS_PANE,
                Material.ARROW,
                Material.ARROW,
                Material.BARRIER,
                Material.BOOK,
                Material.BARRIER,
                "&8Elige el premio"
        );
        return new MenuLayout(
                54, 20, 24, -1, 22, 0, 19, 17, 26, 35, 39, 40, 41,
                List.of(13, 14, 23, 32, 31, 30, 21, 12),
                Material.BLACK_STAINED_GLASS_PANE,
                0,
                " ",
                List.of(),
                icons,
                browser,
                "&8Ruleta"
        );
    }

    private static int normalizeSize(int size) {
        if (size < 9 || size > 54 || size % 9 != 0) {
            return 54;
        }
        return size;
    }

    private static int slot(ConfigurationSection section, String path, int fallback, int size, Logger logger) {
        int value = section == null ? fallback : section.getInt(path, fallback);
        if (value < -1 || value >= size) {
            logger.warning("Slot inválido en menus.yml (" + path + "): " + value);
            return fallback < size ? fallback : -1;
        }
        return value;
    }

    private static Material material(ConfigurationSection section, String path, Material fallback, Logger logger) {
        if (section == null) {
            return fallback;
        }
        String raw = section.getString(path);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(raw);
        if (material == null || !material.isItem()) {
            logger.warning("Material inválido en menus.yml (" + path + "): " + raw);
            return fallback;
        }
        return material;
    }

    public record ArtPiece(List<Integer> slots, Material material, int customModelData, boolean increment, String nexoId, String name) {
        public ArtPiece {
            slots = List.copyOf(slots);
        }
    }

    public record IconSpec(Material material, int modelData, String nexoId) {
        public IconSpec {
            nexoId = nexoId == null ? "" : nexoId;
        }

        public static IconSpec of(Material material) {
            return new IconSpec(material, 0, "");
        }

        private static IconSpec parse(ConfigurationSection parent, String path, IconSpec fallback, Logger logger) {
            if (parent == null || !parent.contains(path)) {
                return fallback;
            }
            if (parent.isString(path)) {
                Material parsed = Material.matchMaterial(parent.getString(path, ""));
                if (parsed != null && parsed.isItem()) {
                    return new IconSpec(parsed, 0, "");
                }
                logger.warning("Material inválido en menus.yml (" + path + "): " + parent.getString(path));
                return fallback;
            }
            ConfigurationSection section = parent.getConfigurationSection(path);
            if (section == null) {
                return fallback;
            }
            Material parsed = MenuLayout.material(section, "material", fallback.material(), logger);
            int modelData = section.getInt("custom-model-data", 0);
            String nexoId = section.getString("nexo-id", "");
            return new IconSpec(parsed, modelData, nexoId == null ? "" : nexoId);
        }
    }

    public record Icons(
            IconSpec chance,
            IconSpec spinning,
            IconSpec spin,
            IconSpec plus1,
            IconSpec plus15,
            IconSpec plus32,
            IconSpec x2,
            IconSpec x4,
            IconSpec x8,
            IconSpec targetEmpty,
            IconSpec guide,
            IconSpec betInfo,
            IconSpec wheelSuccess,
            IconSpec wheelFail,
            IconSpec wheelNeedle
    ) {
        private static Icons load(ConfigurationSection section, Icons defaults, Logger logger) {
            if (section == null) {
                return defaults;
            }
            IconSpec sharedMultiplier = IconSpec.parse(section, "multiplier", defaults.x2, logger);
            return new Icons(
                    IconSpec.parse(section, "chance", defaults.chance, logger),
                    IconSpec.parse(section, "spinning", defaults.spinning, logger),
                    IconSpec.parse(section, "spin", defaults.spin, logger),
                    IconSpec.parse(section, "plus-1", defaults.plus1, logger),
                    IconSpec.parse(section, "plus-15", defaults.plus15, logger),
                    IconSpec.parse(section, "plus-32", defaults.plus32, logger),
                    IconSpec.parse(section, "multiplier-x2", sharedMultiplier, logger),
                    IconSpec.parse(section, "multiplier-x4", sharedMultiplier, logger),
                    IconSpec.parse(section, "multiplier-x8", sharedMultiplier, logger),
                    IconSpec.parse(section, "target-empty", defaults.targetEmpty, logger),
                    IconSpec.parse(section, "guide", defaults.guide, logger),
                    IconSpec.parse(section, "bet-info", defaults.betInfo, logger),
                    IconSpec.parse(section, "wheel-success", defaults.wheelSuccess, logger),
                    IconSpec.parse(section, "wheel-fail", defaults.wheelFail, logger),
                    IconSpec.parse(section, "wheel-needle", defaults.wheelNeedle, logger)
            );
        }
    }

    public record Browser(
            int size,
            int previous,
            int next,
            int back,
            int info,
            List<Integer> content,
            Material filler,
            Material nextIcon,
            Material previousIcon,
            Material backIcon,
            Material infoIcon,
            Material emptyIcon,
            String title
    ) {
        public Browser {
            content = List.copyOf(content);
        }

        private static Browser load(ConfigurationSection section, Browser defaults, Logger logger) {
            if (section == null) {
                return defaults;
            }
            int size = normalizeSize(section.getInt("size", defaults.size));
            List<Integer> content = new ArrayList<>();
            for (int slot : section.getIntegerList("content-slots")) {
                if (slot >= 0 && slot < size) {
                    content.add(slot);
                }
            }
            if (content.isEmpty()) {
                content.addAll(defaults.content);
            }
            ConfigurationSection filler = section.getConfigurationSection("filler");
            ConfigurationSection icons = section.getConfigurationSection("icons");
            return new Browser(
                    size,
                    bounded(section.getInt("previous-slot", defaults.previous), size, defaults.previous),
                    bounded(section.getInt("next-slot", defaults.next), size, defaults.next),
                    bounded(section.getInt("back-slot", defaults.back), size, defaults.back),
                    bounded(section.getInt("info-slot", defaults.info), size, defaults.info),
                    content,
                    material(filler, "material", defaults.filler, logger),
                    material(icons, "next", defaults.nextIcon, logger),
                    material(icons, "previous", defaults.previousIcon, logger),
                    material(icons, "back", defaults.backIcon, logger),
                    material(icons, "info", defaults.infoIcon, logger),
                    material(icons, "empty", defaults.emptyIcon, logger),
                    section.getString("title", defaults.title)
            );
        }

        private static int bounded(int value, int size, int fallback) {
            return value >= 0 && value < size ? value : fallback;
        }
    }
}
