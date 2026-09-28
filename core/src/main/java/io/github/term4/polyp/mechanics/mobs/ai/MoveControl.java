package io.github.term4.polyp.mechanics.mobs.ai;

import io.github.term4.polyp.mechanics.mobs.MobEntity;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.attribute.Attribute;

/** 1.8 EntityMoveHelper: turns the body toward the point (30 degrees a tick) and asks for a jump on a rise. */
public final class MoveControl {

    private final MobEntity entity;
    private double x, y, z;
    private double speed;
    private boolean update;

    public MoveControl(MobEntity entity) {
        this.entity = entity;
        Pos pos = entity.getPosition();
        x = pos.x();
        y = pos.y();
        z = pos.z();
    }

    public boolean isUpdating() { return update; }

    public double speed() { return speed; }

    public void moveTo(double x, double y, double z, double speed) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.speed = speed;
        update = true;
    }

    public void tick() {
        entity.moveForward(0.0f);
        if (!update) return;
        update = false;
        Pos pos = entity.getPosition();
        int feet = (int) Math.floor(pos.y() + 0.5);
        double dx = x - pos.x();
        double dz = z - pos.z();
        double dy = y - feet;
        double d3 = dx * dx + dy * dy + dz * dz;
        if (d3 < 2.500000277905201E-7) return;
        float target = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        entity.yaw(limitAngle(entity.yaw(), target, 30.0f));
        entity.aiMoveSpeed((float) (speed * entity.getAttributeValue(Attribute.MOVEMENT_SPEED)));
        if (dy > 0.0 && dx * dx + dz * dz < 1.0) entity.jumpControl().jump();
    }

    static float limitAngle(float from, float to, float max) {
        float f = wrap(to - from);
        if (f > max) f = max;
        if (f < -max) f = -max;
        float out = from + f;
        if (out < 0.0f) out += 360.0f;
        else if (out > 360.0f) out -= 360.0f;
        return out;
    }

    static float wrap(float angle) {
        angle %= 360.0f;
        if (angle >= 180.0f) angle -= 360.0f;
        if (angle < -180.0f) angle += 360.0f;
        return angle;
    }

    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
}
