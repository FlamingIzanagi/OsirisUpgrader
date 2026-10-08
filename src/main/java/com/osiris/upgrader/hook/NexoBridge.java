package com.osiris.upgrader.hook;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Logger;

public final class NexoBridge {
    private static final String[] CANDIDATES = {
            "dev.lone.nexo.API.NexoItems",
            "com.nexomc.nexo.api.NexoItems"
    };

    private boolean hooked;
    private Method idFromItem;
    private Method itemFromId;
    private Method exists;
    private String hookedClass = "";
    private Logger logger;
    private boolean reportedFailure;

    public void tryHook(Logger logger) {
        hooked = false;
        idFromItem = null;
        itemFromId = null;
        exists = null;
        hookedClass = "";
        this.logger = logger;
        if (Bukkit.getPluginManager().getPlugin("Nexo") == null) {
            logger.info("Nexo no está instalado. Los ítems custom quedan desactivados.");
            return;
        }
        for (String className : CANDIDATES) {
            try {
                Class<?> type = Class.forName(className);
                Method fromItem = type.getMethod("idFromItem", ItemStack.class);
                Method fromId = type.getMethod("itemFromId", String.class);
                Method existsMethod = null;
                try {
                    existsMethod = type.getMethod("exists", String.class);
                } catch (NoSuchMethodException ignored) {
                    existsMethod = null;
                }
                this.idFromItem = fromItem;
                this.itemFromId = fromId;
                this.exists = existsMethod;
                this.hookedClass = className;
                this.hooked = true;
                logger.info("Integración Nexo activa (" + className + ").");
                return;
            } catch (Throwable ex) {
                logger.fine("No se pudo enganchar " + className + ": " + ex.getMessage());
            }
        }
        logger.warning("Nexo está instalado, pero no se encontró NexoItems. Revisa la versión.");
    }

    public boolean isHooked() {
        return hooked;
    }

    public String hookedClass() {
        return hookedClass;
    }

    public String idFromItem(ItemStack stack) {
        if (!hooked || stack == null || stack.getType().isAir()) {
            return null;
        }
        try {
            Object result = idFromItem.invoke(null, stack);
            if (result instanceof String id && !id.isBlank()) {
                return id;
            }
        } catch (Throwable ex) {
            report(ex);
            return null;
        }
        return null;
    }

    public ItemStack createItem(String rawId) {
        if (!hooked || rawId == null || rawId.isBlank()) {
            return null;
        }
        String id = strip(rawId);
        try {
            if (exists != null && !Boolean.TRUE.equals(exists.invoke(null, id))) {
                return null;
            }
            Object built = itemFromId.invoke(null, id);
            ItemStack stack = unwrap(built);
            return stack == null ? null : stack.clone();
        } catch (Throwable ex) {
            report(ex);
            return null;
        }
    }

    private void report(Throwable ex) {
        if (reportedFailure || logger == null) {
            return;
        }
        reportedFailure = true;
        logger.warning("Nexo no pudo leer o crear un ítem (" + ex.getClass().getSimpleName()
                + "). Los premios guardados se usan con su copia, sin reconstruirlos.");
    }

    public static String strip(String raw) {
        String id = raw == null ? "" : raw.trim();
        if (id.regionMatches(true, 0, "nexo:", 0, 5)) {
            return id.substring(5);
        }
        return id;
    }

    public static String normalize(String raw) {
        return strip(raw).toLowerCase(java.util.Locale.ROOT);
    }

    private ItemStack unwrap(Object built) {
        if (built == null) {
            return null;
        }
        if (built instanceof ItemStack stack) {
            return stack;
        }
        for (String name : List.of("build", "get", "getItem")) {
            try {
                Method method = built.getClass().getMethod(name);
                Object value = method.invoke(built);
                if (value instanceof ItemStack stack) {
                    return stack;
                }
            } catch (NoSuchMethodException ignored) {
                // El ItemBuilder de esta versión usa otro método.
            } catch (Throwable ex) {
                report(ex);
                return null;
            }
        }
        return null;
    }
}
