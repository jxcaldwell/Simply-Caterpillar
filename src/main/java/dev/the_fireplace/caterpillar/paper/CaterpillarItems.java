package dev.the_fireplace.caterpillar.paper;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import java.util.List;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
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
        // Selects the original icon in the resource pack; without the pack it changes nothing.
        CustomModelDataComponent modelData = meta.getCustomModelDataComponent();
        modelData.setStrings(List.of("simplycaterpillar:" + type.id));
        meta.setCustomModelDataComponent(modelData);
        stack.setItemMeta(meta);
        return stack;
    }

    /** The same part as made by older versions of the plugin (no custom model data), for recipe matching. */
    public ItemStack createLegacy(PartType type, int amount) {
        ItemStack stack = create(type, amount);
        ItemMeta meta = stack.getItemMeta();
        CustomModelDataComponent modelData = meta.getCustomModelDataComponent();
        modelData.setStrings(List.of());
        meta.setCustomModelDataComponent(modelData);
        stack.setItemMeta(meta);
        return stack;
    }

    /** Brings an existing part item up to date (name, description, icon); returns null if it is not a part. */
    public ItemStack refresh(ItemStack stack) {
        PartType type = identify(stack);
        if (type == null) {
            return null;
        }
        ItemStack fresh = create(type, stack.getAmount());
        return fresh.isSimilar(stack) ? null : fresh;
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
