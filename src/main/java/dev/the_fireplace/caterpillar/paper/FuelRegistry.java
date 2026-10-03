package dev.the_fireplace.caterpillar.paper;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

/** Which items burn in the drill head and for how long, read from the {@code fuels} config section. */
public final class FuelRegistry {

    private final Map<Material, Integer> byMaterial = new EnumMap<>(Material.class);
    private final List<Map.Entry<Tag<Material>, Integer>> byTag = new ArrayList<>();

    public FuelRegistry(ConfigurationSection section, Logger log) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            int ticks = section.getInt(key);
            if (ticks <= 0) {
                log.warning("config.yml: fuel '" + key + "' needs a positive burn time");
                continue;
            }
            if (key.startsWith("#")) {
                NamespacedKey tagKey = NamespacedKey.fromString(key.substring(1));
                Tag<Material> tag = tagKey == null ? null : Bukkit.getTag(Tag.REGISTRY_ITEMS, tagKey, Material.class);
                if (tag == null) {
                    log.warning("config.yml: unknown item tag in fuels: " + key);
                    continue;
                }
                byTag.add(Map.entry(tag, ticks));
            } else {
                Material material = Material.matchMaterial(key);
                if (material == null) {
                    log.warning("config.yml: unknown material in fuels: " + key);
                    continue;
                }
                byMaterial.put(material, ticks);
            }
        }
    }

    /** Burn time in ticks, or 0 if the item is not a fuel. */
    public int burnTime(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return 0;
        }
        Integer exact = byMaterial.get(stack.getType());
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<Tag<Material>, Integer> entry : byTag) {
            if (entry.getKey().isTagged(stack.getType())) {
                return entry.getValue();
            }
        }
        return 0;
    }

    public boolean isFuel(ItemStack stack) {
        return burnTime(stack) > 0;
    }
}
