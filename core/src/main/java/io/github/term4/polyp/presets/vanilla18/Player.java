package io.github.term4.polyp.presets.vanilla18;

import io.github.term4.polyp.platform.player.PlayerConfig;

/**
 * Vanilla 1.8 player platform config: position broadcast every 2 ticks (a networking cadence, not movement physics),
 * and a Q drop or pickup on the eaten stack ends the eat on the client, not on the server.
 */
public final class Player {

    private Player() {}

    public static PlayerConfig config() {
        return PlayerConfig.builder()
                .positionBroadcastInterval(2)
                .countChangeEndsUse(true)
                .build();
    }
}
