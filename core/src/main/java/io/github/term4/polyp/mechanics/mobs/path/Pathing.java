package io.github.term4.polyp.mechanics.mobs.path;

import io.github.term4.polyp.world.MechanicsWorld;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Entity;
import org.jetbrains.annotations.Nullable;

/** How a walking mob plans: which cells are nodes, what each costs, when the search gives up. */
public interface Pathing {

    @Nullable Path toBlock(Navigation nav, Point block);

    @Nullable Path toEntity(Navigation nav, Entity target);

    /** 1.8 WalkNodeProcessor and PathFinder: four neighbors, the vertical-offset codes, the closest point when the target is out of reach. */
    Pathing LEGACY = new Pathing() {
        @Override
        public @Nullable Path toBlock(Navigation nav, Point block) {
            return new PathFinder(new WalkNodes(nav)).pathTo(block);
        }

        @Override
        public @Nullable Path toEntity(Navigation nav, Entity target) {
            return new PathFinder(new WalkNodes(nav)).pathTo(target);
        }
    };

    /** 26.1 WalkNodeEvaluator and PathFinder: typed cells with a cost, diagonals, sixteen visits per block of range. */
    Pathing MODERN = new Pathing() {
        @Override
        public @Nullable Path toBlock(Navigation nav, Point block) {
            MechanicsWorld world = nav.entity().world();
            if (!world.isChunkLoaded(block.blockX() >> 4, block.blockZ() >> 4)) return null;
            return new TypedFinder().find(nav, surface(world, block.asBlockVec()), 1);
        }

        @Override
        public @Nullable Path toEntity(Navigation nav, Entity target) {
            return toBlock(nav, target.getPosition().asBlockVec());
        }

        // GroundPathNavigation.findSurfacePosition: a target in the air drops to the ground, one in the ground rises out
        private BlockVec surface(MechanicsWorld world, BlockVec pos) {
            int minY = world.dimension().minY(), maxY = world.dimension().maxY();
            int x = pos.blockX(), y = pos.blockY(), z = pos.blockZ();
            if (Blocks.at(world, x, y, z).air()) {
                int below = y - 1;
                while (below >= minY && Blocks.at(world, x, below, z).air()) --below;
                if (below >= minY) return new BlockVec(x, below + 1, z);
                y += 1;
                while (y <= maxY && Blocks.at(world, x, y, z).air()) ++y;
            }
            if (!Blocks.at(world, x, y, z).solid()) return new BlockVec(x, y, z);
            ++y;
            while (y <= maxY && Blocks.at(world, x, y, z).solid()) ++y;
            return new BlockVec(x, y, z);
        }
    };
}
