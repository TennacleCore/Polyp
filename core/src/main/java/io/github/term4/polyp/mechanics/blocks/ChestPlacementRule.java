package io.github.term4.polyp.mechanics.blocks;

import io.github.term4.polyp.mechanics.blocks.BlocksConfig.ChestPlacement;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/** The chest slot: MODERN runs the rule this one replaced, LEGACY runs 1.8's. */
final class ChestPlacementRule extends BlockPlacementRule {

    final @Nullable BlockPlacementRule modern;
    private final Function<Block.Getter, ChestPlacement> era;

    ChestPlacementRule(Block block, @Nullable BlockPlacementRule modern, Function<Block.Getter, ChestPlacement> era) {
        super(block);
        this.modern = modern;
        this.era = era;
    }

    @Override
    public @Nullable Block blockPlace(PlacementState state) {
        Block.Getter world = state.instance();
        if (era.apply(world) == ChestPlacement.LEGACY && world instanceof Block.Setter setter) {
            float yaw = state.playerPosition() != null ? state.playerPosition().yaw() : 0f;
            boolean waterlogged = world.getBlock(state.placePosition()).compare(Block.WATER);
            Block placing = state.block().withProperty("waterlogged", String.valueOf(waterlogged));
            return LegacyChestPlacement.place(world, setter, state.placePosition(), placing, yaw);
        }
        return modern != null ? modern.blockPlace(state) : state.block();
    }

    // un-pairing a half whose partner is gone is the same in both eras
    @Override
    public Block blockUpdate(UpdateState updateState) {
        return modern != null ? modern.blockUpdate(updateState) : super.blockUpdate(updateState);
    }

    @Override
    public boolean isSelfReplaceable(Replacement replacement) {
        return modern != null ? modern.isSelfReplaceable(replacement) : super.isSelfReplaceable(replacement);
    }

    @Override
    public int maxUpdateDistance() {
        return modern != null ? modern.maxUpdateDistance() : super.maxUpdateDistance();
    }
}
