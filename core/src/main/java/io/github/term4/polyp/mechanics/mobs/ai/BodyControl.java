package io.github.term4.polyp.mechanics.mobs.ai;

import io.github.term4.polyp.mechanics.mobs.MobEntity;
import net.minestom.server.coordinate.Pos;

/** 1.8 EntityBodyHelper: the body follows the facing while walking, else drifts to the head after a pause. */
public final class BodyControl {

    private final MobEntity entity;
    private int stillTicks;
    private float lastHeadYaw;

    public BodyControl(MobEntity entity) {
        this.entity = entity;
    }

    public void tick() {
        Pos pos = entity.getPosition(), prev = entity.getPreviousPosition();
        double dx = pos.x() - prev.x();
        double dz = pos.z() - prev.z();
        if (dx * dx + dz * dz > 2.500000277905201E-7) {
            entity.bodyYaw(entity.yaw());
            entity.headYaw(bound(entity.bodyYaw(), entity.headYaw(), 75.0f));
            lastHeadYaw = entity.headYaw();
            stillTicks = 0;
            return;
        }
        float limit = 75.0f;
        if (Math.abs(entity.headYaw() - lastHeadYaw) > 15.0f) {
            stillTicks = 0;
            lastHeadYaw = entity.headYaw();
        } else {
            ++stillTicks;
            if (stillTicks > 10) limit = Math.max(1.0f - (stillTicks - 10) / 10.0f, 0.0f) * 75.0f;
        }
        entity.bodyYaw(bound(entity.headYaw(), entity.bodyYaw(), limit));
    }

    private static float bound(float toward, float from, float max) {
        float f = MoveControl.wrap(toward - from);
        if (f < -max) f = -max;
        if (f >= max) f = max;
        return toward - f;
    }
}
