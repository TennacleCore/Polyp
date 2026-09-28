package io.github.term4.polyp.mechanics.mobs.path;

import io.github.term4.polyp.mechanics.mobs.MobEntity;
import io.github.term4.polyp.world.MechanicsWorld;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.utils.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** 26.1 WalkNodeEvaluator: every cell a {@link PathType} with a cost, diagonals, a jump up to the step, a drop to the fall limit. */
final class TypedNodes {

    // Direction.Plane.HORIZONTAL order; clockwise is the next one
    private static final Direction[] HORIZONTAL = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};
    private static final double JUMP_HEIGHT = 1.125;

    private final Navigation nav;
    private final MobEntity mob;
    private final MechanicsWorld world;
    private final Map<Integer, PathPoint> points = new HashMap<>();
    private final Map<Long, PathType> cellTypes = new HashMap<>();
    private final Map<Long, PathType> mobTypes = new HashMap<>();
    private final PathPoint[] sides = new PathPoint[4];
    private final int sizeX, sizeY, sizeZ;
    private final int minY;

    TypedNodes(Navigation nav) {
        this.nav = nav;
        this.mob = nav.entity();
        this.world = mob.world();
        sizeX = (int) Math.floor(mob.width() + 1.0f);
        sizeY = (int) Math.floor(mob.height() + 1.0f);
        sizeZ = sizeX;
        minY = world.dimension().minY();
    }

    private PathPoint node(int x, int y, int z) {
        return points.computeIfAbsent(PathPoint.makeHash(x, y, z), k -> new PathPoint(x, y, z));
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | ((long) y & 0xFFF);
    }

    // Mob.getPathfindingMalus: the type's own, water forbidden for a kind that avoids it
    private float malus(PathType type) {
        if (type == PathType.WATER && nav.avoidsWater()) return -1.0f;
        return type.malus();
    }

    PathPoint start() {
        Pos pos = mob.getPosition();
        int x = pos.blockX(), z = pos.blockZ();
        int y = pos.blockY();
        if (nav.canSwim() && mob.inWater()) {
            Block at = Blocks.at(world, x, y, z);
            while (true) {
                if (!Blocks.waterish(at)) {
                    --y;
                    break;
                }
                at = Blocks.at(world, x, ++y, z);
            }
        } else if (mob.isOnGround()) {
            y = (int) Math.floor(pos.y() + 0.5);
        } else {
            int probe = (int) Math.floor(pos.y() + 1.0);
            while (probe > minY) {
                y = probe;
                --probe;
                Block below = Blocks.at(world, x, probe, z);
                if (!below.air() && !Blocks.pathfindable(below)) break;
            }
        }
        if (!canStartAt(x, y, z)) {
            BoundingBox box = mob.getBoundingBox();
            int minX = (int) Math.floor(pos.x() + box.minX()), maxX = (int) Math.floor(pos.x() + box.maxX());
            int minZ = (int) Math.floor(pos.z() + box.minZ()), maxZ = (int) Math.floor(pos.z() + box.maxZ());
            if (canStartAt(minX, y, minZ)) return startNode(minX, y, minZ);
            if (canStartAt(minX, y, maxZ)) return startNode(minX, y, maxZ);
            if (canStartAt(maxX, y, minZ)) return startNode(maxX, y, minZ);
            if (canStartAt(maxX, y, maxZ)) return startNode(maxX, y, maxZ);
        }
        return startNode(x, y, z);
    }

    private PathPoint startNode(int x, int y, int z) {
        PathPoint node = node(x, y, z);
        node.type = mobType(x, y, z);
        node.costMalus = malus(node.type);
        return node;
    }

    private boolean canStartAt(int x, int y, int z) {
        PathType type = mobType(x, y, z);
        return type != PathType.OPEN && malus(type) >= 0.0f;
    }

    int neighbors(PathPoint[] out, PathPoint pos) {
        int n = 0;
        int jump = 0;
        PathType above = mobType(pos.x, pos.y + 1, pos.z);
        PathType current = mobType(pos.x, pos.y, pos.z);
        if (malus(above) >= 0.0f && current != PathType.STICKY_HONEY) jump = (int) Math.floor(Math.max(1.0, mob.stepHeight()));
        double floor = floorLevel(pos.x, pos.y, pos.z);
        for (int i = 0; i < 4; i++) {
            Direction d = HORIZONTAL[i];
            PathPoint node = accepted(pos.x + d.normalX(), pos.y, pos.z + d.normalZ(), jump, floor, d, current);
            sides[i] = node;
            if (neighborValid(node, pos)) out[n++] = node;
        }
        for (int i = 0; i < 4; i++) {
            Direction d = HORIZONTAL[i], clockwise = HORIZONTAL[(i + 1) & 3];
            if (diagonalValid(pos, sides[i], sides[(i + 1) & 3])) {
                PathPoint diagonal = accepted(pos.x + d.normalX() + clockwise.normalX(), pos.y,
                        pos.z + d.normalZ() + clockwise.normalZ(), jump, floor, d, current);
                if (diagonalValid(diagonal)) out[n++] = diagonal;
            }
        }
        return n;
    }

    private static boolean neighborValid(@Nullable PathPoint neighbor, PathPoint current) {
        return neighbor != null && !neighbor.visited && (neighbor.costMalus >= 0.0f || current.costMalus < 0.0f);
    }

    private boolean diagonalValid(PathPoint pos, @Nullable PathPoint ew, @Nullable PathPoint ns) {
        if (ns == null || ew == null || ns.y > pos.y || ew.y > pos.y) return false;
        if (ew.type == PathType.WALKABLE_DOOR || ns.type == PathType.WALKABLE_DOOR) return false;
        if (mob.width() > 1.0 && (ew.costMalus > 0.0f || ns.costMalus > 0.0f)) return false;
        boolean betweenPosts = ns.type == PathType.FENCE && ew.type == PathType.FENCE && mob.width() < 0.5;
        return (ns.y < pos.y || ns.costMalus >= 0.0f || betweenPosts) && (ew.y < pos.y || ew.costMalus >= 0.0f || betweenPosts);
    }

    private static boolean diagonalValid(@Nullable PathPoint diagonal) {
        if (diagonal == null || diagonal.visited) return false;
        return diagonal.type != PathType.WALKABLE_DOOR && diagonal.costMalus >= 0.0f;
    }

    private static boolean partialCollision(PathType type) {
        return type == PathType.FENCE || type == PathType.DOOR_WOOD_CLOSED || type == PathType.DOOR_IRON_CLOSED;
    }

    private boolean reachableWithoutCollision(PathPoint to) {
        Pos pos = mob.getPosition();
        BoundingBox box = mob.getBoundingBox();
        double sizeX = box.width(), sizeY = box.height(), sizeZ = box.depth();
        Vec delta = new Vec(to.x - pos.x() + sizeX / 2.0, to.y - pos.y() + sizeY / 2.0, to.z - pos.z() + sizeZ / 2.0);
        int steps = (int) Math.ceil(delta.length() / ((sizeX + sizeY + sizeZ) / 3.0));
        delta = delta.mul(1.0f / steps);
        double minX = pos.x() + box.minX(), minY = pos.y() + box.minY(), minZ = pos.z() + box.minZ();
        for (int i = 1; i <= steps; i++) {
            minX += delta.x();
            minY += delta.y();
            minZ += delta.z();
            if (Blocks.collides(world, minX, minY, minZ, minX + sizeX, minY + sizeY, minZ + sizeZ)) return false;
        }
        return true;
    }

    private double floorLevel(int x, int y, int z) {
        if (nav.canSwim() && Blocks.waterish(Blocks.at(world, x, y, z))) return y + 0.5;
        return Blocks.floorLevel(world, x, y, z);
    }

    private @Nullable PathPoint accepted(int x, int y, int z, int jump, double floor, Direction travel, PathType current) {
        PathPoint best = null;
        if (floorLevel(x, y, z) - floor > Math.max(JUMP_HEIGHT, mob.stepHeight())) return null;
        PathType type = mobType(x, y, z);
        float cost = malus(type);
        if (cost >= 0.0f) best = costed(x, y, z, type, cost);
        if (partialCollision(current) && best != null && best.costMalus >= 0.0f && !reachableWithoutCollision(best)) best = null;
        if (type == PathType.WALKABLE) return best;
        if ((best == null || best.costMalus < 0.0f) && jump > 0 && type != PathType.FENCE && type != PathType.UNPASSABLE_RAIL
                && type != PathType.TRAPDOOR && type != PathType.POWDER_SNOW) {
            best = jumpOn(x, y, z, jump, floor, travel, current);
        } else if (type == PathType.WATER && !nav.canSwim()) {
            best = firstNonWaterBelow(x, y, z, best);
        } else if (type == PathType.OPEN) {
            best = firstGroundBelow(x, y, z);
        } else if (partialCollision(type) && best == null) {
            best = closed(x, y, z, type);
        }
        return best;
    }

    private PathPoint costed(int x, int y, int z, PathType type, float cost) {
        PathPoint node = node(x, y, z);
        node.type = type;
        node.costMalus = Math.max(node.costMalus, cost);
        return node;
    }

    private PathPoint blocked(int x, int y, int z) {
        PathPoint node = node(x, y, z);
        node.type = PathType.BLOCKED;
        node.costMalus = -1.0f;
        return node;
    }

    private PathPoint closed(int x, int y, int z, PathType type) {
        PathPoint node = node(x, y, z);
        node.visited = true;
        node.type = type;
        node.costMalus = type.malus();
        return node;
    }

    private @Nullable PathPoint jumpOn(int x, int y, int z, int jump, double floor, Direction travel, PathType current) {
        PathPoint above = accepted(x, y + 1, z, jump - 1, floor, travel, current);
        if (above == null) return null;
        if (mob.width() >= 1.0) return above;
        if (above.type != PathType.OPEN && above.type != PathType.WALKABLE) return above;
        double centerX = x - travel.normalX() + 0.5, centerZ = z - travel.normalZ() + 0.5;
        double half = mob.width() / 2.0;
        double minY = floorLevel((int) Math.floor(centerX), y + 1, (int) Math.floor(centerZ)) + 0.001;
        double maxY = mob.height() + floorLevel(above.x, above.y, above.z) - 0.002;
        return Blocks.collides(world, centerX - half, minY, centerZ - half, centerX + half, maxY, centerZ + half) ? null : above;
    }

    private @Nullable PathPoint firstNonWaterBelow(int x, int y, int z, @Nullable PathPoint best) {
        --y;
        while (y > minY) {
            PathType type = mobType(x, y, z);
            if (type != PathType.WATER) return best;
            best = costed(x, y, z, type, malus(type));
            --y;
        }
        return best;
    }

    private PathPoint firstGroundBelow(int x, int y, int z) {
        for (int below = y - 1; below >= minY; below--) {
            if (y - below > mob.maxFallHeight()) return blocked(x, below, z);
            PathType type = mobType(x, below, z);
            float cost = malus(type);
            if (type != PathType.OPEN) return cost >= 0.0f ? costed(x, below, z, type, cost) : blocked(x, below, z);
        }
        return blocked(x, y, z);
    }

    // getCachedPathType -> getPathTypeOfMob: the worst type within the mob's cells, fences and dead rails first
    private PathType mobType(int x, int y, int z) {
        return mobTypes.computeIfAbsent(key(x, y, z), k -> typeOfMob(x, y, z));
    }

    private PathType typeOfMob(int x, int y, int z) {
        Set<PathType> types = typesWithin(x, y, z);
        if (types.size() == 1) return types.iterator().next();
        if (types.contains(PathType.FENCE)) return PathType.FENCE;
        if (types.contains(PathType.UNPASSABLE_RAIL)) return PathType.UNPASSABLE_RAIL;
        PathType worst = PathType.BLOCKED;
        float worstMalus = malus(worst);
        for (PathType type : types) {
            float m = malus(type);
            if (m < 0.0f) return type;
            if (m >= worstMalus) {
                worstMalus = m;
                worst = type;
            }
        }
        PathType own = cellType(x, y, z);
        if (sizeX > 1) {
            boolean cheaper = malus(own) < worstMalus;
            boolean capped = cheaper && malus(PathType.BIG_MOBS_CLOSE_TO_DANGER) < worstMalus;
            return capped ? PathType.BIG_MOBS_CLOSE_TO_DANGER : worst;
        }
        return own == PathType.OPEN && worst != PathType.OPEN && worstMalus == 0.0f ? PathType.OPEN : worst;
    }

    private Set<PathType> typesWithin(int x, int y, int z) {
        Set<PathType> types = EnumSet.noneOf(PathType.class);
        Pos pos = mob.getPosition();
        int mx = pos.blockX(), my = pos.blockY(), mz = pos.blockZ();
        for (int dx = 0; dx < sizeX; dx++) {
            for (int dy = 0; dy < sizeY; dy++) {
                for (int dz = 0; dz < sizeZ; dz++) {
                    PathType type = cellType(x + dx, y + dy, z + dz);
                    if (type == PathType.DOOR_WOOD_CLOSED && nav.breakDoors() && nav.enterDoors()) type = PathType.WALKABLE_DOOR;
                    if (type == PathType.DOOR_OPEN && !nav.enterDoors()) type = PathType.BLOCKED;
                    if (type == PathType.RAIL && cellType(mx, my, mz) != PathType.RAIL && cellType(mx, my - 1, mz) != PathType.RAIL) {
                        type = PathType.UNPASSABLE_RAIL;
                    }
                    types.add(type);
                }
            }
        }
        return types;
    }

    // getPathTypeStatic: an open cell takes what lies under it, and a walkable one what lies around it
    private PathType cellType(int x, int y, int z) {
        PathType type = stateType(x, y, z);
        if (type != PathType.OPEN || y < minY + 1) return type;
        return switch (stateType(x, y - 1, z)) {
            case OPEN, WATER, LAVA, WALKABLE -> PathType.OPEN;
            case FIRE -> PathType.FIRE;
            case DAMAGING -> PathType.DAMAGING;
            case STICKY_HONEY -> PathType.STICKY_HONEY;
            case POWDER_SNOW -> PathType.ON_TOP_OF_POWDER_SNOW;
            case DAMAGE_CAUTIOUS -> PathType.DAMAGE_CAUTIOUS;
            case TRAPDOOR -> PathType.ON_TOP_OF_TRAPDOOR;
            default -> neighborsOf(x, y, z);
        };
    }

    private PathType neighborsOf(int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    PathType type = stateType(x + dx, y + dy, z + dz);
                    if (type == PathType.DAMAGING) return PathType.DAMAGING_IN_NEIGHBOR;
                    if (type == PathType.FIRE || type == PathType.LAVA) return PathType.FIRE_IN_NEIGHBOR;
                    if (type == PathType.WATER) return PathType.WATER_BORDER;
                    if (type == PathType.DAMAGE_CAUTIOUS) return PathType.DAMAGE_CAUTIOUS;
                }
            }
        }
        return PathType.WALKABLE;
    }

    private PathType stateType(int x, int y, int z) {
        return cellTypes.computeIfAbsent(key(x, y, z), k -> Blocks.pathType(Blocks.at(world, x, y, z)));
    }
}
