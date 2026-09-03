package io.github.term4.polyp.mechanics.projectile.entities;

import net.minestom.server.item.Material;
import net.minestom.server.item.ItemStack;
import io.github.term4.polyp.mechanics.damage.DamageSystem;
import io.github.term4.polyp.mechanics.projectile.ProjectileSnapshot;
import io.github.term4.polyp.mechanics.projectile.types.Pearl;
import io.github.term4.polyp.mechanics.projectile.types.ProjectileTypeConfig;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** The pearl's landing damage is the {@code teleportDamage} knob (vanilla 5); {@code 0} deals no hit at all. */
class PearlTeleportDamageTest extends HeadlessServerTest {

    @Test
    void defaultPearlDealsFiveFallDamageToItsShooter() {
        FakePlayer shooter = FakePlayer.connect(instance, new Pos(5.5, 65, 5.5), "PearlThrower");
        float before = shooter.player.getHealth();
        PearlEntity pearl = new PearlEntity(shooter.player, EntityType.ENDER_PEARL,
                ProjectileSnapshot.of(shooter.player, Pearl.INSTANCE), ProjectileTypeConfig.builder().build());
        pearl.setInstance(instance, new Pos(10.5, 65, 10.5)).join();
        try {
            pearl.onImpact(null);
            assertEquals(before - 5f, shooter.player.getHealth(), 1e-4, "vanilla pearl landing = 5 fall damage");
        } finally {
            pearl.remove();
            shooter.player.remove();
        }
    }

    @Test
    void armorDoesNotSoftenTheLanding() {
        FakePlayer shooter = FakePlayer.connect(instance, new Pos(5.5, 65, 5.5), "PearlArmored");
        shooter.player.setHelmet(ItemStack.of(Material.DIAMOND_HELMET));
        shooter.player.setChestplate(ItemStack.of(Material.DIAMOND_CHESTPLATE));
        shooter.player.setLeggings(ItemStack.of(Material.DIAMOND_LEGGINGS));
        shooter.player.setBoots(ItemStack.of(Material.DIAMOND_BOOTS));
        float before = shooter.player.getHealth();
        PearlEntity pearl = new PearlEntity(shooter.player, EntityType.ENDER_PEARL,
                ProjectileSnapshot.of(shooter.player, Pearl.INSTANCE), ProjectileTypeConfig.builder().build());
        pearl.setInstance(instance, new Pos(10.5, 65, 10.5)).join();
        try {
            pearl.onImpact(null);
            // 1.8 DamageSource.fall bypasses armor: full diamond still takes the whole 5
            assertEquals(before - 5f, shooter.player.getHealth(), 1e-4, "armor never touches a pearl landing");
        } finally {
            pearl.remove();
            shooter.player.remove();
        }
    }

    @Test
    void zeroTeleportDamageLeavesTheShooterUntouched() {
        FakePlayer shooter = FakePlayer.connect(instance, new Pos(5.5, 65, 5.5), "PearlThrower0");
        float before = shooter.player.getHealth();
        PearlEntity pearl = new PearlEntity(shooter.player, EntityType.ENDER_PEARL,
                ProjectileSnapshot.of(shooter.player, Pearl.INSTANCE),
                ProjectileTypeConfig.builder().teleportDamage(0.0).build());
        pearl.setInstance(instance, new Pos(10.5, 65, 10.5)).join();
        try {
            pearl.onImpact(null);
            assertEquals(before, shooter.player.getHealth(), 1e-4, "teleportDamage 0 = no fall damage");
            assertFalse(DamageSystem.isInvulnerableToDamage(shooter.player), "0 opens no i-frame window either");
        } finally {
            pearl.remove();
            shooter.player.remove();
        }
    }
}
