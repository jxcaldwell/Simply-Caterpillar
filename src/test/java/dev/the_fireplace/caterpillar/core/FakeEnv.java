package dev.the_fireplace.caterpillar.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A tiny in-memory world for exercising {@link Machine}. */
final class FakeEnv implements Env {

    final Set<Pos> solids = new HashSet<>();
    final Set<Pos> unbreakable = new HashSet<>();
    final Set<Pos> fluids = new HashSet<>();
    /** Blocks placed by the machine, by position. */
    final Map<Pos, String> placed = new HashMap<>();
    final List<Msg> messages = new ArrayList<>();
    final List<Pos> broken = new ArrayList<>();
    final List<String> moved = new ArrayList<>();
    final List<SegmentKind> segmentTicks = new ArrayList<>();

    int fuelUnits = 0;
    int fuelBurnTime = 1600;
    int fuelTaken = 0;
    boolean loaded = true;
    boolean ownerOnline = true;
    boolean allowBreaking = true;
    int borderRadius = Integer.MAX_VALUE;
    int drillingToggles = 0;
    boolean drillingVisual = false;

    /** Fills a box with stone. */
    void fillSolid(int x1, int y1, int z1, int x2, int y2, int z2) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    solids.add(new Pos(x, y, z));
                }
            }
        }
    }

    /** Renders a freshly created machine into the fake world, as the plugin does on placement. */
    void render(Machine machine) {
        for (HeadCell cell : machine.headCells()) {
            placeCell(machine, cell);
        }
    }

    @Override
    public boolean areaLoaded(Machine machine) {
        return loaded;
    }

    @Override
    public boolean ownerAvailable(Machine machine) {
        return ownerOnline;
    }

    @Override
    public Terrain terrain(Machine machine, Pos pos) {
        if (placed.containsKey(pos)) {
            return Terrain.RESERVED;
        }
        if (unbreakable.contains(pos)) {
            return Terrain.UNBREAKABLE;
        }
        if (solids.contains(pos)) {
            return Terrain.SOLID;
        }
        if (fluids.contains(pos)) {
            return Terrain.FLUID;
        }
        return Terrain.EMPTY;
    }

    @Override
    public boolean insideBorder(Machine machine, Pos pos) {
        return Math.abs(pos.x()) <= borderRadius && Math.abs(pos.z()) <= borderRadius;
    }

    @Override
    public boolean allowBreak(Machine machine, Pos pos) {
        return allowBreaking;
    }

    @Override
    public boolean breakBlock(Machine machine, Pos pos) {
        boolean had = solids.remove(pos) | unbreakable.remove(pos);
        if (had) {
            broken.add(pos);
        }
        return had;
    }

    @Override
    public int takeFuel(Machine machine) {
        if (fuelUnits <= 0) {
            return 0;
        }
        fuelUnits--;
        fuelTaken++;
        return fuelBurnTime;
    }

    @Override
    public void placeCell(Machine machine, HeadCell cell) {
        placed.put(cell.pos(), "head:" + cell.role());
    }

    @Override
    public void placeSegment(Machine machine, Pos pos, SegmentKind kind) {
        placed.put(pos, "segment:" + kind);
    }

    @Override
    public void clear(Machine machine, Pos pos) {
        placed.remove(pos);
    }

    @Override
    public boolean cartSpace(Machine machine, Pos pos) {
        Terrain terrain = terrain(machine, pos);
        return terrain == Terrain.EMPTY || terrain == Terrain.FLUID;
    }

    @Override
    public void segmentMoved(Machine machine, Machine.Segment segment) {
        moved.add(segment.id() + "@" + segment.pos().x() + "," + segment.pos().y() + "," + segment.pos().z());
    }

    @Override
    public void segmentTick(Machine machine, Machine.Segment segment) {
        segmentTicks.add(segment.kind());
    }

    @Override
    public void setDrilling(Machine machine, boolean drilling) {
        drillingVisual = drilling;
        drillingToggles++;
    }

    @Override
    public void drillEffects(Machine machine) {
    }

    @Override
    public void moveSound(Machine machine, Pos pos) {
    }

    @Override
    public void notifyOwner(Machine machine, Msg msg) {
        messages.add(msg);
    }

    void run(Machine machine, int ticks) {
        for (int i = 0; i < ticks; i++) {
            machine.tick(this);
        }
    }
}
