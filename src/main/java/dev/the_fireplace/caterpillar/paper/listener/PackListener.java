package dev.the_fireplace.caterpillar.paper.listener;

import dev.the_fireplace.caterpillar.paper.Bedrock;
import dev.the_fireplace.caterpillar.paper.ResourcePack;
import dev.the_fireplace.caterpillar.paper.SimplyCaterpillarPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

/** Offers the resource pack, keeps track of who has it, and keeps their view of caterpillars up to date. */
public final class PackListener implements Listener {

    private final SimplyCaterpillarPlugin plugin;

    public PackListener(SimplyCaterpillarPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (Bedrock.isBedrock(player)) {
            return; // Bedrock (Geyser) players cannot use Java resource packs: they keep the plain blocks
        }
        // A short delay so the prompt does not get lost among other join messages and packs.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.resourcePack().offer(player);
            }
        }, 20L);
    }

    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        if (!ResourcePack.PACK_ID.equals(event.getID()) || Bedrock.isBedrock(event.getPlayer())) {
            return;
        }
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> plugin.manager().visuals().packLoaded(event.getPlayer());
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED ->
                    plugin.manager().visuals().packGone(event.getPlayer());
            default -> { }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.manager().visuals().packGone(event.getPlayer());
    }
}
