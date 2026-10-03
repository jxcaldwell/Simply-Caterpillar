package dev.the_fireplace.caterpillar.core;

/** What the drill finds at a position, as far as the machine logic cares. */
public enum Terrain {
    /** Air or something the drill can simply move into. */
    EMPTY,
    /** Water or lava: skipped when drilling, overwritten when advancing. */
    FLUID,
    /** An ordinary breakable block. */
    SOLID,
    /** A block that cannot normally be broken (bedrock and friends). */
    UNBREAKABLE,
    /** A block belonging to a caterpillar (this one or another): never touched. */
    RESERVED
}
