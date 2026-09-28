package io.github.term4.polyp.mechanics.mobs.path;

import io.github.term4.polyp.mechanics.mobs.MobEntity;
import io.github.term4.polyp.world.MechanicsWorld;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** 1.8 WalkNodeProcessor: the ground walker's node rules, one instance per search. */
final class WalkNodes {

    private final Map<Integer, PathPoint> points = new HashMap<>();
    private final Navigation nav;
    private final MobEntity entity;
    private final MechanicsWorld world;
    private final int sizeX, sizeY, sizeZ;
    private boolean avoidsWater;

    WalkNodes(Navigation nav) {
        this.nav = nav;
        this.entity = nav.entity();
        this.world = entity.world();
        sizeX = (int) Math.floor(entity.width() + 1.0f);
        sizeY = (int) Math.floor(entity.height() + 1.0f);
        sizeZ = sizeX;
        avoidsWater = nav.avoidsWater();
    }

    float range() { return nav.searchRange(); }

    private PathPoint open(int x, int y, int z) {
        int hash = PathPoint.makeHash(x, y, z);
        PathPoint p = points.get(hash);
        if (p == null) {
            p = new PathPoint(x, y, z);
            points.put(hash, p);
        }
        return p;
    }

    PathPoint pointOf() {
        Pos pos = entity.getPosition();
        int y;
        if (nav.canSwim() && entity.inWater()) {
            y = (int) pos.y();
            int x = (int) Math.floor(pos.x()), z = (int) Math.floor(pos.z());
            for (Block b = Blocks.at(world, x, y, z); Blocks.water(b); b = Blocks.at(world, x, y, z)) ++y;
            avoidsWater = false;
        } else {
            y = (int) Math.floor(pos.y() + 0.5);
        }
        double half = entity.width() / 2.0;
        return open((int) Math.floor(pos.x() - half), y, (int) Math.floor(pos.z() - half));
    }

    PathPoint pointAt(double x, double y, double z) {
        double half = entity.width() / 2.0;
        return open((int) Math.floor(x - half), (int) Math.floor(y), (int) Math.floor(z - half));
    }

    int options(PathPoint[] out, PathPoint from, PathPoint target, float maxDistance) {
        int n = 0;
        int step = verticalOffset(from.x, from.y + 1, from.z) == 1 ? 1 : 0;
        PathPoint south = safePoint(from.x, from.y, from.z + 1, step);
        PathPoint west = safePoint(from.x - 1, from.y, from.z, step);
        PathPoint east = safePoint(from.x + 1, from.y, from.z, step);
        PathPoint north = safePoint(from.x, from.y, from.z - 1, step);
        if (south != null && !south.visited && south.distanceTo(target) < maxDistance) out[n++] = south;
        if (west != null && !west.visited && west.distanceTo(target) < maxDistance) out[n++] = west;
        if (east != null && !east.visited && east.distanceTo(target) < maxDistance) out[n++] = east;
        if (north != null && !north.visited && north.distanceTo(target) < maxDistance) out[n++] = north;
        return n;
    }

    private @Nullable PathPoint safePoint(int x, int y, int z, int step) {
        PathPoint point = null;
        int code = verticalOffset(x, y, z);
        if (code == 2) return open(x, y, z);
        if (code == 1) point = open(x, y, z);
        if (point == null && step > 0 && code != -3 && code != -4 && verticalOffset(x, y + step, z) == 1) {
            point = open(x, y + step, z);
            y += step;
        }
        if (point != null) {
            int fallen = 0;
            int below;
            for (below = 0; y > 0; point = open(x, y, z)) {
                below = verticalOffset(x, y - 1, z);
                if (avoidsWater && below == -1) return null;
                if (below != 1) break;
                if (fallen++ >= entity.maxFallHeight()) return null;
                --y;
                if (y <= 0) return null;
            }
            if (below == -2) return null;
        }
        return point;
    }

    private int verticalOffset(int x, int y, int z) {
        return offset(world, entity, x, y, z, sizeX, sizeY, sizeZ, avoidsWater, nav.breakDoors(), nav.enterDoors());
    }

    /**
     * 1.8 func_176170_a: 2 walkable through a trapdoor or water, 1 clear, 0 blocked, -1 water to avoid,
     * -2 lava, -3 a fence or a rail off-track, -4 a closed trapdoor.
     */
    static int offset(MechanicsWorld world, MobEntity entity, int x, int y, int z, int sizeX, int sizeY, int sizeZ,
                      boolean avoidWater, boolean breakDoors, boolean enterDoors) {
        boolean flag = false;
        Pos at = entity.getPosition();
        int ex = at.blockX(), ey = at.blockY(), ez = at.blockZ();
        for (int i = x; i < x + sizeX; i++) {
            for (int j = y; j < y + sizeY; j++) {
                for (int k = z; k < z + sizeZ; k++) {
                    Block block = Blocks.at(world, i, j, k);
                    if (block.air()) continue;
                    if (Blocks.trapdoor(block)) {
                        flag = true;
                    } else if (Blocks.water(block)) {
                        if (avoidWater) return -1;
                        flag = true;
                    } else if (!enterDoors && Blocks.woodenDoor(block)) {
                        return 0;
                    }
                    if (Blocks.rail(block)) {
                        if (!Blocks.rail(Blocks.at(world, ex, ey, ez)) && !Blocks.rail(Blocks.at(world, ex, ey - 1, ez))) return -3;
                    } else if (!Blocks.passable(block) && (!breakDoors || !Blocks.woodenDoor(block))) {
                        if (Blocks.fenceLike(block)) return -3;
                        if (Blocks.trapdoor(block)) return -4;
                        if (!Blocks.lava(block)) return 0;
                        if (!entity.inLava()) return -2;
                    }
                }
            }
        }
        return flag ? 2 : 1;
    }
}
