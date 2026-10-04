package dev.the_fireplace.caterpillar.paper.listener;

import dev.the_fireplace.caterpillar.core.Facing;
import dev.the_fireplace.caterpillar.core.HeadCell;
import dev.the_fireplace.caterpillar.core.Machine;
import dev.the_fireplace.caterpillar.core.Pos;
import dev.the_fireplace.caterpillar.core.SegmentKind;
import dev.the_fireplace.caterpillar.paper.BuildGuard;
import dev.the_fireplace.caterpillar.paper.PartType;
import dev.the_fireplace.caterpillar.paper.SimplyCaterpillarPlugin;
import java.util.List;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

/**
 * Turns the placement of a tagged part item into a caterpillar structure. The vanilla placement is cancelled
 * and the plugin builds the blocks itself, after asking protection plugins about every block it will touch.
 */
public final class PlacementListener implements Listener {

    private final SimplyCaterpillarPlugin plugin;

    public PlacementListener(SimplyCaterpillarPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (BuildGuard.isSynthetic()) {
            return;
        }
        PartType type = plugin.items().identify(event.getItemInHand());
        if (type == null) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        if (!player.hasPermission("simplycaterpillar.place")) {
            player.sendActionBar(plugin.lang().get("msg.no-permission"));
            return;
        }

        Block placed = event.getBlockPlaced();
        Pos pos = new Pos(placed.getX(), placed.getY(), placed.getZ());
        // Bukkit has already put the item's block into the world while this event runs, so the clicked position
        // has to be judged by what was there before.
        BlockState replaced = event.getBlockReplacedState();
        boolean built = switch (type) {
            case DRILL_HEAD -> placeHead(player, pos, replaced);
            case DRILL_BASE -> placeSegment(player, pos, replaced, event.getBlockAgainst());
        };

        if (built && player.getGameMode() != GameMode.CREATIVE) {
            consume(player, event.getHand());
        }
    }

    /** {@code pos} is the bottom-centre block of the 3x3 cutting face; the base sits behind its middle. */
    private boolean placeHead(Player player, Pos pos, BlockState replaced) {
        World world = player.getWorld();
        Facing facing = Facing.fromYaw(player.getLocation().getYaw());
        Pos center = pos.add(0, 1, 0);
        Pos base = center.relative(facing, -1);

        if (pos.y() < world.getMinHeight() || center.y() + 1 >= world.getMaxHeight()) {
            player.sendActionBar(plugin.lang().get("msg.place.height"));
            return false;
        }

        List<HeadCell> cells = Machine.headCells(base, facing);
        for (HeadCell cell : cells) {
            if (!spaceIsFree(player, world, cell.pos(), replaced)) {
                return false;
            }
        }

        plugin.manager().create(player, world, facing, base);
        return true;
    }

    /**
     * Segments attach directly behind the last part of an existing caterpillar. The player does not have to hit
     * that exact block: clicking any part of the caterpillar, or a block next to the attach spot (the ground
     * behind it, say), snaps the segment to the correct position.
     */
    private boolean placeSegment(Player player, Pos clicked, BlockState replaced, Block against) {
        World world = player.getWorld();
        UUID worldId = world.getUID();

        Machine target = plugin.manager().at(against);
        if (target == null) {
            for (Machine candidate : plugin.manager().all()) {
                if (candidate.world().equals(worldId) && isNear(candidate.nextSegmentPos(), clicked)) {
                    target = candidate;
                    break;
                }
            }
        }
        if (target == null) {
            player.sendActionBar(plugin.lang().get("msg.place.not-behind"));
            return false;
        }
        if (!plugin.manager().canAccess(player, target)) {
            player.sendActionBar(plugin.lang().get("msg.not-owner"));
            return false;
        }

        Pos pos = target.nextSegmentPos();
        String problem = target.attachProblem(pos, plugin.settings().maxSegments);
        if (problem != null) {
            String key = switch (problem) {
                case "moving" -> "msg.place.moving";
                case "too_long" -> "msg.place.too-long";
                default -> "msg.place.not-behind";
            };
            player.sendActionBar(plugin.lang().get(key));
            return false;
        }
        if (!spaceIsFree(player, world, pos, replaced)) {
            return false;
        }

        plugin.manager().attachSegment(target, SegmentKind.SPACER, pos);
        return true;
    }

    private static boolean isNear(Pos spot, Pos clicked) {
        return Math.abs(spot.x() - clicked.x()) <= 1
                && Math.abs(spot.y() - clicked.y()) <= 1
                && Math.abs(spot.z() - clicked.z()) <= 1;
    }

    /** Checks that one block position can be built on, sending the player the reason if not. */
    private boolean spaceIsFree(Player player, World world, Pos pos, BlockState replaced) {
        if (plugin.manager().isPart(world.getUID(), pos)) {
            player.sendActionBar(plugin.lang().get("msg.place.overlap"));
            return false;
        }
        Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        boolean isClickedBlock = block.getX() == replaced.getX() && block.getY() == replaced.getY()
                && block.getZ() == replaced.getZ();
        boolean replaceable = isClickedBlock
                ? replaced.getType().isAir() || replaced.getBlockData().isReplaceable()
                : block.getType().isAir() || block.isReplaceable();
        if (!replaceable) {
            player.sendActionBar(plugin.lang().get("msg.place.no-room"));
            return false;
        }
        if (!world.getNearbyEntities(BoundingBox.of(block), entity -> entity instanceof LivingEntity).isEmpty()) {
            player.sendActionBar(plugin.lang().get("msg.place.entity-in-way"));
            return false;
        }
        if (!BuildGuard.canBuild(player, block)) {
            player.sendActionBar(plugin.lang().get("msg.place.protected"));
            return false;
        }
        return true;
    }

    private static void consume(Player player, EquipmentSlot hand) {
        ItemStack held = player.getInventory().getItem(hand);
        if (held.getType().isAir()) {
            return;
        }
        if (held.getAmount() <= 1) {
            player.getInventory().setItem(hand, null);
        } else {
            held.setAmount(held.getAmount() - 1);
            player.getInventory().setItem(hand, held);
        }
    }
}
