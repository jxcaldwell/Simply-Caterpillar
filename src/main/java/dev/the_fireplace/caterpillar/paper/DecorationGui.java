package dev.the_fireplace.caterpillar.paper;

import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * The decoration placer's ten patterns. Each pattern is the ring of eight blocks around the middle of a tunnel
 * slice, seen from behind; every block the caterpillar moves the placer uses the next pattern, so a pattern repeats
 * every ten blocks.
 *
 * <pre>
 *  pattern position:   0 1 2      GUI:   rows 1-3, columns 3-5 show the selected pattern
 *                      3 # 4             row 5: [previous] [copy to all] [pattern n/10] [clear] [next]
 *                      5 6 7
 * </pre>
 */
public final class DecorationGui implements InventoryHolder {

    public static final int SIZE = 54;
    public static final int PATTERNS = 10;
    public static final int POSITIONS = 8;

    private static final int[] POSITION_SLOTS = {12, 13, 14, 21, 23, 30, 31, 32};
    private static final int CENTER_SLOT = 22;
    private static final int INFO_SLOT = 4;
    private static final int PREVIOUS = 45;
    private static final int COPY_ALL = 47;
    private static final int INDICATOR = 49;
    private static final int CLEAR = 51;
    private static final int NEXT = 53;

    private final UUID machineId;
    private final UUID segmentId;
    private final Lang lang;
    private final Inventory inventory;
    private final Material[][] patterns = new Material[PATTERNS][POSITIONS];
    private int current;
    private int selected;

    public DecorationGui(UUID machineId, UUID segmentId, Lang lang) {
        this.machineId = machineId;
        this.segmentId = segmentId;
        this.lang = lang;
        this.inventory = Bukkit.createInventory(this, SIZE, lang.get("gui.decoration-title"));
    }

    /** The original mod's "mineshaft" default: a rail line, a wooden support frame, torches and a powered rail. */
    public void fillDefaults() {
        for (int p = 0; p < PATTERNS; p++) {
            patterns[p][6] = Material.RAIL;
        }
        patterns[5][0] = Material.OAK_PLANKS;
        patterns[5][1] = Material.OAK_PLANKS;
        patterns[5][2] = Material.OAK_PLANKS;
        patterns[5][3] = Material.OAK_FENCE;
        patterns[5][4] = Material.OAK_FENCE;
        patterns[5][5] = Material.OAK_FENCE;
        patterns[5][7] = Material.OAK_FENCE;
        patterns[6][3] = Material.TORCH;
        patterns[6][4] = Material.TORCH;
        patterns[7][5] = Material.REDSTONE_TORCH;
        patterns[7][6] = Material.POWERED_RAIL;
        render();
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

    public Material material(int pattern, int position) {
        return patterns[pattern][position];
    }

    public void setMaterial(int pattern, int position, Material material) {
        patterns[pattern][position] = Icons.placeable(material) ? material : null;
    }

    /** The pattern that will be placed on the next step. */
    public int current() {
        return current;
    }

    public void setCurrent(int current) {
        this.current = Math.floorMod(current, PATTERNS);
    }

    /** Moves on to the next pattern and returns it. */
    public int advance() {
        current = (current + 1) % PATTERNS;
        if (!inventory.getViewers().isEmpty()) {
            render();
        }
        return current;
    }

    /**
     * Handles a click on a slot of this GUI.
     *
     * @return true if a setting changed
     */
    public boolean click(int slot, ItemStack cursor) {
        for (int i = 0; i < POSITIONS; i++) {
            if (POSITION_SLOTS[i] == slot) {
                if (cursor != null && !cursor.getType().isAir()) {
                    if (!Icons.placeable(cursor.getType())) {
                        return false;
                    }
                    patterns[selected][i] = cursor.getType();
                } else {
                    patterns[selected][i] = null;
                }
                render();
                return true;
            }
        }
        switch (slot) {
            case PREVIOUS -> {
                selected = Math.floorMod(selected - 1, PATTERNS);
                render();
                return false;
            }
            case NEXT -> {
                selected = (selected + 1) % PATTERNS;
                render();
                return false;
            }
            case COPY_ALL -> {
                for (int p = 0; p < PATTERNS; p++) {
                    if (p != selected) {
                        patterns[p] = patterns[selected].clone();
                    }
                }
                render();
                return true;
            }
            case CLEAR -> {
                patterns[selected] = new Material[POSITIONS];
                render();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public void render() {
        ItemStack filler = Icons.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, filler);
        }
        inventory.setItem(INFO_SLOT, Icons.named(Material.BOOK, lang.item("gui.decoration-info-title"),
                lang.itemList("gui.decoration-info-lore"), false));
        inventory.setItem(CENTER_SLOT, Icons.filler(Material.BLACK_STAINED_GLASS_PANE));
        for (int i = 0; i < POSITIONS; i++) {
            Material material = patterns[selected][i];
            if (material == null) {
                inventory.setItem(POSITION_SLOTS[i], Icons.named(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                        lang.item("gui.decoration-empty"), lang.itemList("gui.pattern-set-hint"), false));
            } else {
                ItemStack shown = new ItemStack(material);
                var meta = shown.getItemMeta();
                meta.lore(lang.itemList("gui.pattern-clear-hint"));
                shown.setItemMeta(meta);
                inventory.setItem(POSITION_SLOTS[i], shown);
            }
        }
        String number = String.valueOf(selected + 1);
        inventory.setItem(PREVIOUS, Icons.named(Material.ARROW, lang.item("gui.decoration-previous"), null, false));
        inventory.setItem(NEXT, Icons.named(Material.ARROW, lang.item("gui.decoration-next"), null, false));
        inventory.setItem(COPY_ALL, Icons.named(Material.WRITABLE_BOOK, lang.item("gui.decoration-copy-all"),
                lang.itemList("gui.decoration-copy-all-lore"), false));
        inventory.setItem(CLEAR, Icons.named(Material.BARRIER, lang.item("gui.decoration-clear"), null, false));
        ItemStack indicator = Icons.named(Material.PAPER,
                lang.item("gui.decoration-pattern", Placeholder.unparsed("n", number)),
                List.of(lang.item("gui.decoration-next-placed",
                        Placeholder.unparsed("n", String.valueOf((current + 1) % PATTERNS + 1)))),
                selected == (current + 1) % PATTERNS);
        indicator.setAmount(selected + 1);
        inventory.setItem(INDICATOR, indicator);
    }
}
