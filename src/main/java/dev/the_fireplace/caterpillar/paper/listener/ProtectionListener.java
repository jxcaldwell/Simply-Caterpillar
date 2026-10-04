package dev.the_fireplace.caterpillar.paper.listener;

import dev.the_fireplace.caterpillar.core.Machine;
import dev.the_fireplace.caterpillar.core.Pos;
import dev.the_fireplace.caterpillar.paper.BuildGuard;
import dev.the_fireplace.caterpillar.paper.CaterpillarManager;
import dev.the_fireplace.caterpillar.paper.SimplyCaterpillarPlugin;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/** Breaking caterpillar blocks as a player, and keeping explosions and pistons away from them. */
public final class ProtectionListener implements Listener {

    private final SimplyCaterpillarPlugin plugin;

    public ProtectionListener(SimplyCaterpillarPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (BuildGuard.isSynthetic()) {
            return;
        }
        Block block = event.getBlock();
        Machine machine = plugin.manager().at(block);
        if (machine == null) {
            return;
        }

        Player player = event.getPlayer();
        if (!plugin.manager().canAccess(player, machine)) {
            event.setCancelled(true);
            player.sendActionBar(plugin.lang().get("msg.not-owner"));
            return;
        }

        // The vanilla block drop is replaced by the proper part item(s).
        event.setDropItems(false);
        event.setExpToDrop(0);

        Location at = block.getLocation().add(0.5, 0.5, 0.5);
        boolean dropParts = CaterpillarManager.givesParts(player);
        Pos pos = new Pos(block.getX(), block.getY(), block.getZ());
        Machine.Segment cartOwner = machine.segmentWithCartAt(pos);
        if (cartOwner != null) {
            plugin.manager().breakCart(machine, cartOwner, at, dropParts);
        } else if (machine.isHeadPos(pos)) {
            plugin.manager().dismantle(machine, at, dropParts);
        } else {
            plugin.manager().removeSegment(machine, pos, at, dropParts);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (plugin.settings().explosionProof) {
            event.blockList().removeIf(block -> plugin.manager().at(block) != null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (plugin.settings().explosionProof) {
            event.blockList().removeIf(block -> plugin.manager().at(block) != null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (plugin.manager().at(block) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (plugin.manager().at(block) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
