package io.github.term4.polyp.mechanics.mobs.ai;

import io.github.term4.polyp.mechanics.mobs.MobEntity;
import io.github.term4.polyp.mechanics.mobs.path.Sight;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/** 1.8 EntitySenses: eye-line answers cached for the tick. */
public final class Senses {

    private final MobEntity entity;
    private final List<Entity> seen = new ArrayList<>();
    private final List<Entity> unseen = new ArrayList<>();

    public Senses(MobEntity entity) {
        this.entity = entity;
    }

    public void clear() {
        seen.clear();
        unseen.clear();
    }

    public boolean canSee(Entity other) {
        if (seen.contains(other)) return true;
        if (unseen.contains(other)) return false;
        Pos a = entity.getPosition(), b = other.getPosition();
        boolean clear = Sight.clear(entity.world(), a.add(0, entity.getEyeHeight(), 0), b.add(0, other.getEyeHeight(), 0));
        (clear ? seen : unseen).add(other);
        return clear;
    }
}
