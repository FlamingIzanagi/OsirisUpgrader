package com.osiris.upgrader.roulette;

import com.osiris.upgrader.OsirisUpgraderPlugin;
import com.osiris.upgrader.gui.Evaluation;
import com.osiris.upgrader.gui.UpgraderHolder;
import com.osiris.upgrader.util.Permissions;
import com.osiris.upgrader.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class SpinAnimator {
    private final OsirisUpgraderPlugin plugin;
    private final Map<UUID, BukkitTask> tasks = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playersBySpin = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> spinnerByRoulette = new ConcurrentHashMap<>();

    public SpinAnimator(OsirisUpgraderPlugin plugin) {
        this.plugin = plugin;
    }

    private String rewardName(ItemStack reward, com.osiris.upgrader.item.RewardEntry entry) {
        org.bukkit.inventory.meta.ItemMeta meta = reward.getItemMeta();
        if (meta != null && (meta.hasDisplayName() || meta.hasItemName())) {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(Text.itemTitle(reward))
                    .trim();
            if (!plain.isEmpty()) {
                return plain;
            }
        }
        return plugin.rewards().nameOf(entry);
    }

    private String loggedName(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return "desconocida";
        }
        org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
        if (meta != null && (meta.hasDisplayName() || meta.hasItemName())) {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(Text.itemTitle(stack))
                    .trim();
            if (!plain.isEmpty()) {
                return plain;
            }
        }
        return Text.prettify(stack.getType().name());
    }

    public boolean isPlayerSpinning(UUID playerId) {
        return playersBySpin.containsValue(playerId);
    }

    public boolean isActive(UUID spinId) {
        return tasks.containsKey(spinId);
    }

    public String spinnerName(UUID rouletteId) {
        if (rouletteId == null) {
            return null;
        }
        UUID spinner = spinnerByRoulette.get(rouletteId);
        if (spinner == null) {
            return null;
        }
        Player online = plugin.getServer().getPlayer(spinner);
        if (online != null) {
            return online.getName();
        }
        String name = plugin.getServer().getOfflinePlayer(spinner).getName();
        return name == null || name.isBlank() ? "otro jugador" : name;
    }

    public void start(Player player, UpgraderHolder holder) {
        if (holder.isSpinning() || isPlayerSpinning(player.getUniqueId())) {
            plugin.messages().send(player, "already-spinning");
            return;
        }
        if (holder.target() != null && plugin.rewards().isLimited(holder.target())) {
            holder.setTargetAmount(1);
            holder.setMultiplier(1);
        }
        Evaluation evaluation = plugin.upgraderGui().evaluate(holder);
        switch (evaluation.status()) {
            case NO_ORIGIN -> {
                plugin.messages().send(player, "no-origin");
                return;
            }
            case NO_TARGET -> {
                plugin.messages().send(player, "no-target");
                return;
            }
            case NO_VALUE -> {
                plugin.messages().send(player, "no-value");
                return;
            }
            case OK -> {
                // La apuesta y el premio son válidos.
            }
        }
        if (!evaluation.ok()) {
            plugin.messages().send(player, "no-value");
            return;
        }
        ItemStack sample = holder.origin().clone();
        int needed = evaluation.betAmount();
        int available = plugin.items().count(player, holder.inventory(), holder.layout().origin, sample);
        if (available < needed) {
            plugin.messages().send(player, "not-enough-items", Map.of(
                    "needed", Integer.toString(needed),
                    "multiplier", "x" + holder.multiplier()
            ));
            return;
        }
        ItemStack reward = plugin.rewards().createTemplate(holder.target());
        if (reward == null) {
            plugin.messages().send(player, "nexo-missing");
            return;
        }
        Roulette roulette = holder.rouletteId() == null ? null : plugin.roulettes().get(holder.rouletteId());
        if (roulette != null && roulette.spinning()) {
            String spinner = spinnerName(roulette.id());
            plugin.messages().send(player, "spin-wait", Map.of("player", spinner == null ? "otro jugador" : spinner));
            return;
        }
        boolean bypassCooldown = Permissions.has(player, Permissions.BYPASS_COOLDOWN);
        if (!bypassCooldown && !plugin.cooldowns().available(player.getUniqueId())) {
            plugin.cooldowns().deny(player);
            return;
        }
        boolean limitedHold = plugin.rewards().isLimited(holder.target());
        if (limitedHold && !plugin.rewards().tryHold(holder.target())) {
            plugin.messages().send(player, "limited-gone");
            holder.setTarget(null);
            plugin.upgraderGui().paint(holder);
            return;
        }
        plugin.items().take(player, holder.inventory(), holder.layout().origin, sample, needed);
        if (!bypassCooldown) {
            plugin.cooldowns().consume(player.getUniqueId());
        }
        holder.setSpinning(true);
        boolean win = ThreadLocalRandom.current().nextDouble(100.0d) < evaluation.chance();
        UUID spinId = UUID.randomUUID();
        plugin.pending().remember(new PendingSpins.PendingSpin(
                spinId,
                player.getUniqueId(),
                win,
                evaluation.chance(),
                evaluation.targetAmount(),
                rewardName(reward, holder.target()),
                reward,
                loggedName(sample),
                needed,
                Math.max(1, holder.multiplier()),
                holder.target().key(),
                limitedHold
        ));
        playersBySpin.put(spinId, player.getUniqueId());
        if (roulette != null) {
            roulette.setSpinning(true);
            spinnerByRoulette.put(roulette.id(), player.getUniqueId());
            roulette.bumpDisplay();
            plugin.factory().paintChance(roulette, evaluation.chance());
            ItemStack icon = reward.clone();
            icon.setAmount(1);
            String template = Text.apply(plugin.messages().raw("messages.reward-label"), Map.of(
                    "amount", Integer.toString(evaluation.targetAmount())
            ));
            plugin.factory().showReward(
                    roulette,
                    evaluation.chance(),
                    icon,
                    Text.withToken(template, "reward_name", Text.itemTitle(icon))
            );
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && holder.isSpinning()) {
                    player.closeInventory();
                }
            });
            plugin.upgraderGui().notifySpinStarted(roulette.id(), player.getName());
        }
        double landing = WheelMath.landingAngle(win, evaluation.chance(), ThreadLocalRandom.current());
        double sweep = plugin.configs().extraRotations() * Math.PI * 2.0d + WheelMath.positive(landing);
        int duration = plugin.configs().animationTicks();
        Roulette spinningWheel = roulette;
        BukkitRunnable runnable = new BukkitRunnable() {
            private int tick;
            private boolean finished;

            @Override
            public void run() {
                if (finished) {
                    return;
                }
                if (tick >= duration) {
                    if (spinningWheel != null) {
                        plugin.factory().moveNeedle(spinningWheel, sweep);
                    }
                    finish();
                    return;
                }
                double progress = tick / (double) duration;
                double eased = 1.0d - Math.pow(1.0d - progress, 3.0d);
                double angle = sweep * eased;
                if (spinningWheel != null) {
                    plugin.factory().moveNeedle(spinningWheel, angle);
                }
                if (holder.isOpenFor(player)) {
                    plugin.upgraderGui().paintSpinFrame(holder, evaluation.chance(), angle);
                }
                if (tick % plugin.configs().soundInterval() == 0) {
                    plugin.sounds().play(player, "spin-tick", (float) (0.6d + (1.0d - progress)));
                }
                if (player.isOnline()) {
                    player.sendActionBar(Text.component(plugin.messages().format(
                            "messages.actionbar-spinning",
                            Map.of("chance", plugin.upgraderGui().chanceText(player, evaluation.chance()))
                    )));
                }
                tick++;
            }

            private void finish() {
                if (finished) {
                    return;
                }
                finished = true;
                cancel();
                release(spinId, player.getUniqueId(), spinningWheel);
                holder.setSpinning(false);
                if (spinningWheel != null) {
                    plugin.upgraderGui().notifySpinEnded(spinningWheel.id());
                }
                plugin.sessions().release(holder);
                plugin.pending().complete(spinId, player);
                if (holder.isOpenFor(player)) {
                    plugin.upgraderGui().paint(holder);
                }
                if (spinningWheel != null) {
                    int epoch = spinningWheel.displayEpoch();
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (!plugin.isEnabled() || plugin.roulettes().get(spinningWheel.id()) == null) {
                            return;
                        }
                        if (spinningWheel.spinning() || spinningWheel.displayEpoch() != epoch) {
                            return;
                        }
                        if (spinningWheel.structureVersion() < Roulette.CURRENT_STRUCTURE) {
                            plugin.factory().rebuild(spinningWheel);
                            plugin.roulettes().save();
                            return;
                        }
                        plugin.factory().paintIdle(spinningWheel);
                        plugin.factory().parkNeedle(spinningWheel);
                    }, 60L);
                }
            }
        };
        tasks.put(spinId, runnable.runTaskTimer(plugin, 0L, 1L));
    }

    public void finishAll() {
        for (BukkitTask task : tasks.values()) {
            task.cancel();
        }
        tasks.clear();
        playersBySpin.clear();
        spinnerByRoulette.clear();
        plugin.roulettes().clearSpinningFlags();
        plugin.pending().deliverOfflineReady(id -> false);
    }

    private void release(UUID spinId, UUID playerId, Roulette roulette) {
        tasks.remove(spinId);
        playersBySpin.remove(spinId, playerId);
        if (roulette != null) {
            roulette.setSpinning(false);
            spinnerByRoulette.remove(roulette.id());
        }
    }
}
