package io.github.term4.polyp.mechanics.damage.types.mob;

import io.github.term4.polyp.Services;
import io.github.term4.polyp.mechanics.attribute.Attribute;
import io.github.term4.polyp.mechanics.attribute.AttributeSystem;
import io.github.term4.polyp.mechanics.attribute.combat.CombatFacts;
import io.github.term4.polyp.mechanics.damage.DamageSnapshot;
import io.github.term4.polyp.mechanics.damage.types.DamageType;
import io.github.term4.polyp.mechanics.damage.types.DamageTypeConfig;
import io.github.term4.polyp.mechanics.mobs.MobEntity;
import io.github.term4.polyp.mechanics.mobs.MobsSystem;
import net.kyori.adventure.key.Key;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;

/** A mob's own hit (1.8 DamageSource.causeMobDamage): the world difficulty scales it on a player, before armor. */
public final class MobDamage extends DamageType {

    public static final Key KEY = Key.key("minecraft:mob_attack");
    public static final MobDamage INSTANCE = new MobDamage();

    private MobDamage() {
        super(KEY, "Mob attack", net.minestom.server.entity.damage.DamageType.MOB_ATTACK,
                DamageTypeConfig.builder(KEY).ownsVelocityBroadcast(true).build());
    }

    /**
     * {@code amount} through the attacker's attribute chain when {@code attributes} (Strength on the silverfish,
     * never on the golem's own roll), then the difficulty on a player victim.
     */
    public DamageSnapshot snapshot(MobEntity mob, Entity target, float amount, boolean attributes, Services services) {
        DamageSnapshot prelim = DamageSnapshot.of(target, this).withSource(mob);
        AttributeSystem attrs = attributes ? services.attributes() : null;
        if (attrs != null) {
            amount = (float) attrs.context(mob, null).with(CombatFacts.TARGET, target).value(Attribute.ATTACK_DAMAGE, amount);
        }
        MobsSystem mobs = services.mobs();
        if (mobs != null && target instanceof Player) amount = mobs.scaleOnPlayer(mob, target, amount);
        return prelim.withAmount(amount);
    }
}
