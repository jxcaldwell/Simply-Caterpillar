package dev.the_fireplace.caterpillar.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MachineTest {

    private static final Pos BASE = new Pos(0, 64, 0);

    private static Machine machine(Facing facing, Pos base, int segments, FakeEnv env) {
        Machine m = new Machine(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), facing, base, Params.defaults());
        env.render(m);
        for (int i = 1; i <= segments; i++) {
            Pos p = base.relative(facing, -i);
            m.attachSegment(SegmentKind.SPACER, p, env);
        }
        return m;
    }

    // ------------------------------------------------------------------ geometry

    @Test
    void facingRightIsClockwise() {
        assertEquals(Facing.EAST, Facing.NORTH.right());
        assertEquals(Facing.SOUTH, Facing.EAST.right());
        assertEquals(Facing.WEST, Facing.SOUTH.right());
        assertEquals(Facing.NORTH, Facing.WEST.right());
        assertEquals(Facing.SOUTH, Facing.NORTH.opposite());
        assertEquals(Facing.WEST, Facing.NORTH.left());
    }

    @Test
    void yawMapsToFacing() {
        assertEquals(Facing.SOUTH, Facing.fromYaw(0));
        assertEquals(Facing.WEST, Facing.fromYaw(90));
        assertEquals(Facing.NORTH, Facing.fromYaw(180));
        assertEquals(Facing.NORTH, Facing.fromYaw(-180));
        assertEquals(Facing.EAST, Facing.fromYaw(-90));
        assertEquals(Facing.EAST, Facing.fromYaw(270));
        assertEquals(Facing.SOUTH, Facing.fromYaw(44));
        assertEquals(Facing.WEST, Facing.fromYaw(46));
        assertEquals(Facing.SOUTH, Facing.fromYaw(-44));
        assertEquals(Facing.EAST, Facing.fromYaw(-46));
    }

    @Test
    void headHasTenCellsAroundTheFace() {
        List<HeadCell> cells = Machine.headCells(BASE, Facing.NORTH);
        assertEquals(10, cells.size());
        assertEquals(new HeadCell(BASE, HeadCell.Role.BASE), cells.get(0));

        Set<Pos> positions = new HashSet<>();
        int centers = 0;
        for (HeadCell cell : cells) {
            positions.add(cell.pos());
            if (cell.role() == HeadCell.Role.BIT_CENTER) {
                centers++;
                assertEquals(new Pos(0, 64, -1), cell.pos());
            }
        }
        assertEquals(10, positions.size());
        assertEquals(1, centers);
        // The face is the plane z = -1, spanning x -1..1 and y 63..65.
        for (int x = -1; x <= 1; x++) {
            for (int y = 63; y <= 65; y++) {
                assertTrue(positions.contains(new Pos(x, y, -1)), "missing " + x + "," + y);
            }
        }
    }

    @Test
    void faceRotatesWithFacing() {
        for (Facing facing : Facing.values()) {
            Set<Pos> positions = new HashSet<>();
            for (HeadCell cell : Machine.headCells(BASE, facing)) {
                positions.add(cell.pos());
            }
            Pos center = BASE.relative(facing, 1);
            for (int up = -1; up <= 1; up++) {
                for (int side = -1; side <= 1; side++) {
                    assertTrue(positions.contains(center.offset(facing, 0, up, side)), facing + " " + up + "," + side);
                }
            }
        }
    }

    // ------------------------------------------------------------------ the drill cycle

    @Test
    void drillsAndAdvancesAfterSixtyTicks() {
        FakeEnv env = new FakeEnv();
        env.fillSolid(-5, 60, -30, 5, 70, 5);
        env.fuelUnits = 10;
        Machine m = machine(Facing.NORTH, BASE, 0, env);
        // Hollow out the space the head itself occupies.
        for (Pos p : m.footprint()) {
            env.solids.remove(p);
        }

        assertTrue(m.powerOn(env));
        env.run(m, 59);
        assertEquals(BASE, m.base());
        assertTrue(env.broken.isEmpty());

        env.run(m, 1);
        assertEquals(9, env.broken.size(), "the 3x3 plane in front of the face is broken");
        assertEquals(new Pos(0, 64, -1), m.base());
        for (Pos target : List.of(new Pos(-1, 63, -2), new Pos(0, 64, -2), new Pos(1, 65, -2))) {
            assertFalse(env.solids.contains(target));
        }
        assertTrue(env.solids.contains(new Pos(0, 64, -3)), "the next layer is still stone");
        assertTrue(env.solids.contains(new Pos(2, 64, -2)), "blocks outside the 3x3 are untouched");
        assertEquals(10, env.placed.size());
        assertEquals("head:BASE", env.placed.get(new Pos(0, 64, -1)));
    }

    @Test
    void segmentsCatchUpOneAtATime() {
        FakeEnv env = new FakeEnv();
        env.fillSolid(-5, 60, -30, 5, 70, 8);
        env.fuelUnits = 10;
        Machine m = machine(Facing.NORTH, BASE, 2, env);
        for (Pos p : m.footprint()) {
            env.solids.remove(p);
        }
        assertEquals(new Pos(0, 64, 2), m.tailPos());

        assertTrue(m.powerOn(env));
        env.run(m, 60);
        assertEquals(new Pos(0, 64, -1), m.base());
        assertTrue(m.moving());
        // Right after the head moved there is a one block gap behind it.
        assertEquals(new Pos(0, 64, 1), m.segments().get(0).pos());
        assertEquals(new Pos(0, 64, 2), m.segments().get(1).pos());

        env.run(m, 20);
        assertEquals(new Pos(0, 64, 0), m.segments().get(0).pos());
        assertEquals(new Pos(0, 64, 2), m.segments().get(1).pos());
        assertTrue(m.moving());

        env.run(m, 20);
        assertEquals(new Pos(0, 64, 1), m.segments().get(1).pos());
        assertFalse(m.moving(), "the wave is over once the last segment caught up");

        // The chain is contiguous again: base -1, segments 0 and 1.
        assertEquals(new Pos(0, 64, 1), m.tailPos());
        assertEquals("segment:SPACER", env.placed.get(new Pos(0, 64, 0)));
        assertEquals("segment:SPACER", env.placed.get(new Pos(0, 64, 1)));
        assertNull(env.placed.get(new Pos(0, 64, 2)), "the old tail position was vacated");
    }

    @Test
    void headWaitsForTheWaveBeforeDrillingAgain() {
        FakeEnv env = new FakeEnv();
        env.fillSolid(-5, 60, -40, 5, 70, 8);
        env.fuelUnits = 50;
        Machine m = machine(Facing.NORTH, BASE, 2, env);
        for (Pos p : m.footprint()) {
            env.solids.remove(p);
        }
        assertTrue(m.powerOn(env));

        // 60 (drill) + 2 * 20 (wave) = 100 ticks per block with two segments.
        env.run(m, 100);
        assertEquals(new Pos(0, 64, -1), m.base());
        assertFalse(m.moving());
        env.run(m, 59);
        assertEquals(new Pos(0, 64, -1), m.base());
        env.run(m, 1);
        assertEquals(new Pos(0, 64, -2), m.base());
    }

    @Test
    void tunnelKeepsGoingForManyBlocks() {
        FakeEnv env = new FakeEnv();
        env.fillSolid(-5, 60, -80, 5, 70, 8);
        env.fuelUnits = 100;
        Machine m = machine(Facing.NORTH, BASE, 3, env);
        for (Pos p : m.footprint()) {
            env.solids.remove(p);
        }
        assertTrue(m.powerOn(env));
        env.run(m, 10 * (60 + 3 * 20));

        assertEquals(new Pos(0, 64, -10), m.base());
        // Chain is contiguous behind the base.
        assertEquals(new Pos(0, 64, -9), m.segments().get(0).pos());
        assertEquals(new Pos(0, 64, -8), m.segments().get(1).pos());
        assertEquals(new Pos(0, 64, -7), m.segments().get(2).pos());
        // The machine always occupies exactly 10 + 3 blocks.
        assertEquals(13, env.placed.size());
        // A 3x3 tunnel was cut behind the face.
        assertFalse(env.solids.contains(new Pos(1, 65, -5)));
        assertTrue(env.solids.contains(new Pos(2, 65, -5)));
    }

    @Test
    void worksInEveryDirection() {
        for (Facing facing : Facing.values()) {
            FakeEnv env = new FakeEnv();
            env.fillSolid(-30, 60, -30, 30, 70, 30);
            env.fuelUnits = 10;
            Machine m = machine(facing, BASE, 1, env);
            for (Pos p : m.footprint()) {
                env.solids.remove(p);
            }
            assertTrue(m.powerOn(env));
            env.run(m, 60 + 20);
            assertEquals(BASE.relative(facing, 1), m.base(), facing.toString());
            assertEquals(BASE, m.segments().get(0).pos(), facing.toString());
            assertEquals(9, env.broken.size());
            assertEquals(11, env.placed.size());
        }
    }

    // ------------------------------------------------------------------ fuel

    @Test
    void burnRateGrowsWithLength() {
        FakeEnv env = new FakeEnv();
        env.fuelUnits = 10;
        Machine m = machine(Facing.NORTH, BASE, 4, env);
        assertEquals(5, m.burnPerTick());
        assertTrue(m.powerOn(env));
        int before = m.litTime();
        env.run(m, 1);
        assertEquals(before - 5, m.litTime());
    }

    @Test
    void cannotPowerOnWithoutFuel() {
        FakeEnv env = new FakeEnv();
        Machine m = machine(Facing.NORTH, BASE, 0, env);
        assertFalse(m.powerOn(env));
        assertFalse(m.powered());
    }

    @Test
    void powersOffAndNotifiesWhenFuelRunsOut() {
        FakeEnv env = new FakeEnv();
        env.fillSolid(-5, 60, -80, 5, 70, 8);
        env.fuelUnits = 1;
        env.fuelBurnTime = 100;
        Machine m = machine(Facing.NORTH, BASE, 0, env);
        for (Pos p : m.footprint()) {
            env.solids.remove(p);
        }
        assertTrue(m.powerOn(env));
        env.run(m, 100);
        assertFalse(m.powered());
        assertEquals(List.of(Msg.OUT_OF_FUEL), env.messages);
        // 100 ticks of burning at one unit per tick: exactly one drill step (tick 60) happened.
        assertEquals(new Pos(0, 64, -1), m.base());
    }

    @Test
    void nothingProgressesWhilePoweredOff() {
        FakeEnv env = new FakeEnv();
        env.fillSolid(-5, 60, -30, 5, 70, 5);
        env.fuelUnits = 5;
        Machine m = machine(Facing.NORTH, BASE, 1, env);
        env.run(m, 500);
        assertEquals(BASE, m.base());
        assertEquals(0, env.fuelTaken);
        assertTrue(env.broken.isEmpty());
    }

    @Test
    void refuelsAutomaticallyWhenBurnTimeEnds() {
        FakeEnv env = new FakeEnv();
        env.fillSolid(-5, 60, -80, 5, 70, 8);
        env.fuelUnits = 3;
        env.fuelBurnTime = 70;
        Machine m = machine(Facing.NORTH, BASE, 0, env);
        for (Pos p : m.footprint()) {
            env.solids.remove(p);
        }
        assertTrue(m.powerOn(env));
        env.run(m, 130);
        assertTrue(m.powered());
        // One unit when powering on, one when the first ran out at tick 70.
        assertEquals(2, env.fuelTaken);
        assertEquals(10, m.litTime());
    }

    // ------------------------------------------------------------------ obstacles

    @Test
    void stopsAtBedrock() {
        FakeEnv env = new FakeEnv();
        env.fillSolid(-5, 60, -30, 5, 70, 5);
        env.unbreakable.add(new Pos(1, 64, -2));
        env.fuelUnits = 5;
        Machine m = machine(Facing.NORTH, BASE, 0, env);
        for (Pos p : m.footprint()) {
            env.solids.remove(p);
        }
        assertTrue(m.powerOn(env));
        env.run(m, 60);
        assertFalse(m.powered());
        assertEquals(List.of(Msg.UNBREAKABLE), env.messages);
        assertTrue(env.broken.isEmpty(), "nothing is broken when the plane contains bedrock");
        assertEquals(BASE, m.base());
        assertFalse(env.drillingVisual);
    }

    @Test
    void breaksBedrockWhenConfigured() {
        FakeEnv env = new FakeEnv();
        env.fillSolid(-5, 60, -30, 5, 70, 5);
        env.unbreakable.add(new Pos(1, 64, -2));
        env.fuelUnits = 5;
        Machine m = new Machine(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Facing.NORTH, BASE,
                new Params(60, 20, true, 5));
        env.render(m);
        for (Pos p : m.footprint()) {
            env.solids.remove(p);
        }
        assertTrue(m.powerOn(env));
        env.run(m, 60);
        assertTrue(m.powered());
        assertEquals(new Pos(0, 64, -1), m.base());
        assertFalse(env.unbreakable.contains(new Pos(1, 64, -2)));
    }

    @Test
    void protectionDenialStopsTheDrill() {
        FakeEnv env = new FakeEnv();
        env.fillSolid(-5, 60, -30, 5, 70, 5);
        env.fuelUnits = 5;
        env.allowBreaking = false;
        Machine m = machine(Facing.NORTH, BASE, 0, env);
        for (Pos p : m.footprint()) {
            env.solids.remove(p);
        }
        assertTrue(m.powerOn(env));
        env.run(m, 60);
        assertFalse(m.powered());
        assertEquals(List.of(Msg.PROTECTED), env.messages);
        assertTrue(env.broken.isEmpty());
        assertEquals(BASE, m.base());
    }

    @Test
    void fluidsAreSkippedAndOverwritten() {
        FakeEnv env = new FakeEnv();
        env.fillSolid(-5, 60, -30, 5, 70, 5);
        env.fluids.add(new Pos(0, 64, -2));
        env.solids.remove(new Pos(0, 64, -2));
        env.fuelUnits = 5;
        Machine m = machine(Facing.NORTH, BASE, 0, env);
        for (Pos p : m.footprint()) {
            env.solids.remove(p);
        }
        assertTrue(m.powerOn(env));
        env.run(m, 60);
        assertEquals(8, env.broken.size(), "the fluid block is not 'broken'");
        assertEquals(new Pos(0, 64, -1), m.base());
    }

    @Test
    void anotherMachineBlocksTheWay() {
        FakeEnv env = new FakeEnv();
        env.fillSolid(-5, 60, -30, 5, 70, 5);
        env.fuelUnits = 5;
        env.placed.put(new Pos(0, 64, -2), "other");
        Machine m = machine(Facing.NORTH, BASE, 0, env);
        for (Pos p : m.footprint()) {
            env.solids.remove(p);
        }
        assertTrue(m.powerOn(env));
        env.run(m, 60);
        assertFalse(m.powered());
        assertEquals(List.of(Msg.PATH_BLOCKED), env.messages);
        assertTrue(env.broken.isEmpty());
    }

    @Test
    void worldBorderStopsTheDrill() {
        FakeEnv env = new FakeEnv();
        env.fillSolid(-5, 60, -30, 5, 70, 5);
        env.borderRadius = 1;
        env.fuelUnits = 5;
        Machine m = machine(Facing.NORTH, BASE, 0, env);
        for (Pos p : m.footprint()) {
            env.solids.remove(p);
        }
        assertTrue(m.powerOn(env));
        env.run(m, 60);
        assertFalse(m.powered());
        assertEquals(List.of(Msg.WORLD_BORDER), env.messages);
        assertEquals(BASE, m.base());
        assertTrue(env.broken.isEmpty(), "the border is checked before anything is broken");
    }

    @Test
    void blockedGapStopsTheWave() {
        FakeEnv env = new FakeEnv();
        env.fillSolid(-5, 60, -30, 5, 70, 8);
        env.fuelUnits = 5;
        Machine m = machine(Facing.NORTH, BASE, 1, env);
        for (Pos p : m.footprint()) {
            env.solids.remove(p);
        }
        assertTrue(m.powerOn(env));
        env.run(m, 60);
        // Someone fills the gap behind the head.
        env.solids.add(new Pos(0, 64, 0));
        env.run(m, 20);
        assertFalse(m.powered());
        assertEquals(List.of(Msg.PATH_BLOCKED), env.messages);
        assertEquals(new Pos(0, 64, 1), m.segments().get(0).pos());
    }

    // ------------------------------------------------------------------ world state

    @Test
    void pausesWhenChunksAreUnloadedOrOwnerIsOffline() {
        FakeEnv env = new FakeEnv();
        env.fillSolid(-5, 60, -30, 5, 70, 5);
        env.fuelUnits = 5;
        Machine m = machine(Facing.NORTH, BASE, 0, env);
        for (Pos p : m.footprint()) {
            env.solids.remove(p);
        }
        assertTrue(m.powerOn(env));
        int lit = m.litTime();

        env.loaded = false;
        env.run(m, 200);
        assertEquals(lit, m.litTime(), "no fuel burns while paused");
        assertEquals(BASE, m.base());

        env.loaded = true;
        env.ownerOnline = false;
        env.run(m, 200);
        assertEquals(lit, m.litTime());
        assertEquals(BASE, m.base());

        env.ownerOnline = true;
        env.run(m, 60);
        assertEquals(new Pos(0, 64, -1), m.base());
    }

    @Test
    void drillingVisualFollowsTheCycle() {
        FakeEnv env = new FakeEnv();
        env.fillSolid(-5, 60, -30, 5, 70, 5);
        env.fuelUnits = 5;
        Machine m = machine(Facing.NORTH, BASE, 1, env);
        for (Pos p : m.footprint()) {
            env.solids.remove(p);
        }
        assertTrue(m.powerOn(env));
        env.run(m, 1);
        assertTrue(env.drillingVisual);
        env.run(m, 59);
        assertFalse(env.drillingVisual, "off while the segments catch up");
        m.powerOff(env);
        assertFalse(env.drillingVisual);
    }

    // ------------------------------------------------------------------ editing the chain

    @Test
    void attachRules() {
        FakeEnv env = new FakeEnv();
        Machine m = machine(Facing.NORTH, BASE, 0, env);
        assertEquals(new Pos(0, 64, 1), m.nextSegmentPos());
        assertEquals("not_behind", m.attachProblem(new Pos(0, 64, 2), 8));
        assertEquals("not_behind", m.attachProblem(new Pos(1, 64, 1), 8));
        assertNull(m.attachProblem(new Pos(0, 64, 1), 8));
        m.attachSegment(SegmentKind.SPACER, new Pos(0, 64, 1), env);
        assertEquals(new Pos(0, 64, 2), m.nextSegmentPos());
        assertEquals("too_long", m.attachProblem(new Pos(0, 64, 2), 1));
        assertEquals(1, m.segments().size());
        assertEquals("segment:SPACER", env.placed.get(new Pos(0, 64, 1)));
    }

    @Test
    void cannotAttachWhileMoving() {
        FakeEnv env = new FakeEnv();
        env.fillSolid(-5, 60, -30, 5, 70, 8);
        env.fuelUnits = 5;
        Machine m = machine(Facing.NORTH, BASE, 1, env);
        for (Pos p : m.footprint()) {
            env.solids.remove(p);
        }
        assertTrue(m.powerOn(env));
        env.run(m, 60);
        assertTrue(m.moving());
        assertEquals("moving", m.attachProblem(m.nextSegmentPos(), 8));
    }

    @Test
    void detachingASegmentKeepsTheRestWorking() {
        FakeEnv env = new FakeEnv();
        env.fillSolid(-5, 60, -40, 5, 70, 8);
        env.fuelUnits = 20;
        Machine m = machine(Facing.NORTH, BASE, 3, env);
        for (Pos p : m.footprint()) {
            env.solids.remove(p);
        }
        Machine.Segment removed = m.detachSegment(new Pos(0, 64, 2));
        assertNotNull(removed);
        env.clear(m, removed.pos());
        assertEquals(2, m.segments().size());
        assertNull(m.detachSegment(new Pos(9, 9, 9)));

        assertTrue(m.powerOn(env));
        env.run(m, 60 + 2 * 20);
        assertEquals(new Pos(0, 64, -1), m.base());
        assertFalse(m.moving());
        // The remaining segments each moved one block, so the gap left by the removed one is preserved.
        assertEquals(new Pos(0, 64, 0), m.segments().get(0).pos());
        assertEquals(new Pos(0, 64, 2), m.segments().get(1).pos());
    }

    @Test
    void detachingDuringTheWaveAdjustsTheIndex() {
        FakeEnv env = new FakeEnv();
        env.fillSolid(-5, 60, -40, 5, 70, 8);
        env.fuelUnits = 20;
        Machine m = machine(Facing.NORTH, BASE, 2, env);
        for (Pos p : m.footprint()) {
            env.solids.remove(p);
        }
        assertTrue(m.powerOn(env));
        env.run(m, 60 + 20);
        assertTrue(m.moving());
        assertEquals(1, m.waveIndex());
        // Remove the segment that already moved: the wave must now point at what used to be index 1.
        m.detachSegment(m.segments().get(0).pos());
        assertEquals(0, m.waveIndex());
        assertTrue(m.moving());
        // Remove the last one too: nothing left to move.
        m.detachSegment(m.segments().get(0).pos());
        assertFalse(m.moving());
    }

    @Test
    void restoredStateIsSanitised() {
        FakeEnv env = new FakeEnv();
        Machine m = machine(Facing.NORTH, BASE, 1, env);
        m.restoreState(-5, 100, true, true, 7, -1, -1);
        assertEquals(0, m.litTime());
        assertFalse(m.moving(), "an out-of-range wave index cancels the movement");
        assertEquals(0, m.drillTimer());
        m.restoreState(50, 100, true, true, 0, 3, 4);
        assertTrue(m.moving());
        assertEquals(0.5, m.burnFraction(), 1e-9);
    }
}
