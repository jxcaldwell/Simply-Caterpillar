package dev.the_fireplace.caterpillar.paper;

import dev.the_fireplace.caterpillar.core.Facing;
import dev.the_fireplace.caterpillar.core.Machine;
import dev.the_fireplace.caterpillar.core.Pos;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rail;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * What the reinforcement builder and the decoration placer do each time they step forward. Every block they place
 * is taken from the caterpillar's consumption slots and checked against protection plugins as the owner.
 */
public final class Builders {

    private final SimplyCaterpillarPlugin plugin;

    public Builders(SimplyCaterpillarPlugin plugin) {
        this.plugin = plugin;
    }

    private static BlockFace face(Facing facing) {
        return switch (facing) {
            case NORTH -> BlockFace.NORTH;
            case EAST -> BlockFace.EAST;
            case SOUTH -> BlockFace.SOUTH;
            case WEST -> BlockFace.WEST;
        };
    }

    // ---------------------------------------------------------------- reinforcement

    /**
     * Lines the tunnel around the segment, two blocks ahead to two blocks behind, as in the original mod: the
     * ring of blocks just outside the 3x3 tunnel (ceiling and floor five wide, walls three high).
     */
    public void reinforce(Machine machine, Machine.Segment segment) {
        ReinforcementGui settings = plugin.manager().reinforcement(segment.id());
        World world = Bukkit.getWorld(machine.world());
        Player owner = Bukkit.getPlayer(machine.owner());
        if (settings == null || world == null || owner == null) {
            return;
        }
        Facing facing = machine.facing();
        for (int forward = -2; forward <= 2; forward++) {
            for (int position = 0; position < ReinforcementGui.POSITIONS; position++) {
                Material material = settings.material(position);
                if (material == null) {
                    continue;
                }
                Pos pos = segment.pos().offset(facing, forward, upOf(position), rightOf(position));
                Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
                if (needsReinforcement(machine, settings, ReinforcementGui.sideOf(position), block, material)) {
                    place(machine, owner, block, material, material.createBlockData(), true, PartType.REINFORCEMENT);
                }
            }
        }
    }

    /** Height of a reinforcement position relative to the middle of the tunnel. */
    private static int upOf(int position) {
        return switch (ReinforcementGui.sideOf(position)) {
            case CEILING -> 2;
            case FLOOR -> -2;
            case LEFT -> 1 - (position - 5);
            case RIGHT -> 1 - (position - 8);
        };
    }

    /** Sideways offset of a reinforcement position (negative = left). */
    private static int rightOf(int position) {
        return switch (ReinforcementGui.sideOf(position)) {
            case CEILING -> position - 2;
            case FLOOR -> position - 13;
            case LEFT -> -2;
            case RIGHT -> 2;
        };
    }

    private boolean needsReinforcement(Machine machine, ReinforcementGui settings, ReinforcementGui.Side side,
                                       Block block, Material material) {
        Material current = block.getType();
        if (current == material || plugin.manager().isPart(machine.world(),
                new Pos(block.getX(), block.getY(), block.getZ()))) {
            return false;
        }
        if (plugin.settings().unbreakable.contains(current)) {
            return false;
        }
        if (settings.replaces(side, ReinforcementGui.Replace.WATER) && current == Material.WATER) {
            return true;
        }
        if (settings.replaces(side, ReinforcementGui.Replace.LAVA) && current == Material.LAVA) {
            return true;
        }
        if (settings.replaces(side, ReinforcementGui.Replace.FALLING) && current.hasGravity()) {
            return true;
        }
        if (settings.replaces(side, ReinforcementGui.Replace.AIR)
                && (current.isAir() || (block.isReplaceable() && !block.isLiquid()))) {
            return true;
        }
        // "All": anything else, except blocks that hold data (chests, signs, spawners...), which are never touched.
        return settings.replaces(side, ReinforcementGui.Replace.ALL) && !(block.getState(false) instanceof TileState);
    }

    // ---------------------------------------------------------------- decoration

    /** Places the next pattern in the tunnel slice the segment has just left. */
    public void decorate(Machine machine, Machine.Segment segment) {
        DecorationGui settings = plugin.manager().decoration(segment.id());
        World world = Bukkit.getWorld(machine.world());
        Player owner = Bukkit.getPlayer(machine.owner());
        if (settings == null || world == null || owner == null) {
            return;
        }
        int pattern = settings.advance();
        Facing facing = machine.facing();
        for (int position = 0; position < DecorationGui.POSITIONS; position++) {
            Material material = settings.material(pattern, position);
            if (material == null) {
                continue;
            }
            int up = position <= 2 ? 1 : position <= 4 ? 0 : -1;
            int right = switch (position) {
                case 0, 3, 5 -> -1;
                case 1, 6 -> 0;
                default -> 1;
            };
            Pos pos = segment.pos().offset(facing, -1, up, right);
            Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
            if (plugin.manager().isPart(machine.world(), pos)
                    || !(block.getType().isAir() || block.isReplaceable() || block.isLiquid())
                    || block.getType() == material) {
                continue;
            }
            BlockData data = orient(material, facing, block, right);
            if (data != null) {
                place(machine, owner, block, material, data, false, PartType.DECORATION);
            }
        }
    }

    /**
     * Works out how a decoration block should be placed: rails along the tunnel, directional blocks facing forward,
     * and torches on the floor or, where they would not stand, on the nearest wall. Returns null if it cannot stay.
     */
    private BlockData orient(Material material, Facing facing, Block block, int right) {
        BlockData data = material.createBlockData();
        if (data instanceof Rail rail) {
            Rail.Shape shape = (facing == Facing.NORTH || facing == Facing.SOUTH)
                    ? Rail.Shape.NORTH_SOUTH : Rail.Shape.EAST_WEST;
            if (rail.getShapes().contains(shape)) {
                rail.setShape(shape);
            }
        } else if (data instanceof Directional directional && directional.getFaces().contains(face(facing))) {
            directional.setFacing(face(facing));
        }
        if (block.canPlace(data)) {
            return data;
        }

        Material wall = wallVariant(material);
        if (wall == null) {
            return null;
        }
        List<BlockFace> tries = new ArrayList<>();
        if (right < 0) {
            tries.add(face(facing.right()));
        } else if (right > 0) {
            tries.add(face(facing.left()));
        }
        tries.add(face(facing.opposite()));
        tries.add(face(facing));
        for (BlockFace pointing : tries) {
            BlockData wallData = wall.createBlockData();
            if (wallData instanceof Directional directional && directional.getFaces().contains(pointing)) {
                directional.setFacing(pointing);
                if (block.canPlace(wallData)) {
                    return wallData;
                }
            }
        }
        return null;
    }

    /** The wall-mounted form of a torch (TORCH -> WALL_TORCH, SOUL_TORCH -> SOUL_WALL_TORCH, ...), or null. */
    private static Material wallVariant(Material material) {
        String name = material.name();
        if (!name.endsWith("TORCH") || name.contains("WALL")) {
            return null;
        }
        Material wall = Material.matchMaterial(name.substring(0, name.length() - "TORCH".length()) + "WALL_TORCH");
        return wall != null && wall.isBlock() ? wall : null;
    }

    // ---------------------------------------------------------------- shared

    /**
     * Places one block if the owner may build there and the caterpillar has the item in its consumption slots. A
     * solid block that is being replaced is broken first so its drops are not lost.
     */
    private void place(Machine machine, Player owner, Block block, Material item, BlockData data, boolean breakFirst,
                       PartType part) {
        boolean mustBreak = breakFirst && !block.getType().isAir() && !block.isLiquid() && !block.isReplaceable();
        if (!BuildGuard.canBuild(owner, block) || (mustBreak && !BuildGuard.canBreak(owner, block))) {
            return;
        }
        if (!plugin.manager().takeConsumption(machine, item)) {
            plugin.manager().warnShortage(machine, item, part);
            return;
        }
        if (mustBreak) {
            block.breakNaturally(new ItemStack(plugin.settings().tool));
        }
        block.setBlockData(data, true);
    }
}
