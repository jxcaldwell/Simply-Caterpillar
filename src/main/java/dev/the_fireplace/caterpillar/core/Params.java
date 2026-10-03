package dev.the_fireplace.caterpillar.core;

/**
 * Tunables for the machine logic.
 *
 * @param headInterval       ticks of burning fuel between two drill-and-advance steps (original: 60)
 * @param segmentInterval    ticks between each trailing segment catching up (original: 20)
 * @param breakUnbreakable   whether the drill may destroy bedrock and other normally unbreakable blocks
 * @param drillParticleEvery ticks between particle bursts while drilling (0 disables)
 */
public record Params(int headInterval, int segmentInterval, boolean breakUnbreakable, int drillParticleEvery) {

    public Params {
        if (headInterval < 1) {
            throw new IllegalArgumentException("headInterval must be >= 1");
        }
        if (segmentInterval < 1) {
            throw new IllegalArgumentException("segmentInterval must be >= 1");
        }
    }

    public static Params defaults() {
        return new Params(60, 20, false, 5);
    }
}
