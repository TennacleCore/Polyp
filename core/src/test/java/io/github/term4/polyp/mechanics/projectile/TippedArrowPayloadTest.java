package io.github.term4.polyp.mechanics.projectile;

import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.mechanics.projectile.entities.arrow.ArrowEntity;
import io.github.term4.polyp.mechanics.projectile.types.Arrow;
import io.github.term4.polyp.presets.vanilla18.Vanilla18;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.potion.CustomPotionEffect;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.potion.TimedPotion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A tipped arrow's on-hit payload: vanilla's duration scaling, and the effect's own display settings. */
class TippedArrowPayloadTest extends HeadlessServerTest {

    private static final int BREWED = 900; // 45s, the brewed slowness a tipped arrow is crafted from

    /** Fires a tipped arrow carrying {@code effect} at {@code scale} into a victim {@code 5} blocks north. */
    private TimedPotion strike(double x, CustomPotionEffect effect, float scale) {
        LivingEntity victim = zombie(new Pos(x, 65, 105.5));
        victim.setHealth(20f);
        LivingEntity shooter = looseZombie();
        shooter.setInstance(instance, new Pos(x, 65, 100.5, 0f, 0f)).join();
        try {
            var snap = ProjectileSnapshot.of(shooter, Arrow.INSTANCE)
                    .withConfig(Vanilla18.projectiles()).withItem(ItemStack.of(Material.BOW));
            ArrowEntity arrow = (ArrowEntity) new ProjectileSystem(Polyp.getInstance(), Vanilla18.projectiles()).launch(snap);
            assertNotNull(arrow);
            arrow.setOnHitEffects(List.of(effect), scale);
            awaitSpawn(arrow);
            for (int t = 1; t <= 40 && !arrow.isRemoved(); t++) arrow.tick(t * 50L);
            assertTrue(arrow.isRemoved(), "the arrow must reach the victim");

            var applied = victim.getActiveEffects().stream().filter(p -> p.potion().effect() == effect.id()).findFirst();
            return applied.orElse(null);
        } finally {
            shooter.remove();
            victim.remove();
        }
    }

    private static CustomPotionEffect slowness(boolean ambient, boolean particles, boolean icon) {
        return new CustomPotionEffect(PotionEffect.SLOWNESS, 0, BREWED, ambient, particles, icon);
    }

    /** {@code MobEffectInstance.withScaledDuration} floors; rounding hands a crafted arrow an extra tick. */
    @Test
    void theScaleFloorsLikeVanilla() {
        TimedPotion applied = strike(200.5, slowness(false, true, true), 0.125f);
        assertNotNull(applied, "the payload must land");
        assertEquals(112, applied.potion().duration(), "floor(900 * 0.125), not round");
    }

    @Test
    void theEffectKeepsItsFlags() {
        TimedPotion applied = strike(220.5, slowness(true, false, false), 1.0f);
        assertNotNull(applied, "the payload must land");
        assertEquals(BREWED, applied.potion().duration(), "an unscaled payload lands whole");
        assertEquals(Potion.AMBIENT_FLAG, applied.potion().flags(), "the effect's own settings ride along");
    }
}
