package io.github.term4.polyp.mechanics.mobs.path;

/** A node: 1.8 PathPoint's fields, plus the cost and type the typed walker adds. */
public final class PathPoint {

    public final int x;
    public final int y;
    public final int z;
    private final int hash;
    int index = -1;
    float totalPathDistance;
    float distanceToNext;
    float distanceToTarget;
    PathPoint previous;
    boolean visited;
    float walkedDistance;
    float costMalus;
    PathType type = PathType.BLOCKED;

    public PathPoint(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.hash = makeHash(x, y, z);
    }

    public static int makeHash(int x, int y, int z) {
        return y & 255 | (x & 32767) << 8 | (z & 32767) << 24 | (x < 0 ? Integer.MIN_VALUE : 0) | (z < 0 ? 32768 : 0);
    }

    public float distanceTo(PathPoint other) {
        float dx = other.x - x, dy = other.y - y, dz = other.z - z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public float distanceToSquared(PathPoint other) {
        float dx = other.x - x, dy = other.y - y, dz = other.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public float distanceManhattan(PathPoint other) {
        return Math.abs(other.x - x) + Math.abs(other.y - y) + Math.abs(other.z - z);
    }

    public boolean isAssigned() { return index >= 0; }

    public PathType type() { return type; }

    @Override
    public boolean equals(Object o) {
        return o instanceof PathPoint p && p.hash == hash && p.x == x && p.y == y && p.z == z;
    }

    @Override
    public int hashCode() { return hash; }

    @Override
    public String toString() { return x + ", " + y + ", " + z; }
}
