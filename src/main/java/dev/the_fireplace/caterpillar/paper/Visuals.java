package dev.the_fireplace.caterpillar.paper;

import dev.the_fireplace.caterpillar.core.Facing;
import dev.the_fireplace.caterpillar.core.HeadCell;
import dev.the_fireplace.caterpillar.core.Machine;
import dev.the_fireplace.caterpillar.core.Pos;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The original mod's 3D models, shown on caterpillars to players who have the resource pack.
 *
 * <p>Each part gets one or more item display entities (some original parts were three or five blocks wide; their
 * side pieces are shown as models only). The displays are invisible to everyone by default and shown only to
 * players who loaded the pack. For those players the plain placeholder blocks are replaced, on their client only, by
 * barrier blocks: invisible, but still solid and clickable. Everyone else, Bedrock players included, keeps seeing the
 * plain blocks.
 */
public final class Visuals {

    private static final String NS = "simplycaterpillar";
    private static final BlockData HIDDEN = Material.BARRIER.createBlockData();

    /**
     * One model at one position. {@code onlyIfFree} marks the side pieces of the originally wider parts (storage
     * chests, reinforcement pistons, collector hopper...): they stand in the tunnel next to the part and are only
     * drawn where nothing else is, so they never overlap rails, fences or other real blocks.
     */
    private record Element(Pos pos, String model, boolean onlyIfFree) {
        Element(Pos pos, String model) {
            this(pos, model, false);
        }
    }

    /** Slightly larger than a block, so a model always draws over a plain block in the same place (no flicker). */
    private static final float SCALE = 1.002f;
    /** Ticks before a display that vanished may be spawned again, so a half-loaded chunk cannot cause a spawn storm. */
    private static final int RESPAWN_COOLDOWN = 100;

    private final SimplyCaterpillarPlugin plugin;
    private final Set<UUID> packPlayers = new HashSet<>();
    /** Display entities per caterpillar, by element key. */
    private final Map<UUID, Map<String, ItemDisplay>> displays = new HashMap<>();
    private final Map<UUID, Map<String, String>> shownModels = new HashMap<>();
    private final Map<UUID, Integer> shownState = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> lastSpawn = new HashMap<>();
    /** Caterpillars whose hidden blocks must be re-sent on the next tick (after the server sent the real ones). */
    private Set<UUID> resendNext = new HashSet<>();

    public Visuals(SimplyCaterpillarPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean enabled() {
        return plugin.settings().packEnabled && plugin.settings().worldModels;
    }

    public boolean hasPack(Player player) {
        return packPlayers.contains(player.getUniqueId());
    }

    // ---------------------------------------------------------------- players

    public void packLoaded(Player player) {
        packPlayers.add(player.getUniqueId());
        for (Map<String, ItemDisplay> byKey : displays.values()) {
            for (ItemDisplay display : byKey.values()) {
                if (display.isValid()) {
                    player.showEntity(plugin, display);
                }
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Machine machine : plugin.manager().all()) {
                hideBlocks(machine, player);
            }
        });
    }

    public void packGone(Player player) {
        packPlayers.remove(player.getUniqueId());
    }

    /** A pack player received a chunk: hide the placeholder blocks in it again (after the chunk data arrived). */
    public void chunkSent(Player player, Chunk chunk) {
        if (!hasPack(player) || !enabled()) {
            return;
        }
        UUID world = chunk.getWorld().getUID();
        int cx = chunk.getX();
        int cz = chunk.getZ();
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Machine machine : plugin.manager().all()) {
                if (!machine.world().equals(world)) {
                    continue;
                }
                for (Pos pos : machine.footprint()) {
                    if (pos.chunkX() == cx && pos.chunkZ() == cz) {
                        hideBlocks(machine, player);
                        break;
                    }
                }
            }
        });
    }

    // ---------------------------------------------------------------- per tick

    /** Called at the start of every manager tick, before the machines run. */
    public void beforeTick() {
        if (resendNext.isEmpty()) {
            return;
        }
        Set<UUID> due = resendNext;
        resendNext = new HashSet<>();
        for (UUID id : due) {
            Machine machine = plugin.manager().get(id);
            if (machine != null) {
                hideBlocks(machine);
            }
        }
    }

    /** Called after a machine ticked; updates its models if something visible changed. */
    public void afterTick(Machine machine, boolean periodic) {
        if (!enabled()) {
            return;
        }
        int state = Objects.hash(machine.layoutVersion(), machine.drillingVisual());
        Integer shown = shownState.get(machine.id());
        if (periodic || shown == null || shown != state) {
            if (sync(machine)) {
                shownState.put(machine.id(), state);
            }
            resendNext.add(machine.id());
        }
    }

    // ---------------------------------------------------------------- displays

    /** Makes the displays match the machine. Returns false if its area is not loaded yet. */
    private boolean sync(Machine machine) {
        World world = Bukkit.getWorld(machine.world());
        if (world == null || !plugin.env().areaLoaded(machine)) {
            return false;
        }
        for (Pos pos : machine.footprint()) {
            if (!world.getChunkAt(pos.chunkX(), pos.chunkZ()).isEntitiesLoaded()) {
                return false;
            }
        }
        Map<String, Element> wanted = elements(machine);
        wanted.values().removeIf(element -> element.onlyIfFree() && !world.getBlockAt(
                element.pos().x(), element.pos().y(), element.pos().z()).getType().isAir());
        Map<String, ItemDisplay> current = displays.computeIfAbsent(machine.id(), id -> new HashMap<>());
        Map<String, String> models = shownModels.computeIfAbsent(machine.id(), id -> new HashMap<>());
        Transformation rotation = rotation(machine.facing());

        Iterator<Map.Entry<String, ItemDisplay>> it = current.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ItemDisplay> entry = it.next();
            if (!wanted.containsKey(entry.getKey()) || !entry.getValue().isValid()) {
                entry.getValue().remove();
                it.remove();
                models.remove(entry.getKey());
            }
        }

        for (Map.Entry<String, Element> entry : wanted.entrySet()) {
            Element element = entry.getValue();
            Location at = new Location(world, element.pos().x() + 0.5, element.pos().y() + 0.5,
                    element.pos().z() + 0.5);
            ItemDisplay display = current.get(entry.getKey());
            if (display == null) {
                Map<String, Integer> spawned = lastSpawn.computeIfAbsent(machine.id(), id -> new HashMap<>());
                Integer last = spawned.get(entry.getKey());
                int now = Bukkit.getCurrentTick();
                if (last != null && now - last < RESPAWN_COOLDOWN) {
                    continue;
                }
                spawned.put(entry.getKey(), now);
                display = world.spawn(at, ItemDisplay.class, spawned -> {
                    spawned.setItemStack(modelItem(element.model()));
                    spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                    spawned.setTransformation(rotation);
                    spawned.setPersistent(false);
                    spawned.setVisibleByDefault(false);
                    spawned.setTeleportDuration(3);
                });
                for (UUID viewer : packPlayers) {
                    Player player = Bukkit.getPlayer(viewer);
                    if (player != null) {
                        player.showEntity(plugin, display);
                    }
                }
                current.put(entry.getKey(), display);
                models.put(entry.getKey(), element.model());
                continue;
            }
            Location now = display.getLocation();
            if (now.getBlockX() != element.pos().x() || now.getBlockY() != element.pos().y()
                    || now.getBlockZ() != element.pos().z()) {
                display.teleport(at);
            }
            if (!element.model().equals(models.get(entry.getKey()))) {
                display.setItemStack(modelItem(element.model()));
                models.put(entry.getKey(), element.model());
            }
        }
        return true;
    }

    private static ItemStack modelItem(String model) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(new NamespacedKey(NS, model));
        stack.setItemMeta(meta);
        return stack;
    }

    /** The original models face north; turn them to the caterpillar's direction (clockwise seen from above). */
    private static Transformation rotation(Facing facing) {
        int degrees = switch (facing) {
            case NORTH -> 0;
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
        };
        return new Transformation(new Vector3f(), new Quaternionf().rotateY((float) Math.toRadians(-degrees)),
                new Vector3f(SCALE, SCALE, SCALE), new Quaternionf());
    }

    /** Every model a caterpillar shows, by a stable key (so a display follows its part when it moves). */
    private static Map<String, Element> elements(Machine machine) {
        Map<String, Element> out = new LinkedHashMap<>();
        Facing facing = machine.facing();
        String drilling = machine.drillingVisual() ? "_drilling" : "";
        Pos base = machine.base();
        for (HeadCell cell : machine.headCells()) {
            if (cell.role() == HeadCell.Role.BASE) {
                out.put("head", new Element(cell.pos(), "drill_head_base" + drilling));
                continue;
            }
            int up = cell.pos().y() - base.y();
            Facing right = facing.right();
            int side = (cell.pos().x() - base.x()) * right.dx + (cell.pos().z() - base.z()) * right.dz;
            String name = "drill_head_bit_" + bitName(up, side) + drilling;
            out.put("bit:" + up + ":" + side, new Element(cell.pos(), name));
        }
        for (Machine.Segment segment : machine.segments()) {
            String key = "seg:" + segment.id() + ":";
            Pos pos = segment.pos();
            switch (segment.kind()) {
                case SPACER -> out.put(key + "c", new Element(pos, "drill_base_block"));
                case STORAGE -> {
                    out.put(key + "c", new Element(pos, "drill_base_block"));
                    out.put(key + "l", new Element(pos.offset(facing, 0, 0, -1), "storage_left", true));
                    out.put(key + "r", new Element(pos.offset(facing, 0, 0, 1), "storage_right", true));
                }
                case COLLECTOR -> {
                    out.put(key + "c", new Element(pos, "drill_base_block"));
                    out.put(key + "b", new Element(pos.add(0, -1, 0), "collector_lower", true));
                }
                case INCINERATOR -> out.put(key + "c", new Element(pos, "incinerator_block"));
                case SEAT -> out.put(key + "c", new Element(pos, "drill_seat_block"));
                case TRANSPORTER -> {
                    out.put(key + "c", new Element(pos, "transporter_base"));
                    if (segment.cart()) {
                        out.put(key + "cart", new Element(segment.cartPos(), "transporter_lower"));
                    }
                }
                case REINFORCEMENT -> {
                    out.put(key + "c", new Element(pos, "reinforcement_base"));
                    out.put(key + "t", new Element(pos.add(0, 1, 0), "reinforcement_top", true));
                    out.put(key + "b", new Element(pos.add(0, -1, 0), "reinforcement_bottom", true));
                    out.put(key + "l", new Element(pos.offset(facing, 0, 0, -1), "reinforcement_left", true));
                    out.put(key + "r", new Element(pos.offset(facing, 0, 0, 1), "reinforcement_right", true));
                }
                case DECORATION -> {
                    out.put(key + "c", new Element(pos, "drill_base_block"));
                    out.put(key + "l", new Element(pos.offset(facing, 0, 0, -1), "decoration_left", true));
                    out.put(key + "r", new Element(pos.offset(facing, 0, 0, 1), "decoration_right", true));
                }
            }
        }
        return out;
    }

    private static String bitName(int up, int side) {
        String vertical = up > 0 ? "top" : up < 0 ? "bottom" : "";
        String horizontal = side < 0 ? "left" : side > 0 ? "right" : "";
        if (vertical.isEmpty() && horizontal.isEmpty()) {
            return "middle";
        }
        if (vertical.isEmpty()) {
            return horizontal;
        }
        return horizontal.isEmpty() ? vertical : vertical + "_" + horizontal;
    }

    public void remove(UUID machineId) {
        Map<String, ItemDisplay> byKey = displays.remove(machineId);
        if (byKey != null) {
            byKey.values().forEach(ItemDisplay::remove);
        }
        shownModels.remove(machineId);
        shownState.remove(machineId);
        lastSpawn.remove(machineId);
    }

    public void removeAll() {
        for (UUID id : new ArrayList<>(displays.keySet())) {
            remove(id);
        }
    }

    // ---------------------------------------------------------------- hidden placeholder blocks

    private void hideBlocks(Machine machine) {
        for (UUID viewer : packPlayers) {
            Player player = Bukkit.getPlayer(viewer);
            if (player != null) {
                hideBlocks(machine, player);
            }
        }
    }

    /** Shows the player barriers (invisible, still solid) instead of the plain blocks, if the caterpillar is near. */
    private void hideBlocks(Machine machine, Player player) {
        if (!enabled() || !player.getWorld().getUID().equals(machine.world())) {
            return;
        }
        int range = (player.getClientViewDistance() + 1) * 16;
        Location eye = player.getLocation();
        Pos base = machine.base();
        double dx = eye.getX() - base.x();
        double dz = eye.getZ() - base.z();
        if (dx * dx + dz * dz > (double) range * range + 64 * 64) {
            return;
        }
        World world = player.getWorld();
        List<Pos> footprint = machine.footprint();
        for (Pos pos : footprint) {
            player.sendBlockChange(new Location(world, pos.x(), pos.y(), pos.z()), HIDDEN);
        }
    }
}
