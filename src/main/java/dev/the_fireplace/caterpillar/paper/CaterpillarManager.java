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
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

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
    /** Per-segment data, keyed by segment id. */
    private final Map<UUID, StorageGui> storages = new HashMap<>();
    private final Map<UUID, IncineratorGui> incinerators = new HashMap<>();
    private final Map<UUID, TransporterGui> transporters = new HashMap<>();
    private final Map<UUID, ReinforcementGui> reinforcements = new HashMap<>();
    private final Map<UUID, DecorationGui> decorations = new HashMap<>();
    private final Builders builders;
    private final Seats seats;
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
        this.seats = new Seats(plugin);
        this.builders = new Builders(plugin);
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

    public StorageGui storage(UUID segmentId) {
        return storages.get(segmentId);
    }

    public IncineratorGui incinerator(UUID segmentId) {
        return incinerators.get(segmentId);
    }

    public TransporterGui transporter(UUID segmentId) {
        return transporters.get(segmentId);
    }

    public ReinforcementGui reinforcement(UUID segmentId) {
        return reinforcements.get(segmentId);
    }

    public DecorationGui decoration(UUID segmentId) {
        return decorations.get(segmentId);
    }

    public Builders builders() {
        return builders;
    }

    public Seats seats() {
        return seats;
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
        seats.removeAll();
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

        if (tickCount % 5 == 0) {
            seats.cleanup();
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

    // ---------------------------------------------------------------- transporters

    /** Takes one item from the consumption slots: the drill head's first, then each storage segment's. */
    public boolean takeConsumption(Machine machine, Material material) {
        HeadGui head = guis.get(machine.id());
        if (head != null && takeOne(head.getInventory(), HeadGui.CONSUMPTION_START, HeadGui.CONSUMPTION_END, material)) {
            dirty = true;
            return true;
        }
        for (Machine.Segment segment : machine.segments()) {
            StorageGui storage = storages.get(segment.id());
            if (storage != null && takeOne(storage.getInventory(), StorageGui.CONSUMPTION_START,
                    StorageGui.CONSUMPTION_END, material)) {
                dirty = true;
                return true;
            }
        }
        return false;
    }

    private static boolean takeOne(org.bukkit.inventory.Inventory inventory, int from, int to, Material material) {
        for (int slot = from; slot <= to; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && stack.getType() == material) {
                if (stack.getAmount() <= 1) {
                    inventory.setItem(slot, null);
                } else {
                    stack.setAmount(stack.getAmount() - 1);
                    inventory.setItem(slot, stack);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * One round of a transporter's work: fetch a chest minecart if it has none, move full stacks of gathered items
     * into the cart, and send the cart off once every slot is a full stack.
     */
    public void tickTransporter(Machine machine, Machine.Segment segment) {
        TransporterGui data = transporters.get(segment.id());
        World world = Bukkit.getWorld(machine.world());
        if (data == null || world == null) {
            return;
        }

        if (!segment.cart()) {
            Pos below = segment.cartPos();
            if (!plugin.env().cartSpace(machine, below) || !takeConsumption(machine, Material.CHEST_MINECART)) {
                return;
            }
            Block block = world.getBlockAt(below.x(), below.y(), below.z());
            data.setPreviousBlock(block.getBlockData().getAsString());
            block.setBlockData(plugin.settings().transporterCart.createBlockData(), false);
            machine.setCart(segment, true);
            dirty = true;
        }

        HeadGui head = guis.get(machine.id());
        if (head != null) {
            moveFullStacks(head.getInventory(), HeadGui.GATHERED_START, HeadGui.GATHERED_END, data);
        }
        for (Machine.Segment other : machine.segments()) {
            StorageGui storage = storages.get(other.id());
            if (storage != null) {
                moveFullStacks(storage.getInventory(), StorageGui.GATHERED_START, StorageGui.GATHERED_END, data);
            }
        }

        if (data.isFull()) {
            releaseCart(machine, segment, data, world);
        }
    }

    private void moveFullStacks(org.bukkit.inventory.Inventory from, int first, int last, TransporterGui data) {
        for (int slot = first; slot <= last; slot++) {
            ItemStack stack = from.getItem(slot);
            if (stack != null && !stack.getType().isAir() && stack.getAmount() >= stack.getMaxStackSize()
                    && data.addStack(stack.clone())) {
                from.setItem(slot, null);
                dirty = true;
            }
        }
    }

    /** Sends the loaded chest minecart off and puts back the block the cart was covering. */
    private void releaseCart(Machine machine, Machine.Segment segment, TransporterGui data, World world) {
        Pos cartPos = segment.cartPos();
        ItemStack[] cargo = data.getInventory().getContents();
        data.getInventory().clear();

        String previous = data.previousBlock();
        restoreBlock(world, cartPos, previous);
        data.setPreviousBlock(null);
        machine.setCart(segment, false);
        dirty = true;

        Location at = new Location(world, cartPos.x() + 0.5, cartPos.y(), cartPos.z() + 0.5);
        StorageMinecart cart = world.spawn(at, StorageMinecart.class, spawned -> spawned.getInventory().setContents(cargo));
        if (previous != null && Tag.RAILS.isTagged(restoreData(previous).getMaterial())) {
            // Roll back down the rails, away from the drill.
            cart.setVelocity(new Vector(-machine.facing().dx * 0.4, 0, -machine.facing().dz * 0.4));
        }
    }

    /** Moves a transporter's cart block along with the segment, which has just stepped one block forward. */
    public void moveCart(Machine machine, Machine.Segment segment) {
        TransporterGui data = transporters.get(segment.id());
        World world = Bukkit.getWorld(machine.world());
        if (data == null || world == null || !segment.cart()) {
            return;
        }
        Pos to = segment.cartPos();
        Pos from = to.relative(machine.facing(), -1);
        Block target = world.getBlockAt(to.x(), to.y(), to.z());
        String coveredNow = target.getBlockData().getAsString();
        restoreBlock(world, from, data.previousBlock());
        data.setPreviousBlock(coveredNow);
        target.setBlockData(plugin.settings().transporterCart.createBlockData(), false);
        dirty = true;
    }

    /** A player broke the cart block under a transporter: give back the minecart and its cargo. */
    public void breakCart(Machine machine, Machine.Segment segment, Location dropAt, boolean dropMinecart) {
        TransporterGui data = transporters.get(segment.id());
        if (data == null) {
            return;
        }
        World world = Bukkit.getWorld(machine.world());
        Pos cartPos = segment.cartPos();
        String previous = data.previousBlock();
        data.setPreviousBlock(null);
        machine.setCart(segment, false);
        reindex(machine);
        dirty = true;

        for (ItemStack stack : data.getInventory().getContents()) {
            if (stack != null && !stack.getType().isAir()) {
                drop(dropAt, stack);
            }
        }
        data.getInventory().clear();
        if (dropMinecart) {
            drop(dropAt, new ItemStack(Material.CHEST_MINECART));
        }
        // The break itself finishes after this event, so the old block is put back a tick later.
        Bukkit.getScheduler().runTask(plugin, () -> restoreBlock(world, cartPos, previous));
    }

    private static org.bukkit.block.data.BlockData restoreData(String data) {
        try {
            return Bukkit.createBlockData(data);
        } catch (IllegalArgumentException ex) {
            return Material.AIR.createBlockData();
        }
    }

    private static void restoreBlock(World world, Pos pos, String data) {
        if (world == null) {
            return;
        }
        world.getBlockAt(pos.x(), pos.y(), pos.z()).setBlockData(
                data == null ? Material.AIR.createBlockData() : restoreData(data), false);
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
        Machine.Segment segment = machine.attachSegment(kind, pos, plugin.env());
        createSegmentData(machine, segment);
        IncineratorGui filter = incinerators.get(segment.id());
        if (filter != null) {
            filter.fillDefaults();
        }
        ReinforcementGui reinforcement = reinforcements.get(segment.id());
        if (reinforcement != null) {
            reinforcement.fillDefaults();
        }
        DecorationGui decoration = decorations.get(segment.id());
        if (decoration != null) {
            decoration.fillDefaults();
        }
        reindex(machine);
        dirty = true;
    }

    /** Creates the inventory a segment of this kind needs (storage contents, incinerator filter). */
    private void createSegmentData(Machine machine, Machine.Segment segment) {
        switch (segment.kind()) {
            case STORAGE -> storages.put(segment.id(), new StorageGui(machine.id(), segment.id(), plugin.lang()));
            case INCINERATOR ->
                    incinerators.put(segment.id(), new IncineratorGui(machine.id(), segment.id(), plugin.lang()));
            case TRANSPORTER ->
                    transporters.put(segment.id(), new TransporterGui(machine.id(), segment.id(), plugin.lang()));
            case REINFORCEMENT ->
                    reinforcements.put(segment.id(), new ReinforcementGui(machine.id(), segment.id(), plugin.lang()));
            case DECORATION ->
                    decorations.put(segment.id(), new DecorationGui(machine.id(), segment.id(), plugin.lang()));
            default -> { }
        }
    }

    /** Closes the GUI of a segment, returns what it stored, and forgets its per-segment data. */
    private List<ItemStack> releaseSegmentData(Machine machine, Machine.Segment segment, List<Runnable> afterClear) {
        List<ItemStack> contents = new ArrayList<>();
        TransporterGui transporter = transporters.remove(segment.id());
        if (transporter != null) {
            for (HumanEntity viewer : new ArrayList<>(transporter.getInventory().getViewers())) {
                viewer.closeInventory();
            }
            for (ItemStack stack : transporter.getInventory().getContents()) {
                if (stack != null && !stack.getType().isAir()) {
                    contents.add(stack);
                }
            }
            if (segment.cart()) {
                contents.add(new ItemStack(Material.CHEST_MINECART));
                World world = Bukkit.getWorld(machine.world());
                Pos cartPos = segment.cartPos();
                String previous = transporter.previousBlock();
                afterClear.add(() -> restoreBlock(world, cartPos, previous));
            }
        }
        StorageGui storage = storages.remove(segment.id());
        if (storage != null) {
            for (HumanEntity viewer : new ArrayList<>(storage.getInventory().getViewers())) {
                viewer.closeInventory();
            }
            for (int slot = StorageGui.CONSUMPTION_START; slot <= StorageGui.GATHERED_END; slot++) {
                ItemStack stack = storage.getInventory().getItem(slot);
                if (stack != null && !stack.getType().isAir()) {
                    contents.add(stack);
                }
            }
        }
        IncineratorGui incinerator = incinerators.remove(segment.id());
        if (incinerator != null) {
            for (HumanEntity viewer : new ArrayList<>(incinerator.getInventory().getViewers())) {
                viewer.closeInventory();
            }
        }
        for (InventoryHolder settings : new InventoryHolder[] {
                reinforcements.remove(segment.id()), decorations.remove(segment.id())}) {
            if (settings != null) {
                for (HumanEntity viewer : new ArrayList<>(settings.getInventory().getViewers())) {
                    viewer.closeInventory();
                }
            }
        }
        seats.remove(segment.id());
        return contents;
    }

    // ---------------------------------------------------------------- gathered items

    /**
     * Puts collected items into the caterpillar's gathered slots: the drill head first, then the storage segments
     * in order. Returns what did not fit, or null if everything was stored.
     */
    public ItemStack depositGathered(Machine machine, ItemStack stack) {
        HeadGui head = guis.get(machine.id());
        if (head == null) {
            return stack;
        }
        ItemStack rest = Slots.add(head.getInventory(), HeadGui.GATHERED_START, HeadGui.GATHERED_END, stack);
        for (Machine.Segment segment : machine.segments()) {
            if (rest == null) {
                break;
            }
            StorageGui storage = storages.get(segment.id());
            if (storage != null) {
                rest = Slots.add(storage.getInventory(), StorageGui.GATHERED_START, StorageGui.GATHERED_END, rest);
            }
        }
        dirty = true;
        return rest;
    }

    /** Destroys every gathered item of the given types, in the head and in all storage segments. */
    public void incinerateGathered(Machine machine, java.util.Set<org.bukkit.Material> types) {
        HeadGui head = guis.get(machine.id());
        int removed = 0;
        if (head != null) {
            removed += Slots.removeMatching(head.getInventory(), HeadGui.GATHERED_START, HeadGui.GATHERED_END, types);
        }
        for (Machine.Segment segment : machine.segments()) {
            StorageGui storage = storages.get(segment.id());
            if (storage != null) {
                removed += Slots.removeMatching(storage.getInventory(), StorageGui.GATHERED_START,
                        StorageGui.GATHERED_END, types);
            }
        }
        if (removed > 0) {
            dirty = true;
        }
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
        List<Runnable> restores = new ArrayList<>();
        for (ItemStack stored : releaseSegmentData(machine, segment, restores)) {
            drop(dropAt, stored);
        }
        restores.forEach(Runnable::run);
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
        List<Runnable> restores = new ArrayList<>();
        for (Machine.Segment segment : machine.segments()) {
            drops.addAll(releaseSegmentData(machine, segment, restores));
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
        restores.forEach(Runnable::run);
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
                entry.put("id", segment.id().toString());
                entry.put("kind", segment.kind().name());
                entry.put("pos", coords(segment.pos()));
                StorageGui storage = storages.get(segment.id());
                if (storage != null) {
                    entry.put("items", encode(storage.getInventory(), StorageGui.CONSUMPTION_START,
                            StorageGui.GATHERED_END));
                }
                IncineratorGui incinerator = incinerators.get(segment.id());
                if (incinerator != null) {
                    entry.put("filter", encode(incinerator.getInventory(), 0, IncineratorGui.SIZE - 1));
                }
                ReinforcementGui reinforcement = reinforcements.get(segment.id());
                if (reinforcement != null) {
                    List<String> pattern = new ArrayList<>();
                    for (int i = 0; i < ReinforcementGui.POSITIONS; i++) {
                        pattern.add(name(reinforcement.material(i)));
                    }
                    entry.put("pattern", pattern);
                    Map<String, List<String>> replace = new LinkedHashMap<>();
                    for (ReinforcementGui.Side side : ReinforcementGui.Side.values()) {
                        List<String> on = new ArrayList<>();
                        for (ReinforcementGui.Replace what : ReinforcementGui.Replace.values()) {
                            if (reinforcement.replaces(side, what)) {
                                on.add(what.name());
                            }
                        }
                        replace.put(side.name(), on);
                    }
                    entry.put("replace", replace);
                }
                DecorationGui decoration = decorations.get(segment.id());
                if (decoration != null) {
                    List<List<String>> patterns = new ArrayList<>();
                    for (int p = 0; p < DecorationGui.MAX_PATTERNS; p++) {
                        List<String> row = new ArrayList<>();
                        for (int i = 0; i < DecorationGui.POSITIONS; i++) {
                            row.add(name(decoration.material(p, i)));
                        }
                        patterns.add(row);
                    }
                    entry.put("patterns", patterns);
                    entry.put("cycle-length", decoration.cycle());
                    entry.put("current-pattern", decoration.current());
                }
                TransporterGui transporter = transporters.get(segment.id());
                if (transporter != null) {
                    entry.put("cart", segment.cart());
                    if (transporter.previousBlock() != null) {
                        entry.put("previous", transporter.previousBlock());
                    }
                    entry.put("cargo", encode(transporter.getInventory(), 0, TransporterGui.SIZE - 1));
                }
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
                Object savedId = entry.get("id");
                UUID segmentId = savedId == null ? UUID.randomUUID() : UUID.fromString(String.valueOf(savedId));
                boolean cart = Boolean.parseBoolean(String.valueOf(entry.get("cart")));
                machine.restoreSegment(segmentId, kind, pos((List<?>) entry.get("pos")), cart);
                Machine.Segment restored = machine.segments().get(machine.segments().size() - 1);
                createSegmentData(machine, restored);
                ReinforcementGui restoredReinforcement = reinforcements.get(segmentId);
                if (restoredReinforcement != null) {
                    if (entry.get("pattern") instanceof List<?> savedPattern) {
                        for (int i = 0; i < Math.min(savedPattern.size(), ReinforcementGui.POSITIONS); i++) {
                            restoredReinforcement.setMaterial(i, material(savedPattern.get(i)));
                        }
                    }
                    if (entry.get("replace") instanceof Map<?, ?> savedReplace) {
                        for (ReinforcementGui.Side side : ReinforcementGui.Side.values()) {
                            Object on = savedReplace.get(side.name());
                            for (ReinforcementGui.Replace what : ReinforcementGui.Replace.values()) {
                                restoredReinforcement.set(side, what,
                                        on instanceof List<?> list && list.contains(what.name()));
                            }
                        }
                    }
                    restoredReinforcement.render();
                }
                DecorationGui restoredDecoration = decorations.get(segmentId);
                if (restoredDecoration != null) {
                    if (entry.get("patterns") instanceof List<?> savedPatterns) {
                        for (int p = 0; p < Math.min(savedPatterns.size(), DecorationGui.MAX_PATTERNS); p++) {
                            if (savedPatterns.get(p) instanceof List<?> row) {
                                for (int i = 0; i < Math.min(row.size(), DecorationGui.POSITIONS); i++) {
                                    restoredDecoration.setMaterial(p, i, material(row.get(i)));
                                }
                            }
                        }
                    }
                    restoredDecoration.setCycle(entry.get("cycle-length") instanceof Number length
                            ? length.intValue() : DecorationGui.DEFAULT_CYCLE);
                    if (entry.get("current-pattern") instanceof Number number) {
                        restoredDecoration.setCurrent(number.intValue());
                    }
                    restoredDecoration.render();
                }
                TransporterGui restoredTransporter = transporters.get(segmentId);
                if (restoredTransporter != null) {
                    if (entry.get("previous") != null) {
                        restoredTransporter.setPreviousBlock(String.valueOf(entry.get("previous")));
                    }
                    if (entry.get("cargo") instanceof Map<?, ?> savedCargo) {
                        decode(savedCargo, restoredTransporter.getInventory(), idText);
                    }
                }
                if (entry.get("items") instanceof Map<?, ?> saved && storages.get(segmentId) != null) {
                    decode(saved, storages.get(segmentId).getInventory(), idText);
                }
                if (entry.get("filter") instanceof Map<?, ?> saved && incinerators.get(segmentId) != null) {
                    decode(saved, incinerators.get(segmentId).getInventory(), idText);
                }
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

    private static Map<String, String> encode(org.bukkit.inventory.Inventory inventory, int from, int to) {
        Map<String, String> out = new LinkedHashMap<>();
        for (int slot = from; slot <= to; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && !stack.getType().isAir()) {
                out.put(String.valueOf(slot), Base64.getEncoder().encodeToString(stack.serializeAsBytes()));
            }
        }
        return out;
    }

    private void decode(Map<?, ?> saved, org.bukkit.inventory.Inventory inventory, String machineId) {
        for (Map.Entry<?, ?> item : saved.entrySet()) {
            try {
                int slot = Integer.parseInt(String.valueOf(item.getKey()));
                byte[] bytes = Base64.getDecoder().decode(String.valueOf(item.getValue()));
                inventory.setItem(slot, ItemStack.deserializeBytes(bytes));
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING,
                        "Skipping an unreadable item in caterpillar " + machineId + " segment slot " + item.getKey(), ex);
            }
        }
    }

    private static String name(Material material) {
        return material == null ? "" : material.name();
    }

    private static Material material(Object name) {
        return name == null || String.valueOf(name).isEmpty() ? null : Material.matchMaterial(String.valueOf(name));
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
