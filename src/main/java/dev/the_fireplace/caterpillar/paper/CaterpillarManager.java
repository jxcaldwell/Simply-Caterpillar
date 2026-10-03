package dev.the_fireplace.caterpillar.paper;

import dev.the_fireplace.caterpillar.core.Env;
import dev.the_fireplace.caterpillar.core.Facing;
import dev.the_fireplace.caterpillar.core.HeadCell;
import dev.the_fireplace.caterpillar.core.Machine;
import dev.the_fireplace.caterpillar.core.Params;
import dev.the_fireplace.caterpillar.core.Pos;
import dev.the_fireplace.caterpillar.core.SegmentKind;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

/**
 * Owns every caterpillar: ticks them, tracks which blocks belong to which machine, and persists everything to
 * {@code caterpillars.yml}.
 */
public final class CaterpillarManager {

    /** A block position in a particular world. */
    public record BlockKey(UUID world, int x, int y, int z) {
    }

    private static final int SAVE_INTERVAL_TICKS = 600;
    private static final int GUI_REFRESH_TICKS = 10;

    private final SimplyCaterpillarPlugin plugin;
    private final Map<UUID, Machine> machines = new LinkedHashMap<>();
    private final Map<UUID, HeadGui> guis = new HashMap<>();
    private final Map<BlockKey, UUID> occupancy = new HashMap<>();
    private final Map<UUID, List<BlockKey>> keysByMachine = new HashMap<>();
    private final Map<UUID, Integer> seenVersion = new HashMap<>();
    /** Saved machines that could not be restored yet (their world is not loaded). Kept so they are never lost. */
    private final Map<String, Map<String, Object>> pending = new LinkedHashMap<>();

    private BukkitTask task;
    private boolean dirty;
    private int tickCount;

    public CaterpillarManager(SimplyCaterpillarPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------- lookups

    public Collection<Machine> all() {
        return Collections.unmodifiableCollection(machines.values());
    }

    public Machine get(UUID id) {
        return machines.get(id);
    }

    public HeadGui gui(UUID machineId) {
        return guis.get(machineId);
    }

    public Machine at(UUID world, Pos pos) {
        UUID id = occupancy.get(new BlockKey(world, pos.x(), pos.y(), pos.z()));
        return id == null ? null : machines.get(id);
    }

    public Machine at(Block block) {
        return at(block.getWorld().getUID(), new Pos(block.getX(), block.getY(), block.getZ()));
    }

    public boolean isPart(UUID world, Pos pos) {
        return occupancy.containsKey(new BlockKey(world, pos.x(), pos.y(), pos.z()));
    }

    public boolean canAccess(Player player, Machine machine) {
        return player.getUniqueId().equals(machine.owner()) || player.hasPermission("simplycaterpillar.admin");
    }

    public void markDirty() {
        dirty = true;
    }

    // ---------------------------------------------------------------- lifecycle

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Applies configuration changes (tunables) to every running machine. */
    public void applySettings() {
        Params params = plugin.settings().params();
        for (Machine machine : machines.values()) {
            machine.setParams(params);
        }
    }

    private void tickAll() {
        tickCount++;
        Env env = plugin.env();

        for (Machine machine : new ArrayList<>(machines.values())) {
            try {
                boolean wasPowered = machine.powered();
                machine.tick(env);
                if (wasPowered || machine.powered()) {
                    dirty = true;
                }
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.SEVERE, "Caterpillar " + machine.id() + " failed and was switched off", ex);
                try {
                    machine.powerOff(env);
                } catch (RuntimeException ignored) {
                    // nothing more we can do for this machine
                }
            }
            Integer seen = seenVersion.get(machine.id());
            if (seen == null || seen != machine.layoutVersion()) {
                reindex(machine);
                dirty = true;
            }
        }

        if (tickCount % GUI_REFRESH_TICKS == 0) {
            for (Map.Entry<UUID, HeadGui> entry : guis.entrySet()) {
                Machine machine = machines.get(entry.getKey());
                if (machine != null && !entry.getValue().getInventory().getViewers().isEmpty()) {
                    entry.getValue().refresh(machine);
                }
            }
        }

        if (tickCount % SAVE_INTERVAL_TICKS == 0 && dirty) {
            save();
        }
    }

    // ---------------------------------------------------------------- creating and removing

    public Machine create(Player owner, World world, Facing facing, Pos base) {
        Machine machine = new Machine(UUID.randomUUID(), owner.getUniqueId(), world.getUID(), facing, base,
                plugin.settings().params());
        HeadGui gui = new HeadGui(machine.id(), plugin.lang());
        machines.put(machine.id(), machine);
        guis.put(machine.id(), gui);
        for (HeadCell cell : machine.headCells()) {
            plugin.env().placeCell(machine, cell);
        }
        gui.refresh(machine);
        reindex(machine);
        dirty = true;
        return machine;
    }

    public void attachSegment(Machine machine, SegmentKind kind, Pos pos) {
        machine.attachSegment(kind, pos, plugin.env());
        reindex(machine);
        dirty = true;
    }

    /** Removes one segment from the chain and the world, optionally dropping it as an item. */
    public void removeSegment(Machine machine, Pos pos, Location dropAt, boolean dropPart) {
        Machine.Segment segment = machine.detachSegment(pos);
        if (segment == null) {
            return;
        }
        plugin.env().clear(machine, pos);
        reindex(machine);
        dirty = true;
        if (dropPart) {
            drop(dropAt, plugin.items().create(PartType.forSegment(segment.kind()), 1));
        }
    }

    /**
     * Takes the whole machine apart: clears all its blocks and drops the stored items (and, if requested,
     * the parts themselves as items).
     */
    public void dismantle(Machine machine, Location dropAt, boolean dropParts) {
        List<ItemStack> drops = new ArrayList<>();
        if (dropParts) {
            drops.add(plugin.items().create(PartType.DRILL_HEAD, 1));
            for (Machine.Segment segment : machine.segments()) {
                drops.add(plugin.items().create(PartType.forSegment(segment.kind()), 1));
            }
        }

        HeadGui gui = guis.remove(machine.id());
        if (gui != null) {
            for (HumanEntity viewer : new ArrayList<>(gui.getInventory().getViewers())) {
                viewer.closeInventory();
            }
            for (int slot = 0; slot < HeadGui.SIZE; slot++) {
                if (HeadGui.isStorageSlot(slot)) {
                    ItemStack stack = gui.getInventory().getItem(slot);
                    if (stack != null && !stack.getType().isAir()) {
                        drops.add(stack);
                    }
                }
            }
        }

        for (Pos pos : machine.footprint()) {
            plugin.env().clear(machine, pos);
        }
        unindex(machine.id());
        machines.remove(machine.id());
        dirty = true;

        for (ItemStack stack : drops) {
            drop(dropAt, stack);
        }
    }

    private static void drop(Location at, ItemStack stack) {
        if (at.getWorld() != null) {
            at.getWorld().dropItemNaturally(at, stack);
        }
    }

    // ---------------------------------------------------------------- block index

    private void reindex(Machine machine) {
        unindex(machine.id());
        List<BlockKey> keys = new ArrayList<>();
        for (Pos pos : machine.footprint()) {
            BlockKey key = new BlockKey(machine.world(), pos.x(), pos.y(), pos.z());
            occupancy.put(key, machine.id());
            keys.add(key);
        }
        keysByMachine.put(machine.id(), keys);
        seenVersion.put(machine.id(), machine.layoutVersion());
    }

    private void unindex(UUID machineId) {
        List<BlockKey> old = keysByMachine.remove(machineId);
        if (old != null) {
            for (BlockKey key : old) {
                occupancy.remove(key, machineId);
            }
        }
        seenVersion.remove(machineId);
    }

    // ---------------------------------------------------------------- persistence

    private File dataFile() {
        return new File(plugin.getDataFolder(), "caterpillars.yml");
    }

    public void save() {
        YamlConfiguration out = new YamlConfiguration();

        for (Machine machine : machines.values()) {
            String path = "caterpillars." + machine.id();
            out.set(path + ".owner", machine.owner().toString());
            out.set(path + ".world", machine.world().toString());
            out.set(path + ".facing", machine.facing().name());
            out.set(path + ".base", coords(machine.base()));
            out.set(path + ".state.lit-time", machine.litTime());
            out.set(path + ".state.lit-duration", machine.litDuration());
            out.set(path + ".state.powered", machine.powered());
            out.set(path + ".state.moving", machine.moving());
            out.set(path + ".state.wave-index", machine.waveIndex());
            out.set(path + ".state.drill-timer", machine.drillTimer());
            out.set(path + ".state.wave-timer", machine.waveTimer());

            List<Map<String, Object>> segments = new ArrayList<>();
            for (Machine.Segment segment : machine.segments()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("kind", segment.kind().name());
                entry.put("pos", coords(segment.pos()));
                segments.add(entry);
            }
            out.set(path + ".segments", segments);

            HeadGui gui = guis.get(machine.id());
            if (gui != null) {
                for (int slot = 0; slot < HeadGui.SIZE; slot++) {
                    if (!HeadGui.isStorageSlot(slot)) {
                        continue;
                    }
                    ItemStack stack = gui.getInventory().getItem(slot);
                    if (stack != null && !stack.getType().isAir()) {
                        out.set(path + ".items." + slot, Base64.getEncoder().encodeToString(stack.serializeAsBytes()));
                    }
                }
            }
        }

        for (Map.Entry<String, Map<String, Object>> entry : pending.entrySet()) {
            out.set("caterpillars." + entry.getKey(), entry.getValue());
        }

        try {
            Files.createDirectories(plugin.getDataFolder().toPath());
            Path target = dataFile().toPath();
            Path temp = target.resolveSibling("caterpillars.yml.tmp");
            Files.writeString(temp, out.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save caterpillars.yml", ex);
        }
    }

    public void load() {
        File file = dataFile();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration in = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = in.getConfigurationSection("caterpillars");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            if (!restore(id, section)) {
                pending.put(id, plain(section));
            }
        }
        plugin.getLogger().info("Loaded " + machines.size() + " caterpillar(s)"
                + (pending.isEmpty() ? "" : ", " + pending.size() + " waiting for their world to load"));
    }

    /** Called when a world loads, to restore machines that were waiting for it. */
    public void onWorldLoaded() {
        Iterator<Map.Entry<String, Map<String, Object>>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Map<String, Object>> entry = it.next();
            ConfigurationSection section = new YamlConfiguration().createSection("e", entry.getValue());
            if (restore(entry.getKey(), section)) {
                it.remove();
            }
        }
    }

    private boolean restore(String idText, ConfigurationSection section) {
        try {
            UUID id = UUID.fromString(idText);
            UUID worldId = UUID.fromString(section.getString("world", ""));
            if (Bukkit.getWorld(worldId) == null) {
                return false;
            }
            UUID owner = UUID.fromString(section.getString("owner", ""));
            Facing facing = Facing.valueOf(section.getString("facing", ""));
            Pos base = pos(section.getIntegerList("base"));

            Machine machine = new Machine(id, owner, worldId, facing, base, plugin.settings().params());
            for (Map<?, ?> entry : section.getMapList("segments")) {
                SegmentKind kind = SegmentKind.valueOf(String.valueOf(entry.get("kind")));
                machine.restoreSegment(kind, pos((List<?>) entry.get("pos")));
            }
            machine.restoreState(
                    section.getInt("state.lit-time"),
                    section.getInt("state.lit-duration"),
                    section.getBoolean("state.powered"),
                    section.getBoolean("state.moving"),
                    section.getInt("state.wave-index"),
                    section.getInt("state.drill-timer"),
                    section.getInt("state.wave-timer"));

            HeadGui gui = new HeadGui(id, plugin.lang());
            ConfigurationSection items = section.getConfigurationSection("items");
            if (items != null) {
                for (String slotText : items.getKeys(false)) {
                    try {
                        int slot = Integer.parseInt(slotText);
                        if (HeadGui.isStorageSlot(slot)) {
                            byte[] bytes = Base64.getDecoder().decode(items.getString(slotText, ""));
                            gui.getInventory().setItem(slot, ItemStack.deserializeBytes(bytes));
                        }
                    } catch (RuntimeException ex) {
                        plugin.getLogger().log(Level.WARNING,
                                "Skipping an unreadable item in caterpillar " + idText + " slot " + slotText, ex);
                    }
                }
            }
            gui.refresh(machine);

            machines.put(id, machine);
            guis.put(id, gui);
            reindex(machine);
            return true;
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not restore caterpillar " + idText + ": " + ex.getMessage());
            return false;
        }
    }

    private static List<Integer> coords(Pos pos) {
        return List.of(pos.x(), pos.y(), pos.z());
    }

    private static Pos pos(List<?> values) {
        if (values == null || values.size() != 3) {
            throw new IllegalArgumentException("a position needs three numbers");
        }
        return new Pos(((Number) values.get(0)).intValue(), ((Number) values.get(1)).intValue(),
                ((Number) values.get(2)).intValue());
    }

    /** Copies a YAML section into plain maps so it can be written back unchanged. */
    private static Map<String, Object> plain(ConfigurationSection section) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            map.put(key, value instanceof ConfigurationSection nested ? plain(nested) : value);
        }
        return map;
    }

    /** Whether a creative-mode player's break should hand out parts. */
    public static boolean givesParts(Player player) {
        return player.getGameMode() != GameMode.CREATIVE;
    }
}
