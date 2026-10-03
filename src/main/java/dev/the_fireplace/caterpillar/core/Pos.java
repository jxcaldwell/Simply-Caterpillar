package dev.the_fireplace.caterpillar.core;

/** An integer block position, independent of any world implementation. */
public record Pos(int x, int y, int z) {

    public Pos add(int dx, int dy, int dz) {
        return new Pos(x + dx, y + dy, z + dz);
    }

    /** Moves {@code n} blocks along a horizontal facing. */
    public Pos relative(Facing facing, int n) {
        return new Pos(x + facing.dx * n, y, z + facing.dz * n);
    }

    /**
     * Offsets relative to a facing: {@code forward} along it, {@code up} vertically and {@code right}
     * towards the right-hand side of someone looking along it.
     */
    public Pos offset(Facing facing, int forward, int up, int right) {
        Facing r = facing.right();
        return new Pos(
                x + facing.dx * forward + r.dx * right,
                y + up,
                z + facing.dz * forward + r.dz * right
        );
    }

    public int chunkX() {
        return x >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }
}
