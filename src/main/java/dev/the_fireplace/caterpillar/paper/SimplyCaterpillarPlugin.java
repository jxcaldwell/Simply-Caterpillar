package dev.the_fireplace.caterpillar.paper;

import dev.the_fireplace.caterpillar.paper.listener.CraftListener;
import dev.the_fireplace.caterpillar.paper.listener.GuiListener;
import dev.the_fireplace.caterpillar.paper.listener.InteractListener;
import dev.the_fireplace.caterpillar.paper.listener.PlacementListener;
import dev.the_fireplace.caterpillar.paper.listener.ProtectionListener;
import dev.the_fireplace.caterpillar.paper.listener.WorldListener;
import java.util.List;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Simply Caterpillar as a Paper plugin. The machine logic lives in {@code dev.the_fireplace.caterpillar.core};
 * this package connects it to the server.
 */
public final class SimplyCaterpillarPlugin extends JavaPlugin {

    private Settings settings;
    private Lang lang;
    private FuelRegistry fuels;
    private CaterpillarItems items;
    private PaperEnv env;
    private CaterpillarManager manager;
    private List<NamespacedKey> recipeKeys = List.of();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfiguration();

        env = new PaperEnv(this);
        manager = new CaterpillarManager(this);
        manager.load();

        getServer().getPluginManager().registerEvents(new PlacementListener(this), this);
        getServer().getPluginManager().registerEvents(new InteractListener(this), this);
        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new CraftListener(this), this);
        getServer().getPluginManager().registerEvents(new WorldListener(this), this);

        PluginCommand command = getCommand("caterpillar");
        if (command != null) {
            CaterpillarCommand handler = new CaterpillarCommand(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }

        recipeKeys = Recipes.register(this);
        manager.start();
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.stop();
            manager.save();
        }
        Recipes.unregister(recipeKeys);
    }

    private void loadConfiguration() {
        settings = new Settings(getConfig(), getLogger());
        lang = new Lang(this, settings.language);
        fuels = new FuelRegistry(getConfig().getConfigurationSection("fuels"), getLogger());
        items = new CaterpillarItems(this, settings, lang);
    }

    /** Re-reads config.yml and the language file and applies them to running machines. */
    public void reload() {
        reloadConfig();
        loadConfiguration();
        recipeKeys = Recipes.register(this);
        manager.applySettings();
    }

    public Settings settings() {
        return settings;
    }

    public Lang lang() {
        return lang;
    }

    public FuelRegistry fuels() {
        return fuels;
    }

    public CaterpillarItems items() {
        return items;
    }

    public PaperEnv env() {
        return env;
    }

    public CaterpillarManager manager() {
        return manager;
    }

    public List<NamespacedKey> recipeKeys() {
        return recipeKeys;
    }
}
