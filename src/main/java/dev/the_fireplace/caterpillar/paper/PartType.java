package dev.the_fireplace.caterpillar.paper;

import dev.the_fireplace.caterpillar.core.SegmentKind;

/** The craftable, placeable parts of a caterpillar. */
public enum PartType {
    DRILL_HEAD("drill_head"),
    DRILL_BASE("drill_base");

    public final String id;

    PartType(String id) {
        this.id = id;
    }

    public static PartType fromId(String id) {
        for (PartType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return null;
    }

    public static PartType forSegment(SegmentKind kind) {
        return switch (kind) {
            case SPACER -> DRILL_BASE;
        };
    }
}
