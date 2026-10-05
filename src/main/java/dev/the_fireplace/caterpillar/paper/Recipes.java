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
        head.setIngredient('d', new RecipeChoice.ExactChoice(
                List.of(items.create(PartType.DRILL_BASE, 1), items.createLegacy(PartType.DRILL_BASE, 1))));
        head.setIngredient('f', Material.FURNACE);
        Bukkit.addRecipe(head);
        keys.add(headKey);

        // The attachments all start from a Basic Drill Segment (the original mod's recipes).
        RecipeChoice.ExactChoice segment = new RecipeChoice.ExactChoice(
                List.of(items.create(PartType.DRILL_BASE, 1), items.createLegacy(PartType.DRILL_BASE, 1)));

        // Storage:  c d c   (c = chest)
        ShapedRecipe storage = shaped(plugin, keys, PartType.STORAGE);
        storage.shape("cdc");
        storage.setIngredient('c', Material.CHEST);
        storage.setIngredient('d', segment);
        Bukkit.addRecipe(storage);

        // Collector:  d / h   (h = hopper)
        ShapedRecipe collector = shaped(plugin, keys, PartType.COLLECTOR);
        collector.shape("d", "h");
        collector.setIngredient('d', segment);
        collector.setIngredient('h', Material.HOPPER);
        Bukkit.addRecipe(collector);

        // Incinerator:  f / d / l   (f = furnace, l = lava bucket)
        ShapedRecipe incinerator = shaped(plugin, keys, PartType.INCINERATOR);
        incinerator.shape("f", "d", "l");
        incinerator.setIngredient('f', Material.FURNACE);
        incinerator.setIngredient('d', segment);
        incinerator.setIngredient('l', Material.LAVA_BUCKET);
        Bukkit.addRecipe(incinerator);

        // Drill Seat:  c / d   (c = cauldron)
        ShapedRecipe seat = shaped(plugin, keys, PartType.DRILL_SEAT);
        seat.shape("c", "d");
        seat.setIngredient('c', Material.CAULDRON);
        seat.setIngredient('d', segment);
        Bukkit.addRecipe(seat);

        // Transporter:  c d c /  h    (c = iron chain, h = hopper)
        ShapedRecipe transporter = shaped(plugin, keys, PartType.TRANSPORTER);
        transporter.shape("cdc", " h ");
        transporter.setIngredient('c', Material.IRON_CHAIN);
        transporter.setIngredient('d', segment);
        transporter.setIngredient('h', Material.HOPPER);
        Bukkit.addRecipe(transporter);

        // Reinforcement builder:   p  / p d p /  p    (p = piston)
        ShapedRecipe reinforcement = shaped(plugin, keys, PartType.REINFORCEMENT);
        reinforcement.shape(" p ", "pdp", " p ");
        reinforcement.setIngredient('p', Material.PISTON);
        reinforcement.setIngredient('d', segment);
        Bukkit.addRecipe(reinforcement);

        // Decoration placer:  b d b   (b = dispenser)
        ShapedRecipe decoration = shaped(plugin, keys, PartType.DECORATION);
        decoration.shape("bdb");
        decoration.setIngredient('b', Material.DISPENSER);
        decoration.setIngredient('d', segment);
        Bukkit.addRecipe(decoration);

        return keys;
    }

    /** Starts the recipe for a part: removes any old copy, records the key and creates the empty recipe. */
    private static ShapedRecipe shaped(SimplyCaterpillarPlugin plugin, List<NamespacedKey> keys, PartType type) {
        NamespacedKey key = new NamespacedKey(plugin, type.id);
        Bukkit.removeRecipe(key);
        keys.add(key);
        return new ShapedRecipe(key, plugin.items().create(type, 1));
    }

    public static void unregister(List<NamespacedKey> keys) {
        for (NamespacedKey key : keys) {
            Bukkit.removeRecipe(key);
        }
    }
}
