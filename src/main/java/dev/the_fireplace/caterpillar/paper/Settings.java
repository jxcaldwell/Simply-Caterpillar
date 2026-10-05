package dev.the_fireplace.caterpillar.paper;

import dev.the_fireplace.caterpillar.core.Params;
import java.util.EnumSet;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

/** Typed, validated view of config.yml. A fresh instance is created on every reload. */
public final class Settings {

    public final String language;

    public final int headInterval;
    public final int segmentInterval;
    public final int maxSegments;
    public final boolean breakUnbreakable;
    public final Material tool;

    public final boolean sounds;
    public final boolean particles;
    public final boolean explosionProof;
    public final boolean discoverOnJoin;

    public final Material headBase;
    public final Material headBit;
    public final Material headBitCenter;
    public final Material headBitCenterActive;
    public final Material spacer;
    public final Material storage;
    public final Material collector;
    public final Material incinerator;
    public final Material seat;
    public final Material transporter;
    public final Material transporterCart;
    public final Material reinforcement;
    public final Material decoration;

    /** Height above the seat block's floor at which the invisible seat entity is placed. */
    public final double seatYOffset;
    /** How far from a collector (in blocks, measured from its faces) dropped items are picked up. */
    public final double collectorRadius;
    /** Collected items that a part uses are kept in the consumption slots up to this many (0 = off). */
    public final int supplyAmount;
    /** Minimum seconds between two "out of supply" messages for the same item (0 = never warn). */
    public final int supplyWarningSeconds;

    public final boolean packEnabled;
    public final boolean packRequired;
    public final boolean worldModels;
    public final String packUrl;
    public final String packSha1;

    public final Set<Material> unbreakable;

    public Settings(FileConfiguration config, Logger log) {
        language = config.getString("language", "en_us");

        headInterval = Math.max(1, config.getInt("drill.head-interval-ticks", 60));
        segmentInterval = Math.max(1, config.getInt("drill.segment-interval-ticks", 20));
        maxSegments = Math.max(0, config.getInt("drill.max-segments", 32));
        breakUnbreakable = config.getBoolean("drill.break-unbreakable", false);

        Material toolMaterial = Material.matchMaterial(config.getString("drill.tool", "NETHERITE_PICKAXE"));
        if (toolMaterial == null || toolMaterial.isAir()) {
            log.warning("config.yml: drill.tool is not a valid item, using NETHERITE_PICKAXE");
            toolMaterial = Material.NETHERITE_PICKAXE;
        }
        tool = toolMaterial;

        sounds = config.getBoolean("effects.sounds", true);
        particles = config.getBoolean("effects.particles", true);
        explosionProof = config.getBoolean("protection.explosion-proof", true);
        discoverOnJoin = config.getBoolean("recipes.discover-on-join", true);

        headBase = block(config, "blocks.head-base", Material.GRAY_GLAZED_TERRACOTTA, log);
        headBit = block(config, "blocks.head-bit", Material.IRON_BLOCK, log);
        headBitCenter = block(config, "blocks.head-bit-center", Material.REDSTONE_LAMP, log);
        headBitCenterActive = block(config, "blocks.head-bit-center-active", Material.GLOWSTONE, log);
        spacer = block(config, "blocks.segment-spacer", Material.WAXED_COPPER_BLOCK, log);
        storage = block(config, "blocks.storage", Material.DARK_OAK_PLANKS, log);
        collector = block(config, "blocks.collector", Material.LAPIS_BLOCK, log);
        incinerator = block(config, "blocks.incinerator", Material.RED_NETHER_BRICKS, log);
        seat = block(config, "blocks.seat", Material.QUARTZ_STAIRS, log);
        transporter = block(config, "blocks.transporter", Material.CYAN_CONCRETE, log);
        transporterCart = block(config, "blocks.transporter-cart", Material.BROWN_TERRACOTTA, log);
        reinforcement = block(config, "blocks.reinforcement", Material.CHISELED_STONE_BRICKS, log);
        decoration = block(config, "blocks.decoration", Material.CHISELED_SANDSTONE, log);

        seatYOffset = config.getDouble("seat.y-offset", 0.4);
        collectorRadius = Math.max(0.5, Math.min(16, config.getDouble("collector.radius", 3.0)));
        supplyAmount = Math.max(0, config.getInt("collector.supply-amount", 64));
        supplyWarningSeconds = Math.max(0, config.getInt("supply-warning-seconds", 60));

        packEnabled = config.getBoolean("resource-pack.enabled", true);
        packRequired = config.getBoolean("resource-pack.required", false);
        worldModels = config.getBoolean("resource-pack.world-models", true);
        packUrl = config.getString("resource-pack.url", "").trim();
        packSha1 = config.getString("resource-pack.sha1", "").trim();

        Set<Material> blocked = EnumSet.noneOf(Material.class);
        for (String name : config.getStringList("unbreakable-blocks")) {
            Material material = Material.matchMaterial(name);
            if (material == null) {
                log.warning("config.yml: unknown material in unbreakable-blocks: " + name);
            } else {
                blocked.add(material);
            }
        }
        unbreakable = blocked;
    }

    /** The block that represents a part in the world and as an item. */
    public Material partMaterial(PartType type) {
        return switch (type) {
            case DRILL_HEAD -> headBase;
            case DRILL_BASE -> spacer;
            case STORAGE -> storage;
            case COLLECTOR -> collector;
            case INCINERATOR -> incinerator;
            case DRILL_SEAT -> seat;
            case TRANSPORTER -> transporter;
            case REINFORCEMENT -> reinforcement;
            case DECORATION -> decoration;
        };
    }

    public Params params() {
        return new Params(headInterval, segmentInterval, breakUnbreakable, particles ? 5 : 0);
    }

    private static Material block(FileConfiguration config, String path, Material fallback, Logger log) {
        String name = config.getString(path);
        if (name == null) {
            return fallback;
        }
        Material material = Material.matchMaterial(name);
        if (material == null || !material.isBlock() || !material.isItem()) {
            log.warning("config.yml: " + path + " must be a placeable block, using " + fallback.name());
            return fallback;
        }
        return material;
    }
}
