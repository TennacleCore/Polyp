package io.github.term4.polyp.config;

import io.github.term4.polyp.MechanicsKeys;
import io.github.term4.polyp.MechanicsProfile;
import io.github.term4.polyp.mechanics.damage.DamageConfig;
import io.github.term4.polyp.mechanics.damage.types.melee.MeleeDamageConfig;
import io.github.term4.polyp.mechanics.damage.types.melee.MeleeDamage;
import io.github.term4.polyp.fx.Fx;
import io.github.term4.polyp.fx.FxHandler;
import io.github.term4.polyp.fx.FxRegistry;
import io.github.term4.polyp.mechanics.consumable.ConsumableBehavior;
import io.github.term4.polyp.mechanics.explosion.DamageModel;
import io.github.term4.polyp.mechanics.explosion.ExplosionConfig;
import io.github.term4.polyp.presets.hypixel.Hypixel;
import io.github.term4.polyp.mechanics.projectile.ProjectileConfig;
import io.github.term4.polyp.mechanics.projectile.types.Arrow;
import io.github.term4.polyp.mechanics.projectile.types.ProjectileTypeConfig;
import io.github.term4.polyp.presets.vanilla18.Vanilla18;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Path writes layer over the inherited config - they can flip one knob and can never wipe base tuning.
 * Server-backed because the fx cases touch {@code Fx}'s keys and the sound registry; the path layer itself
 * needs no server.
 */
class PathEditsTest extends io.github.term4.polyp.testsupport.HeadlessServerTest {

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

    /** The whole Hypixel.bedwars() delta as data: any effect, delivered to any audience. */
    @Test
    void fxComposesAnEffectWithAnAudience() {
        MechanicsProfile hypixelish = MechanicsProfile.builder().set(MechanicsKeys.FX, Hypixel.fx()).build();
        MechanicsProfile.Builder b = MechanicsProfile.builder();
        PathEdits.apply(b, hypixelish, "fx/polyp:pearl_teleport",
                "to(at-listener(watchers), sound(entity.player.teleport, player, 1, 1))");

        FxRegistry fx = b.build().get(MechanicsKeys.FX);
        assertNotNull(fx.get(Fx.PEARL_TELEPORT));
        assertNotNull(fx.get(Fx.ARROW_HIT_PLAYER), "the rest of the registry rides along");

        MechanicsProfile.Builder silence = MechanicsProfile.builder();
        PathEdits.apply(silence, hypixelish, "fx/polyp:pearl_teleport", "none");
        assertEquals(FxHandler.NONE, silence.build().get(MechanicsKeys.FX).get(Fx.PEARL_TELEPORT));
    }

    /** Effects and audiences are independent, so every pairing works without being registered as a pairing. */
    @Test
    void everyAudienceComposesWithEveryEffect() {
        for (String audience : java.util.List.of(
                // primitives
                "members", "watchers", "instance", "server", "viewers", "source", "nobody", "predicted",
                // compositions - none of these is a registered case
                "except(instance, watchers)", "both(members, viewers)", "only(instance, members)",
                "tree(members)", "at-listener(tree(watchers))", "within(20, except(watchers, source))",
                "at-listener(server)", "at-listener(watchers)")) {
            for (String effect : java.util.List.of("sound(entity.player.teleport, player, 1, 1)",
                    "particle(crit, 8, 0.5, 0)", "animation(SWING_MAIN_ARM)")) {
                String spec = "to(" + audience + ", " + effect + ")";
                MechanicsProfile.Builder b = MechanicsProfile.builder();
                PathEdits.apply(b, null, "fx/polyp:pearl_teleport", spec);
                assertNotNull(b.build().get(MechanicsKeys.FX).get(Fx.PEARL_TELEPORT), spec);
            }
        }
        // a bare effect keeps the vanilla shard audience
        MechanicsProfile.Builder bare = MechanicsProfile.builder();
        PathEdits.apply(bare, null, "fx/polyp:pearl_teleport", "sound(entity.player.teleport, player, 1, 1)");
        assertNotNull(bare.build().get(MechanicsKeys.FX).get(Fx.PEARL_TELEPORT));
    }

    @Test
    void anUnknownFxFactoryListsWhatIsAvailable() {
        MechanicsProfile.Builder b = MechanicsProfile.builder();
        String message = assertThrows(IllegalArgumentException.class,
                () -> PathEdits.apply(b, null, "fx/polyp:pearl_teleport", "sideways")).getMessage();
        assertTrue(message.contains("to") && message.contains("none"), message);
        String audience = assertThrows(IllegalArgumentException.class,
                () -> PathEdits.apply(b, null, "fx/polyp:pearl_teleport", "to(elsewhere, sound(entity.player.teleport, player, 1, 1))")).getMessage();
        assertTrue(audience.contains("no Recipients named"), audience);
    }

    /** A @GenerateKnobs config: plain values, no FieldValue, and an edit keeps every other field. */
    @Test
    void plainValueConfigsArePathAddressableToo() {
        MechanicsProfile base = MechanicsProfile.builder()
                .set(MechanicsKeys.VRI, io.github.term4.polyp.vri.VriConfig.all())
                .set(MechanicsKeys.HUNGER, io.github.term4.polyp.mechanics.hunger.HungerConfig.builder()
                        .enabled(true).naturalRegen(true).regenInterval(80).build())
                .build();

        MechanicsProfile.Builder b = MechanicsProfile.builder();
        PathEdits.apply(b, base, "vri/itemDrop", "false");
        PathEdits.apply(b, base, "hunger/enabled", "false");
        PathEdits.apply(b, base, "hunger/naturalRegen", "false");
        MechanicsProfile out = b.build();

        var vri = out.get(MechanicsKeys.VRI);
        assertFalse(vri.itemDrop, "the edited knob");
        assertTrue(vri.itemPickup, "everything else survives the edit");
        assertTrue(vri.blockBreakProgress);

        var hunger = out.get(MechanicsKeys.HUNGER);
        assertEquals(Boolean.FALSE, hunger.enabled());
        assertEquals(Boolean.FALSE, hunger.naturalRegen());
        assertEquals(80, hunger.regenInterval(), "an untouched knob keeps the base value");
    }

    /** "only melee and arrows hurt here" as a selection over the catalog, not eleven enabled=false lines. */
    @Test
    void damageTypesAreSelectedNotRestated() {
        MechanicsProfile.Builder b = MechanicsProfile.builder();
        PathEdits.apply(b, base(), "damage/enabledTypes", "only(minecraft:player_attack, minecraft:thrown)");
        var types = b.build().get(MechanicsKeys.DAMAGE).enabledTypes.constantOrNull();
        assertNotNull(types);
        assertTrue(types.admits(io.github.term4.polyp.mechanics.damage.types.melee.MeleeDamage.KEY));
        assertTrue(types.admits(io.github.term4.polyp.mechanics.damage.types.projectile.ProjectileDamage.KEY));
        assertFalse(types.admits(net.kyori.adventure.key.Key.key("minecraft:fall")));

        MechanicsProfile.Builder allBut = MechanicsProfile.builder();
        PathEdits.apply(allBut, base(), "damage/enabledTypes", "except(minecraft:fall)");
        var kept = allBut.build().get(MechanicsKeys.DAMAGE).enabledTypes.constantOrNull();
        assertFalse(kept.admits(net.kyori.adventure.key.Key.key("minecraft:fall")));
        assertTrue(kept.admits(io.github.term4.polyp.mechanics.damage.types.melee.MeleeDamage.KEY));
    }

    /**
     * Data SELECTS a code-defined behaviour by name - it never authors one. The item, its eat time and its
     * edibility gate all stay; only what it dishes out is swapped.
     */
    @Test
    void aConsumablePointsAtANamedBehaviour() {
        ConsumableBehavior custom = new ConsumableBehavior() {};
        FieldFns.register(ConsumableBehavior.class, "test-apple", "a stand-in", args -> custom);

        MechanicsProfile.Builder b = MechanicsProfile.builder();
        PathEdits.apply(b, MechanicsProfile.builder()
                        .set(MechanicsKeys.CONSUMABLES, io.github.term4.polyp.presets.vanilla18.Consumables.config())
                        .build(),
                "consumables/minecraft:golden_apple/behavior", "test-apple");

        var apple = b.build().get(MechanicsKeys.CONSUMABLES)
                .typeConfig(net.kyori.adventure.key.Key.key("minecraft:golden_apple"));
        assertNotNull(apple);
        assertEquals(custom, apple.behavior.constantOrNull(), "the named behaviour, not a rebuilt one");
        assertNotNull(apple.canConsume, "the preset's 1.8 edibility gate rides along");
    }

    /** The dry run a server can sweep its rulesets with at boot - same checks, nothing written. */
    @Test
    void validateReportsTheReasonWithoutApplying() {
        assertNull(PathEdits.validate(base(), "projectiles/minecraft:arrow/critDamage", "false"));
        String bad = PathEdits.validate(base(), "consumables/minecraft:golden_apple/behavior", "heel-apple");
        assertNotNull(bad);
        assertTrue(bad.contains("no ConsumableBehavior named"), bad);
    }

    @Test
    void everythingInvalidThrowsWithTheReason() {
        MechanicsProfile.Builder b = MechanicsProfile.builder();
        MechanicsProfile base = base();
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> PathEdits.apply(b, base, "nosuchmember/enabled", "false")).getMessage().contains("unknown member"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> PathEdits.apply(b, base, "projectiles/minecraft:arrow/noSuchKnob", "1")).getMessage().contains("unknown knob"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> PathEdits.apply(b, base, "projectiles/minecraft:arrow/critDamage", "maybe")).getMessage().contains("not a Boolean"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> PathEdits.apply(b, base, "projectiles", "1")).getMessage().contains("member/knob"));
        // a nullary factory handed arguments is a typo, not a no-op
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> PathEdits.apply(b, base, "explosion/damageModel", "curve(9)"))
                .getMessage().contains("takes no arguments"));
    }
}
