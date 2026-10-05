package dev.the_fireplace.caterpillar.paper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.entity.Player;

/**
 * Offers the Simply Caterpillar resource pack (the original mod's models) to players. By default the pack published
 * with this exact plugin build is used: its download link and SHA-1 are written into the jar at build time.
 */
public final class ResourcePack {

    public static final UUID PACK_ID = UUID.nameUUIDFromBytes("simplycaterpillar:resourcepack".getBytes(StandardCharsets.UTF_8));

    private final SimplyCaterpillarPlugin plugin;
    private final String url;
    private final String sha1;

    public ResourcePack(SimplyCaterpillarPlugin plugin) {
        this.plugin = plugin;
        Properties built = new Properties();
        try (InputStream in = plugin.getResource("resourcepack.properties")) {
            if (in != null) {
                built.load(in);
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not read the bundled resource pack information", ex);
        }
        Settings settings = plugin.settings();
        this.url = settings.packUrl.isEmpty() ? built.getProperty("url", "") : settings.packUrl;
        this.sha1 = settings.packUrl.isEmpty() ? built.getProperty("sha1", "") : settings.packSha1;
        if (settings.packEnabled && url.isEmpty()) {
            plugin.getLogger().info("No resource pack link is known for this build (local build?); set resource-pack.url "
                    + "in config.yml to offer the pack with the original models.");
        }
    }

    /** True if the pack is switched on and there is a link to offer. */
    public boolean available() {
        return plugin.settings().packEnabled && !url.isEmpty();
    }

    public void offer(Player player) {
        if (!available()) {
            return;
        }
        ResourcePackInfo.Builder info = ResourcePackInfo.resourcePackInfo().id(PACK_ID).uri(URI.create(url));
        if (!sha1.isEmpty()) {
            info.hash(sha1);
        }
        player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(info.build())
                .required(plugin.settings().packRequired)
                .prompt(plugin.lang().get("msg.pack.prompt"))
                .replace(false)
                .build());
    }
}
