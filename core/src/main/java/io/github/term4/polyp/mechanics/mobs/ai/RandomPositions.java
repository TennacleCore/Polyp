package io.github.term4.polyp.mechanics.mobs.ai;

import io.github.term4.polyp.mechanics.mobs.MobEntity;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

/** 1.8 RandomPositionGenerator: ten random cells, the best path weight wins. */
public final class RandomPositions {

    private RandomPositions() {}

    public static @Nullable Vec find(MobEntity entity, int xz, int y) {
        return find(entity, xz, y, null);
    }

    public static @Nullable Vec findTowards(MobEntity entity, int xz, int y, Point target) {
        Pos p = entity.getPosition();
        return find(entity, xz, y, new Vec(target.x() - p.x(), target.y() - p.y(), target.z() - p.z()));
    }

    public static @Nullable Vec findAwayFrom(MobEntity entity, int xz, int y, Point target) {
        Pos p = entity.getPosition();
        return find(entity, xz, y, new Vec(p.x() - target.x(), p.y() - target.y(), p.z() - target.z()));
    }

    private static @Nullable Vec find(MobEntity entity, int xz, int y, @Nullable Vec toward) {
        Random random = entity.random();
        Pos pos = entity.getPosition();
        boolean found = false;
        int bx = 0, by = 0, bz = 0;
        float best = -99999.0f;
        boolean nearHome;
        if (entity.hasHome()) {
            BlockVec home = entity.home();
            double d0 = home.distanceSquared(new Vec(pos.blockX(), pos.blockY(), pos.blockZ())) + 4.0;
            double d1 = entity.homeDistance() + xz;
            nearHome = d0 < d1 * d1;
        } else {
            nearHome = false;
        }
        for (int i = 0; i < 10; i++) {
            int dx = random.nextInt(2 * xz + 1) - xz;
            int dy = random.nextInt(2 * y + 1) - y;
            int dz = random.nextInt(2 * xz + 1) - xz;
            if (toward != null && dx * toward.x() + dz * toward.z() < 0.0) continue;
            if (entity.hasHome() && xz > 1) {
                BlockVec home = entity.home();
                if (pos.x() > home.x()) dx -= random.nextInt(xz / 2);
                else dx += random.nextInt(xz / 2);
                if (pos.z() > home.z()) dz -= random.nextInt(xz / 2);
                else dz += random.nextInt(xz / 2);
            }
            int x = dx + pos.blockX();
            int yy = dy + pos.blockY();
            int z = dz + pos.blockZ();
            BlockVec cell = new BlockVec(x, yy, z);
            if (nearHome && !entity.withinHome(cell)) continue;
            float weight = entity.pathWeight(cell);
            if (weight > best) {
                best = weight;
                bx = x;
                by = yy;
                bz = z;
                found = true;
            }
        }
        return found ? new Vec(bx, by, bz) : null;
    }
}
