package com.osiris.upgrader.gui;

import com.osiris.upgrader.OsirisUpgraderPlugin;
import com.osiris.upgrader.item.ItemSnapshots;
import com.osiris.upgrader.item.RewardEntry;
import com.osiris.upgrader.util.Permissions;
import com.osiris.upgrader.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RewardEditor {
    public static final int LIST_SIZE = 54;
    public static final int PREVIEW_SLOT = 13;
    public static final int VALUE_SLOT = 11;
    public static final int LIMIT_SLOT = 15;
    public static final int STOCK_SLOT = 20;
    public static final int DELETE_SLOT = 22;
    public static final int GLOW_SLOT = 24;

    private final OsirisUpgraderPlugin plugin;
    private final Map<UUID, Draft> drafts = new ConcurrentHashMap<>();

    public RewardEditor(OsirisUpgraderPlugin plugin) {
        this.plugin = plugin;
    }

    public void openList(Player player) {
        if (!Permissions.has(player, Permissions.REWARDS)) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        RewardListHolder holder = new RewardListHolder();
        Inventory inventory = Bukkit.createInventory(holder, LIST_SIZE, Text.component("&8Premios"));
        holder.attach(inventory);
        paintList(inventory);
        player.openInventory(inventory);
    }

    public void paintList(Inventory inventory) {
        inventory.clear();
        List<RewardEntry> rewards = plugin.rewards().all();
        int limit = Math.min(rewards.size(), inventory.getSize());
        for (int slot = 0; slot < limit; slot++) {
            RewardEntry entry = rewards.get(slot);
            try {
                inventory.setItem(slot, icon(entry));
            } catch (RuntimeException ex) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "No se pudo mostrar el premio " + entry.key(), ex);
                inventory.setItem(slot, GuiItems.of(Material.BARRIER, entry.key(), List.of("&cNo se pudo mostrar este premio."), false, 1));
            }
        }
    }

    public void openEdit(Player player, RewardEntry entry) {
        if (player == null || entry == null || !player.isOnline()) {
            return;
        }
        if (!Permissions.has(player, Permissions.REWARDS)) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        RewardEditHolder holder = new RewardEditHolder(entry.key());
        Inventory inventory = Bukkit.createInventory(holder, 27, Text.component("&8Editar premio"));
        holder.attach(inventory);
        ItemStack filler = GuiItems.filler(Material.BLACK_STAINED_GLASS_PANE, 0, " ", true);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
        inventory.setItem(PREVIEW_SLOT, preview(entry));
        inventory.setItem(VALUE_SLOT, valueButton(entry));
        if (!plugin.rewards().isLimited(entry)) {
            inventory.setItem(LIMIT_SLOT, limitButton(entry));
        }
        inventory.setItem(STOCK_SLOT, stockButton(entry));
        inventory.setItem(GLOW_SLOT, glowButton(entry));
        inventory.setItem(DELETE_SLOT, GuiItems.of(
                Material.BARRIER,
                "&cBorrar de la lista",
                List.of("&7Clic izquierdo para quitar este premio."),
                false,
                1
        ));
        player.openInventory(inventory);
    }

    public boolean accept(Player player, ItemStack source) {
        if (player == null || !Permissions.has(player, Permissions.REWARDS)) {
            if (player != null) {
                plugin.messages().send(player, "no-permission");
            }
            return false;
        }
        if (source == null || source.getType().isAir() || plugin.specialItems().isSpecial(source)) {
            plugin.messages().send(player, "rewards-denied");
            return false;
        }
        if (!source.getType().isItem()) {
            plugin.messages().send(player, "rewards-denied");
            return false;
        }
        String nexoId = plugin.nexo().idFromItem(source);
        boolean custom = nexoId == null && ItemSnapshots.isCustom(source);
        if (!custom && nexoId == null && plugin.itemValues().isBlocked(source.getType())) {
            plugin.messages().send(player, "rewards-denied");
            return false;
        }
        if (plugin.rewards().all().size() >= LIST_SIZE) {
            plugin.messages().send(player, "rewards-full");
            return false;
        }
        if (plugin.rewards().matching(source) != null) {
            plugin.messages().send(player, "rewards-exists");
            return false;
        }
        RewardEntry added = plugin.rewards().add(source);
        if (added == null) {
            plugin.messages().send(player, "rewards-denied");
            return false;
        }
        plugin.messages().send(player, "rewards-added", Map.of("item", label(added)));
        return true;
    }

    public void beginValue(Player player, String key) {
        begin(player, key, Field.VALUE);
    }

    public void beginLimit(Player player, String key) {
        begin(player, key, Field.LIMIT);
    }

    public void beginStock(Player player, String key) {
        begin(player, key, Field.STOCK);
    }

    public void toggleGlow(Player player, String key) {
        RewardEntry entry = plugin.rewards().byKey(key);
        if (entry == null) {
            openList(player);
            return;
        }
        plugin.rewards().toggleGlow(entry);
        openEdit(player, plugin.rewards().byKey(key));
    }

    public void delete(Player player, String key) {
        RewardEntry entry = plugin.rewards().byKey(key);
        String name = entry == null ? key : label(entry);
        if (!plugin.rewards().remove(key)) {
            plugin.messages().send(player, "rewards-removed", Map.of("item", name));
            openList(player);
            return;
        }
        drafts.remove(player.getUniqueId());
        plugin.messages().send(player, "rewards-removed", Map.of("item", name));
        openList(player);
    }

    public boolean hasDraft(UUID playerId) {
        return drafts.containsKey(playerId);
    }

    public void clearDrafts() {
        drafts.clear();
    }

    public void forget(UUID playerId) {
        drafts.remove(playerId);
    }

    public void receive(Player player, String raw) {
        Draft draft = drafts.get(player.getUniqueId());
        if (draft == null) {
            return;
        }
        if (!Permissions.has(player, Permissions.REWARDS)) {
            drafts.remove(player.getUniqueId());
            plugin.messages().send(player, "no-permission");
            return;
        }
        String text = raw == null ? "" : raw.trim();
        if (text.equalsIgnoreCase("cancelar") || text.equalsIgnoreCase("cancel")) {
            drafts.remove(player.getUniqueId());
            plugin.messages().send(player, "rewards-cancelled");
            reopen(player, draft.key());
            return;
        }
        RewardEntry entry = plugin.rewards().byKey(draft.key());
        if (entry == null) {
            drafts.remove(player.getUniqueId());
            return;
        }
        String normalized = text.replace(',', '.');
        try {
            if (draft.field() == Field.VALUE) {
                double value = Double.parseDouble(normalized);
                if (!Double.isFinite(value) || value < 0.0d) {
                    throw new NumberFormatException();
                }
                plugin.rewards().setPrizeValue(entry, value);
                drafts.remove(player.getUniqueId());
                plugin.messages().send(player, "rewards-value-set", Map.of(
                        "item", label(entry),
                        "value", Text.compact(value)
                ));
            } else {
                if (normalized.contains(".")) {
                    throw new NumberFormatException();
                }
                int limit = Integer.parseInt(normalized);
                if (limit < 0) {
                    throw new NumberFormatException();
                }
                if (draft.field() == Field.STOCK) {
                    plugin.rewards().setLimited(entry, limit);
                    drafts.remove(player.getUniqueId());
                    if (limit == 0) {
                        plugin.messages().send(player, "rewards-stock-cleared", Map.of("item", label(entry)));
                    } else {
                        plugin.messages().send(player, "rewards-stock-set", Map.of(
                                "item", label(entry),
                                "stock", Integer.toString(limit)
                        ));
                    }
                } else {
                    plugin.rewards().setPrizeLimit(entry, limit);
                    drafts.remove(player.getUniqueId());
                    if (limit == 0) {
                        plugin.messages().send(player, "rewards-limit-cleared", Map.of("item", label(entry)));
                    } else {
                        plugin.messages().send(player, "rewards-limit-set", Map.of(
                                "item", label(entry),
                                "limit", Integer.toString(limit)
                        ));
                    }
                }
            }
        } catch (NumberFormatException ex) {
            plugin.messages().send(player, "rewards-invalid-number");
            return;
        }
        reopen(player, draft.key());
    }

    private void begin(Player player, String key, Field field) {
        RewardEntry entry = plugin.rewards().byKey(key);
        if (entry == null) {
            openList(player);
            return;
        }
        drafts.put(player.getUniqueId(), new Draft(key, field));
        player.closeInventory();
        String message = switch (field) {
            case VALUE -> "rewards-type-value";
            case STOCK -> "rewards-type-stock";
            case LIMIT -> "rewards-type-limit";
        };
        plugin.messages().send(player, message, Map.of("item", label(entry)));
    }

    private void reopen(Player player, String key) {
        RewardEntry entry = plugin.rewards().byKey(key);
        if (entry == null || !player.isOnline()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> openEdit(player, plugin.rewards().byKey(key)));
    }

    private ItemStack icon(RewardEntry entry) {
        ItemStack stack = template(entry);
        double value = plugin.rewards().prizeValue(entry);
        boolean limited = plugin.rewards().isLimited(entry);
        int stock = plugin.rewards().limitedRemaining(entry);
        stack.editMeta(meta -> meta.lore(List.of(
                Text.itemText("&7Valor: &f" + Text.compact(value)),
                Text.itemText(limited
                        ? "&7Premio limitado: &f" + stock
                        : "&7Límite: &f" + limitText(plugin.rewards().prizeLimit(entry))),
                Text.itemText("&eClic derecho para editar")
        )));
        if (limited && stock > 0) {
            stack.setAmount(Math.min(stock, Math.max(1, stack.getMaxStackSize())));
        }
        return stack;
    }

    private ItemStack preview(RewardEntry entry) {
        ItemStack stack = template(entry);
        double value = plugin.rewards().prizeValue(entry);
        int limit = plugin.rewards().prizeLimit(entry);
        stack.editMeta(meta -> {
            List<Component> lore = new ArrayList<>();
            if (meta.lore() != null) {
                lore.addAll(meta.lore());
            }
            lore.add(Text.itemText("&7Tipo: &f" + kind(entry)));
            lore.add(Text.itemText("&7Id: &f" + entry.id()));
            lore.add(Text.itemText("&7Valor del premio: &f" + Text.compact(value)));
            if (plugin.rewards().isLimited(entry)) {
                lore.add(Text.itemText("&7Premio limitado: &f" + plugin.rewards().limitedRemaining(entry)));
            } else {
                lore.add(Text.itemText("&7Límite de premio: &f" + limitText(limit)));
            }
            lore.add(Text.itemText("&7Efecto encantado: &f" + (plugin.rewards().glow(entry) ? "Sí" : "No")));
            meta.lore(lore);
        });
        return stack;
    }

    private ItemStack valueButton(RewardEntry entry) {
        return GuiItems.of(
                Material.GOLD_INGOT,
                "&eValor del premio",
                List.of(
                        "&7Actual: &f" + Text.compact(plugin.rewards().prizeValue(entry)),
                        "&7Solo de este premio.",
                        "&eClic izquierdo &7y escribe el nuevo valor."
                ),
                false,
                1
        );
    }

    private ItemStack stockButton(RewardEntry entry) {
        boolean limited = plugin.rewards().isLimited(entry);
        int stock = plugin.rewards().limitedRemaining(entry);
        return GuiItems.of(
                Material.CHEST,
                "&ePremio limitado",
                List.of(
                        "&7Actual: &f" + (limited ? Integer.toString(stock) : "sin stock global"),
                        "&7Cuántos se pueden ganar en total.",
                        "&7Al acabarse, el premio se quita.",
                        "&70 &7vuelve al límite por apuesta.",
                        "&eClic izquierdo &7y escribe la cantidad."
                ),
                false,
                limited ? Math.min(Math.max(1, stock), 64) : 1
        );
    }

    private ItemStack glowButton(RewardEntry entry) {
        boolean on = plugin.rewards().glow(entry);
        return GuiItems.of(
                Material.ENCHANTED_BOOK,
                "&dEfecto encantado",
                List.of(
                        on ? "&aActivado" : "&7Desactivado",
                        "&7Clic izquierdo para cambiar."
                ),
                on,
                1
        );
    }

    private ItemStack limitButton(RewardEntry entry) {
        int limit = plugin.rewards().prizeLimit(entry);
        return GuiItems.of(
                Material.HOPPER,
                "&eLímite de premio",
                List.of(
                        "&7Actual: &f" + limitText(limit),
                        "&70 &7quita el límite.",
                        "&eClic izquierdo &7y escribe el nuevo límite."
                ),
                false,
                1
        );
    }

    private ItemStack template(RewardEntry entry) {
        ItemStack stack = plugin.rewards().createTemplate(entry);
        if (stack != null && !stack.getType().isAir()) {
            return stack;
        }
        ItemStack missing = new ItemStack(Material.BARRIER);
        missing.editMeta(meta -> meta.displayName(Text.itemText("&c" + entry.key())));
        return missing;
    }

    private String label(RewardEntry entry) {
        ItemStack stack = plugin.rewards().createTemplate(entry);
        String name = Text.plainName(stack);
        return name == null || name.isBlank() ? entry.key() : name;
    }

    private static String kind(RewardEntry entry) {
        if (entry.nexo()) {
            return "Nexo";
        }
        if (entry.snapshot()) {
            return "Personalizado";
        }
        return "Vanilla";
    }

    private static String limitText(int limit) {
        return limit > 0 ? Integer.toString(limit) : "sin límite";
    }

    private enum Field {
        VALUE,
        LIMIT,
        STOCK
    }

    private record Draft(String key, Field field) {
    }
}
