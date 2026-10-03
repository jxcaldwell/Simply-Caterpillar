package dev.the_fireplace.caterpillar.paper.listener;

import dev.the_fireplace.caterpillar.core.Machine;
import dev.the_fireplace.caterpillar.paper.HeadGui;
import dev.the_fireplace.caterpillar.paper.SimplyCaterpillarPlugin;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Right-clicking any block of a caterpillar opens the drill head's GUI. */
public final class InteractListener implements Listener {

    private final SimplyCaterpillarPlugin plugin;

    public InteractListener(SimplyCaterpillarPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Machine machine = plugin.manager().at(block);
        if (machine == null) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        // Holding a part: the player is attaching a segment, let the placement listener handle it.
        if (plugin.items().identify(held) != null) {
            return;
        }
        // Sneaking with an item: the player wants to build against the machine, as with any block.
        if (player.isSneaking() && !held.getType().isAir()) {
            return;
        }

        event.setCancelled(true);
        if (!player.hasPermission("simplycaterpillar.use")) {
            player.sendActionBar(plugin.lang().get("msg.no-permission"));
            return;
        }
        if (!plugin.manager().canAccess(player, machine)) {
            player.sendActionBar(plugin.lang().get("msg.not-owner"));
            return;
        }

        HeadGui gui = plugin.manager().gui(machine.id());
        if (gui == null) {
            return;
        }
        gui.refresh(machine);
        player.openInventory(gui.getInventory());
    }
}
