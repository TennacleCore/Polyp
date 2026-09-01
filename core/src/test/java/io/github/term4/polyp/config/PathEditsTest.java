package io.github.term4.polyp.config;

import io.github.term4.polyp.MechanicsKeys;
import io.github.term4.polyp.MechanicsProfile;
import io.github.term4.polyp.mechanics.damage.DamageConfig;
import io.github.term4.polyp.mechanics.damage.types.melee.MeleeDamageConfig;
import io.github.term4.polyp.mechanics.damage.types.melee.MeleeDamage;
import io.github.term4.polyp.fx.Fx;
import io.github.term4.polyp.fx.FxHandler;
import io.github.term4.polyp.fx.FxHandlers;
import io.github.term4.polyp.fx.FxRegistry;
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
        PathEdits.apply(b, null, "explosion/flatDamage", "2.0");
        ExplosionConfig cfg = b.build().get(MechanicsKeys.EXPLOSION);
        assertEquals(2.0, cfg.flatDamage.constantOrNull());
    }

    /** Hypixel's flat TNT switched back to the vanilla curve for SkyWars - the model is a value, so it overlays. */
    @Test
    void anOverlayCanSwitchTheExplosionDamageModelBothWays() {
        MechanicsProfile hypixelish = MechanicsProfile.builder()
                .set(MechanicsKeys.EXPLOSION, ExplosionConfig.builder()
                        .damageModel(ExplosionConfig.DamageModel.FLAT).flatDamage(2.0).build()).build();

        MechanicsProfile.Builder toCurve = MechanicsProfile.builder();
        PathEdits.apply(toCurve, hypixelish, "explosion/damageModel", "curve");
        ExplosionConfig curved = toCurve.build().get(MechanicsKeys.EXPLOSION);
        assertEquals(ExplosionConfig.DamageModel.CURVE, curved.damageModel.constantOrNull());
        assertEquals(2.0, curved.flatDamage.constantOrNull(), "the parameter survives - the model chooses");

        MechanicsProfile.Builder toFlat = MechanicsProfile.builder();
        PathEdits.apply(toFlat, hypixelish, "explosion/damageModel", "flat");
        assertEquals(ExplosionConfig.DamageModel.FLAT,
                toFlat.build().get(MechanicsKeys.EXPLOSION).damageModel.constantOrNull());
    }

    @Test
    void enumValuesDecode() {
        MechanicsProfile.Builder b = MechanicsProfile.builder();
        PathEdits.apply(b, base(), "projectiles/minecraft:arrow/knockbackSource", "projectile");
        ProjectileTypeConfig arrow = b.build().get(MechanicsKeys.PROJECTILES).typeConfig(Arrow.KEY);
        assertEquals(ProjectileTypeConfig.KnockbackSource.PROJECTILE, arrow.knockbackSource.constantOrNull());
    }

    /** The whole Hypixel.bedwars() delta as data: the game-wide pearl landing, one path. */
    @Test
    void fxHandlersAreSwappableByName() {
        MechanicsProfile hypixelish = MechanicsProfile.builder().set(MechanicsKeys.FX, Hypixel.fx()).build();
        MechanicsProfile.Builder b = MechanicsProfile.builder();
        PathEdits.apply(b, hypixelish, "fx/polyp:pearl_teleport", "game-wide");

        FxRegistry fx = b.build().get(MechanicsKeys.FX);
        assertEquals(FxHandlers.get(Fx.PEARL_TELEPORT, "game-wide"), fx.get(Fx.PEARL_TELEPORT));
        assertNotNull(fx.get(Fx.ARROW_HIT_PLAYER), "the rest of the registry rides along");

        MechanicsProfile.Builder silence = MechanicsProfile.builder();
        PathEdits.apply(silence, hypixelish, "fx/polyp:pearl_teleport", "none");
        assertEquals(FxHandler.NONE, silence.build().get(MechanicsKeys.FX).get(Fx.PEARL_TELEPORT));
    }

    @Test
    void anUnknownFxNameListsWhatTheKeyOffers() {
        MechanicsProfile.Builder b = MechanicsProfile.builder();
        String message = assertThrows(IllegalArgumentException.class,
                () -> PathEdits.apply(b, null, "fx/polyp:pearl_teleport", "sideways")).getMessage();
        assertTrue(message.contains("game-wide") && message.contains("none"), message);
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
