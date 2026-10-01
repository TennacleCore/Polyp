package io.github.term4.polyp.presets.vanilla;

import io.github.term4.polyp.mechanics.blocks.BlocksConfig;

/** Modern blocks: a chest pairs as the registered rule pairs it; a dig keeps the 0.7 finish rule 26.1 shares with 1.8. */
public final class Blocks {

    private Blocks() {}

    public static BlocksConfig config() {
        return BlocksConfig.builder()
                .chestPlacement(BlocksConfig.ChestPlacement.MODERN)
                .digFinishShare(0.7)
                .digFinishLate(true)
                .build();
    }
}
