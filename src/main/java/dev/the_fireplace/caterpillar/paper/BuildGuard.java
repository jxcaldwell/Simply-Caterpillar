package dev.the_fireplace.caterpillar.paper;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Asks protection plugins (WorldGuard, GriefPrevention, Towny, ...) whether a player may build or break at a
 * location by firing the same events a real player action would fire. The events are synthetic: nothing is
 * changed by them, and this plugin's own listeners ignore them (see {@link #isSynthetic()}).
 */
public final class BuildGuard {

    private static boolean firing;

    private BuildGuard() {
    }

    /** True while a synthetic event is being dispatched, so our own listeners can skip it. */
    public static boolean isSynthetic() {
        return firing;
    }

    public static boolean canBuild(Player player, Block block) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            hand = new ItemStack(Material.STONE);
        }
        firing = true;
        try {
            BlockPlaceEvent event = new BlockPlaceEvent(block, block.getState(), block.getRelative(BlockFace.DOWN),
                    hand, player, true, EquipmentSlot.HAND);
            Bukkit.getPluginManager().callEvent(event);
            return !event.isCancelled() && event.canBuild();
        } finally {
            firing = false;
        }
    }

    public static boolean canBreak(Player player, Block block) {
        firing = true;
        try {
            BlockBreakEvent event = new BlockBreakEvent(block, player);
            Bukkit.getPluginManager().callEvent(event);
            return !event.isCancelled();
        } finally {
            firing = false;
        }
    }
}
