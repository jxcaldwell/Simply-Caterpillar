package dev.the_fireplace.caterpillar.core;

/** Reasons the machine wants to tell its owner about. The Paper layer maps each to a translated message. */
public enum Msg {
    UNBREAKABLE("unbreakable"),
    PROTECTED("protected"),
    PATH_BLOCKED("path_blocked"),
    OUT_OF_FUEL("out_of_fuel"),
    WORLD_BORDER("world_border");

    public final String key;

    Msg(String key) {
        this.key = key;
    }
}
