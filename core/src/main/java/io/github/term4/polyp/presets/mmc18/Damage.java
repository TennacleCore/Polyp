package io.github.term4.polyp.presets.mmc18;

import io.github.term4.polyp.api.event.damage.DamageEvent;
import io.github.term4.polyp.mechanics.damage.DamageConfig;
import io.github.term4.polyp.mechanics.damage.DamageConfigResolver.DamageContext;
import io.github.term4.polyp.mechanics.damage.DamageSystem;
import io.github.term4.polyp.mechanics.damage.types.melee.MeleeDamage;
import io.github.term4.polyp.presets.vanilla18.Vanilla18;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * mmc18 damage: the vanilla 1.8 damage ({@link io.github.term4.polyp.presets.vanilla18.Damage}) with
 * silent overdamage, no hurt-tick knockback, and the same-weapon crit discount below.
 */
public final class Damage {

    private Damage() {}

    public static DamageConfig config() {
        return DamageConfig.builder(Vanilla18.damage())
                .overdamageSilent(true)
                // no KB on generic damage ticks; killing the broadcast is the only way off an inherited
                // hurtKnockback (merge semantics can't null it)
                .syncHurtVelocity(false)
                .addCustomComponent(Damage::discountSameItemCrit)
                .build();
    }

    // a crit with the same item as the opening hit replaces only what the swing would have dealt un-critted: the
    // roll's share never counts, the weapon's does. Same sword, nothing changed -> nothing lands; the same sword
    // given Sharpness since -> the Sharpness lands, the roll still does not. A different item keeps the full crit
    @Nullable
    private static Float discountSameItemCrit(DamageContext ctx, DamageEvent event, float amount, boolean overdamage) {
        if (!overdamage || amount <= 0) return null;
        if (!MeleeDamage.KEY.equals(event.type().key())) return null;
        if (!(event.detail() instanceof MeleeDamage.Hit hit) || !hit.critical()) return null;
        if (!(event.target() instanceof LivingEntity le)) return null;
        if (sameItem(event.item(), DamageSystem.openingHitItem(le))) return hit.uncritted(amount);
        return null;
    }

    private static boolean sameItem(@Nullable ItemStack a, @Nullable ItemStack b) {
        boolean aFist = a == null || a.isAir();
        boolean bFist = b == null || b.isAir();
        if (aFist && bFist) return true;
        if (aFist != bFist) return false;
        return a.material().equals(b.material());
    }
}
