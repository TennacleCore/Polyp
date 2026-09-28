package io.github.term4.polyp.mechanics.mobs.path;

import io.github.term4.polyp.world.MechanicsWorld;
import net.minestom.server.collision.Shape;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;

/** 1.8 World.rayTraceBlocks for eye lines: liquids are clear, a fence is one block tall. */
public final class Sight {

    private static final double EPS = 1e-7;

    private Sight() {}

    public static boolean clear(MechanicsWorld world, Point from, Point to) {
        double x0 = from.x(), y0 = from.y(), z0 = from.z();
        double x1 = to.x(), y1 = to.y(), z1 = to.z();
        int ex = (int) Math.floor(x1), ey = (int) Math.floor(y1), ez = (int) Math.floor(z1);
        int cx = (int) Math.floor(x0), cy = (int) Math.floor(y0), cz = (int) Math.floor(z0);
        if (blocks(world, cx, cy, cz, from, to)) return false;
        int steps = 200;
        while (steps-- >= 0) {
            if (cx == ex && cy == ey && cz == ez) return true;
            boolean stepX = true, stepY = true, stepZ = true;
            double nx = 999.0, ny = 999.0, nz = 999.0;
            if (ex > cx) nx = cx + 1.0;
            else if (ex < cx) nx = cx + 0.0;
            else stepX = false;
            if (ey > cy) ny = cy + 1.0;
            else if (ey < cy) ny = cy + 0.0;
            else stepY = false;
            if (ez > cz) nz = cz + 1.0;
            else if (ez < cz) nz = cz + 0.0;
            else stepZ = false;
            double tx = 999.0, ty = 999.0, tz = 999.0;
            double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
            if (stepX) tx = (nx - x0) / dx;
            if (stepY) ty = (ny - y0) / dy;
            if (stepZ) tz = (nz - z0) / dz;
            if (tx == -0.0) tx = -1.0E-4;
            if (ty == -0.0) ty = -1.0E-4;
            if (tz == -0.0) tz = -1.0E-4;
            int side;
            if (tx < ty && tx < tz) {
                side = ex > cx ? 4 : 5;
                x0 = nx;
                y0 += dy * tx;
                z0 += dz * tx;
            } else if (ty < tz) {
                side = ey > cy ? 0 : 1;
                x0 += dx * ty;
                y0 = ny;
                z0 += dz * ty;
            } else {
                side = ez > cz ? 2 : 3;
                x0 += dx * tz;
                y0 += dy * tz;
                z0 = nz;
            }
            cx = (int) Math.floor(x0) - (side == 5 ? 1 : 0);
            cy = (int) Math.floor(y0) - (side == 1 ? 1 : 0);
            cz = (int) Math.floor(z0) - (side == 3 ? 1 : 0);
            if (blocks(world, cx, cy, cz, from, to)) return false;
        }
        return true;
    }

    private static boolean blocks(MechanicsWorld world, int x, int y, int z, Point from, Point to) {
        Block block = Blocks.at(world, x, y, z);
        if (block.air() || block.liquid()) return false;
        Shape shape = block.collisionShape();
        Point s = shape.relativeStart(), e = shape.relativeEnd();
        if (e.x() - s.x() <= 0 || e.y() - s.y() <= 0 || e.z() - s.z() <= 0) return false;
        // 1.8 rays the selection envelope: a fence or wall is one block tall to a ray
        double top = Math.min(e.y(), 1.0);
        return hits(from, to, x + s.x(), y + s.y(), z + s.z(), x + e.x(), y + top, z + e.z());
    }

    private static boolean hits(Point from, Point to, double minX, double minY, double minZ,
                                double maxX, double maxY, double maxZ) {
        double tEnter = 0, tExit = 1;
        double[] p = {from.x(), from.y(), from.z()};
        double[] v = {to.x() - from.x(), to.y() - from.y(), to.z() - from.z()};
        double[] lo = {minX, minY, minZ};
        double[] hi = {maxX, maxY, maxZ};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(v[i]) < EPS) {
                if (p[i] <= lo[i] || p[i] >= hi[i]) return false;
                continue;
            }
            double t1 = (lo[i] - p[i]) / v[i], t2 = (hi[i] - p[i]) / v[i];
            tEnter = Math.max(tEnter, Math.min(t1, t2));
            tExit = Math.min(tExit, Math.max(t1, t2));
            if (tEnter > tExit) return false;
        }
        return true;
    }
}
