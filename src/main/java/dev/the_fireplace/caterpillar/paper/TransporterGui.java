package dev.the_fireplace.caterpillar.paper;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * A transporter's cargo: 27 slots, like the chest minecart it will be released in. It is also the transporter's
 * only per-segment state besides the cart flag: the block that was below it before the cart arrived (a rail, say)
 * is remembered here so it can be put back when the cart moves on.
 */
public final class TransporterGui implements InventoryHolder {

    public static final int SIZE = 27;

    private final UUID machineId;
    private final UUID segmentId;
    private final Inventory inventory;
    /** Block data string of the block under the cart's position before the cart covered it, or null. */
    private String previousBlock;

    public TransporterGui(UUID machineId, UUID segmentId, Lang lang) {
        this.machineId = machineId;
        this.segmentId = segmentId;
        this.inventory = Bukkit.createInventory(this, SIZE, lang.get("gui.transporter-title"));
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

    public String previousBlock() {
        return previousBlock;
    }

    public void setPreviousBlock(String previousBlock) {
        this.previousBlock = previousBlock;
    }

    /** True when every slot holds a full stack, the point at which the cart is sent off. */
    public boolean isFull() {
        for (int slot = 0; slot < SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType().isAir() || stack.getAmount() < stack.getMaxStackSize()) {
                return false;
            }
        }
        return true;
    }

    /** Puts the stack in the first empty slot. Returns false if the cargo has no free slot. */
    public boolean addStack(ItemStack stack) {
        for (int slot = 0; slot < SIZE; slot++) {
            ItemStack current = inventory.getItem(slot);
            if (current == null || current.getType().isAir()) {
                inventory.setItem(slot, stack);
                return true;
            }
        }
        return false;
    }
}
