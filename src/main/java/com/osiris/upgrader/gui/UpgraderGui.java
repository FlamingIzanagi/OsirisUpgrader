package com.osiris.upgrader.gui;

import com.osiris.upgrader.OsirisUpgraderPlugin;
import com.osiris.upgrader.config.MenuLayout;
import com.osiris.upgrader.item.RewardEntry;
import com.osiris.upgrader.roulette.ChanceCalculator;
import com.osiris.upgrader.roulette.SpinCooldowns;
import com.osiris.upgrader.roulette.WheelMath;
import com.osiris.upgrader.util.Permissions;
import com.osiris.upgrader.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class UpgraderGui {
    private final OsirisUpgraderPlugin plugin;
    private final Map<UUID, Long> debounce = new HashMap<>();

    public UpgraderGui(OsirisUpgraderPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, UUID rouletteId) {
        if (!Permissions.has(player, Permissions.USE)) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        if (plugin.animator().isPlayerSpinning(player.getUniqueId())) {
            plugin.messages().send(player, "already-spinning");
            return;
        }
        if (blocked(player, rouletteId)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = debounce.get(player.getUniqueId());
        if (last != null && now - last < 200L) {
            if (rouletteId == null) {
                return;
            }
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof UpgraderHolder session
                    && session.rouletteId() != null) {
                return;
            }
        }
        debounce.put(player.getUniqueId(), now);
        show(player, new UpgraderHolder(player.getUniqueId(), rouletteId));
    }

    public void reopen(Player player, UpgraderHolder session) {
        session.setSwitching(false);
        show(player, session);
    }

    public Evaluation evaluate(UpgraderHolder holder) {
        ItemStack origin = holder.origin();
        if (origin == null) {
            return Evaluation.missing(Evaluation.Status.NO_ORIGIN);
        }
        if (plugin.specialItems().isSpecial(origin)) {
            return Evaluation.missing(Evaluation.Status.NO_VALUE);
        }
        RewardEntry target = holder.target();
        if (target == null) {
            return Evaluation.missing(Evaluation.Status.NO_TARGET);
        }
        double unitBet = plugin.itemValues().unitValue(origin);
        double unitTarget = plugin.rewards().prizeValue(target);
        if (unitBet <= 0.0d || unitTarget <= 0.0d) {
            return Evaluation.missing(Evaluation.Status.NO_VALUE);
        }
        int betAmount = origin.getAmount();
        int winAmount = Math.max(1, holder.targetAmount()) * Math.max(1, holder.multiplier());
        double chance = ChanceCalculator.calculate(
                unitBet * betAmount,
                unitTarget * winAmount,
                plugin.configs().chanceFactor(),
                plugin.configs().minChance(),
                plugin.configs().maxChance()
        );
        return new Evaluation(Evaluation.Status.OK, chance, unitBet, unitTarget, betAmount, winAmount);
    }

    public void paint(UpgraderHolder holder) {
        Inventory inventory = holder.inventory();
        MenuLayout layout = holder.layout();
        if (inventory == null || layout == null) {
            return;
        }
        if (holder.target() != null && (plugin.rewards().byKey(holder.target().key()) == null
                || plugin.rewards().createTemplate(holder.target()) == null)) {
            holder.setTarget(null);
        }
        if (holder.target() != null && plugin.rewards().isLimited(holder.target())) {
            holder.setTargetAmount(1);
            holder.setMultiplier(1);
        }
        int winLimit = plugin.rewards().prizeLimit(holder.target());
        if (winLimit > 0) {
            int cap = Math.min(64, Math.max(1, winLimit / Math.max(1, holder.multiplier())));
            if (holder.targetAmount() > cap) {
                holder.setTargetAmount(cap);
            }
        }
        ItemStack origin = inventory.getItem(layout.origin);
        Evaluation evaluation = evaluate(holder);
        for (int slot = 0; slot < layout.size; slot++) {
            if (slot == layout.origin) {
                continue;
            }
            inventory.setItem(slot, GuiItems.filler(layout.fillerMaterial, layout.fillerModelData, layout.fillerName, true));
        }
        if (plugin.configs().nexoMenu()) {
            paintArt(inventory, layout);
        }
        Map<String, String> placeholders = placeholders(holder, evaluation, viewer(holder));
        paintWheel(inventory, layout, evaluation, placeholders, 0.0d, false);
        paintButtons(inventory, layout, holder, placeholders);
        paintTarget(inventory, layout, holder, placeholders);
        paintGuide(inventory, layout);
        paintBetInfo(inventory, layout, holder);
        inventory.setItem(layout.origin, origin);
    }

    public void paintSpinFrame(UpgraderHolder holder, double chance, double angle) {
        if (!holder.isSpinning() || holder.inventory() == null) {
            return;
        }
        MenuLayout layout = holder.layout();
        Map<String, String> placeholders = Map.of("chance", chanceText(viewer(holder), chance));
        paintWheel(holder.inventory(), layout, null, placeholders, angle, true);
        if (layout.spin >= 0) {
            holder.inventory().setItem(layout.spin, button(
                    layout.icons.spinning(),
                    plugin.messages().format("gui-texts.spinning-button.name", placeholders),
                    plugin.messages().formatList("gui-texts.spinning-button.lore", placeholders),
                    true,
                    1
            ));
        }
    }

    private boolean blocked(Player player, UUID rouletteId) {
        if (!Permissions.has(player, Permissions.BYPASS_COOLDOWN)) {
            long remaining = plugin.cooldowns().remainingMillis(player.getUniqueId());
            if (remaining > 0L) {
                plugin.messages().send(player, "spin-cooldown", Map.of("time", SpinCooldowns.formatRemaining(remaining)));
                return true;
            }
        }
        if (rouletteId == null) {
            UUID occupant = plugin.sessions().occupant(rouletteId);
            if (occupant != null && !occupant.equals(player.getUniqueId())) {
                plugin.messages().send(player, "roulette-in-use", Map.of("player", playerName(occupant)));
                return true;
            }
        }
        return false;
    }

    private static String playerName(UUID playerId) {
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) {
            return online.getName();
        }
        String name = Bukkit.getOfflinePlayer(playerId).getName();
        return name == null || name.isBlank() ? "otro jugador" : name;
    }

    private void show(Player player, UpgraderHolder session) {
        if (!plugin.sessions().claim(session)) {
            UUID occupant = plugin.sessions().occupant(session.rouletteId());
            plugin.messages().send(player, "roulette-in-use", Map.of(
                    "player", occupant == null ? "otro jugador" : playerName(occupant)
            ));
            return;
        }
        MenuLayout layout = plugin.configs().menu();
        String title = layout.title == null || layout.title.isBlank()
                ? plugin.messages().raw(plugin.configs().nexoMenu() ? "gui-texts.title-nexo" : "gui-texts.title-vanilla")
                : layout.title;
        Inventory inventory = Bukkit.createInventory(session, layout.size, Text.component(title));
        ItemStack carried = session.originSnapshot();
        session.setOriginSnapshot(null);
        session.attach(inventory, layout);
        if (carried != null) {
            inventory.setItem(layout.origin, carried);
        }
        paint(session);
        player.openInventory(inventory);
        if (session.rouletteId() != null) {
            String spinner = plugin.animator().spinnerName(session.rouletteId());
            if (spinner != null) {
                plugin.messages().send(player, "spin-wait", Map.of("player", spinner));
            }
        }
    }

    private void paintArt(Inventory inventory, MenuLayout layout) {
        for (MenuLayout.ArtPiece piece : layout.art) {
            List<Integer> slots = piece.slots();
            for (int index = 0; index < slots.size(); index++) {
                int slot = slots.get(index);
                if (slot == layout.origin) {
                    continue;
                }
                inventory.setItem(slot, artItem(piece, index));
            }
        }
    }

    private ItemStack artItem(MenuLayout.ArtPiece piece, int index) {
        if (piece.nexoId() != null && !piece.nexoId().isBlank()) {
            ItemStack nexoItem = plugin.nexo().createItem(piece.nexoId());
            if (nexoItem != null) {
                nexoItem.setAmount(1);
                nexoItem.editMeta(meta -> meta.displayName(Text.itemText(piece.name())));
                return nexoItem;
            }
        }
        int modelData = piece.customModelData();
        if (piece.increment() && modelData > 0) {
            modelData += index;
        }
        return GuiItems.filler(piece.material(), modelData, piece.name(), true);
    }

    private void paintWheel(Inventory inventory, MenuLayout layout, Evaluation evaluation, Map<String, String> placeholders, double angle, boolean spinning) {
        List<Integer> slots = layout.wheelSlots;
        if (slots.isEmpty()) {
            return;
        }
        double chance = evaluation != null && evaluation.ok() ? evaluation.chance() : 0.0d;
        if (spinning && placeholders.containsKey("chance")) {
            try {
                chance = Double.parseDouble(placeholders.get("chance"));
            } catch (NumberFormatException ignored) {
                chance = 0.0d;
            }
        }
        int count = slots.size();
        int needle = 0;
        if (spinning) {
            needle = (int) Math.round(WheelMath.positive(angle) / (Math.PI * 2.0d) * count) % count;
        }
        String needleName;
        List<String> needleLore;
        if (spinning) {
            needleName = plugin.messages().format("gui-texts.spinning-display.name", placeholders);
            needleLore = plugin.messages().formatList("gui-texts.spinning-display.lore", placeholders);
        } else if (evaluation != null && evaluation.ok()) {
            needleName = plugin.messages().format("gui-texts.chance-display.name", placeholders);
            needleLore = plugin.messages().formatList("gui-texts.chance-display.lore", placeholders);
        } else {
            needleName = plugin.messages().raw("gui-texts.empty-display.name");
            needleLore = plugin.messages().list("gui-texts.empty-display.lore");
        }
        for (int i = 0; i < count; i++) {
            double slotAngle = (Math.PI * 2.0d * i) / count;
            boolean success = chance > 0.0d && WheelMath.inSuccessZone(slotAngle, chance);
            if (i == needle) {
                inventory.setItem(slots.get(i), button(layout.icons.wheelNeedle(), needleName, needleLore, true, 1));
            } else if (success) {
                inventory.setItem(slots.get(i), button(
                        layout.icons.wheelSuccess(),
                        plugin.messages().raw("gui-texts.wheel-success.name"),
                        List.of(),
                        false,
                        1
                ));
            } else {
                inventory.setItem(slots.get(i), button(
                        layout.icons.wheelFail(),
                        plugin.messages().raw("gui-texts.wheel-fail.name"),
                        List.of(),
                        false,
                        1
                ));
            }
        }
    }

    private void paintButtons(Inventory inventory, MenuLayout layout, UpgraderHolder holder, Map<String, String> placeholders) {
        inventory.setItem(layout.plus1, amountButton(layout.icons.plus1(), "gui-texts.plus-1", placeholders, 1));
        inventory.setItem(layout.plus15, amountButton(layout.icons.plus15(), "gui-texts.plus-15", placeholders, 15));
        inventory.setItem(layout.plus32, amountButton(layout.icons.plus32(), "gui-texts.plus-32", placeholders, 32));
        inventory.setItem(layout.x2, multiplierButton(layout.icons.x2(), placeholders, 2, holder.multiplier() == 2));
        inventory.setItem(layout.x4, multiplierButton(layout.icons.x4(), placeholders, 4, holder.multiplier() == 4));
        inventory.setItem(layout.x8, multiplierButton(layout.icons.x8(), placeholders, 8, holder.multiplier() == 8));
        if (layout.spin >= 0) {
            boolean bet = holder.origin() != null;
            boolean prize = holder.target() != null;
            String spinner = holder.rouletteId() == null || holder.isSpinning()
                    ? null
                    : plugin.animator().spinnerName(holder.rouletteId());
            ItemStack icon = button(
                    layout.icons.spin(),
                    plugin.messages().format("gui-texts.spin-button.name", placeholders),
                    List.of(),
                    bet && prize && spinner == null,
                    1
            );
            icon.editMeta(meta -> meta.lore(spinLore(holder, placeholders, bet, prize, spinner)));
            inventory.setItem(layout.spin, icon);
        }
    }

    public void notifySpinStarted(UUID rouletteId, String spinnerName) {
        if (rouletteId == null || spinnerName == null) {
            return;
        }
        Map<String, String> tokens = Map.of("player", spinnerName);
        for (UpgraderHolder holder : plugin.sessions().viewing(rouletteId)) {
            Player player = Bukkit.getPlayer(holder.playerId());
            if (player == null || !player.isOnline() || holder.isSpinning()) {
                continue;
            }
            plugin.messages().send(player, "spin-wait", tokens);
            if (holder.isOpenFor(player)) {
                paint(holder);
            }
        }
    }

    public void notifySpinEnded(UUID rouletteId) {
        if (rouletteId == null) {
            return;
        }
        for (UpgraderHolder holder : plugin.sessions().viewing(rouletteId)) {
            Player player = Bukkit.getPlayer(holder.playerId());
            if (player != null && holder.isOpenFor(player) && !holder.isSpinning()) {
                paint(holder);
            }
        }
    }

    private List<net.kyori.adventure.text.Component> spinLore(UpgraderHolder holder, Map<String, String> placeholders, boolean bet, boolean prize, String spinner) {
        List<net.kyori.adventure.text.Component> lines;
        if (!bet && !prize) {
            lines = new ArrayList<>(Text.itemLines(plugin.messages().list("gui-texts.empty-display.lore"), placeholders, Map.of()));
        } else {
            List<String> source = plugin.messages().list("gui-texts.spin-button.lore");
            if (bet && prize) {
                lines = new ArrayList<>(Text.itemLines(source, placeholders, spinNames(holder)));
            } else {
                List<String> partial = new ArrayList<>();
                for (String raw : source) {
                    String line = Text.prizeQuantity(raw);
                    boolean betLine = line.contains("%origin_item%");
                    boolean prizeLine = line.contains("%target_item%") || line.contains("%win_amount%");
                    if (!betLine && !prizeLine) {
                        continue;
                    }
                    if (betLine && !bet) {
                        partial.add(line.replace("%origin_item%", "Pendiente"));
                    } else if (prizeLine && !prize) {
                        partial.add(pendingPrize(line));
                    } else {
                        partial.add(line);
                    }
                }
                lines = new ArrayList<>(Text.itemLines(partial, placeholders, spinNames(holder)));
            }
        }
        if (spinner != null) {
            for (String raw : plugin.messages().list("gui-texts.spin-wait")) {
                lines.add(Text.itemText(Text.apply(raw, Map.of("player", spinner))));
            }
        }
        return lines;
    }

    private String pendingPrize(String line) {
        String value = line
                .replace("x%win_amount% %target_item%", "Pendiente")
                .replace("x%win_amount%%target_item%", "Pendiente")
                .replace("%win_amount%x %target_item%", "Pendiente")
                .replace("%target_item%", "Pendiente")
                .replace("x%win_amount%", "")
                .replace("%win_amount%", "");
        return value.replaceAll(" +", " ").trim();
    }

    private Map<String, net.kyori.adventure.text.Component> spinNames(UpgraderHolder holder) {
        Map<String, net.kyori.adventure.text.Component> names = new HashMap<>();
        ItemStack origin = holder.origin();
        String none = plugin.messages().raw("gui-texts.none");
        if (origin == null) {
            names.put("origin_item", net.kyori.adventure.text.Component.text(none));
        } else {
            names.put("origin_item", Text.withCount(Text.itemTitle(origin), origin.getAmount()));
        }
        names.put("target_item", rewardTitle(holder.target()));
        return names;
    }

    private net.kyori.adventure.text.Component rewardTitle(RewardEntry entry) {
        if (entry == null) {
            return net.kyori.adventure.text.Component.text(plugin.messages().raw("gui-texts.none"));
        }
        ItemStack stack = plugin.rewards().createTemplate(entry);
        if (stack != null) {
            return Text.itemTitle(stack);
        }
        if (entry.displayName() != null && !entry.displayName().isBlank()) {
            return Text.component(entry.displayName());
        }
        return net.kyori.adventure.text.Component.text(plugin.rewards().nameOf(entry));
    }

    private ItemStack amountButton(MenuLayout.IconSpec spec, String path, Map<String, String> placeholders, int amount) {
        Map<String, String> copy = new HashMap<>(placeholders);
        copy.put("step", Integer.toString(amount));
        return button(
                spec,
                plugin.messages().format(path + ".name", copy),
                plugin.messages().formatList(path + ".lore", copy),
                false,
                amount
        );
    }

    private ItemStack multiplierButton(MenuLayout.IconSpec spec, Map<String, String> placeholders, int multiplier, boolean active) {
        Map<String, String> copy = new HashMap<>(placeholders);
        copy.put("multiplier", Integer.toString(multiplier));
        copy.put("active", active ? "x" + multiplier : "x1");
        int base = 1;
        try {
            base = Integer.parseInt(placeholders.getOrDefault("amount", "1"));
        } catch (NumberFormatException ignored) {
            base = 1;
        }
        copy.put("win_preview", Integer.toString(Math.max(1, base) * multiplier));
        ItemStack stack = button(
                spec,
                plugin.messages().format("gui-texts.multiplier.name", copy),
                plugin.messages().formatList("gui-texts.multiplier.lore", copy),
                false,
                1
        );
        stack.editMeta(meta -> {
            meta.getEnchants().keySet().forEach(meta::removeEnchant);
            if (active) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            }
        });
        return stack;
    }

    private ItemStack button(MenuLayout.IconSpec spec, String name, List<String> lore, boolean glow, int amount) {
        ItemStack base = null;
        if (spec != null && spec.nexoId() != null && !spec.nexoId().isBlank()) {
            base = plugin.nexo().createItem(spec.nexoId());
        }
        Material material = spec == null || spec.material() == null ? Material.PAPER : spec.material();
        int modelData = spec == null ? 0 : spec.modelData();
        return GuiItems.of(base, material, name, lore, glow, amount, modelData);
    }

    private void paintTarget(Inventory inventory, MenuLayout layout, UpgraderHolder holder, Map<String, String> placeholders) {
        RewardEntry target = holder.target();
        if (target == null) {
            inventory.setItem(layout.target, button(
                    layout.icons.targetEmpty(),
                    plugin.messages().raw("gui-texts.target-empty.name"),
                    plugin.messages().list("gui-texts.target-empty.lore"),
                    false,
                    1
            ));
            return;
        }
        ItemStack icon = plugin.rewards().createTemplate(target);
        if (icon == null) {
            inventory.setItem(layout.target, button(
                    layout.icons.targetEmpty(),
                    plugin.messages().raw("gui-texts.target-empty.name"),
                    plugin.messages().list("gui-texts.target-empty.lore"),
                    false,
                    1
            ));
            return;
        }
        int visual = Math.min(Math.max(1, holder.targetAmount()), Math.max(1, icon.getMaxStackSize()));
        icon.setAmount(visual);
        Map<String, String> prizeText = new HashMap<>(placeholders);
        prizeText.put("target_value", Text.compact(plugin.rewards().prizeValue(target) * holder.targetAmount()));
        List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
        if (icon.getItemMeta() != null && icon.getItemMeta().lore() != null) {
            lore.addAll(icon.getItemMeta().lore());
        }
        if (plugin.rewards().isLimited(target)) {
            for (String line : plugin.messages().lines("gui-texts.limited-badge")) {
                lore.add(Text.itemText(line));
            }
        }
        for (String line : plugin.messages().formatList("gui-texts.target-selected", prizeText)) {
            lore.add(Text.itemText(line));
        }
        icon.editMeta(meta -> meta.lore(lore));
        inventory.setItem(layout.target, icon);
    }

    private void paintBetInfo(Inventory inventory, MenuLayout layout, UpgraderHolder holder) {
        if (layout.betInfo < 0) {
            return;
        }
        ItemStack origin = holder.origin();
        if (origin == null) {
            inventory.setItem(layout.betInfo, button(
                    layout.icons.betInfo(),
                    plugin.messages().raw("gui-texts.bet-empty.name"),
                    plugin.messages().list("gui-texts.bet-empty.lore"),
                    false,
                    1
            ));
            return;
        }
        double each = plugin.itemValues().unitValue(origin);
        Map<String, String> tokens = new HashMap<>();
        tokens.put("each", Text.compact(each));
        tokens.put("count", Integer.toString(origin.getAmount()));
        tokens.put("stack_value", Text.compact(each * origin.getAmount()));
        if (each <= 0.0d) {
            tokens.put("origin_name", Text.plainName(origin));
            inventory.setItem(layout.betInfo, button(
                    layout.icons.betInfo(),
                    plugin.messages().format("gui-texts.bet-invalid.name", tokens),
                    plugin.messages().formatList("gui-texts.bet-invalid.lore", tokens),
                    false,
                    1
            ));
            return;
        }
        List<String> lore = plugin.messages().list("gui-texts.bet-info.lore");
        if (origin.getAmount() < 2) {
            lore = lore.stream().filter(line -> line == null || !line.contains("%stack_value%")).toList();
        }
        ItemStack icon = button(
                layout.icons.betInfo(),
                " ",
                lore.stream().map(line -> Text.apply(line, tokens)).toList(),
                false,
                1
        );
        net.kyori.adventure.text.Component title = Text.itemTextWithToken(
                plugin.messages().raw("gui-texts.bet-info.name"),
                tokens,
                "origin_name",
                Text.itemTitle(origin)
        );
        icon.editMeta(meta -> meta.displayName(title));
        inventory.setItem(layout.betInfo, icon);
    }

    private void paintGuide(Inventory inventory, MenuLayout layout) {
        if (layout.guide < 0) {
            return;
        }
        inventory.setItem(layout.guide, button(
                layout.icons.guide(),
                plugin.messages().raw("gui-texts.guide.name"),
                plugin.messages().list("gui-texts.guide.lore"),
                false,
                1
        ));
    }

    private Map<String, String> placeholders(UpgraderHolder holder, Evaluation evaluation, Player viewer) {
        Map<String, String> map = new HashMap<>();
        String none = plugin.messages().raw("gui-texts.none");
        ItemStack origin = holder.origin();
        map.put("origin_item", origin == null ? none : Text.plainName(origin) + " x" + origin.getAmount());
        map.put("origin_name", origin == null ? none : Text.plainName(origin));
        map.put("count", origin == null ? "0" : Integer.toString(origin.getAmount()));
        map.put("target_item", holder.target() == null ? none : plugin.rewards().nameOf(holder.target()));
        map.put("amount", Integer.toString(holder.targetAmount()));
        int winAmount = evaluation.ok()
                ? evaluation.targetAmount()
                : Math.max(1, holder.targetAmount()) * Math.max(1, holder.multiplier());
        map.put("win_amount", Integer.toString(winAmount));
        map.put("multiplier", "x" + holder.multiplier());
        map.put("needed", Integer.toString(Math.max(0, evaluation.betAmount())));
        map.put("chance", evaluation.ok() ? chanceText(viewer, evaluation.chance()) : chanceText(viewer, 0.0d));
        map.put("bet_value", Text.compact(evaluation.betTotal()));
        map.put("target_value", Text.compact(evaluation.targetTotal()));
        map.put("unit_value", Text.compact(evaluation.unitTarget()));
        map.put("active", "x" + holder.multiplier());
        return map;
    }

    public String chanceText(Player player, double chance) {
        boolean precise = player != null && Permissions.has(player, Permissions.PRECISE_CHANCE);
        return Text.chance(chance, precise);
    }

    private Player viewer(UpgraderHolder holder) {
        return holder == null ? null : Bukkit.getPlayer(holder.playerId());
    }
}
