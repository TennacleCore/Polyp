package io.github.term4.polyp.presets.vanilla18;

import io.github.term4.polyp.platform.player.PlayerConfig;

/**
 * Vanilla 1.8 player platform config: position broadcast every 2 ticks behind the tracker's gates (a networking
 * cadence, not movement physics), a Q drop or pickup on the eaten stack ends the eat on the client, not on the
 * server, and a closed window drops its cursor item.
 */
public final class Player {

    private Player() {}

    public static PlayerConfig config() {
        return PlayerConfig.builder()
                .positionBroadcastInterval(2)
                .positionRelay(PlayerConfig.PositionRelay.LEGACY)
                .countChangeEndsUse(true)
                .cursorOnClose(PlayerConfig.CursorOnClose.DROP)
                .build();
    }
}
