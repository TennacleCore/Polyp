package io.github.term4.polyp.presets.vanilla18;

import io.github.term4.polyp.mechanics.blocks.BlocksConfig;

/** 1.8 block placement: {@code BlockChest} pairs by adjacency, and a chest beside two never lands. */
public final class Blocks {

    private Blocks() {}

    public static BlocksConfig config() {
        return BlocksConfig.builder()
                .chestPlacement(BlocksConfig.ChestPlacement.LEGACY)
                .build();
    }
}
