package dev.the_fireplace.caterpillar.paper.listener;

import dev.the_fireplace.caterpillar.paper.SimplyCaterpillarPlugin;
import java.util.Locale;
import org.bukkit.Keyed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

/**
 * Keeps the part items from being used as ordinary ingredients (their base block is a normal block, so
 * vanilla recipes would otherwise accept them), and unlocks the recipes for players.
 */
public final class CraftListener implements Listener {

    private final SimplyCaterpillarPlugin plugin;

    public CraftListener(SimplyCaterpillarPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        boolean ours = recipe instanceof Keyed keyed
                && keyed.getKey().getNamespace().equals(plugin.getName().toLowerCase(Locale.ROOT));

        if (ours) {
            if (!event.getView().getPlayer().hasPermission("simplycaterpillar.craft")) {
                event.getInventory().setResult(null);
            }
            return;
        }
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            if (plugin.items().identify(ingredient) != null) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.settings().discoverOnJoin) {
            event.getPlayer().discoverRecipes(plugin.recipeKeys());
        }
        // Part items made by older versions get the current name, description and icon.
        var inventory = event.getPlayer().getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack fresh = plugin.items().refresh(inventory.getItem(slot));
            if (fresh != null) {
                inventory.setItem(slot, fresh);
            }
        }
    }
}
