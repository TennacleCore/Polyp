package io.github.term4.polyp.config;

import io.github.term4.polyp.config.PathEdits;
import io.github.term4.polyp.MechanicsKeys;
import io.github.term4.polyp.mechanics.hunger.HungerConfig;
import io.github.term4.polyp.MechanicsProfile;
import io.github.term4.polyp.mechanics.damage.DamageConfigResolver.DamageContext;
import io.github.term4.polyp.mechanics.damage.DamageSnapshot;
import io.github.term4.polyp.mechanics.damage.types.melee.MeleeDamage;
import io.github.term4.polyp.mechanics.damage.types.melee.MeleeDamageConfig;
import io.github.term4.polyp.mechanics.projectile.ProjectileConfigResolver.ProjectileContext;
import io.github.term4.polyp.mechanics.projectile.ProjectileSnapshot;
import io.github.term4.polyp.mechanics.projectile.types.Arrow;
import io.github.term4.polyp.mechanics.projectile.types.ProjectileTypeConfig;
import io.github.term4.polyp.presets.vanilla18.Vanilla18;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Per-player variation is a VALUE inside one world profile, not a player scope: a team-targeted entry becomes
 * a knob that answers differently by the context's subject, with the inherited value for everyone else.
 */
class TargetedKnobsTest extends HeadlessServerTest {

    private static MechanicsProfile base() {
        return MechanicsProfile.builder()
                .set(MechanicsKeys.PROJECTILES, Vanilla18.projectiles())
                .set(MechanicsKeys.DAMAGE, Vanilla18.damage())
                .build();
    }

    private ProjectileContext shot(Player shooter) {
        return ProjectileContext.of(ProjectileSnapshot.of(shooter, Arrow.INSTANCE), polyp.services());
    }

    @Test
    void aTeamScopedKnobVariesPerShooterInsideOneProfile() {
        FakePlayer red = FakePlayer.connect(instance, new Pos(0.5, 65, 0.5), "TgtRed");
        FakePlayer blue = FakePlayer.connect(instance, new Pos(1.5, 65, 0.5), "TgtBlue");
        try {
            MechanicsProfile.Builder b = MechanicsProfile.builder();
            PathEdits.apply(b, base(), "projectiles/minecraft:arrow/critDamage", "false");            // the look
            PathEdits.apply(b, base(), "projectiles/minecraft:arrow/critDamage", "true", p -> p == red.player); // red only
            ProjectileTypeConfig arrow = b.build().get(MechanicsKeys.PROJECTILES).typeConfig(Arrow.KEY);

            assertEquals(Boolean.TRUE, arrow.critDamage.resolve(shot(red.player)), "red's arrows roll crits");
            assertEquals(Boolean.FALSE, arrow.critDamage.resolve(shot(blue.player)), "blue keeps the look's value");
            assertEquals(3.0, arrow.speed.constantOrNull(), "the rest of the entry is untouched");

            // still inspectable: the wrapper says who, what, and what it replaced
            assertNull(arrow.critDamage.constantOrNull());
            FieldValue.Targeted<?, ?> t = assertInstanceOf(FieldValue.Targeted.class, arrow.critDamage.fn());
            assertEquals(Boolean.TRUE, t.value().constantOrNull());
            assertEquals(Boolean.FALSE, t.fallback().constantOrNull(), "the value it wraps is the fallback");
        } finally {
            red.player.remove();
            blue.player.remove();
        }
    }

    /** Damage is ABOUT the victim, so a red-targeted damage knob applies when red is hit - not when red hits. */
    @Test
    void theSubjectIsTheRoleThatSystemResolvesAgainst() {
        FakePlayer red = FakePlayer.connect(instance, new Pos(2.5, 65, 0.5), "SubRed");
        FakePlayer blue = FakePlayer.connect(instance, new Pos(3.5, 65, 0.5), "SubBlue");
        try {
            MechanicsProfile.Builder b = MechanicsProfile.builder();
            PathEdits.apply(b, base(), "damage/minecraft:player_attack/critMultiplier", "2.0", p -> p == red.player);
            MeleeDamageConfig melee = (MeleeDamageConfig) b.build().get(MechanicsKeys.DAMAGE).typeConfig(MeleeDamage.KEY);
            Double vanilla = ((MeleeDamageConfig) Vanilla18.damage().typeConfig(MeleeDamage.KEY)).critMultiplier.constantOrNull();

            DamageContext redIsHit = new DamageContext(DamageSnapshot.of(red.player, MeleeDamage.INSTANCE).withSource(blue.player), polyp.services());
            DamageContext blueIsHit = new DamageContext(DamageSnapshot.of(blue.player, MeleeDamage.INSTANCE).withSource(red.player), polyp.services());
            assertEquals(2.0, melee.critMultiplier.resolve(redIsHit), "targets the victim");
            assertEquals(vanilla, melee.critMultiplier.resolve(blueIsHit), "red attacking is not red being the subject");
        } finally {
            red.player.remove();
            blue.player.remove();
        }
    }

    /** "Red has hunger, blue does not" is a targeted knob too - every member carries a subject. */
    @Test
    void hungerVariesPerTeamToo() {
        FakePlayer red = FakePlayer.connect(instance, new Pos(4.5, 65, 0.5), "HunRed");
        FakePlayer blue = FakePlayer.connect(instance, new Pos(5.5, 65, 0.5), "HunBlue");
        try {
            MechanicsProfile base = MechanicsProfile.builder()
                    .set(MechanicsKeys.HUNGER, HungerConfig.builder().enabled(true).build()).build();
            MechanicsProfile.Builder b = MechanicsProfile.builder();
            PathEdits.apply(b, base, "hunger/enabled", "false", p -> p == blue.player);
            HungerConfig hunger = b.build().get(MechanicsKeys.HUNGER);
            assertEquals(Boolean.TRUE, hunger.enabled.resolve(new HungerConfig.HungerContext(red.player)));
            assertEquals(Boolean.FALSE, hunger.enabled.resolve(new HungerConfig.HungerContext(blue.player)));
        } finally {
            red.player.remove();
            blue.player.remove();
        }
    }

    @Test
    void orKeepsTheTargetingAndLayersTheFallback() {
        FakePlayer red = FakePlayer.connect(instance, new Pos(0.5, 65, 0.5), "OrRed");
        FakePlayer blue = FakePlayer.connect(instance, new Pos(1.5, 65, 0.5), "OrBlue");
        try {
            FieldValue<ProjectileContext, Boolean> targeted = FieldValue.targeted(p -> p == red.player, FieldValue.constant(true), null);
            FieldValue<ProjectileContext, Boolean> layered = targeted.or(FieldValue.constant(false));
            assertInstanceOf(FieldValue.Targeted.class, layered.fn(), "or() layers under the targeting, never over it");
            assertEquals(Boolean.TRUE, layered.resolve(shot(red.player)));
            assertEquals(Boolean.FALSE, layered.resolve(shot(blue.player)), "everyone else reads the fallback");
        } finally {
            red.player.remove();
            blue.player.remove();
        }
    }

    @Test
    void velocityAndCompatVaryPerPlayerThroughTheSameProfile() {
        FakePlayer red = FakePlayer.connect(instance, new Pos(0.5, 65, 0.5), "VcRed");
        FakePlayer blue = FakePlayer.connect(instance, new Pos(1.5, 65, 0.5), "VcBlue");
        try {
            MechanicsProfile.Builder b = MechanicsProfile.builder();
            PathEdits.apply(b, base(), "velocity/entityPush", "false");
            PathEdits.apply(b, base(), "velocity/entityPush", "true", p -> p == red.player);
            PathEdits.apply(b, base(), "compat/legacyHitbox", "false");
            PathEdits.apply(b, base(), "compat/legacyHitbox", "true", p -> p == red.player);
            MechanicsProfile profile = b.build();
            var velocity = profile.get(MechanicsKeys.VELOCITY).reconstructionConfig();
            assertNotNull(velocity);
            assertTrue(velocity.entityPush(io.github.term4.polyp.tracking.motion.VelocityContext.of(red.player)));
            assertFalse(velocity.entityPush(io.github.term4.polyp.tracking.motion.VelocityContext.of(blue.player)));
            var compat = profile.get(MechanicsKeys.COMPAT);
            assertEquals(Boolean.TRUE, compat.legacyHitbox(new io.github.term4.polyp.platform.compatibility.CompatConfig.CompatContext(red.player)));
            assertEquals(Boolean.FALSE, compat.legacyHitbox(new io.github.term4.polyp.platform.compatibility.CompatConfig.CompatContext(blue.player)));
        } finally {
            red.player.remove();
            blue.player.remove();
        }
    }

    @Test
    void aScalarMemberCannotVaryPerPlayer() {
        MechanicsProfile.Builder b = MechanicsProfile.builder();
        String message = assertThrows(IllegalArgumentException.class,
                () -> PathEdits.apply(b, base(), "item-physics", "modern", p -> true)).getMessage();
        assertTrue(message.contains("cannot vary per player"), message);
    }
}
