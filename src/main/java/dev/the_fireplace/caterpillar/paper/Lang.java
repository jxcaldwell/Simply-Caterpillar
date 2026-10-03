package dev.the_fireplace.caterpillar.paper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Translated messages in MiniMessage format. The language file lives in {@code plugins/SimplyCaterpillar/lang};
 * missing keys fall back to the English file bundled in the jar, so old language files keep working.
 */
public final class Lang {

    private static final String DEFAULT_LANGUAGE = "en_us";

    private final MiniMessage mini = MiniMessage.miniMessage();
    private final YamlConfiguration user;
    private final YamlConfiguration defaults;

    public Lang(JavaPlugin plugin, String language) {
        File file = new File(new File(plugin.getDataFolder(), "lang"), language + ".yml");
        if (!file.exists() && plugin.getResource("lang/" + language + ".yml") != null) {
            plugin.saveResource("lang/" + language + ".yml", false);
        }
        if (!file.exists() && !language.equals(DEFAULT_LANGUAGE)) {
            plugin.getLogger().warning("Language '" + language + "' not found, using " + DEFAULT_LANGUAGE);
        }
        this.user = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();

        YamlConfiguration bundled = new YamlConfiguration();
        try (InputStream in = plugin.getResource("lang/" + DEFAULT_LANGUAGE + ".yml")) {
            if (in != null) {
                bundled = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not read the bundled language file: " + e.getMessage());
        }
        this.defaults = bundled;
    }

    private String raw(String key) {
        String value = user.getString(key);
        if (value == null) {
            value = defaults.getString(key);
        }
        return value == null ? key : value;
    }

    private List<String> rawList(String key) {
        List<String> value = user.isList(key) ? user.getStringList(key) : null;
        if (value == null) {
            value = defaults.getStringList(key);
        }
        return value;
    }

    /** A chat/action-bar message. */
    public Component get(String key, TagResolver... resolvers) {
        return mini.deserialize(raw(key), resolvers);
    }

    /** Text for item names and lore: same as {@link #get} but without the default italics. */
    public Component item(String key, TagResolver... resolvers) {
        return get(key, resolvers).decoration(TextDecoration.ITALIC, false);
    }

    public List<Component> itemList(String key, TagResolver... resolvers) {
        List<Component> lines = new ArrayList<>();
        for (String line : rawList(key)) {
            lines.add(mini.deserialize(line, resolvers).decoration(TextDecoration.ITALIC, false));
        }
        return lines;
    }
}
