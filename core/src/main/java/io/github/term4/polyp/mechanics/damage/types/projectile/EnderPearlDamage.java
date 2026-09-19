package io.github.term4.polyp.mechanics.damage.types.projectile;

import io.github.term4.polyp.mechanics.damage.types.DamageType;
import io.github.term4.polyp.mechanics.damage.types.DamageTypeConfig;
import net.kyori.adventure.key.Key;

/**
 * The cost a pearl charges its thrower on arrival. 1.8 bills it as plain fall damage
 * ({@code DamageSource.fall}); 26.1 gave it a source of its own, which is what a ruleset selecting by damage type
 * sees and what the death message reads. Pick the era's with {@code teleportDamageType}.
 */
public final class EnderPearlDamage extends DamageType {

    public static final Key KEY = Key.key("minecraft:ender_pearl");
    public static final EnderPearlDamage INSTANCE = new EnderPearlDamage();

    private EnderPearlDamage() {
        super(KEY, "Ender pearl", net.minestom.server.entity.damage.DamageType.ENDER_PEARL,
                DamageTypeConfig.builder(KEY).baseAmount(5.0).bypassArmor(true).build());
    }

    /** Vanilla bills it under the fall message and the Feather Falling category, as 1.8's fall source did. */
    @Override
    public java.util.Set<io.github.term4.polyp.mechanics.attribute.defense.ProtectionCategory> protectionCategories() {
        return java.util.Set.of(io.github.term4.polyp.mechanics.attribute.defense.ProtectionCategory.FALL);
    }
}
