package io.github.term4.polyp.mechanics.mobs.path;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.entity.attribute.Attribute;
import org.jetbrains.annotations.Nullable;

/** 26.1 PathFinder: A* over {@link TypedNodes} with a visit budget, ending at the best node seen when the target is out of reach. */
final class TypedFinder {

    private static final float FUDGING = 1.5f;

    private final PathHeap open = new PathHeap();
    private final PathPoint[] neighbors = new PathPoint[32];
    private PathPoint target;
    private PathPoint best;
    private float bestH = Float.MAX_VALUE;

    /** PathNavigation.createPath: the range is the follow range, sixteen at least; the budget sixteen visits a block. */
    @Nullable Path find(Navigation nav, BlockVec to, int reach) {
        float range = Math.max((float) nav.entity().getAttributeValue(Attribute.FOLLOW_RANGE), 16.0f);
        return find(new TypedNodes(nav), to, range, reach, (int) Math.floor(range * 16.0f));
    }

    @Nullable Path find(TypedNodes nodes, BlockVec to, float maxLength, int reach, int maxVisited) {
        PathPoint from = nodes.start();
        target = new PathPoint(to.blockX(), to.blockY(), to.blockZ());
        from.totalPathDistance = 0.0f;
        from.distanceToNext = h(from);
        from.distanceToTarget = from.distanceToNext;
        open.clear();
        open.add(from);
        int visited = 0;
        while (!open.isEmpty()) {
            if (++visited >= maxVisited) break;
            PathPoint current = open.dequeue();
            current.visited = true;
            if (current.distanceManhattan(target) <= reach) break;
            if (current.distanceTo(from) >= maxLength) continue;
            int n = nodes.neighbors(neighbors, current);
            for (int i = 0; i < n; i++) {
                PathPoint next = neighbors[i];
                float step = current.distanceTo(next);
                next.walkedDistance = current.walkedDistance + step;
                float g = current.totalPathDistance + step + next.costMalus;
                if (next.walkedDistance < maxLength && (!next.isAssigned() || g < next.totalPathDistance)) {
                    next.previous = current;
                    next.totalPathDistance = g;
                    next.distanceToNext = h(next) * FUDGING;
                    if (next.isAssigned()) {
                        open.changeDistance(next, g + next.distanceToNext);
                    } else {
                        next.distanceToTarget = g + next.distanceToNext;
                        open.add(next);
                    }
                }
            }
        }
        return best == null ? null : PathFinder.build(best);
    }

    private float h(PathPoint node) {
        float h = node.distanceTo(target);
        if (h < bestH) {
            bestH = h;
            best = node;
        }
        return h;
    }
}
