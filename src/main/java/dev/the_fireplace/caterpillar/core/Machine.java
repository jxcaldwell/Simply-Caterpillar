package dev.the_fireplace.caterpillar.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * One caterpillar: a drill head with an ordered chain of segments trailing behind it.
 *
 * <p>Geometry, relative to the head's {@code base} position and the machine's facing {@code f}:
 * <ul>
 *   <li>the cutting face is a 3x3 plane one block in front of the base ({@code base + f});</li>
 *   <li>the blocks the drill breaks are the 3x3 plane directly in front of the cutting face
 *       ({@code base + 2f});</li>
 *   <li>segments sit in a line behind the base ({@code base - f}, {@code base - 2f}, ...).</li>
 * </ul>
 *
 * <p>Behaviour follows the original mod: while powered and burning, fuel is consumed every tick (one unit
 * per part); every {@code headInterval} ticks the head breaks its 3x3 target and steps one block forward,
 * then each trailing segment steps forward in turn, one per {@code segmentInterval} ticks. Nothing
 * progresses while the machine is unpowered or out of fuel.
 */
public final class Machine {

    /** A segment trailing behind the head. */
    public static final class Segment {
        private final SegmentKind kind;
        private Pos pos;

        public Segment(SegmentKind kind, Pos pos) {
            this.kind = kind;
            this.pos = pos;
        }

        public SegmentKind kind() {
            return kind;
        }

        public Pos pos() {
            return pos;
        }
    }

    private final UUID id;
    private final UUID owner;
    private final UUID world;
    private final Facing facing;
    private Params params;
    private final List<Segment> segments = new ArrayList<>();

    private Pos base;
    private int litTime;
    private int litDuration;
    private boolean powered;
    private boolean moving;
    private int waveIndex;
    private int drillTimer;
    private int waveTimer;
    private boolean drillingVisual;
    private int layoutVersion;

    public Machine(UUID id, UUID owner, UUID world, Facing facing, Pos base, Params params) {
        this.id = id;
        this.owner = owner;
        this.world = world;
        this.facing = facing;
        this.base = base;
        this.params = params;
    }

    // ---------------------------------------------------------------- identity and layout

    public UUID id() {
        return id;
    }

    public UUID owner() {
        return owner;
    }

    public UUID world() {
        return world;
    }

    public Facing facing() {
        return facing;
    }

    public Pos base() {
        return base;
    }

    /** Applies new tunables, e.g. after a configuration reload. */
    public void setParams(Params params) {
        this.params = params;
    }

    public List<Segment> segments() {
        return Collections.unmodifiableList(segments);
    }

    /** Incremented every time a block of this machine is added, removed or moved. */
    public int layoutVersion() {
        return layoutVersion;
    }

    /** The 10 blocks of the head: the base plus the 3x3 cutting face. */
    public static List<HeadCell> headCells(Pos base, Facing facing) {
        List<HeadCell> cells = new ArrayList<>(10);
        cells.add(new HeadCell(base, HeadCell.Role.BASE));
        for (int up = -1; up <= 1; up++) {
            for (int right = -1; right <= 1; right++) {
                HeadCell.Role role = (up == 0 && right == 0) ? HeadCell.Role.BIT_CENTER : HeadCell.Role.BIT_EDGE;
                cells.add(new HeadCell(base.offset(facing, 1, up, right), role));
            }
        }
        return cells;
    }

    public List<HeadCell> headCells() {
        return headCells(base, facing);
    }

    /** The 3x3 plane of blocks the head breaks next. */
    public List<Pos> drillTargets() {
        return targetsFor(base, facing);
    }

    private static List<Pos> targetsFor(Pos base, Facing facing) {
        List<Pos> targets = new ArrayList<>(9);
        for (int up = -1; up <= 1; up++) {
            for (int right = -1; right <= 1; right++) {
                targets.add(base.offset(facing, 2, up, right));
            }
        }
        return targets;
    }

    /** Every block currently occupied by this machine. */
    public List<Pos> footprint() {
        List<Pos> all = new ArrayList<>(10 + segments.size());
        for (HeadCell cell : headCells()) {
            all.add(cell.pos());
        }
        for (Segment segment : segments) {
            all.add(segment.pos);
        }
        return all;
    }

    /** Blocks the machine may touch on its next steps, used to check that the chunks are loaded. */
    public List<Pos> reach() {
        List<Pos> reach = new ArrayList<>(drillTargets());
        reach.add(base.relative(facing, 3));
        for (Segment segment : segments) {
            reach.add(segment.pos.relative(facing, 1));
        }
        return reach;
    }

    public boolean isHeadPos(Pos pos) {
        for (HeadCell cell : headCells()) {
            if (cell.pos().equals(pos)) {
                return true;
            }
        }
        return false;
    }

    /** The segment at {@code pos}, or null. */
    public Segment segmentAt(Pos pos) {
        for (Segment segment : segments) {
            if (segment.pos.equals(pos)) {
                return segment;
            }
        }
        return null;
    }

    /** The position of the last part of the chain: the final segment, or the head base when there is none. */
    public Pos tailPos() {
        return segments.isEmpty() ? base : segments.get(segments.size() - 1).pos;
    }

    /** Where the next segment has to be placed to attach to this machine. */
    public Pos nextSegmentPos() {
        return tailPos().relative(facing, -1);
    }

    // ---------------------------------------------------------------- state

    public boolean powered() {
        return powered;
    }

    public boolean moving() {
        return moving;
    }

    public int litTime() {
        return litTime;
    }

    public int litDuration() {
        return litDuration;
    }

    public int waveIndex() {
        return waveIndex;
    }

    public int drillTimer() {
        return drillTimer;
    }

    public int waveTimer() {
        return waveTimer;
    }

    /** Fuel units burnt per tick: one for the head plus one per segment. */
    public int burnPerTick() {
        return 1 + segments.size();
    }

    /** Fraction of the current fuel item that is left, 0..1. */
    public double burnFraction() {
        if (litDuration <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(1, litTime / (double) litDuration));
    }

    /** Restores persisted state; used when loading from disk. */
    public void restoreState(int litTime, int litDuration, boolean powered, boolean moving,
                             int waveIndex, int drillTimer, int waveTimer) {
        this.litTime = Math.max(0, litTime);
        this.litDuration = Math.max(0, litDuration);
        this.powered = powered;
        this.moving = moving;
        this.waveIndex = Math.max(0, waveIndex);
        this.drillTimer = Math.max(0, drillTimer);
        this.waveTimer = Math.max(0, waveTimer);
        if (this.moving && (segments.isEmpty() || this.waveIndex >= segments.size())) {
            this.moving = false;
            this.waveIndex = 0;
        }
    }

    /** Adds a segment without touching the world; used when loading from disk. */
    public void restoreSegment(SegmentKind kind, Pos pos) {
        segments.add(new Segment(kind, pos));
        layoutVersion++;
    }

    // ---------------------------------------------------------------- editing the chain

    /** Why a segment cannot be attached right now, or null if it can be. */
    public String attachProblem(Pos pos, int maxSegments) {
        if (moving) {
            return "moving";
        }
        if (segments.size() >= maxSegments) {
            return "too_long";
        }
        if (!nextSegmentPos().equals(pos)) {
            return "not_behind";
        }
        return null;
    }

    /** Attaches a segment at {@code pos} (which must satisfy {@link #attachProblem}) and renders it. */
    public void attachSegment(SegmentKind kind, Pos pos, Env env) {
        Segment segment = new Segment(kind, pos);
        segments.add(segment);
        layoutVersion++;
        env.placeSegment(this, pos, kind);
    }

    /** Removes the segment at {@code pos} from the chain without touching the world. */
    public Segment detachSegment(Pos pos) {
        for (int i = 0; i < segments.size(); i++) {
            if (segments.get(i).pos.equals(pos)) {
                Segment removed = segments.remove(i);
                if (i < waveIndex) {
                    waveIndex--;
                }
                if (moving && waveIndex >= segments.size()) {
                    moving = false;
                    waveIndex = 0;
                    waveTimer = 0;
                    drillTimer = 0;
                }
                layoutVersion++;
                return removed;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- power

    /** Turns the machine on. Returns false (and stays off) when there is no fuel. */
    public boolean powerOn(Env env) {
        if (litTime <= 0) {
            int burn = env.takeFuel(this);
            if (burn <= 0) {
                return false;
            }
            litTime = burn;
            litDuration = burn;
        }
        powered = true;
        return true;
    }

    public void powerOff(Env env) {
        powered = false;
        setDrillingVisual(env, false);
    }

    private void powerOff(Env env, Msg reason) {
        powerOff(env);
        env.notifyOwner(this, reason);
    }

    private void setDrillingVisual(Env env, boolean drilling) {
        if (drillingVisual != drilling) {
            drillingVisual = drilling;
            env.setDrilling(this, drilling);
        }
    }

    // ---------------------------------------------------------------- the tick

    public void tick(Env env) {
        if (!env.areaLoaded(this) || !env.ownerAvailable(this)) {
            return;
        }

        boolean running = powered && litTime > 0;
        if (running) {
            litTime = Math.max(0, litTime - burnPerTick());

            if (moving) {
                waveTimer++;
                if (waveTimer >= params.segmentInterval()) {
                    waveTimer = 0;
                    advanceWave(env);
                }
            } else {
                setDrillingVisual(env, true);
                drillTimer++;
                if (params.drillParticleEvery() > 0 && drillTimer % params.drillParticleEvery() == 0) {
                    env.drillEffects(this);
                }
                if (drillTimer >= params.headInterval()) {
                    drillTimer = 0;
                    setDrillingVisual(env, false);
                    drillAndAdvance(env);
                }
            }
        }

        if (powered && litTime <= 0) {
            int burn = env.takeFuel(this);
            if (burn > 0) {
                litTime = burn;
                litDuration = burn;
            } else {
                powerOff(env, Msg.OUT_OF_FUEL);
            }
        }
    }

    /** Breaks the 3x3 target in front of the cutting face and, if that worked, steps the head forward. */
    private void drillAndAdvance(Env env) {
        List<Pos> targets = drillTargets();

        // Phase 1: make sure we are allowed to clear all nine blocks before breaking any of them.
        for (Pos target : targets) {
            if (!env.insideBorder(this, target)) {
                powerOff(env, Msg.WORLD_BORDER);
                return;
            }
            Terrain terrain = env.terrain(this, target);
            switch (terrain) {
                case RESERVED -> {
                    powerOff(env, Msg.PATH_BLOCKED);
                    return;
                }
                case UNBREAKABLE -> {
                    if (!params.breakUnbreakable()) {
                        powerOff(env, Msg.UNBREAKABLE);
                        return;
                    }
                    if (!env.allowBreak(this, target)) {
                        powerOff(env, Msg.PROTECTED);
                        return;
                    }
                }
                case SOLID -> {
                    if (!env.allowBreak(this, target)) {
                        powerOff(env, Msg.PROTECTED);
                        return;
                    }
                }
                case EMPTY, FLUID -> { }
            }
        }

        // Phase 2: break them.
        for (Pos target : targets) {
            Terrain terrain = env.terrain(this, target);
            if (terrain == Terrain.SOLID || terrain == Terrain.UNBREAKABLE) {
                env.breakBlock(this, target);
            }
        }

        // Phase 3: step forward if the way is really clear.
        for (Pos target : targets) {
            Terrain terrain = env.terrain(this, target);
            if (terrain != Terrain.EMPTY && terrain != Terrain.FLUID) {
                powerOff(env, Msg.PATH_BLOCKED);
                return;
            }
        }

        shiftHead(env);
    }

    private void shiftHead(Env env) {
        for (HeadCell cell : headCells()) {
            env.clear(this, cell.pos());
        }
        base = base.relative(facing, 1);
        for (HeadCell cell : headCells()) {
            env.placeCell(this, cell);
        }
        if (drillingVisual) {
            // The new centre block was placed in its idle look; keep showing that the head is working.
            env.setDrilling(this, true);
        }
        env.moveSound(this, base);
        layoutVersion++;

        if (!segments.isEmpty()) {
            moving = true;
            waveIndex = 0;
            waveTimer = 0;
        }
    }

    /** Moves the next trailing segment one block forward to close the gap the head opened up. */
    private void advanceWave(Env env) {
        if (waveIndex >= segments.size()) {
            moving = false;
            waveIndex = 0;
            return;
        }

        Segment segment = segments.get(waveIndex);
        Pos next = segment.pos.relative(facing, 1);
        Terrain terrain = env.terrain(this, next);
        if (terrain != Terrain.EMPTY && terrain != Terrain.FLUID) {
            powerOff(env, Msg.PATH_BLOCKED);
            return;
        }

        env.clear(this, segment.pos);
        segment.pos = next;
        env.placeSegment(this, next, segment.kind);
        env.moveSound(this, next);
        layoutVersion++;

        waveIndex++;
        if (waveIndex >= segments.size()) {
            moving = false;
            waveIndex = 0;
        }
    }
}
