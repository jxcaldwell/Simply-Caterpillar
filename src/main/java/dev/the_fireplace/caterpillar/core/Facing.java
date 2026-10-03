package dev.the_fireplace.caterpillar.core;

/**
 * The four horizontal directions a caterpillar can face. Declared clockwise (seen from above) so that
 * {@link #right()} is simply the next constant.
 */
public enum Facing {
    NORTH(0, -1),
    EAST(1, 0),
    SOUTH(0, 1),
    WEST(-1, 0);

    public final int dx;
    public final int dz;

    Facing(int dx, int dz) {
        this.dx = dx;
        this.dz = dz;
    }

    /** The direction to the right of someone looking along this facing. */
    public Facing right() {
        return values()[(ordinal() + 1) % 4];
    }

    public Facing left() {
        return values()[(ordinal() + 3) % 4];
    }

    public Facing opposite() {
        return values()[(ordinal() + 2) % 4];
    }

    /** Converts a Minecraft yaw (0 = south, 90 = west, 180 = north, -90 = east) to the nearest facing. */
    public static Facing fromYaw(float yaw) {
        int quadrant = Math.floorMod((int) Math.floor(yaw / 90.0 + 0.5), 4);
        return switch (quadrant) {
            case 0 -> SOUTH;
            case 1 -> WEST;
            case 2 -> NORTH;
            default -> EAST;
        };
    }
}
