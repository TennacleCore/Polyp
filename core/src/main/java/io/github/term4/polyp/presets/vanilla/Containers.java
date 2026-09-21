package io.github.term4.polyp.presets.vanilla;

import io.github.term4.polyp.mechanics.containers.ContainerTypeConfig;
import io.github.term4.polyp.mechanics.containers.ContainersConfig;
import io.github.term4.polyp.mechanics.containers.Spill;
import net.kyori.adventure.text.Component;
import net.minestom.server.instance.block.Block;

/** Modern containers: 1.8's set with the modern pair order, plus the barrel and the shulker boxes, which drop as an
 *  item carrying their contents. */
public final class Containers {

    private Containers() {}

    public static ContainersConfig config() {
        ContainerTypeConfig chest = io.github.term4.polyp.presets.vanilla18.Containers.chest().toBuilder()
                .pairing(ContainerTypeConfig.Pairing.MODERN).build();
        ContainersConfig.Builder b = io.github.term4.polyp.presets.vanilla18.Containers.config().toBuilder()
                .block(Block.CHEST, chest)
                .block(Block.TRAPPED_CHEST, chest)
                .block(Block.BARREL, ContainerTypeConfig.builder().title(Component.translatable("container.barrel")).build());
        ContainerTypeConfig shulker = ContainerTypeConfig.builder()
                .title(Component.translatable("container.shulkerBox")).spill(Spill.PACK).build();
        for (Block block : Block.values()) {
            if (block.key().value().endsWith("shulker_box")) b.block(block, shulker);
        }
        return b.build();
    }
}
