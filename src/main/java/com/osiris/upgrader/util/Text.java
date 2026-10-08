package com.osiris.upgrader.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Text {
    private static final Pattern PLAIN_HEX = Pattern.compile("(?<!&)#([A-Fa-f0-9]{6})");
    private static final Pattern MINI_TAG = Pattern.compile("</?[a-zA-Z_#][^>]*>");
    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern LEGACY_HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern LEGACY_X = Pattern.compile("&x((?:&[A-Fa-f0-9]){6})", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEGACY_CODE = Pattern.compile("&([0-9a-fk-orA-FK-OR])");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private static final MiniMessage MINI = MiniMessage.builder().strict(false).build();

    private Text() {
    }

    public static String legacy(Component component) {
        if (component == null) {
            return null;
        }
        String value = LEGACY.serialize(component);
        return value == null || value.isBlank() ? null : value;
    }

    public static Component component(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        if (MINI_TAG.matcher(input).find()) {
            try {
                return MINI.deserialize(legacyOutsideTags(input));
            } catch (Exception ignored) {
                return LEGACY.deserialize(normalizeColors(input));
            }
        }
        return LEGACY.deserialize(normalizeColors(input));
    }

    public static Component itemText(String input) {
        return Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(component(input));
    }

    public static Component itemTextWithToken(String template, Map<String, String> placeholders, String token, Component insertion) {
        String applied = apply(template, placeholders);
        String needle = "%" + token + "%";
        int index = applied.indexOf(needle);
        Component root = Component.empty().decoration(TextDecoration.ITALIC, false);
        Component piece = insertion == null
                ? Component.empty()
                : insertion.decoration(TextDecoration.ITALIC, false);
        if (index < 0) {
            return root.append(component(applied));
        }
        if (index > 0) {
            root = root.append(component(applied.substring(0, index)));
        }
        root = root.append(piece);
        if (index + needle.length() < applied.length()) {
            root = root.append(component(applied.substring(index + needle.length())));
        }
        return root;
    }

    public static Component withToken(String template, String token, Component insertion) {
        if (template == null || template.isBlank()) {
            template = "&6%reward_name%";
        }
        String needle = "%" + token + "%";
        int index = template.indexOf(needle);
        Component root = Component.empty().decoration(TextDecoration.ITALIC, false);
        if (index < 0) {
            return root.append(component(template));
        }
        Component prefix = index == 0 ? Component.empty() : component(template.substring(0, index));
        Component piece = insertion == null ? Component.empty() : insertion;
        if (!hasColor(piece)) {
            piece = piece.style(trailingStyle(prefix));
        }
        piece = piece.decoration(TextDecoration.ITALIC, false);
        if (index > 0) {
            root = root.append(prefix);
        }
        root = root.append(piece);
        int after = index + needle.length();
        if (after < template.length()) {
            root = root.append(component(template.substring(after)));
        }
        return root;
    }

    private static boolean hasColor(Component component) {
        if (component == null) {
            return false;
        }
        if (component.color() != null) {
            return true;
        }
        for (Component child : component.children()) {
            if (hasColor(child)) {
                return true;
            }
        }
        return false;
    }

    private static Style trailingStyle(Component component) {
        Style style = component.style();
        var children = component.children();
        if (!children.isEmpty()) {
            Style nested = trailingStyle(children.get(children.size() - 1));
            if (!nested.isEmpty()) {
                return nested;
            }
        }
        return style;
    }

    private static String legacyOutsideTags(String input) {
        Matcher tags = TAG.matcher(input);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (tags.find()) {
            out.append(convertLegacyFragment(input.substring(last, tags.start())));
            out.append(tags.group());
            last = tags.end();
        }
        out.append(convertLegacyFragment(input.substring(last)));
        return out.toString();
    }

    private static String convertLegacyFragment(String input) {
        if (input.isEmpty()) {
            return input;
        }
        String value = normalizeColors(input);
        value = LEGACY_HEX.matcher(value).replaceAll("<#$1>");
        Matcher repeated = LEGACY_X.matcher(value);
        StringBuilder hexed = new StringBuilder();
        while (repeated.find()) {
            repeated.appendReplacement(hexed, "<#" + repeated.group(1).replace("&", "") + ">");
        }
        repeated.appendTail(hexed);
        value = hexed.toString();
        Matcher codes = LEGACY_CODE.matcher(value);
        StringBuilder out = new StringBuilder();
        while (codes.find()) {
            codes.appendReplacement(out, Matcher.quoteReplacement(miniFor(codes.group(1))));
        }
        codes.appendTail(out);
        return out.toString();
    }

    private static String miniFor(String code) {
        return switch (code.toLowerCase(Locale.ROOT)) {
            case "0" -> "<black>";
            case "1" -> "<dark_blue>";
            case "2" -> "<dark_green>";
            case "3" -> "<dark_aqua>";
            case "4" -> "<dark_red>";
            case "5" -> "<dark_purple>";
            case "6" -> "<gold>";
            case "7" -> "<gray>";
            case "8" -> "<dark_gray>";
            case "9" -> "<blue>";
            case "a" -> "<green>";
            case "b" -> "<aqua>";
            case "c" -> "<red>";
            case "d" -> "<light_purple>";
            case "e" -> "<yellow>";
            case "f" -> "<white>";
            case "k" -> "<obfuscated>";
            case "l" -> "<bold>";
            case "m" -> "<strikethrough>";
            case "n" -> "<underlined>";
            case "o" -> "<italic>";
            case "r" -> "<reset>";
            default -> "";
        };
    }

    private static String normalizeColors(String input) {
        String legacy = input.replace('§', '&');
        Matcher matcher = PLAIN_HEX.matcher(legacy);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, "&#" + matcher.group(1));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    public static String prizeQuantity(String template) {
        if (template == null) {
            return "";
        }
        return template.replace("%win_amount%x", "x%win_amount%").replace("%amount%x", "x%amount%");
    }

    public static Component itemTitle(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return Component.empty();
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName()) {
                Component custom = meta.displayName();
                if (readable(custom)) {
                    return custom;
                }
            }
            if (meta.hasItemName()) {
                Component named = meta.itemName();
                if (readable(named)) {
                    return named;
                }
            }
        }
        return Component.translatable(stack.getType().translationKey());
    }

    public static Component withCount(Component title, int count) {
        Component name = guiName(title);
        Component suffix = Component.text(" x" + Math.max(0, count))
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false);
        return name.append(suffix);
    }

    public static List<Component> itemLines(List<String> templates, Map<String, String> strings, Map<String, Component> insertions) {
        List<Component> lines = new ArrayList<>();
        if (templates == null) {
            return lines;
        }
        for (String template : templates) {
            lines.add(itemLine(template, strings, insertions));
        }
        return lines;
    }

    public static Component itemLine(String template, Map<String, String> strings, Map<String, Component> insertions) {
        String applied = applyExcept(prizeQuantity(template), strings, insertions == null ? Set.of() : insertions.keySet());
        Component root = Component.empty().decoration(TextDecoration.ITALIC, false);
        while (insertions != null && !insertions.isEmpty()) {
            int best = -1;
            String token = null;
            for (String candidate : insertions.keySet()) {
                int index = applied.indexOf("%" + candidate + "%");
                if (index >= 0 && (best < 0 || index < best)) {
                    best = index;
                    token = candidate;
                }
            }
            if (token == null) {
                break;
            }
            if (best > 0) {
                root = root.append(component(applied.substring(0, best)));
            }
            Component piece = insertions.get(token);
            if (piece != null) {
                root = root.append(guiName(piece));
            }
            applied = applied.substring(best + token.length() + 2);
        }
        if (!applied.isEmpty()) {
            root = root.append(component(applied));
        }
        return root;
    }

    public static String chance(double value, boolean precise) {
        if (precise) {
            return decimal(value);
        }
        return Long.toString(Math.round(value));
    }

    private static Component guiName(Component component) {
        Component clean = (component == null ? Component.empty() : component).decoration(TextDecoration.ITALIC, false);
        if (clean.color() == null) {
            return clean.color(NamedTextColor.WHITE);
        }
        return clean;
    }

    private static boolean readable(Component component) {
        if (component == null) {
            return false;
        }
        return !PlainTextComponentSerializer.plainText().serialize(component).trim().isEmpty();
    }

    private static String applyExcept(String template, Map<String, String> placeholders, Set<String> skip) {
        if (template == null) {
            return "";
        }
        String result = template;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                if (skip.contains(entry.getKey())) {
                    continue;
                }
                String value = entry.getValue() == null ? "" : entry.getValue();
                result = result.replace("%" + entry.getKey() + "%", value);
            }
        }
        return result;
    }

    public static String apply(String template, Map<String, String> placeholders) {
        if (template == null) {
            return "";
        }
        String result = template;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                String value = entry.getValue() == null ? "" : entry.getValue();
                result = result.replace("%" + entry.getKey() + "%", value);
            }
        }
        return result;
    }

    public static String decimal(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    public static String compact(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.001d) {
            return String.format(Locale.US, "%.0f", value);
        }
        return decimal(value);
    }

    public static String plainName(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            Component display = meta.displayName();
            if (display != null) {
                String plain = PlainTextComponentSerializer.plainText().serialize(display).trim();
                if (!plain.isEmpty()) {
                    return plain;
                }
            }
        }
        return prettify(stack.getType().name());
    }

    public static String prettify(String materialName) {
        String[] parts = materialName.toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }
}
