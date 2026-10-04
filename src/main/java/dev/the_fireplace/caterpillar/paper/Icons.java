package dev.the_fireplace.caterpillar.paper;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Builds the decorative and button items used in the GUIs. */
public final class Icons {

    private Icons() {
    }

    public static ItemStack named(Material material, Component name, List<Component> lore, boolean glint) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name);
        if (lore != null && !lore.isEmpty()) {
            meta.lore(lore);
        }
        if (glint) {
            meta.setEnchantmentGlintOverride(true);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    public static ItemStack filler(Material pane) {
        return named(pane, Component.text(" "), null, false);
    }

    /** True if the material can be placed as a block and exists as an item (so it can be shown and consumed). */
    public static boolean placeable(Material material) {
        return material != null && material.isBlock() && material.isItem() && !material.isAir();
    }
}
