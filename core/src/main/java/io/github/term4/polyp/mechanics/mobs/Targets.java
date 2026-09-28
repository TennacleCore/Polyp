package io.github.term4.polyp.mechanics.mobs;

import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;

import java.util.function.Predicate;

/** The target sets vanilla's mobs scan for. */
public final class Targets {

    private Targets() {}

    /** 1.8 EntityPlayer.class. */
    public static final Predicate<LivingEntity> PLAYERS = e -> e instanceof Player;

    /** 1.8 IMob.VISIBLE_MOB_SELECTOR: monsters that are not invisible. */
    public static final Predicate<LivingEntity> VISIBLE_MONSTERS = e -> e instanceof MobEntity m && m.hostile() && !e.isInvisible();

    public static final Predicate<LivingEntity> ANY = e -> true;
}
