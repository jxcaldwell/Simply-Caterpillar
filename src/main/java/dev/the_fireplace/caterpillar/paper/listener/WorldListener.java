package dev.the_fireplace.caterpillar.paper.listener;

import dev.the_fireplace.caterpillar.paper.SimplyCaterpillarPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

/** Restores caterpillars whose world was not loaded yet when the plugin started (e.g. added by a world plugin). */
public final class WorldListener implements Listener {

    private final SimplyCaterpillarPlugin plugin;

    public WorldListener(SimplyCaterpillarPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        plugin.manager().onWorldLoaded();
    }
}
