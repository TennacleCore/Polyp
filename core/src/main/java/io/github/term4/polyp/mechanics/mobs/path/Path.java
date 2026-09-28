package io.github.term4.polyp.mechanics.mobs.path;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import org.jetbrains.annotations.Nullable;

/** 1.8 PathEntity: the points to walk, with a cursor. */
public final class Path {

    private final PathPoint[] points;
    private int currentIndex;
    private int length;

    public Path(PathPoint[] points) {
        this.points = points;
        this.length = points.length;
    }

    public void incrementIndex() { ++currentIndex; }

    public boolean isFinished() { return currentIndex >= length; }

    public @Nullable PathPoint finalPoint() { return length > 0 ? points[length - 1] : null; }

    public PathPoint point(int index) { return points[index]; }

    public int length() { return length; }

    public void length(int length) { this.length = length; }

    public int currentIndex() { return currentIndex; }

    public void currentIndex(int index) { currentIndex = index; }

    /** The point's centre for an entity of this width. */
    public Vec vector(Entity entity, int index) {
        double half = (int) (width(entity) + 1.0f) * 0.5;
        return new Vec(points[index].x + half, points[index].y, points[index].z + half);
    }

    public Vec position(Entity entity) { return vector(entity, currentIndex); }

    public boolean isSamePath(@Nullable Path other) {
        if (other == null || other.points.length != points.length) return false;
        for (int i = 0; i < points.length; i++) {
            PathPoint a = points[i], b = other.points[i];
            if (a.x != b.x || a.y != b.y || a.z != b.z) return false;
        }
        return true;
    }

    public boolean isDestinationSame(Point at) {
        PathPoint last = finalPoint();
        return last != null && last.x == (int) at.x() && last.z == (int) at.z();
    }

    static float width(Entity entity) {
        return (float) entity.getBoundingBox().width();
    }
}
