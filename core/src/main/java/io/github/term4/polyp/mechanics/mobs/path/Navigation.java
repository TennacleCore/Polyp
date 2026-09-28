package io.github.term4.polyp.mechanics.mobs.path;

import io.github.term4.polyp.mechanics.mobs.MobEntity;
import io.github.term4.polyp.world.MechanicsWorld;
import net.minestom.server.collision.Shape;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.Nullable;

/** 1.8 PathNavigateGround: finds and follows a walking path, feeding the move control one point at a time. */
public final class Navigation {

    private final MobEntity entity;
    private final WalkNodes nodes = new WalkNodes();
    private final PathFinder finder;
    private @Nullable Path current;
    private double speed;
    private int totalTicks;
    private int ticksAtLastPos;
    private Vec lastPosCheck = Vec.ZERO;
    private float heightRequirement = 1.0f;

    public Navigation(MobEntity entity) {
        this.entity = entity;
        nodes.enterDoors(true);
        this.finder = new PathFinder(nodes);
    }

    public float searchRange() {
        return (float) entity.getAttributeValue(Attribute.FOLLOW_RANGE);
    }

    public @Nullable Path pathTo(double x, double y, double z) {
        return pathTo(new BlockVec((int) Math.floor(x), (int) y, (int) Math.floor(z)));
    }

    public @Nullable Path pathTo(Point block) {
        if (!canNavigate()) return null;
        return finder.pathTo(entity.world(), entity, block, searchRange());
    }

    public boolean moveTo(double x, double y, double z, double speed) {
        return setPath(pathTo(Math.floor(x), (int) y, Math.floor(z)), speed);
    }

    public void heightRequirement(float jumpHeight) {
        heightRequirement = jumpHeight;
    }

    public @Nullable Path pathTo(Entity target) {
        if (!canNavigate()) return null;
        return finder.pathTo(entity.world(), entity, target, searchRange());
    }

    public boolean moveTo(Entity target, double speed) {
        Path path = pathTo(target);
        return path != null && setPath(path, speed);
    }

    public boolean setPath(@Nullable Path path, double speed) {
        if (path == null) {
            current = null;
            return false;
        }
        if (!path.isSamePath(current)) current = path;
        if (current.length() == 0) return false;
        this.speed = speed;
        lastPosCheck = entityPosition();
        ticksAtLastPos = totalTicks;
        return true;
    }

    public @Nullable Path path() { return current; }

    public void tick() {
        ++totalTicks;
        if (noPath()) return;
        if (canNavigate()) {
            follow();
        } else if (current != null && current.currentIndex() < current.length()) {
            Vec pos = entityPosition();
            Vec next = current.vector(entity, current.currentIndex());
            if (pos.y() > next.y() && !entity.isOnGround()
                    && Math.floor(pos.x()) == Math.floor(next.x()) && Math.floor(pos.z()) == Math.floor(next.z())) {
                current.currentIndex(current.currentIndex() + 1);
            }
        }
        if (!noPath()) {
            Vec target = current.position(entity);
            entity.moveControl().moveTo(target.x(), target.y() + floorDrop(target), target.z(), speed);
        }
    }

    // 1.8 onUpdateNavigation: a half-block probe a block above the point, dropped onto whatever lies under it
    private double floorDrop(Vec target) {
        MechanicsWorld world = entity.world();
        double probeMinY = target.y() + 0.5;
        double minX = target.x() - 0.5, maxX = target.x() + 0.5, minZ = target.z() - 0.5, maxZ = target.z() + 0.5;
        double drop = -1.0;
        int x0 = (int) Math.floor(minX), x1 = (int) Math.floor(maxX);
        int y0 = (int) Math.floor(target.y() - 1.5), y1 = (int) Math.floor(target.y() + 0.5);
        int z0 = (int) Math.floor(minZ), z1 = (int) Math.floor(maxZ);
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    Block block = Blocks.at(world, x, y, z);
                    if (Blocks.passable(block)) continue;
                    Shape shape = block.collisionShape();
                    Point s = shape.relativeStart(), e = shape.relativeEnd();
                    double bMinX = x + s.x(), bMaxX = x + e.x(), bMinZ = z + s.z(), bMaxZ = z + e.z(), bMaxY = y + e.y();
                    if (maxX > bMinX && minX < bMaxX && maxZ > bMinZ && minZ < bMaxZ && probeMinY >= bMaxY) {
                        double d = bMaxY - probeMinY;
                        if (d > drop) drop = d;
                    }
                }
            }
        }
        return drop;
    }

    private void follow() {
        Vec pos = entityPosition();
        int end = current.length();
        for (int j = current.currentIndex(); j < current.length(); j++) {
            if (current.point(j).y != (int) pos.y()) {
                end = j;
                break;
            }
        }
        float width = (float) entity.width();
        float close = width * width * heightRequirement;
        for (int k = current.currentIndex(); k < end; k++) {
            if (pos.distanceSquared(current.vector(entity, k)) < close) current.currentIndex(k + 1);
        }
        int sizeX = (int) Math.ceil(width);
        int sizeY = (int) entity.height() + 1;
        for (int i = end - 1; i >= current.currentIndex(); i--) {
            if (directPath(pos, current.vector(entity, i), sizeX, sizeY, sizeX)) {
                current.currentIndex(i);
                break;
            }
        }
        checkStuck(pos);
    }

    private void checkStuck(Vec pos) {
        if (totalTicks - ticksAtLastPos > 100) {
            if (pos.distanceSquared(lastPosCheck) < 2.25) clear();
            ticksAtLastPos = totalTicks;
            lastPosCheck = pos;
        }
    }

    public boolean noPath() {
        return current == null || current.isFinished();
    }

    public void clear() {
        current = null;
    }

    private Vec entityPosition() {
        Pos pos = entity.getPosition();
        return new Vec(pos.x(), pathableY(), pos.z());
    }

    private int pathableY() {
        Pos pos = entity.getPosition();
        if (entity.inWater() && nodes.canSwim()) {
            int y = (int) pos.y();
            int x = pos.blockX(), z = pos.blockZ();
            Block block = Blocks.at(entity.world(), x, y, z);
            int n = 0;
            while (Blocks.water(block)) {
                ++y;
                block = Blocks.at(entity.world(), x, y, z);
                if (++n > 16) return (int) pos.y();
            }
            return y;
        }
        return (int) (pos.y() + 0.5);
    }

    private boolean canNavigate() {
        return entity.isOnGround() || nodes.canSwim() && (entity.inWater() || entity.inLava());
    }

    private boolean directPath(Vec from, Vec to, int sizeX, int sizeY, int sizeZ) {
        int x = (int) Math.floor(from.x());
        int z = (int) Math.floor(from.z());
        double dx = to.x() - from.x();
        double dz = to.z() - from.z();
        double d2 = dx * dx + dz * dz;
        if (d2 < 1.0E-8) return false;
        double inv = 1.0 / Math.sqrt(d2);
        dx *= inv;
        dz *= inv;
        sizeX += 2;
        sizeZ += 2;
        if (!safeToStandAt(x, (int) from.y(), z, sizeX, sizeY, sizeZ, from, dx, dz)) return false;
        sizeX -= 2;
        sizeZ -= 2;
        double stepX = 1.0 / Math.abs(dx);
        double stepZ = 1.0 / Math.abs(dz);
        double tx = x - from.x();
        double tz = z - from.z();
        if (dx >= 0.0) ++tx;
        if (dz >= 0.0) ++tz;
        tx /= dx;
        tz /= dz;
        int dirX = dx < 0.0 ? -1 : 1;
        int dirZ = dz < 0.0 ? -1 : 1;
        int endX = (int) Math.floor(to.x());
        int endZ = (int) Math.floor(to.z());
        int remX = endX - x;
        int remZ = endZ - z;
        while (remX * dirX > 0 || remZ * dirZ > 0) {
            if (tx < tz) {
                tx += stepX;
                x += dirX;
                remX = endX - x;
            } else {
                tz += stepZ;
                z += dirZ;
                remZ = endZ - z;
            }
            if (!safeToStandAt(x, (int) from.y(), z, sizeX, sizeY, sizeZ, from, dx, dz)) return false;
        }
        return true;
    }

    private boolean safeToStandAt(int x, int y, int z, int sizeX, int sizeY, int sizeZ, Vec from, double dx, double dz) {
        int minX = x - sizeX / 2;
        int minZ = z - sizeZ / 2;
        if (!positionClear(minX, y, minZ, sizeX, sizeY, sizeZ, from, dx, dz)) return false;
        for (int i = minX; i < minX + sizeX; i++) {
            for (int k = minZ; k < minZ + sizeZ; k++) {
                double ox = i + 0.5 - from.x();
                double oz = k + 0.5 - from.z();
                if (ox * dx + oz * dz >= 0.0) {
                    Block block = Blocks.at(entity.world(), i, y - 1, k);
                    if (block.air()) return false;
                    if (Blocks.water(block) && !entity.inWater()) return false;
                    if (Blocks.lava(block)) return false;
                }
            }
        }
        return true;
    }

    private boolean positionClear(int x, int y, int z, int sizeX, int sizeY, int sizeZ, Vec from, double dx, double dz) {
        for (int i = x; i < x + sizeX; i++) {
            for (int j = y; j < y + sizeY; j++) {
                for (int k = z; k < z + sizeZ; k++) {
                    double ox = i + 0.5 - from.x();
                    double oz = k + 0.5 - from.z();
                    if (ox * dx + oz * dz >= 0.0 && !Blocks.passable(Blocks.at(entity.world(), i, j, k))) return false;
                }
            }
        }
        return true;
    }

    public void avoidsWater(boolean v) { nodes.avoidsWater(v); }
    public boolean avoidsWater() { return nodes.avoidsWater(); }
    public void breakDoors(boolean v) { nodes.breakDoors(v); }
    public void enterDoors(boolean v) { nodes.enterDoors(v); }
    public void canSwim(boolean v) { nodes.canSwim(v); }
    public boolean canSwim() { return nodes.canSwim(); }
}
