package dev.the_fireplace.caterpillar.core;

/**
 * Everything the machine logic needs from the outside world. The Paper plugin implements this against
 * the Bukkit API; the unit tests implement it with an in-memory block map.
 *
 * <p>All methods are called on the server main thread.
 */
public interface Env {

    /** True when every chunk the machine touches (including the ones it is about to enter) is loaded. */
    boolean areaLoaded(Machine machine);

    /** True when the owner is online, so protection checks can be run on their behalf. */
    boolean ownerAvailable(Machine machine);

    Terrain terrain(Machine machine, Pos pos);

    boolean insideBorder(Machine machine, Pos pos);

    /** Asks protection plugins whether the owner may break the block at {@code pos}. */
    boolean allowBreak(Machine machine, Pos pos);

    /** Breaks the block (with drops). Returns false if nothing was broken. */
    boolean breakBlock(Machine machine, Pos pos);

    /**
     * Consumes one unit of fuel from the head inventory.
     *
     * @return the burn time in ticks it provides, or 0 when there is no (valid) fuel
     */
    int takeFuel(Machine machine);

    void placeCell(Machine machine, HeadCell cell);

    void placeSegment(Machine machine, Pos pos, SegmentKind kind);

    void clear(Machine machine, Pos pos);

    /** True if a transporter's cart may occupy this position (air, liquid or a passable block such as a rail). */
    boolean cartSpace(Machine machine, Pos pos);

    /** A segment has just stepped forward to its new position (already rendered there). */
    void segmentMoved(Machine machine, Machine.Segment segment);

    /** Periodic work of a segment whose kind {@link SegmentKind#ticks() ticks} (collecting, incinerating). */
    void segmentTick(Machine machine, Machine.Segment segment);

    /** Visual state of the head while it is cutting. */
    void setDrilling(Machine machine, boolean drilling);

    void drillEffects(Machine machine);

    void moveSound(Machine machine, Pos pos);

    void notifyOwner(Machine machine, Msg msg);
}
