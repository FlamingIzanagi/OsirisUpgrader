package com.osiris.upgrader.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Inserta claves que faltan en un YAML ya existente.
 * No reescribe el archivo, así que los comentarios y los valores puestos se quedan.
 */
final class YamlMerger {
    private YamlMerger() {
    }

    static String insert(String text, List<Placement> placements) {
        String normalized = text == null ? "" : text.replace("\r\n", "\n");
        List<String> lines = split(normalized);
        Map<Integer, StringBuilder> blocks = new LinkedHashMap<>();
        for (Placement placement : placements) {
            int line = placement.parentPath().isEmpty()
                    ? lines.size()
                    : insertLine(lines, placement.parentPath());
            if (line < 0) {
                continue;
            }
            blocks.computeIfAbsent(line, ignored -> new StringBuilder()).append(placement.fragment());
        }
        if (blocks.isEmpty()) {
            return normalized;
        }
        StringBuilder out = new StringBuilder();
        for (int index = 0; index <= lines.size(); index++) {
            StringBuilder block = blocks.get(index);
            if (block != null) {
                out.append(block);
            }
            if (index < lines.size()) {
                out.append(lines.get(index)).append('\n');
            }
        }
        return out.toString();
    }

    static boolean canPlace(String text, String parentPath) {
        if (parentPath == null || parentPath.isEmpty()) {
            return true;
        }
        return insertLine(split(text == null ? "" : text.replace("\r\n", "\n")), parentPath) >= 0;
    }

    static String fragment(String key, Object value, int indent) {
        StringBuilder out = new StringBuilder();
        write(out, " ".repeat(Math.max(0, indent)), key, value);
        return out.toString();
    }

    static int indentOfPath(String text, String path) {
        List<String> lines = split(text == null ? "" : text.replace("\r\n", "\n"));
        int line = findKeyLine(lines, path);
        if (line < 0) {
            return -1;
        }
        return indentOf(lines.get(line));
    }

    private static void write(StringBuilder out, String pad, String key, Object value) {
        if (value instanceof Map<?, ?> map) {
            out.append(pad).append(key).append(":\n");
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                write(out, pad + "  ", String.valueOf(entry.getKey()), entry.getValue());
            }
            return;
        }
        if (value instanceof List<?> list) {
            out.append(pad).append(key).append(":\n");
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    out.append(pad).append("  -");
                    boolean first = true;
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        if (first) {
                            out.append('\n');
                            first = false;
                        }
                        write(out, pad + "    ", String.valueOf(entry.getKey()), entry.getValue());
                    }
                    if (first) {
                        out.append('\n');
                    }
                } else {
                    out.append(pad).append("  - ").append(scalar(item)).append('\n');
                }
            }
            return;
        }
        out.append(pad).append(key).append(": ").append(scalar(value)).append('\n');
    }

    private static String scalar(Object value) {
        if (value == null) {
            return "''";
        }
        if (value instanceof Boolean || value instanceof Integer || value instanceof Long) {
            return value.toString();
        }
        if (value instanceof Number number) {
            double decimal = number.doubleValue();
            if (!Double.isInfinite(decimal) && decimal == Math.rint(decimal)) {
                return Long.toString(number.longValue());
            }
            return Double.toString(decimal);
        }
        String text = String.valueOf(value);
        return "'" + text.replace("'", "''") + "'";
    }

    private static int insertLine(List<String> lines, String parentPath) {
        int keyLine = findKeyLine(lines, parentPath);
        if (keyLine < 0) {
            return -1;
        }
        int parentIndent = indentOf(lines.get(keyLine));
        int boundary = lines.size();
        for (int index = keyLine + 1; index < lines.size(); index++) {
            String line = lines.get(index);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (indentOf(line) <= parentIndent) {
                boundary = index;
                break;
            }
        }
        int insert = boundary;
        while (insert > keyLine + 1) {
            String previous = lines.get(insert - 1).trim();
            if (previous.isEmpty() || previous.startsWith("#")) {
                insert--;
                continue;
            }
            break;
        }
        return insert;
    }

    private static int findKeyLine(List<String> lines, String path) {
        String[] parts = path.split("\\.");
        int searchFrom = 0;
        int expectedIndent = 0;
        int found = -1;
        for (String part : parts) {
            found = -1;
            for (int index = searchFrom; index < lines.size(); index++) {
                String line = lines.get(index);
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int indent = indentOf(line);
                if (indent < expectedIndent) {
                    return -1;
                }
                if (indent != expectedIndent || !trimmed.contains(":")) {
                    continue;
                }
                if (trimmed.startsWith("-")) {
                    continue;
                }
                if (keyName(trimmed).equals(part)) {
                    found = index;
                    searchFrom = index + 1;
                    expectedIndent = indent + 2;
                    break;
                }
            }
            if (found < 0) {
                return -1;
            }
        }
        return found;
    }

    private static String keyName(String trimmed) {
        int colon = trimmed.indexOf(':');
        String name = trimmed.substring(0, colon).trim();
        if (name.length() >= 2 && ((name.startsWith("\"") && name.endsWith("\"")) || (name.startsWith("'") && name.endsWith("'")))) {
            name = name.substring(1, name.length() - 1);
        }
        return name;
    }

    private static int indentOf(String line) {
        int index = 0;
        while (index < line.length() && line.charAt(index) == ' ') {
            index++;
        }
        return index;
    }

    private static List<String> split(String text) {
        String[] raw = text.split("\n", -1);
        List<String> lines = new ArrayList<>();
        for (String line : raw) {
            lines.add(line);
        }
        if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    record Placement(String parentPath, String fragment) {
    }
}
