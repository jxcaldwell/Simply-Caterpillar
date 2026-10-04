package dev.the_fireplace.caterpillar.paper;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * The incinerator's filter: nine "ghost" slots naming the item types it destroys. Putting an item on a slot with
 * the cursor only copies its type (the item itself is not taken); clicking a filled slot clears it.
 */
public final class IncineratorGui implements InventoryHolder {

    public static final int SIZE = 9;

    private final UUID machineId;
    private final UUID segmentId;
    private final Inventory inventory;

    public IncineratorGui(UUID machineId, UUID segmentId, Lang lang) {
        this.machineId = machineId;
        this.segmentId = segmentId;
        this.inventory = Bukkit.createInventory(this, SIZE, lang.get("gui.incinerator-title"));
    }

    /** The filter a new incinerator starts with, as in the original mod. */
    public void fillDefaults() {
        set(0, Material.GRAVEL);
        set(1, Material.SAND);
        set(2, Material.RED_SAND);
        set(3, Material.COBBLESTONE);
        set(4, Material.DIRT);
    }

    public UUID machineId() {
        return machineId;
    }

    public UUID segmentId() {
        return segmentId;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void set(int slot, Material material) {
        inventory.setItem(slot, new ItemStack(material));
    }

    public boolean contains(Material material) {
        return types().contains(material);
    }

    /** The item types that are destroyed. */
    public Set<Material> types() {
        Set<Material> types = EnumSet.noneOf(Material.class);
        for (ItemStack stack : inventory.getContents()) {
            if (stack != null && !stack.getType().isAir()) {
                types.add(stack.getType());
            }
        }
        return types;
    }

    /** Adds a type to the first free slot unless it is already listed. Returns false if there was no room. */
    public boolean addType(Material material) {
        if (contains(material)) {
            return true;
        }
        for (int slot = 0; slot < SIZE; slot++) {
            ItemStack current = inventory.getItem(slot);
            if (current == null || current.getType().isAir()) {
                set(slot, material);
                return true;
            }
        }
        return false;
    }
}
