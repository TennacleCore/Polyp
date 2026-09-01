package io.github.term4.polyp.config;

import io.github.term4.polyp.MechanicsKeys;
import io.github.term4.polyp.MechanicsProfile;
import io.github.term4.polyp.mechanics.damage.DamageConfig;
import io.github.term4.polyp.mechanics.damage.types.melee.MeleeDamageConfig;
import io.github.term4.polyp.mechanics.damage.types.melee.MeleeDamage;
import io.github.term4.polyp.fx.Fx;
import io.github.term4.polyp.fx.FxHandler;
import io.github.term4.polyp.fx.FxRegistry;
import io.github.term4.polyp.mechanics.explosion.DamageModel;
import io.github.term4.polyp.mechanics.explosion.ExplosionConfig;
import io.github.term4.polyp.presets.hypixel.Hypixel;
import io.github.term4.polyp.mechanics.projectile.ProjectileConfig;
import io.github.term4.polyp.mechanics.projectile.types.Arrow;
import io.github.term4.polyp.mechanics.projectile.types.ProjectileTypeConfig;
import io.github.term4.polyp.presets.vanilla18.Vanilla18;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Path writes layer over the inherited config - they can flip one knob and can never wipe base tuning. */
class PathEditsTest {

    /** A stand-in target: the shipped models never look at it. */
    private static net.minestom.server.entity.Entity dummy() {
        return new net.minestom.server.entity.Entity(net.minestom.server.entity.EntityType.ZOMBIE);
    }

    private static MechanicsProfile base() {
        return MechanicsProfile.builder()
                .set(MechanicsKeys.PROJECTILES, Vanilla18.projectiles())
                .set(MechanicsKeys.DAMAGE, Vanilla18.damage())
                .build();
    }

    @Test
    void typedEntryEditKeepsTheRestOfTheEntry() {
        MechanicsProfile.Builder b = MechanicsProfile.builder();
        PathEdits.apply(b, base(), "projectiles/minecraft:arrow/critDamage", "false");
        ProjectileTypeConfig arrow = b.build().get(MechanicsKeys.PROJECTILES).typeConfig(Arrow.KEY);
        assertNotNull(arrow);
        assertEquals(Boolean.FALSE, arrow.critDamage.constantOrNull());
        // the 1.8 arrow tuning must ride along untouched
        assertEquals(3.0, arrow.speed.constantOrNull());
        assertEquals(Boolean.FALSE, arrow.removeOnBlockHit.constantOrNull());
    }

    @Test
    void editsStackOnTheBuilderNotTheFallback() {
        MechanicsProfile.Builder b = MechanicsProfile.builder();
        PathEdits.apply(b, base(), "projectiles/minecraft:arrow/critDamage", "false");
        PathEdits.apply(b, base(), "projectiles/minecraft:arrow/critParticles", "true");
        ProjectileTypeConfig arrow = b.build().get(MechanicsKeys.PROJECTILES).typeConfig(Arrow.KEY);
        assertEquals(Boolean.FALSE, arrow.critDamage.constantOrNull(), "the first edit survives the second");
        assertEquals(Boolean.TRUE, arrow.critParticles.constantOrNull());
    }

    @Test
    void subclassEntryKeepsItsClassAndTuning() {
        MechanicsProfile.Builder b = MechanicsProfile.builder();
        PathEdits.apply(b, base(), "damage/minecraft:player_attack/critMultiplier", "2.0");
        DamageConfig damage = b.build().get(MechanicsKeys.DAMAGE);
        var melee = damage.typeConfig(MeleeDamage.KEY);
        MeleeDamageConfig cfg = assertInstanceOf(MeleeDamageConfig.class, melee, "the melee entry keeps its subclass");
        assertEquals(2.0, cfg.critMultiplier.constantOrNull());
    }

    @Test
    void flatMemberEditWithNoBaseMakesASparseConfig() {
        MechanicsProfile.Builder b = MechanicsProfile.builder();
        PathEdits.apply(b, null, "explosion/damageScale", "0.5");
        ExplosionConfig cfg = b.build().get(MechanicsKeys.EXPLOSION);
        assertEquals(0.5, cfg.damageScale.constantOrNull());
    }

    /** Behaviour-typed values are built by named FACTORIES with arguments - open, not a fixed menu. */
    @Test
    void behaviourValuesAreBuiltByFactoryCall() {
        MechanicsProfile hypixelish = MechanicsProfile.builder()
                .set(MechanicsKeys.EXPLOSION, ExplosionConfig.builder().damageModel(DamageModel.flat(2.0)).build())
                .build();

        // hypixel's flat TNT back to the vanilla curve for SkyWars
        MechanicsProfile.Builder toCurve = MechanicsProfile.builder();
        PathEdits.apply(toCurve, hypixelish, "explosion/damageModel", "curve");
        DamageModel curved = toCurve.build().get(MechanicsKeys.EXPLOSION).damageModel.constantOrNull();
        assertNotNull(curved);
        assertEquals(7.5f, curved.amount(new DamageModel.Hit(dummy(), 2.0, 1.0f, 7.5f)), 1e-4);

        // and any flat amount, not just the one someone predeclared
        MechanicsProfile.Builder toFlat = MechanicsProfile.builder();
        PathEdits.apply(toFlat, hypixelish, "explosion/damageModel", "flat(3.5)");
        DamageModel flat = toFlat.build().get(MechanicsKeys.EXPLOSION).damageModel.constantOrNull();
        assertNotNull(flat);
        assertEquals(3.5f, flat.amount(new DamageModel.Hit(dummy(), 2.0, 1.0f, 7.5f)), 1e-4);
    }

    @Test
    void factoryArgumentsAreValidatedByPosition() {
        MechanicsProfile.Builder b = MechanicsProfile.builder();
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> PathEdits.apply(b, null, "explosion/damageModel", "flat(soon)"))
                .getMessage().contains("must be a number"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> PathEdits.apply(b, null, "explosion/damageModel", "flat(1, 2)"))
                .getMessage().contains("takes 1 argument"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> PathEdits.apply(b, null, "explosion/damageModel", "quadratic(2)"))
                .getMessage().contains("no DamageModel named"));
    }

    @Test
    void enumValuesDecode() {
        MechanicsProfile.Builder b = MechanicsProfile.builder();
        PathEdits.apply(b, base(), "projectiles/minecraft:arrow/knockbackSource", "projectile");
        ProjectileTypeConfig arrow = b.build().get(MechanicsKeys.PROJECTILES).typeConfig(Arrow.KEY);
        assertEquals(ProjectileTypeConfig.KnockbackSource.PROJECTILE, arrow.knockbackSource.constantOrNull());
    }

    /** The whole Hypixel.bedwars() delta as data - and any sound, not a preordained "game-wide" instance. */
    @Test
    void fxIsBuiltFromTheSameFactoryVocabulary() {
        MechanicsProfile hypixelish = MechanicsProfile.builder().set(MechanicsKeys.FX, Hypixel.fx()).build();
        MechanicsProfile.Builder b = MechanicsProfile.builder();
        PathEdits.apply(b, hypixelish, "fx/polyp:pearl_teleport", "global-sound(entity.player.teleport, player, 1, 1)");

        FxRegistry fx = b.build().get(MechanicsKeys.FX);
        assertNotNull(fx.get(Fx.PEARL_TELEPORT));
        assertNotNull(fx.get(Fx.ARROW_HIT_PLAYER), "the rest of the registry rides along");

        MechanicsProfile.Builder silence = MechanicsProfile.builder();
        PathEdits.apply(silence, hypixelish, "fx/polyp:pearl_teleport", "none");
        assertEquals(FxHandler.NONE, silence.build().get(MechanicsKeys.FX).get(Fx.PEARL_TELEPORT));
    }

    @Test
    void anUnknownFxFactoryListsWhatIsAvailable() {
        MechanicsProfile.Builder b = MechanicsProfile.builder();
        String message = assertThrows(IllegalArgumentException.class,
                () -> PathEdits.apply(b, null, "fx/polyp:pearl_teleport", "sideways")).getMessage();
        assertTrue(message.contains("global-sound") && message.contains("none"), message);
    }

    @Test
    void everythingInvalidThrowsWithTheReason() {
        MechanicsProfile.Builder b = MechanicsProfile.builder();
        MechanicsProfile base = base();
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> PathEdits.apply(b, base, "hunger/enabled", "false")).getMessage().contains("unknown member"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> PathEdits.apply(b, base, "projectiles/minecraft:arrow/noSuchKnob", "1")).getMessage().contains("unknown knob"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> PathEdits.apply(b, base, "projectiles/minecraft:arrow/critDamage", "maybe")).getMessage().contains("not a Boolean"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> PathEdits.apply(b, base, "projectiles", "1")).getMessage().contains("member/knob"));
    }
}
