package com.osiris.upgrader;

import com.osiris.upgrader.command.OsirisCommand;
import com.osiris.upgrader.config.ConfigManager;
import com.osiris.upgrader.config.Keys;
import com.osiris.upgrader.config.Messages;
import com.osiris.upgrader.gui.BaseEditHolder;
import com.osiris.upgrader.gui.BaseEditor;
import com.osiris.upgrader.gui.BaseListHolder;
import com.osiris.upgrader.gui.RewardBrowserGui;
import com.osiris.upgrader.gui.RewardBrowserHolder;
import com.osiris.upgrader.gui.RewardEditHolder;
import com.osiris.upgrader.gui.RewardEditor;
import com.osiris.upgrader.gui.RewardListHolder;
import com.osiris.upgrader.gui.RouletteSessions;
import com.osiris.upgrader.gui.UpgraderGui;
import com.osiris.upgrader.gui.UpgraderHolder;
import com.osiris.upgrader.gui.ValueCatalogGui;
import com.osiris.upgrader.hook.BedrockBridge;
import com.osiris.upgrader.hook.NexoBridge;
import com.osiris.upgrader.item.BaseValueCatalog;
import com.osiris.upgrader.item.ItemMover;
import com.osiris.upgrader.item.ItemValueService;
import com.osiris.upgrader.item.RewardCatalog;
import com.osiris.upgrader.item.SpecialItems;
import com.osiris.upgrader.listener.BaseEditorListener;
import com.osiris.upgrader.listener.GuiListener;
import com.osiris.upgrader.listener.ItemListener;
import com.osiris.upgrader.listener.RewardEditorListener;
import com.osiris.upgrader.listener.RouletteListener;
import com.osiris.upgrader.roulette.PendingSpins;
import com.osiris.upgrader.roulette.BedrockView;
import com.osiris.upgrader.roulette.RouletteFactory;
import com.osiris.upgrader.roulette.RouletteManager;
import com.osiris.upgrader.roulette.SpinAnimator;
import com.osiris.upgrader.roulette.SpinCooldowns;
import com.osiris.upgrader.roulette.SpinHistory;
import com.osiris.upgrader.util.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class OsirisUpgraderPlugin extends JavaPlugin {
    private ConfigManager configs;
    private Messages messages;
    private NexoBridge nexo;
    private BedrockBridge bedrock;
    private BedrockView bedrockView;
    private ItemValueService itemValues;
    private RewardCatalog rewards;
    private SpecialItems specialItems;
    private ItemMover items;
    private Sounds sounds;
    private RouletteFactory factory;
    private RouletteManager roulettes;
    private PendingSpins pending;
    private SpinHistory history;
    private SpinCooldowns cooldowns;
    private RouletteSessions sessions;
    private SpinAnimator animator;
    private UpgraderGui upgraderGui;
    private RewardBrowserGui browser;
    private RewardEditor rewardEditor;
    private BaseValueCatalog baseValues;
    private BaseEditor baseEditor;
    private ValueCatalogGui valuesGui;
    private boolean shuttingDown;
    private boolean reloading;

    @Override
    public void onEnable() {
        Keys.init(this);
        configs = new ConfigManager(this);
        messages = new Messages();
        nexo = new NexoBridge();
        bedrock = new BedrockBridge();
        bedrockView = new BedrockView(this);
        specialItems = new SpecialItems(this);
        itemValues = new ItemValueService(this);
        rewards = new RewardCatalog(this);
        sounds = new Sounds(configs);
        factory = new RouletteFactory(this);
        roulettes = new RouletteManager(this);
        pending = new PendingSpins(this);
        history = new SpinHistory(this);
        cooldowns = new SpinCooldowns(this);
        sessions = new RouletteSessions();
        animator = new SpinAnimator(this);
        upgraderGui = new UpgraderGui(this);
        browser = new RewardBrowserGui(this);
        rewardEditor = new RewardEditor(this);
        baseValues = new BaseValueCatalog(this);
        baseEditor = new BaseEditor(this);
        valuesGui = new ValueCatalogGui(this);
        if (!reloadFiles()) {
            getLogger().severe("OsirisUpgrader no pudo cargar sus configuraciones.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        items = new ItemMover(nexo);
        var command = getCommand("osiris");
        if (command == null) {
            getLogger().severe("El comando /osiris no está registrado en plugin.yml.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        OsirisCommand executor = new OsirisCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
        Bukkit.getPluginManager().registerEvents(new GuiListener(this), this);
        Bukkit.getPluginManager().registerEvents(new RewardEditorListener(this, rewardEditor), this);
        Bukkit.getPluginManager().registerEvents(new BaseEditorListener(this, baseEditor), this);
        Bukkit.getPluginManager().registerEvents(new ItemListener(this), this);
        Bukkit.getPluginManager().registerEvents(new RouletteListener(this), this);
        roulettes.load();
        pending.load();
        cooldowns.load();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            roulettes.ensureLoadedChunks();
            bedrockView.refreshOnline();
            roulettes.startShowcase();
        }, 20L);
        if (configs.nexoMenu() && !nexo.isHooked()) {
            getLogger().warning("menu-type está en 'nexo', pero Nexo no está disponible. El menú usará el respaldo visual.");
        }
        getLogger().info("OsirisUpgrader listo. Ruletas persistentes: " + roulettes.all().size());
    }

    @Override
    public void onDisable() {
        shuttingDown = true;
        if (roulettes != null) {
            roulettes.stopShowcase();
        }
        if (animator != null) {
            animator.finishAll();
        }
        closeMenus();
        if (roulettes != null) {
            roulettes.save();
        }
        HandlerList.unregisterAll(this);
    }

    public boolean reloadAll() {
        reloading = true;
        try {
            closeMenus();
            if (!reloadFiles()) {
                return false;
            }
            roulettes.refreshHolograms();
            roulettes.ensureLoadedChunks();
            bedrockView.refreshOnline();
            roulettes.startShowcase();
            return true;
        } catch (RuntimeException ex) {
            getLogger().log(Level.SEVERE, "Fallo al recargar OsirisUpgrader", ex);
            return false;
        } finally {
            reloading = false;
        }
    }

    private boolean reloadFiles() {
        try {
            configs.loadAll();
            messages.load(configs.messages());
            nexo.tryHook(getLogger());
            bedrock.tryHook(getLogger());
            itemValues.reload();
            rewards.reload();
            baseValues.reload();
            return true;
        } catch (RuntimeException ex) {
            getLogger().log(Level.SEVERE, "No se pudieron leer los YAML", ex);
            return false;
        }
    }

    public void closeMenus() {
        if (rewardEditor != null) {
            rewardEditor.clearDrafts();
        }
        if (baseEditor != null) {
            baseEditor.clearDrafts();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            var holder = player.getOpenInventory().getTopInventory().getHolder();
            if (holder instanceof UpgraderHolder session) {
                boolean spinning = session.isSpinning();
                session.abandon(player);
                if (!spinning && sessions != null) {
                    sessions.release(session);
                }
                player.closeInventory();
            } else if (holder instanceof RewardBrowserHolder browserHolder) {
                UpgraderHolder session = browserHolder.session();
                boolean spinning = session.isSpinning();
                browserHolder.setReturning(true);
                session.abandon(player);
                if (!spinning && sessions != null) {
                    sessions.release(session);
                }
                player.closeInventory();
            } else if (holder instanceof RewardListHolder || holder instanceof RewardEditHolder
                    || holder instanceof BaseListHolder || holder instanceof BaseEditHolder) {
                player.closeInventory();
            }
        }
    }

    public ConfigManager configs() {
        return configs;
    }

    public Messages messages() {
        return messages;
    }

    public NexoBridge nexo() {
        return nexo;
    }

    public BedrockBridge bedrock() {
        return bedrock;
    }

    public BedrockView bedrockView() {
        return bedrockView;
    }

    public ItemValueService itemValues() {
        return itemValues;
    }

    public RewardCatalog rewards() {
        return rewards;
    }

    public SpecialItems specialItems() {
        return specialItems;
    }

    public ItemMover items() {
        return items;
    }

    public Sounds sounds() {
        return sounds;
    }

    public RouletteFactory factory() {
        return factory;
    }

    public RouletteManager roulettes() {
        return roulettes;
    }

    public PendingSpins pending() {
        return pending;
    }

    public SpinHistory history() {
        return history;
    }

    public SpinCooldowns cooldowns() {
        return cooldowns;
    }

    public RouletteSessions sessions() {
        return sessions;
    }

    public SpinAnimator animator() {
        return animator;
    }

    public UpgraderGui upgraderGui() {
        return upgraderGui;
    }

    public RewardBrowserGui browser() {
        return browser;
    }

    public RewardEditor rewardEditor() {
        return rewardEditor;
    }

    public BaseValueCatalog baseValues() {
        return baseValues;
    }

    public BaseEditor baseEditor() {
        return baseEditor;
    }

    public ValueCatalogGui valuesGui() {
        return valuesGui;
    }

    public boolean isShuttingDown() {
        return shuttingDown;
    }

    public boolean isReloading() {
        return reloading;
    }
}
