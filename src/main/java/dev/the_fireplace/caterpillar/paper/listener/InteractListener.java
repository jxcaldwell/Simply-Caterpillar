package dev.the_fireplace.caterpillar.paper.listener;

import dev.the_fireplace.caterpillar.core.Machine;
import dev.the_fireplace.caterpillar.core.Pos;
import dev.the_fireplace.caterpillar.core.SegmentKind;
import dev.the_fireplace.caterpillar.paper.DecorationGui;
import dev.the_fireplace.caterpillar.paper.HeadGui;
import dev.the_fireplace.caterpillar.paper.ReinforcementGui;
import dev.the_fireplace.caterpillar.paper.IncineratorGui;
import dev.the_fireplace.caterpillar.paper.StorageGui;
import dev.the_fireplace.caterpillar.paper.TransporterGui;
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

/**
 * Right-clicking a caterpillar block opens the drill head's GUI; storage and incinerator segments open their own,
 * and a seat lets the player sit.
 */
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

        Pos pos = new Pos(block.getX(), block.getY(), block.getZ());
        Machine.Segment segment = machine.segmentAt(pos);
        if (segment == null) {
            segment = machine.segmentWithCartAt(pos);
        }

        // Anyone may take a seat; everything else is for the owner (or an admin).
        if (segment != null && segment.kind() == SegmentKind.SEAT) {
            if (!plugin.manager().seats().sit(player, machine, segment)) {
                player.sendActionBar(plugin.lang().get("msg.seat.occupied"));
            }
            return;
        }
        if (!plugin.manager().canAccess(player, machine)) {
            player.sendActionBar(plugin.lang().get("msg.not-owner"));
            return;
        }

        if (segment != null && segment.kind() == SegmentKind.STORAGE) {
            StorageGui storage = plugin.manager().storage(segment.id());
            if (storage != null) {
                player.openInventory(storage.getInventory());
                return;
            }
        }
        if (segment != null && segment.kind() == SegmentKind.INCINERATOR) {
            IncineratorGui filter = plugin.manager().incinerator(segment.id());
            if (filter != null) {
                player.openInventory(filter.getInventory());
                return;
            }
        }

        if (segment != null && segment.kind() == SegmentKind.REINFORCEMENT) {
            ReinforcementGui settings = plugin.manager().reinforcement(segment.id());
            if (settings != null) {
                settings.render();
                player.openInventory(settings.getInventory());
                return;
            }
        }
        if (segment != null && segment.kind() == SegmentKind.DECORATION) {
            DecorationGui settings = plugin.manager().decoration(segment.id());
            if (settings != null) {
                settings.render();
                player.openInventory(settings.getInventory());
                return;
            }
        }
        if (segment != null && segment.kind() == SegmentKind.TRANSPORTER) {
            TransporterGui cargo = plugin.manager().transporter(segment.id());
            if (cargo != null) {
                player.openInventory(cargo.getInventory());
                return;
            }
        }

        HeadGui gui = plugin.manager().gui(machine.id());
        if (gui == null) {
            return;
        }
        gui.refresh(machine);
        player.openInventory(gui.getInventory());
    }
}
