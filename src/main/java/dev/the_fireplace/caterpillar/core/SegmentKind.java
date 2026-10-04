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
    SEAT(false),
    /** Carries a chest minecart under itself, fills it with full stacks and releases it when it is full. */
    TRANSPORTER(true),
    /** Lines the tunnel (ceiling, walls, floor) with chosen blocks as it moves, e.g. to seal off water and lava. */
    REINFORCEMENT(false),
    /** Places a repeating pattern of blocks (rails, fences, torches...) in the tunnel it leaves behind. */
    DECORATION(false);

    private final boolean ticks;

    SegmentKind(boolean ticks) {
        this.ticks = ticks;
    }

    /** True for kinds that do something periodically while the machine is running. */
    public boolean ticks() {
        return ticks;
    }
}
