package io.github.term4.polyp.mechanics.mobs.ai;

import io.github.term4.polyp.mechanics.mobs.MobEntity;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.LivingEntity;

/** 1.8 EntityLookHelper: turns the head (and pitch) toward a point; the head stays within 75 degrees of the body while walking. */
public final class LookControl {

    private final MobEntity entity;
    private float deltaYaw;
    private float deltaPitch;
    private boolean looking;
    private double x, y, z;

    public LookControl(MobEntity entity) {
        this.entity = entity;
    }

    public void lookAt(Entity target, float deltaYaw, float deltaPitch) {
        Pos p = target.getPosition();
        x = p.x();
        y = target instanceof LivingEntity ? p.y() + target.getEyeHeight()
                : p.y() + (target.getBoundingBox().minY() + target.getBoundingBox().maxY()) / 2.0;
        z = p.z();
        this.deltaYaw = deltaYaw;
        this.deltaPitch = deltaPitch;
        looking = true;
    }

    public void lookAt(double x, double y, double z, float deltaYaw, float deltaPitch) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.deltaYaw = deltaYaw;
        this.deltaPitch = deltaPitch;
        looking = true;
    }

    public void tick() {
        entity.pitch(0.0f);
        if (looking) {
            looking = false;
            Pos p = entity.getPosition();
            double dx = x - p.x();
            double dy = y - (p.y() + entity.getEyeHeight());
            double dz = z - p.z();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
            float pitch = (float) (-(Math.atan2(dy, horizontal) * 180.0 / Math.PI));
            entity.pitch(turn(entity.pitch(), pitch, deltaPitch));
            entity.headYaw(turn(entity.headYaw(), yaw, deltaYaw));
        } else {
            entity.headYaw(turn(entity.headYaw(), entity.bodyYaw(), 10.0f));
        }
        float off = MoveControl.wrap(entity.headYaw() - entity.bodyYaw());
        if (!entity.navigation().noPath()) {
            if (off < -75.0f) entity.headYaw(entity.bodyYaw() - 75.0f);
            if (off > 75.0f) entity.headYaw(entity.bodyYaw() + 75.0f);
        }
    }

    private static float turn(float from, float to, float max) {
        float f = MoveControl.wrap(to - from);
        if (f > max) f = max;
        if (f < -max) f = -max;
        return from + f;
    }

    public boolean isLooking() { return looking; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
}
