package io.github.term4.polyp.presets.vanilla;

import io.github.term4.polyp.mechanics.containers.Spill;
import io.github.term4.polyp.mechanics.damage.DeathConfig;

/** Modern (26.1+) death/respawn cleanup and drops: 1.8's, but Curse of Vanishing items are destroyed. */
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
                .vanishingCurse(true)
                .build();
    }
}
