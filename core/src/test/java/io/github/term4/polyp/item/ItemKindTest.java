package io.github.term4.polyp.item;

import io.github.term4.polyp.mechanics.projectile.ProjectileConfig;
import io.github.term4.polyp.mechanics.projectile.ProjectileConfigResolver;
import io.github.term4.polyp.mechanics.projectile.ProjectileSnapshot;
import io.github.term4.polyp.mechanics.projectile.types.Fireball;
import io.github.term4.polyp.mechanics.projectile.types.ProjectileTypeConfig;
import io.github.term4.polyp.presets.hypixel.Projectiles;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.kyori.adventure.key.Key;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A stamp names the kind, and a thrown stack's kind picks the variant registered under it. */
class ItemKindTest extends HeadlessServerTest {

    @Test
    void aStampNamesTheKind() {
        ItemStack plain = ItemStack.of(Material.FIRE_CHARGE);
        assertNull(ItemKind.of(plain));
        assertEquals(Key.key("minecraft:fire_charge"), ItemKind.keyOf(plain));
        ItemStack stamped = ItemKind.stamp(plain, Projectiles.FIREBALL);
        assertTrue(ItemKind.is(stamped, Projectiles.FIREBALL));
        assertEquals(Projectiles.FIREBALL, ItemKind.keyOf(stamped));
        assertEquals(Material.FIRE_CHARGE, stamped.material(), "the material is untouched");
    }

    @Test
    void aStampedThrowResolvesItsVariant() {
        ProjectileConfig cfg = ProjectileConfig.builder(Projectiles.config())
                .variant(Key.key("test:lobbed"), ProjectileTypeConfig.builder(Fireball.KEY).gravity(0.25).build())
                .build();
        ItemStack lobbed = ItemKind.stamp(ItemStack.of(Material.FIRE_CHARGE), Key.key("test:lobbed"));
        ProjectileSnapshot snap = ProjectileSnapshot.of(null, Fireball.INSTANCE).withConfig(cfg);
        double plain = ProjectileConfigResolver.resolveFlight(ProjectileConfigResolver.ProjectileContext.of(snap, null).typeConfig(),
                ProjectileConfigResolver.ProjectileContext.of(snap, null)).gravity();
        ProjectileSnapshot stamped = snap.withItem(lobbed);
        double variant = ProjectileConfigResolver.resolveFlight(ProjectileConfigResolver.ProjectileContext.of(stamped, null).typeConfig(),
                ProjectileConfigResolver.ProjectileContext.of(stamped, null)).gravity();
        assertEquals(0.0, plain, 1e-9, "the fire charge throws as the world's fireball");
        assertEquals(0.25, variant, 1e-9, "the stamped one as its own kind, over the world's");
    }
}
