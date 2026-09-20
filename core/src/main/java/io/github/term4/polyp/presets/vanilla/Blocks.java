package io.github.term4.polyp.presets.vanilla;

import io.github.term4.polyp.mechanics.blocks.BlocksConfig;

/** Modern block placement: a chest pairs as the registered rule pairs it. */
public final class Blocks {

    private Blocks() {}

    public static BlocksConfig config() {
        return BlocksConfig.builder()
                .chestPlacement(BlocksConfig.ChestPlacement.MODERN)
                .build();
    }
}
