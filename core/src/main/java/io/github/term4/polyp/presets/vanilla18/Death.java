package io.github.term4.polyp.presets.vanilla18;

import io.github.term4.polyp.mechanics.containers.Spill;
import io.github.term4.polyp.mechanics.damage.DeathConfig;

/** Vanilla 1.8 death/respawn cleanup, and the inventory thrown from the body; 1.8 has no Curse of Vanishing. */
public final class Death {

    private Death() {}

    public static DeathConfig config() {
        return DeathConfig.builder()
                .clearEffects(true)
                .resetMechanicsState(true)
                .hideCorpse(true)
                .deathAnimationTicks(20)
                .spill(Spill.DROP)
                .dropThrow(Spill.Throw.PLAYER)
                .vanishingCurse(false)
                .build();
    }
}
