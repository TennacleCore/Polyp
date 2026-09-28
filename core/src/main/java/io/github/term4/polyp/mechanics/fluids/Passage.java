package io.github.term4.polyp.mechanics.fluids;

import io.github.term4.polyp.mechanics.fluids.FluidsConfigResolver.FluidContext;
import net.minestom.server.collision.Shape;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.utils.Direction;

/** Whether a fluid may cross the face between two cells, given the blocks standing in them. */
@FunctionalInterface
public interface Passage {

    boolean through(FluidContext ctx, Block from, Direction direction, Block to);

    /** 1.8: cells, never faces; {@code blocked} alone decides. */
    Passage LEGACY = (ctx, from, direction, to) -> true;

    /** 26.1 FlowingFluid.canPassThroughWall: a full cube on either side shuts the face, two partial shapes shut it when their faces together cover it. */
    Passage MODERN = (ctx, from, direction, to) -> {
        Shape a = from.collisionShape(), b = to.collisionShape();
        if (full(b) || full(a)) return false;
        if (empty(a) && empty(b)) return true;
        return !a.isOccluded(b, BlockFace.fromDirection(direction));
    };

    private static boolean full(Shape s) {
        for (BlockFace face : BlockFace.values()) if (!s.isFaceFull(face)) return false;
        return true;
    }

    private static boolean empty(Shape s) {
        Point a = s.relativeStart(), e = s.relativeEnd();
        return e.x() - a.x() <= 0 || e.y() - a.y() <= 0 || e.z() - a.z() <= 0;
    }
}
