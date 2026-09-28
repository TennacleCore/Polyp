package io.github.term4.polyp.mechanics.mobs.kinds;

import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;

/** 1.8 EntityDragonPart: a hit box the dragon carries, with the wire id the clients give it. */
public final class DragonPart {

    public final String name;
    public final int id;
    private double width, height;
    private Pos position = Pos.ZERO;

    DragonPart(String name, int id, double width, double height) {
        this.name = name;
        this.id = id;
        this.width = width;
        this.height = height;
    }

    void size(double width, double height) {
        this.width = width;
        this.height = height;
    }

    void moveTo(double x, double y, double z) {
        position = new Pos(x, y, z);
    }

    public Pos position() { return position; }
    public double width() { return width; }
    public double height() { return height; }

    public BoundingBox box() {
        return new BoundingBox(width, height, width);
    }

    /** The box grown {@code xz} sideways and {@code y} up and down, shifted {@code dy}: 1.8's expand then offset. */
    public Box grown(double xz, double y, double dy) {
        double half = width / 2.0 + xz;
        return new Box(position.x() - half, position.y() - y + dy, position.z() - half,
                position.x() + half, position.y() + height + y + dy, position.z() + half);
    }

    public boolean contains(Point p) {
        return grown(0, 0, 0).contains(p);
    }

    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public boolean contains(Point p) {
            return p.x() >= minX && p.x() <= maxX && p.y() >= minY && p.y() <= maxY && p.z() >= minZ && p.z() <= maxZ;
        }

        public boolean meets(Point at, BoundingBox other) {
            return at.x() + other.maxX() > minX && at.x() + other.minX() < maxX
                    && at.y() + other.maxY() > minY && at.y() + other.minY() < maxY
                    && at.z() + other.maxZ() > minZ && at.z() + other.minZ() < maxZ;
        }

        public Vec random(java.util.Random random) {
            return new Vec(minX + (maxX - minX) * random.nextFloat(), minY + (maxY - minY) * random.nextFloat(),
                    minZ + (maxZ - minZ) * random.nextFloat());
        }
    }
}
