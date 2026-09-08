package io.github.term4.polyp.presets.scrims18;

import io.github.term4.polyp.tracking.motion.VelocityRule;

/** Scrims velocity rule: the {@link io.github.term4.polyp.presets.hypixel.Movement Hypixel} arc on 1.8's clock. */
public final class Movement {

    private Movement() {}

    /**
     * Hypixel's arc knobs, stepped once per flying packet instead of once per server tick: 1.8 runs the whole
     * living update from {@code PlayerConnection.a}, so motY's gravity accrues on the player's own movement.
     * Everything else - the apex reseed off, no entity push, the fluid and climb models - stays Hypixel's.
     */
    public static VelocityRule velocity() {
        var base = io.github.term4.polyp.presets.hypixel.Movement.velocity().reconstructionConfig();
        return VelocityRule.simulated(base.toBuilder().motYOnMovePacket(true).build());
    }
}
