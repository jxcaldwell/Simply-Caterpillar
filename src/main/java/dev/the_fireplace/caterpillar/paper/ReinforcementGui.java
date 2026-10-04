package dev.the_fireplace.caterpillar.paper;

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

/**
 * The reinforcement builder's settings, shown as a cross-section of the tunnel seen from behind:
 *
 * <pre>
 *        C C C C C          C = ceiling (5), L/R = left and right walls (3 each), F = floor (5)
 *        L . . . R          . = the 3x3 tunnel itself
 *        L . # . R          # = this segment
 *        L . . . R
 *        F F F F F
 *  [ceiling][left][right][floor]   [water][lava][falling][air][all]
 * </pre>
 *
 * Each position holds the block type placed there (click with a block to set, with an empty hand to clear). The
 * bottom row picks a side and, for that side, what may be replaced: water, lava, falling blocks (sand, gravel...),
 * air and other replaceable blocks, or everything.
 */
public final class ReinforcementGui implements InventoryHolder {

    public static final int SIZE = 54;
    public static final int POSITIONS = 16;

    public enum Side { CEILING, LEFT, RIGHT, FLOOR }

    public enum Replace { WATER, LAVA, FALLING, AIR, ALL }

    /** GUI slot of each pattern position: ceiling 0-4 (left to right), left 5-7 and right 8-10 (top to bottom), floor 11-15. */
    private static final int[] POSITION_SLOTS = {
            2, 3, 4, 5, 6,
            11, 20, 29,
            15, 24, 33,
            38, 39, 40, 41, 42
    };
    private static final int[] TUNNEL_SLOTS = {12, 13, 14, 21, 23, 30, 31, 32};
    private static final int CENTER_SLOT = 22;
    private static final int SIDE_BUTTON_START = 45;
    private static final int TOGGLE_START = 49;

    private final UUID machineId;
    private final UUID segmentId;
    private final Lang lang;
    private final Inventory inventory;
    private final Material[] pattern = new Material[POSITIONS];
    private final boolean[][] replace = new boolean[Side.values().length][Replace.values().length];
    private Side selected = Side.CEILING;

    public ReinforcementGui(UUID machineId, UUID segmentId, Lang lang) {
        this.machineId = machineId;
        this.segmentId = segmentId;
        this.lang = lang;
        this.inventory = Bukkit.createInventory(this, SIZE, lang.get("gui.reinforcement-title"));
    }

    /** The original mod's defaults: cobblestone everywhere; seal off liquids, hold up falling ceilings, fill floor gaps. */
    public void fillDefaults() {
        for (int i = 0; i < POSITIONS; i++) {
            pattern[i] = Material.COBBLESTONE;
        }
        for (Side side : Side.values()) {
            set(side, Replace.WATER, true);
            set(side, Replace.LAVA, true);
        }
        set(Side.CEILING, Replace.FALLING, true);
        set(Side.FLOOR, Replace.AIR, true);
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

    /** Block for a position (see the class comment for the numbering), or null for "leave alone". */
    public Material material(int position) {
        return pattern[position];
    }

    public void setMaterial(int position, Material material) {
        pattern[position] = Icons.placeable(material) ? material : null;
    }

    public boolean replaces(Side side, Replace what) {
        return replace[side.ordinal()][what.ordinal()];
    }

    public void set(Side side, Replace what, boolean on) {
        replace[side.ordinal()][what.ordinal()] = on;
    }

    /** Which side a position belongs to. */
    public static Side sideOf(int position) {
        if (position <= 4) {
            return Side.CEILING;
        }
        if (position <= 7) {
            return Side.LEFT;
        }
        if (position <= 10) {
            return Side.RIGHT;
        }
        return Side.FLOOR;
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
                    pattern[i] = cursor.getType();
                } else {
                    pattern[i] = null;
                }
                render();
                return true;
            }
        }
        if (slot >= SIDE_BUTTON_START && slot < SIDE_BUTTON_START + Side.values().length) {
            selected = Side.values()[slot - SIDE_BUTTON_START];
            render();
            return false;
        }
        if (slot >= TOGGLE_START && slot < TOGGLE_START + Replace.values().length) {
            Replace what = Replace.values()[slot - TOGGLE_START];
            set(selected, what, !replaces(selected, what));
            render();
            return true;
        }
        return false;
    }

    /** Redraws every slot from the current settings. */
    public void render() {
        ItemStack filler = Icons.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, filler);
        }
        ItemStack tunnel = Icons.filler(Material.BLACK_STAINED_GLASS_PANE);
        for (int slot : TUNNEL_SLOTS) {
            inventory.setItem(slot, tunnel);
        }
        inventory.setItem(CENTER_SLOT, Icons.named(Material.PISTON, lang.item("gui.reinforcement-info-title"),
                lang.itemList("gui.reinforcement-info-lore"), false));

        for (int i = 0; i < POSITIONS; i++) {
            Component side = lang.item("gui.side." + sideOf(i).name().toLowerCase(java.util.Locale.ROOT));
            if (pattern[i] == null) {
                inventory.setItem(POSITION_SLOTS[i], Icons.named(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                        lang.item("gui.reinforcement-empty", Placeholder.component("side", side)),
                        lang.itemList("gui.pattern-set-hint"), false));
            } else {
                ItemStack shown = new ItemStack(pattern[i]);
                var meta = shown.getItemMeta();
                List<Component> lore = new ArrayList<>();
                lore.add(lang.item("gui.reinforcement-position", Placeholder.component("side", side)));
                lore.addAll(lang.itemList("gui.pattern-clear-hint"));
                meta.lore(lore);
                shown.setItemMeta(meta);
                inventory.setItem(POSITION_SLOTS[i], shown);
            }
        }

        for (Side side : Side.values()) {
            boolean active = side == selected;
            inventory.setItem(SIDE_BUTTON_START + side.ordinal(), Icons.named(
                    active ? Material.LIME_STAINED_GLASS_PANE : Material.WHITE_STAINED_GLASS_PANE,
                    lang.item("gui.side." + side.name().toLowerCase(java.util.Locale.ROOT)),
                    List.of(lang.item(active ? "gui.side-selected" : "gui.side-select-hint")), active));
        }
        Material[] toggleIcons = {Material.WATER_BUCKET, Material.LAVA_BUCKET, Material.SAND, Material.GLASS, Material.TNT};
        for (Replace what : Replace.values()) {
            boolean on = replaces(selected, what);
            String name = what.name().toLowerCase(java.util.Locale.ROOT);
            inventory.setItem(TOGGLE_START + what.ordinal(), Icons.named(toggleIcons[what.ordinal()],
                    lang.item(on ? "gui.replace-on" : "gui.replace-off",
                            Placeholder.component("what", lang.item("gui.replace." + name)),
                            Placeholder.component("side", lang.item("gui.side." + selected.name().toLowerCase(java.util.Locale.ROOT)))),
                    lang.itemList("gui.replace-help." + name), on));
        }
    }
}
