package dev.the_fireplace.caterpillar.paper;

import dev.the_fireplace.caterpillar.core.Machine;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The drill head's chest-style GUI, which also *is* the head's inventory (it is created once per machine and
 * the machine logic reads the fuel slot from it directly).
 *
 * <pre>
 *  row 0:  [power] [FUEL] [status] [filler x6]
 *  row 1:  consumption slots (9)   - items that attachments consume, as in the original mod
 *  row 2:  gathered slots (9)      - items collected by the machine
 * </pre>
 */
public final class HeadGui implements InventoryHolder {

    public static final int SIZE = 27;
    public static final int BUTTON = 0;
    public static final int FUEL = 1;
    public static final int INFO = 2;
    public static final int FILLER_START = 3;
    public static final int FILLER_END = 8;
    public static final int CONSUMPTION_START = 9;
    public static final int CONSUMPTION_END = 17;
    public static final int GATHERED_START = 18;
    public static final int GATHERED_END = 26;

    private final UUID machineId;
    private final Lang lang;
    private final Inventory inventory;

    public HeadGui(UUID machineId, Lang lang) {
        this.machineId = machineId;
        this.lang = lang;
        this.inventory = Bukkit.createInventory(this, SIZE, lang.get("gui.title"));
        ItemStack filler = filler();
        for (int slot = FILLER_START; slot <= FILLER_END; slot++) {
            inventory.setItem(slot, filler);
        }
    }

    public UUID machineId() {
        return machineId;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** Slots a player may put items into and take items out of. */
    public static boolean isStorageSlot(int slot) {
        return slot == FUEL || (slot >= CONSUMPTION_START && slot <= GATHERED_END);
    }

    /** Re-renders the power button and the status item. */
    public void refresh(Machine machine) {
        inventory.setItem(BUTTON, powerButton(machine.powered()));
        inventory.setItem(INFO, infoItem(machine));
    }

    private ItemStack powerButton(boolean on) {
        ItemStack stack = new ItemStack(on ? Material.LIME_CONCRETE : Material.RED_CONCRETE);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(lang.item(on ? "gui.power-on" : "gui.power-off"));
        meta.lore(List.of(lang.item(on ? "gui.power-on-hint" : "gui.power-off-hint")));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack infoItem(Machine machine) {
        ItemStack stack = new ItemStack(Material.BLAZE_POWDER);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(lang.item("gui.info-title"));
        List<Component> lore = new ArrayList<>();
        int percent = (int) Math.round(machine.burnFraction() * 100);
        lore.add(lang.item("gui.info-burn", Placeholder.unparsed("percent", String.valueOf(percent))));
        lore.add(lang.item("gui.info-segments",
                Placeholder.unparsed("count", String.valueOf(machine.segments().size()))));
        if (machine.moving()) {
            lore.add(lang.item("gui.info-moving"));
        }
        lore.add(Component.empty());
        lore.add(lang.item("gui.info-fuel-hint"));
        lore.add(lang.item("gui.info-consumption"));
        lore.add(lang.item("gui.info-gathered"));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack filler() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.text(" "));
        pane.setItemMeta(meta);
        return pane;
    }

    /**
     * Shift-click routing for items moved in from the player's inventory: fuel goes to the fuel slot first,
     * everything else (and leftover fuel) to the consumption row.
     *
     * @return what did not fit, or null if everything was inserted
     */
    public ItemStack insert(ItemStack stack, FuelRegistry fuels) {
        ItemStack rest = stack.clone();
        if (fuels.isFuel(rest)) {
            rest = addToRange(rest, FUEL, FUEL);
        }
        if (rest != null) {
            rest = addToRange(rest, CONSUMPTION_START, CONSUMPTION_END);
        }
        return rest;
    }

    private ItemStack addToRange(ItemStack stack, int from, int to) {
        for (int slot = from; slot <= to; slot++) {
            ItemStack current = inventory.getItem(slot);
            if (current != null && !current.getType().isAir() && current.isSimilar(stack)
                    && current.getAmount() < current.getMaxStackSize()) {
                int moved = Math.min(stack.getAmount(), current.getMaxStackSize() - current.getAmount());
                current.setAmount(current.getAmount() + moved);
                inventory.setItem(slot, current);
                stack.setAmount(stack.getAmount() - moved);
                if (stack.getAmount() <= 0) {
                    return null;
                }
            }
        }
        for (int slot = from; slot <= to; slot++) {
            ItemStack current = inventory.getItem(slot);
            if (current == null || current.getType().isAir()) {
                inventory.setItem(slot, stack);
                return null;
            }
        }
        return stack;
    }
}
