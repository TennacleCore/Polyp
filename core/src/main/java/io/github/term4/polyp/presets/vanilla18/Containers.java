package io.github.term4.polyp.presets.vanilla18;

import io.github.term4.polyp.mechanics.containers.ContainerFill;
import io.github.term4.polyp.mechanics.containers.ContainerKey;
import io.github.term4.polyp.mechanics.containers.ContainerTypeConfig;
import io.github.term4.polyp.mechanics.containers.ContainersConfig;
import io.github.term4.polyp.mechanics.containers.Spill;
import net.kyori.adventure.text.Component;
import net.minestom.server.instance.block.Block;

/** Vanilla 1.8 containers: chests pair and spill, the ender chest is the opener's and outlives its block. */
public final class Containers {

    private Containers() {}

    public static ContainersConfig config() {
        return ContainersConfig.builder()
                .defaults(ContainerTypeConfig.builder()
                        .rows(3).key(ContainerKey.BLOCK).fill(ContainerFill.EMPTY).spill(Spill.DROP)
                        .pairing(ContainerTypeConfig.Pairing.NONE)
                        .build())
                .block(Block.CHEST, chest())
                .block(Block.TRAPPED_CHEST, chest())
                .block(Block.ENDER_CHEST, ContainerTypeConfig.builder()
                        .title(Component.translatable("container.enderchest"))
                        .key(ContainerKey.VIEWER).spill(Spill.KEEP)
                        .build())
                .build();
    }

    public static ContainerTypeConfig chest() {
        return ContainerTypeConfig.builder().title(Component.translatable("container.chest"))
                .pairing(ContainerTypeConfig.Pairing.LEGACY).build();
    }
}
