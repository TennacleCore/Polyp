package io.github.term4.polyp.mechanics.fluids;

import io.github.term4.polyp.world.MechanicsWorld;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import org.jetbrains.annotations.Nullable;

/**
 * Minestom's neighbor-change hook, standing in for 1.8's onNeighborBlockChange, on the fluids and on every block
 * that can hold water; a previous rule keeps its say.
 */
final class FluidPlacementRule extends BlockPlacementRule {

    final @Nullable BlockPlacementRule previous;
    private final FluidSystem system;

    FluidPlacementRule(Block block, @Nullable BlockPlacementRule previous, FluidSystem system) {
        super(block);
        this.previous = previous;
        this.system = system;
    }

    @Override
    public @Nullable Block blockPlace(PlacementState state) {
        Block placed = previous != null ? previous.blockPlace(state) : state.block();
        MechanicsWorld world = MechanicsWorld.ofView(state.instance());
        // the block lands after this returns: its first tick reads it there
        if (world != null && placed != null && Fluids.fluidOf(placed, true) != null) system.schedule(world, state.placePosition(), 1);
        return placed;
    }

    @Override
    public Block blockUpdate(UpdateState state) {
        Block block = previous != null ? previous.blockUpdate(state) : state.currentBlock();
        MechanicsWorld world = MechanicsWorld.ofView(state.instance());
        if (world == null || !block.compare(getBlock())) return block;
        BlockVec pos = state.blockPosition().asBlockVec();
        Block mixed = system.changed(world, pos, block);
        return mixed != null ? mixed : block;
    }

    @Override
    public boolean isSelfReplaceable(Replacement replacement) {
        return previous != null ? previous.isSelfReplaceable(replacement) : super.isSelfReplaceable(replacement);
    }

    @Override
    public int maxUpdateDistance() {
        return previous != null ? previous.maxUpdateDistance() : super.maxUpdateDistance();
    }
}
