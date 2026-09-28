package io.github.term4.polyp.mechanics.mobs.ai;

import io.github.term4.polyp.mechanics.mobs.MobEntity;

/** 1.8 EntityJumpHelper. */
public final class JumpControl {

    private final MobEntity entity;
    private boolean jumping;

    public JumpControl(MobEntity entity) {
        this.entity = entity;
    }

    public void jump() { jumping = true; }

    public void tick() {
        entity.jumping(jumping);
        jumping = false;
    }
}
