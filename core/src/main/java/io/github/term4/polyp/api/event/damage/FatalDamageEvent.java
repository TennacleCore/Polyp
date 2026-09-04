package io.github.term4.polyp.api.event.damage;

import io.github.term4.polyp.Services;
import io.github.term4.polyp.api.event.CancellableMechanicsEvent;
import io.github.term4.polyp.mechanics.damage.DamageSnapshot;
import io.github.term4.polyp.mechanics.damage.types.DamageType;
import net.minestom.server.entity.Entity;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Fired when a hit's final applied amount would kill the target (absorption included), before it applies.
 * Cancelling drops the damage but keeps the hit's hurt flash and sound - the respawn seam for rulesets,
 * since {@code EntityDeathEvent} is not cancellable.
 */
public final class FatalDamageEvent extends CancellableMechanicsEvent<DamageSnapshot> {

    private final float amount;

    public FatalDamageEvent(DamageSnapshot snap, float amount, Services services) {
        super(snap, services);
        this.amount = amount;
    }

    /** The final applied amount (post-mitigation; an overdamage replacement's diff). */
    public float amount() { return amount; }

    public DamageType type() { return finalSnap().type(); }
    public Entity target() { return finalSnap().target(); }
    public @Nullable Entity source() { return finalSnap().source(); }
    /** The attacker's weapon, or {@code null}. */
    public @Nullable ItemStack item() { return finalSnap().item(); }
    /** Type-specific payload from the producer (the projectile that hit, a fall distance), or {@code null}. */
    public @Nullable Object detail() { return finalSnap().detail(); }
}
