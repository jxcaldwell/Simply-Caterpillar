package dev.the_fireplace.caterpillar.core;

/** One block of the drill head structure. */
public record HeadCell(Pos pos, Role role) {

    public enum Role {
        /** The block behind the cutting face; the point the rest of the caterpillar attaches to. */
        BASE,
        /** The middle block of the 3x3 cutting face. */
        BIT_CENTER,
        /** One of the eight outer blocks of the cutting face. */
        BIT_EDGE
    }
}
