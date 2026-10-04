package dev.the_fireplace.caterpillar.paper;

import java.util.Set;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Small helpers for working on a range of inventory slots. */
public final class Slots {

    private Slots() {
    }

    /**
     * Adds {@code stack} to the slots {@code from..to} (inclusive), topping up similar stacks first and then using
     * empty slots. The argument is not modified.
     *
     * @return what did not fit, or null if everything was inserted
     */
    public static ItemStack add(Inventory inventory, int from, int to, ItemStack stack) {
        ItemStack rest = stack.clone();
        for (int slot = from; slot <= to; slot++) {
            ItemStack current = inventory.getItem(slot);
            if (current != null && !current.getType().isAir() && current.isSimilar(rest)
                    && current.getAmount() < current.getMaxStackSize()) {
                int moved = Math.min(rest.getAmount(), current.getMaxStackSize() - current.getAmount());
                current.setAmount(current.getAmount() + moved);
                inventory.setItem(slot, current);
                rest.setAmount(rest.getAmount() - moved);
                if (rest.getAmount() <= 0) {
                    return null;
                }
            }
        }
        for (int slot = from; slot <= to; slot++) {
            ItemStack current = inventory.getItem(slot);
            if (current == null || current.getType().isAir()) {
                int moved = Math.min(rest.getAmount(), rest.getMaxStackSize());
                ItemStack placed = rest.clone();
                placed.setAmount(moved);
                inventory.setItem(slot, placed);
                rest.setAmount(rest.getAmount() - moved);
                if (rest.getAmount() <= 0) {
                    return null;
                }
            }
        }
        return rest;
    }

    /** Empties every slot in {@code from..to} that holds one of the given materials. Returns the items removed. */
    public static int removeMatching(Inventory inventory, int from, int to, Set<Material> types) {
        int removed = 0;
        for (int slot = from; slot <= to; slot++) {
            ItemStack current = inventory.getItem(slot);
            if (current != null && types.contains(current.getType())) {
                removed += current.getAmount();
                inventory.setItem(slot, null);
            }
        }
        return removed;
    }
}
