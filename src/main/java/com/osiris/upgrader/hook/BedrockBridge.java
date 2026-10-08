package com.osiris.upgrader.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

public final class BedrockBridge {
    private boolean hooked;
    private Object api;
    private Method isBedrock;
    private Logger logger;
    private boolean reportedFailure;

    public void tryHook(Logger logger) {
        hooked = false;
        api = null;
        isBedrock = null;
        reportedFailure = false;
        this.logger = logger;
        if (hook("org.geysermc.floodgate.api.FloodgateApi", "getInstance", "isFloodgatePlayer", "Floodgate")) {
            return;
        }
        if (hook("org.geysermc.geyser.api.GeyserApi", "api", "isBedrockPlayer", "Geyser")) {
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("floodgate") != null
                || Bukkit.getPluginManager().getPlugin("Geyser-Spigot") != null) {
            logger.warning("Hay un puente de Bedrock, pero no se pudo enganchar. La ruleta de Java sigue igual.");
            return;
        }
        logger.info("Sin Floodgate ni Geyser. La ruleta solo se muestra a Java.");
    }

    public boolean isHooked() {
        return hooked;
    }

    public boolean isBedrock(Player player) {
        if (!hooked || player == null || isBedrock == null || api == null) {
            return false;
        }
        try {
            Object result = isBedrock.invoke(api, player.getUniqueId());
            return result instanceof Boolean bedrock && bedrock;
        } catch (Throwable ex) {
            if (!reportedFailure) {
                reportedFailure = true;
                logger.warning("No se pudo saber si " + player.getName() + " entra desde Bedrock: " + ex.getMessage());
            }
            return false;
        }
    }

    private boolean hook(String className, String factory, String check, String label) {
        try {
            Class<?> type = Class.forName(className);
            Method opener = type.getMethod(factory);
            Object instance = opener.invoke(null);
            Method checker = type.getMethod(check, UUID.class);
            if (instance == null) {
                return false;
            }
            this.api = instance;
            this.isBedrock = checker;
            this.hooked = true;
            logger.info(label + " detectado. Los jugadores de Bedrock también verán la ruleta.");
            return true;
        } catch (Throwable ex) {
            return false;
        }
    }
}
