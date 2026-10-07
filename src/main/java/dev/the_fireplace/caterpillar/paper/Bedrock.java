package dev.the_fireplace.caterpillar.paper;

import java.lang.reflect.Method;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Recognises Bedrock players who join through Geyser/Floodgate. They cannot use Java resource packs, display entities
 * with custom models, or the client-side block tricks the model view relies on, so they always get the plain blocks.
 *
 * <p>Uses Floodgate's API when Floodgate is installed (looked up at runtime, so it is not a dependency), and
 * otherwise Floodgate's UUID scheme (Bedrock players get UUIDs whose upper 64 bits are zero).
 */
public final class Bedrock {

    private static Object floodgate;
    private static Method isFloodgatePlayer;
    private static boolean looked;

    private Bedrock() {
    }

    public static boolean isBedrock(Player player) {
        UUID id = player.getUniqueId();
        lookUp();
        if (isFloodgatePlayer != null) {
            try {
                return (Boolean) isFloodgatePlayer.invoke(floodgate, id);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // fall back to the UUID check below
            }
        }
        return id.getMostSignificantBits() == 0L;
    }

    private static void lookUp() {
        if (looked) {
            return;
        }
        looked = true;
        if (Bukkit.getPluginManager().getPlugin("floodgate") == null) {
            return;
        }
        try {
            Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            floodgate = api.getMethod("getInstance").invoke(null);
            isFloodgatePlayer = api.getMethod("isFloodgatePlayer", UUID.class);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            floodgate = null;
            isFloodgatePlayer = null;
        }
    }
}
