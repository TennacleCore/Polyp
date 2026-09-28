package io.github.term4.polyp.mechanics.mobs.path;

import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Entity;
import org.jetbrains.annotations.Nullable;

/** 1.8 PathFinder: A* over {@link WalkNodes}, returning the closest reachable point when the target is not. */
final class PathFinder {

    private final PathHeap open = new PathHeap();
    private final PathPoint[] options = new PathPoint[32];
    private final WalkNodes nodes;

    PathFinder(WalkNodes nodes) {
        this.nodes = nodes;
    }

    @Nullable Path pathTo(Entity to) {
        return pathTo(to.getPosition().x(), to.getPosition().y(), to.getPosition().z());
    }

    @Nullable Path pathTo(Point block) {
        return pathTo(block.blockX() + 0.5, block.blockY() + 0.5, block.blockZ() + 0.5);
    }

    private @Nullable Path pathTo(double x, double y, double z) {
        PathPoint start = nodes.pointOf();
        PathPoint end = nodes.pointAt(x, y, z);
        return search(start, end, nodes.range());
    }

    private @Nullable Path search(PathPoint start, PathPoint end, float maxDistance) {
        start.totalPathDistance = 0.0f;
        start.distanceToNext = start.distanceToSquared(end);
        start.distanceToTarget = start.distanceToNext;
        open.clear();
        open.add(start);
        PathPoint closest = start;
        while (!open.isEmpty()) {
            PathPoint current = open.dequeue();
            if (current.equals(end)) return build(end);
            if (current.distanceToSquared(end) < closest.distanceToSquared(end)) closest = current;
            current.visited = true;
            int n = nodes.options(options, current, end, maxDistance);
            for (int i = 0; i < n; i++) {
                PathPoint next = options[i];
                float total = current.totalPathDistance + current.distanceToSquared(next);
                if (total < maxDistance * 2.0f && (!next.isAssigned() || total < next.totalPathDistance)) {
                    next.previous = current;
                    next.totalPathDistance = total;
                    next.distanceToNext = next.distanceToSquared(end);
                    if (next.isAssigned()) {
                        open.changeDistance(next, next.totalPathDistance + next.distanceToNext);
                    } else {
                        next.distanceToTarget = next.totalPathDistance + next.distanceToNext;
                        open.add(next);
                    }
                }
            }
        }
        return closest == start ? null : build(closest);
    }

    static Path build(PathPoint end) {
        int n = 1;
        for (PathPoint p = end; p.previous != null; p = p.previous) ++n;
        PathPoint[] points = new PathPoint[n];
        PathPoint p = end;
        --n;
        for (points[n] = end; p.previous != null; points[n] = p) {
            p = p.previous;
            --n;
        }
        return new Path(points);
    }
}
