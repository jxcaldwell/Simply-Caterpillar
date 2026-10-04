package dev.the_fireplace.caterpillar.paper;

import dev.the_fireplace.caterpillar.core.SegmentKind;

/** The craftable, placeable parts of a caterpillar. */
public enum PartType {
    DRILL_HEAD("drill_head", null),
    DRILL_BASE("drill_base", SegmentKind.SPACER),
    STORAGE("storage", SegmentKind.STORAGE),
    COLLECTOR("collector", SegmentKind.COLLECTOR),
    INCINERATOR("incinerator", SegmentKind.INCINERATOR),
    DRILL_SEAT("drill_seat", SegmentKind.SEAT),
    TRANSPORTER("transporter", SegmentKind.TRANSPORTER),
    REINFORCEMENT("reinforcement", SegmentKind.REINFORCEMENT),
    DECORATION("decoration", SegmentKind.DECORATION);

    public final String id;
    /** The kind of segment this part becomes when placed behind a head; null for the head itself. */
    public final SegmentKind segmentKind;

    PartType(String id, SegmentKind segmentKind) {
        this.id = id;
        this.segmentKind = segmentKind;
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
        for (PartType type : values()) {
            if (type.segmentKind == kind) {
                return type;
            }
        }
        throw new IllegalArgumentException("No part for " + kind);
    }
}
