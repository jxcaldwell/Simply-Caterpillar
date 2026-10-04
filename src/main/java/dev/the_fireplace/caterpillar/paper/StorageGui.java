package dev.the_fireplace.caterpillar.paper;

import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The inventory of one storage segment, shown as a chest GUI that is also the storage itself.
 *
 * <pre>
 *  row 0:  [info] [filler x8]
 *  row 1:  consumption slots (9)
 *  row 2:  gathered slots (9)   - collectors deposit here after the drill head's own gathered slots are full
 * </pre>
 */
public final class StorageGui implements InventoryHolder {

    public static final int SIZE = 27;
    public static final int INFO = 0;
    public static final int FILLER_START = 1;
    public static final int FILLER_END = 8;
    public static final int CONSUMPTION_START = 9;
    public static final int CONSUMPTION_END = 17;
    public static final int GATHERED_START = 18;
    public static final int GATHERED_END = 26;

    private final UUID machineId;
    private final UUID segmentId;
    private final Inventory inventory;

    public StorageGui(UUID machineId, UUID segmentId, Lang lang) {
        this.machineId = machineId;
        this.segmentId = segmentId;
        this.inventory = Bukkit.createInventory(this, SIZE, lang.get("gui.storage-title"));

        ItemStack info = new ItemStack(Material.CHEST);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(lang.item("gui.storage-info-title"));
        infoMeta.lore(lang.itemList("gui.storage-info-lore"));
        info.setItemMeta(infoMeta);
        inventory.setItem(INFO, info);

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.text(" "));
        filler.setItemMeta(fillerMeta);
        for (int slot = FILLER_START; slot <= FILLER_END; slot++) {
            inventory.setItem(slot, filler);
        }
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

    public static boolean isStorageSlot(int slot) {
        return slot >= CONSUMPTION_START && slot <= GATHERED_END;
    }

    /** Shift-click from the player's inventory goes to the consumption row. Returns what did not fit, or null. */
    public ItemStack insert(ItemStack stack) {
        return Slots.add(inventory, CONSUMPTION_START, CONSUMPTION_END, stack);
    }
}
