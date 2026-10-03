package dev.the_fireplace.caterpillar.paper;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

/** Crafting recipes, ported from the original mod's data pack JSON. */
public final class Recipes {

    private Recipes() {
    }

    /** Registers (or re-registers) the recipes and returns their keys. */
    public static List<NamespacedKey> register(SimplyCaterpillarPlugin plugin) {
        CaterpillarItems items = plugin.items();
        List<NamespacedKey> keys = new ArrayList<>();

        // Basic Drill Segment:  c c / crc / cpc   (c = cobblestone, r = redstone, p = any planks)
        NamespacedKey baseKey = new NamespacedKey(plugin, PartType.DRILL_BASE.id);
        Bukkit.removeRecipe(baseKey);
        ShapedRecipe base = new ShapedRecipe(baseKey, items.create(PartType.DRILL_BASE, 1));
        base.shape("c c", "crc", "cpc");
        base.setIngredient('c', Material.COBBLESTONE);
        base.setIngredient('r', Material.REDSTONE);
        base.setIngredient('p', new RecipeChoice.MaterialChoice(Tag.PLANKS));
        Bukkit.addRecipe(base);
        keys.add(baseKey);

        // Drill Head:  iii /  d  /  f    (i = iron ingot, d = drill segment, f = furnace)
        NamespacedKey headKey = new NamespacedKey(plugin, PartType.DRILL_HEAD.id);
        Bukkit.removeRecipe(headKey);
        ShapedRecipe head = new ShapedRecipe(headKey, items.create(PartType.DRILL_HEAD, 1));
        head.shape("iii", " d ", " f ");
        head.setIngredient('i', Material.IRON_INGOT);
        head.setIngredient('d', new RecipeChoice.ExactChoice(items.create(PartType.DRILL_BASE, 1)));
        head.setIngredient('f', Material.FURNACE);
        Bukkit.addRecipe(head);
        keys.add(headKey);

        return keys;
    }

    public static void unregister(List<NamespacedKey> keys) {
        for (NamespacedKey key : keys) {
            Bukkit.removeRecipe(key);
        }
    }
}
