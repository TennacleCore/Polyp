package io.github.term4.polyp.presets.mmc18;

import io.github.term4.polyp.platform.player.PlayerConfig;
import io.github.term4.polyp.presets.vanilla18.Vanilla18;

/** mmc18 player platform config: the vanilla 1.8 base with every move broadcast at once (not yet measured on MineMen). */
public final class Player {

    private Player() {}

    public static PlayerConfig config() {
        return PlayerConfig.builder(Vanilla18.player())
                .positionBroadcastInterval(1)
                .positionRelay(PlayerConfig.PositionRelay.EVERY_MOVE)
                .build();
    }
}
