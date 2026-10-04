package dev.the_fireplace.caterpillar.paper;

import dev.the_fireplace.caterpillar.core.Env;
import dev.the_fireplace.caterpillar.core.Facing;
import dev.the_fireplace.caterpillar.core.HeadCell;
import dev.the_fireplace.caterpillar.core.Machine;
import dev.the_fireplace.caterpillar.core.Msg;
import dev.the_fireplace.caterpillar.core.Pos;
import dev.the_fireplace.caterpillar.core.SegmentKind;
import dev.the_fireplace.caterpillar.core.Terrain;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

/** Implements the machine's view of the world with the Bukkit/Paper API. */
public final class PaperEnv implements Env {

    private final SimplyCaterpillarPlugin plugin;

    public PaperEnv(SimplyCaterpillarPlugin plugin) {
        this.plugin = plugin;
    }

    private Settings settings() {
        return plugin.settings();
    }

    private static World world(Machine machine) {
        return Bukkit.getWorld(machine.world());
    }

    private static Block block(World world, Pos pos) {
        return world.getBlockAt(pos.x(), pos.y(), pos.z());
    }

    private static BlockFace face(Facing facing) {
        return switch (facing) {
            case NORTH -> BlockFace.NORTH;
            case EAST -> BlockFace.EAST;
            case SOUTH -> BlockFace.SOUTH;
            case WEST -> BlockFace.WEST;
        };
    }

    private static BlockData data(Material material, Facing facing) {
        BlockData data = material.createBlockData();
        if (data instanceof Directional directional) {
            BlockFace face = face(facing);
            if (directional.getFaces().contains(face)) {
                directional.setFacing(face);
            }
        }
        return data;
    }

    // ---------------------------------------------------------------- world state

    @Override
    public boolean areaLoaded(Machine machine) {
        World world = world(machine);
        if (world == null) {
            return false;
        }
        for (Pos pos : machine.footprint()) {
            if (!world.isChunkLoaded(pos.chunkX(), pos.chunkZ())) {
                return false;
            }
        }
        for (Pos pos : machine.reach()) {
            if (!world.isChunkLoaded(pos.chunkX(), pos.chunkZ())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean ownerAvailable(Machine machine) {
        return Bukkit.getPlayer(machine.owner()) != null;
    }

    @Override
    public Terrain terrain(Machine machine, Pos pos) {
        World world = world(machine);
        if (world == null) {
            return Terrain.RESERVED;
        }
        if (pos.y() < world.getMinHeight() || pos.y() >= world.getMaxHeight()) {
            return Terrain.UNBREAKABLE;
        }
        if (plugin.manager().isPart(machine.world(), pos)) {
            return Terrain.RESERVED;
        }
        Block block = block(world, pos);
        Material type = block.getType();
        if (type.isAir()) {
            return Terrain.EMPTY;
        }
        if (block.isLiquid()) {
            return Terrain.FLUID;
        }
        if (settings().unbreakable.contains(type)) {
            return Terrain.UNBREAKABLE;
        }
        return Terrain.SOLID;
    }

    @Override
    public boolean insideBorder(Machine machine, Pos pos) {
        World world = world(machine);
        return world != null
                && world.getWorldBorder().isInside(new Location(world, pos.x() + 0.5, pos.y(), pos.z() + 0.5));
    }

    // ---------------------------------------------------------------- breaking

    @Override
    public boolean allowBreak(Machine machine, Pos pos) {
        World world = world(machine);
        Player owner = Bukkit.getPlayer(machine.owner());
        return world != null && owner != null && BuildGuard.canBreak(owner, block(world, pos));
    }

    @Override
    public boolean breakBlock(Machine machine, Pos pos) {
        World world = world(machine);
        if (world == null) {
            return false;
        }
        Block block = block(world, pos);
        if (settings().unbreakable.contains(block.getType())) {
            block.setType(Material.AIR, false);
            return true;
        }
        return block.breakNaturally(new ItemStack(settings().tool));
    }

    // ---------------------------------------------------------------- fuel

    @Override
    public int takeFuel(Machine machine) {
        HeadGui gui = plugin.manager().gui(machine.id());
        if (gui == null) {
            return 0;
        }
        Inventory inventory = gui.getInventory();
        ItemStack stack = inventory.getItem(HeadGui.FUEL);
        int burn = plugin.fuels().burnTime(stack);
        if (burn <= 0) {
            return 0;
        }
        if (stack.getAmount() > 1) {
            stack.setAmount(stack.getAmount() - 1);
            inventory.setItem(HeadGui.FUEL, stack);
        } else if (stack.getType() == Material.LAVA_BUCKET) {
            inventory.setItem(HeadGui.FUEL, new ItemStack(Material.BUCKET));
        } else {
            inventory.setItem(HeadGui.FUEL, null);
        }
        return burn;
    }

    // ---------------------------------------------------------------- rendering

    @Override
    public void placeCell(Machine machine, HeadCell cell) {
        World world = world(machine);
        if (world == null) {
            return;
        }
        Material material = switch (cell.role()) {
            case BASE -> settings().headBase;
            case BIT_CENTER -> settings().headBitCenter;
            case BIT_EDGE -> settings().headBit;
        };
        block(world, cell.pos()).setBlockData(data(material, machine.facing()), false);
    }

    @Override
    public void placeSegment(Machine machine, Pos pos, SegmentKind kind) {
        World world = world(machine);
        if (world == null) {
            return;
        }
        Material material = settings().partMaterial(PartType.forSegment(kind));
        block(world, pos).setBlockData(data(material, machine.facing()), false);
    }

    @Override
    public boolean cartSpace(Machine machine, Pos pos) {
        World world = world(machine);
        if (world == null || pos.y() < world.getMinHeight() || pos.y() >= world.getMaxHeight()) {
            return false;
        }
        if (plugin.manager().isPart(machine.world(), pos)) {
            return false;
        }
        Block block = block(world, pos);
        return block.getType().isAir() || block.isLiquid() || block.isPassable();
    }

    @Override
    public void segmentMoved(Machine machine, Machine.Segment segment) {
        switch (segment.kind()) {
            case SEAT -> plugin.manager().seats().follow(machine, segment);
            case TRANSPORTER -> plugin.manager().moveCart(machine, segment);
            default -> { }
        }
    }

    @Override
    public void segmentTick(Machine machine, Machine.Segment segment) {
        switch (segment.kind()) {
            case COLLECTOR -> collect(machine, segment);
            case TRANSPORTER -> plugin.manager().tickTransporter(machine, segment);
            case INCINERATOR -> {
                IncineratorGui filter = plugin.manager().incinerator(segment.id());
                if (filter != null) {
                    Set<Material> types = filter.types();
                    if (!types.isEmpty()) {
                        plugin.manager().incinerateGathered(machine, types);
                    }
                }
            }
            default -> { }
        }
    }

    /** Pulls dropped items near the collector into the caterpillar's gathered slots. */
    private void collect(Machine machine, Machine.Segment segment) {
        World world = world(machine);
        if (world == null) {
            return;
        }
        Pos pos = segment.pos();
        BoundingBox area = BoundingBox.of(block(world, pos)).expand(settings().collectorRadius);
        for (Entity entity : world.getNearbyEntities(area, candidate -> candidate instanceof Item)) {
            Item item = (Item) entity;
            if (!item.isValid()) {
                continue;
            }
            ItemStack rest = plugin.manager().depositGathered(machine, item.getItemStack());
            if (rest == null) {
                item.remove();
            } else {
                item.setItemStack(rest);
            }
        }
    }

    @Override
    public void clear(Machine machine, Pos pos) {
        World world = world(machine);
        if (world != null) {
            block(world, pos).setType(Material.AIR, false);
        }
    }

    @Override
    public void setDrilling(Machine machine, boolean drilling) {
        World world = world(machine);
        if (world == null) {
            return;
        }
        Block center = block(world, machine.base().offset(machine.facing(), 1, 0, 0));
        // A redstone lamp switches itself off again without power, so the working look is a different block.
        Material wanted = drilling ? settings().headBitCenterActive : settings().headBitCenter;
        if (center.getType() != wanted) {
            center.setBlockData(data(wanted, machine.facing()), false);
        }
    }

    // ---------------------------------------------------------------- feedback

    @Override
    public void drillEffects(Machine machine) {
        World world = world(machine);
        if (world == null || !settings().particles) {
            return;
        }
        Pos front = machine.base().offset(machine.facing(), 2, 0, 0);
        Location at = new Location(world, front.x() + 0.5, front.y() + 0.5, front.z() + 0.5);
        world.spawnParticle(Particle.SMOKE, at, 8, 0.9, 0.9, 0.9, 0.0);
    }

    @Override
    public void moveSound(Machine machine, Pos pos) {
        World world = world(machine);
        if (world == null || !settings().sounds) {
            return;
        }
        world.playSound(new Location(world, pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5),
                Sound.BLOCK_PISTON_EXTEND, SoundCategory.BLOCKS, 1.0f, 1.0f);
    }

    @Override
    public void notifyOwner(Machine machine, Msg msg) {
        Player owner = Bukkit.getPlayer(machine.owner());
        if (owner != null) {
            owner.sendActionBar(plugin.lang().get("msg.stop." + msg.key));
        }
    }
}
