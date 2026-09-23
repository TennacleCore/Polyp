package io.github.term4.polyp.presets.vanilla;

import io.github.term4.polyp.platform.player.PlayerConfig;

/** Modern (26) player platform config: a Q drop or pickup on the eaten stack leaves the eat running. */
public final class Player {

    private Player() {}

    public static PlayerConfig config() {
        return PlayerConfig.builder()
                .countChangeEndsUse(false)
                .build();
    }
}
