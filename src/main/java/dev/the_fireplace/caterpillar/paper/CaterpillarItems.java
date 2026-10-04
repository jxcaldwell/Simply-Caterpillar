package dev.the_fireplace.caterpillar.paper;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Creates and recognises the caterpillar part items. A part is an ordinary block item carrying a tag in its
 * persistent data, so vanilla clients need nothing installed. (When the resource pack milestone lands, the
 * same tag can also select a custom item model.)
 */
public final class CaterpillarItems {

    private final NamespacedKey partKey;
    private final Settings settings;
    private final Lang lang;

    public CaterpillarItems(SimplyCaterpillarPlugin plugin, Settings settings, Lang lang) {
        this.partKey = new NamespacedKey(plugin, "part");
        this.settings = settings;
        this.lang = lang;
    }

    public ItemStack create(PartType type, int amount) {
        ItemStack stack = new ItemStack(settings.partMaterial(type), amount);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(lang.item("item." + type.id + ".name"));
        meta.lore(lang.itemList("item." + type.id + ".lore"));
        meta.getPersistentDataContainer().set(partKey, PersistentDataType.STRING, type.id);
        stack.setItemMeta(meta);
        return stack;
    }

    /** The part this item is, or null for any other item. */
    public PartType identify(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        String id = stack.getItemMeta().getPersistentDataContainer().get(partKey, PersistentDataType.STRING);
        return id == null ? null : PartType.fromId(id);
    }
}
