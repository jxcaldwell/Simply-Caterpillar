package dev.the_fireplace.caterpillar.core;

/** Kinds of segment that can trail behind the drill head. */
public enum SegmentKind {
    /** The plain "Basic Drill Segment". */
    SPACER(false),
    /** Extra inventory: nine consumption and nine gathered slots. */
    STORAGE(false),
    /** Vacuums up dropped items into the caterpillar's gathered slots. */
    COLLECTOR(true),
    /** Destroys the configured item types in the gathered slots. */
    INCINERATOR(true),
    /** A seat a player can ride the caterpillar on. */
    SEAT(false);

    private final boolean ticks;

    SegmentKind(boolean ticks) {
        this.ticks = ticks;
    }

    /** True for kinds that do something periodically while the machine is running. */
    public boolean ticks() {
        return ticks;
    }
}
