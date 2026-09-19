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
    void typedEditKeepsTheRest() {
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
    void editsStackOnTheBuilder() {
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
    void flatEditWithoutBase() {
        MechanicsProfile.Builder b = MechanicsProfile.builder();
        PathEdits.apply(b, null, "explosion/damageModel", "scale(0.5, curve)");
        ExplosionConfig cfg = b.build().get(MechanicsKeys.EXPLOSION);
        assertEquals(3.75f, cfg.damageModel.constantOrNull().amount(new DamageModel.Hit(dummy(), null, 2.0, 1.0f, 7.5f)), 1e-4,
                "a scaled curve is a model, not a second knob");
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
        assertEquals(7.5f, curved.amount(new DamageModel.Hit(dummy(), null, 2.0, 1.0f, 7.5f)), 1e-4);

        // and any flat amount, not just the one someone predeclared
        MechanicsProfile.Builder toFlat = MechanicsProfile.builder();
        PathEdits.apply(toFlat, hypixelish, "explosion/damageModel", "flat(3.5)");
        DamageModel flat = toFlat.build().get(MechanicsKeys.EXPLOSION).damageModel.constantOrNull();
        assertNotNull(flat);
        assertEquals(3.5f, flat.amount(new DamageModel.Hit(dummy(), null, 2.0, 1.0f, 7.5f)), 1e-4);
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
                "members", "watchers", "block-viewers", "instance", "server", "viewers", "source", "both()",
                // compositions - none of these is a registered case
                "except(instance, watchers)", "both(members, viewers)", "both(members, viewers, source)", "only(instance, members)",
                "tree(members)", "at-listener(tree(watchers))", "within(20, except(watchers, source))",
                "at-listener(server)", "at-listener(watchers)", "predicted-for(viewers)", "predicted-for(block-viewers)")) {
            for (String effect : java.util.List.of("sound(entity.player.teleport, player, 1, 1)",
                    "particle(crit, 8, 0.5, 0)", "animation(SWING_MAIN_ARM)")) {
                String spec = "to(" + audience + ", " + effect + ")";
                MechanicsProfile.Builder b = MechanicsProfile.builder();
                PathEdits.apply(b, null, "fx/polyp:pearl_teleport", spec);
                assertNotNull(b.build().get(MechanicsKeys.FX).get(Fx.PEARL_TELEPORT), spec);
            }
        }
        // a bare effect keeps the watchers audience
        MechanicsProfile.Builder bare = MechanicsProfile.builder();
        PathEdits.apply(bare, null, "fx/polyp:pearl_teleport", "sound(entity.player.teleport, player, 1, 1)");
        assertNotNull(bare.build().get(MechanicsKeys.FX).get(Fx.PEARL_TELEPORT));
    }

    @Test
    void unknownFactoryLists() {
        MechanicsProfile.Builder b = MechanicsProfile.builder();
        String message = assertThrows(IllegalArgumentException.class,
                () -> PathEdits.apply(b, null, "fx/polyp:pearl_teleport", "sideways")).getMessage();
        assertTrue(message.contains("to") && message.contains("none"), message);
        String audience = assertThrows(IllegalArgumentException.class,
                () -> PathEdits.apply(b, null, "fx/polyp:pearl_teleport", "to(elsewhere, sound(entity.player.teleport, player, 1, 1))")).getMessage();
        assertTrue(audience.contains("no Recipients named"), audience);
    }

    /** The members that were once plain values: one table kind, and an edit keeps every other field. */
    @Test
    void hungerAndVriArePathAddressable() {
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
        assertEquals(Boolean.FALSE, vri.itemDrop.constantOrNull(), "the edited knob");
        assertEquals(Boolean.TRUE, vri.itemPickup.constantOrNull(), "everything else survives the edit");
        assertEquals(Boolean.TRUE, vri.blockBreakProgress.constantOrNull());

        var hunger = out.get(MechanicsKeys.HUNGER);
        assertEquals(Boolean.FALSE, hunger.enabled.constantOrNull());
        assertEquals(Boolean.FALSE, hunger.naturalRegen.constantOrNull());
        assertEquals(80, hunger.regenInterval.constantOrNull(), "an untouched knob keeps the base value");
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

    /** A mutation edits what the base admits instead of restating it - the shape every list-like knob can take. */
    @Test
    void mutationsEditTheInheritedSet() {
        MechanicsProfile.Builder b = MechanicsProfile.builder();
        PathEdits.apply(b, base(), "damage/enabledTypes", "only(minecraft:player_attack, minecraft:fall)");
        PathEdits.apply(b, base(), "damage/enabledTypes", "without(minecraft:fall)");
        var narrowed = b.build().get(MechanicsKeys.DAMAGE).enabledTypes.constantOrNull();
        assertNotNull(narrowed);
        assertTrue(narrowed.admits(io.github.term4.polyp.mechanics.damage.types.melee.MeleeDamage.KEY), "the rest of the selection stands");
        assertFalse(narrowed.admits(net.kyori.adventure.key.Key.key("minecraft:fall")));
        assertFalse(narrowed.admits(io.github.term4.polyp.mechanics.damage.types.projectile.ProjectileDamage.KEY), "never selected, still not");

        PathEdits.apply(b, base(), "damage/enabledTypes", "with(minecraft:thrown)");
        var widened = b.build().get(MechanicsKeys.DAMAGE).enabledTypes.constantOrNull();
        assertTrue(widened.admits(io.github.term4.polyp.mechanics.damage.types.projectile.ProjectileDamage.KEY));
        assertTrue(widened.admits(io.github.term4.polyp.mechanics.damage.types.melee.MeleeDamage.KEY));
        assertFalse(widened.admits(net.kyori.adventure.key.Key.key("minecraft:fall")), "the earlier edit holds");
    }

    /** Over a base that says nothing the inherited set is everything, so a mutation reads like except()/only(). */
    @Test
    void aMutationOverNothingEditsEverything() {
        MechanicsProfile.Builder b = MechanicsProfile.builder();
        PathEdits.apply(b, MechanicsProfile.builder().build(), "damage/enabledTypes", "without(minecraft:fall)");
        var types = b.build().get(MechanicsKeys.DAMAGE).enabledTypes.constantOrNull();
        assertNotNull(types);
        assertTrue(types.admits(io.github.term4.polyp.mechanics.damage.types.melee.MeleeDamage.KEY));
        assertFalse(types.admits(net.kyori.adventure.key.Key.key("minecraft:fall")));
        // the vocabulary names the edit forms beside the factories, marked as edits
        assertTrue(FieldFns.vocabulary(KeySet.class).stream().anyMatch(line -> line.startsWith("without(key...)") && line.contains("inherited")));
        // a mutation is not a value on its own
        assertThrows(IllegalArgumentException.class, () -> FieldFns.parse(KeySet.class, "without(minecraft:fall)", "test"));
    }

    /**
     * Data SELECTS a code-defined behaviour by name - it never authors one. The item, its eat time and its
     * edibility gate all stay; only what it dishes out is swapped.
     */
    @Test
    void aConsumablePointsAtANamedBehaviour() {
        ConsumableBehavior custom = new ConsumableBehavior() {};
        FieldFns.register(ConsumableBehavior.class, "test-apple", "a stand-in", args -> custom);
        try {
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
        } finally {
            FieldFns.unregister(ConsumableBehavior.class, "test-apple");
        }
    }

    /** The dry run a server can sweep its rulesets with at boot - same checks, nothing written. */
    @Test
    void validateReportsTheReasonWithoutApplying() {
        assertNull(PathEdits.validate(base(), "projectiles/minecraft:arrow/critDamage", "false"));
        String slash = PathEdits.validate(base(), "/hunger/enabled", "false");
        assertNotNull(slash);
        assertTrue(slash.contains("unknown member"), slash);
        String bad = PathEdits.validate(base(), "consumables/minecraft:golden_apple/behavior", "heel-apple");
        assertNotNull(bad);
        assertTrue(bad.contains("no ConsumableBehavior named"), bad);
    }

    /** The members that used to be code-only: every profile member now takes a path. */
    @Test
    void everyRemainingMemberIsAddressable() {
        MechanicsProfile base = MechanicsProfile.builder()
                .set(MechanicsKeys.DURABILITY, io.github.term4.polyp.mechanics.durability.DurabilityConfig.builder().enabled(true).build())
                .set(MechanicsKeys.COOLDOWNS, io.github.term4.polyp.mechanics.cooldown.CooldownConfig.builder()
                        .cooldown(net.minestom.server.item.Material.CHORUS_FRUIT, 20).build())
                .set(MechanicsKeys.TICK_SCALING, io.github.term4.polyp.util.tick.TickScalingConfig.builder().clientTps(20).build())
                .set(MechanicsKeys.ITEMS, io.github.term4.polyp.presets.vanilla18.Items.registry())
                .set(MechanicsKeys.FIXES, io.github.term4.polyp.platform.fixes.FixesConfig.builder()
                        .legacyConsume(io.github.term4.polyp.platform.fixes.FixToggleConfig.of(false)).build())
                .set(MechanicsKeys.BLOCKING, io.github.term4.polyp.mechanics.blocking.BlockingConfig.builder()
                        .materials(net.minestom.server.item.Material.IRON_SWORD).build())
                .build();
        MechanicsProfile.Builder b = MechanicsProfile.builder();
        PathEdits.apply(b, base, "durability/enabled", "false");
        PathEdits.apply(b, base, "cooldowns/minecraft:ender_pearl", "15");
        PathEdits.apply(b, base, "tick-scaling/referenceTps", "40");
        PathEdits.apply(b, base, "items/minecraft:iron_sword/attack_damage", "9");
        PathEdits.apply(b, base, "fixes/legacyConsume/enabled", "true");
        PathEdits.apply(b, base, "visuals/legacyArrowVisibility/deflectParticles", "true");
        PathEdits.apply(b, base, "item-physics", "modern");
        PathEdits.apply(b, base, "velocity", "simulated");
        String blockingKnob = io.github.term4.polyp.mechanics.blocking.BlockingTypeConfigBuilderBase.KNOBS.values().stream()
                .filter(k -> k.valueType() == Boolean.class).map(ConfigKnob::name).findFirst().orElseThrow();
        PathEdits.apply(b, base, "blocking/minecraft:iron_sword/" + blockingKnob, "true");
        PathEdits.apply(b, base, "blocking/" + blockingKnob, "false"); // the family's defaults entry
        MechanicsProfile out = b.build();

        assertEquals(Boolean.FALSE, out.get(MechanicsKeys.DURABILITY).enabled.constantOrNull());
        var cooldowns = out.get(MechanicsKeys.COOLDOWNS);
        assertEquals(15, cooldowns.ticks(net.minestom.server.item.Material.ENDER_PEARL));
        assertEquals(20, cooldowns.ticks(net.minestom.server.item.Material.CHORUS_FRUIT), "the inherited entry survives");
        var scaling = out.get(MechanicsKeys.TICK_SCALING);
        assertEquals(40, scaling.referenceTps());
        assertEquals(20, scaling.clientTps(), "the other knob survives");
        var items = out.get(MechanicsKeys.ITEMS);
        // the path sets the item's modifier; a read adds a player's base
        assertEquals(10.0, items.value(net.minestom.server.item.ItemStack.of(net.minestom.server.item.Material.IRON_SWORD), null,
                io.github.term4.polyp.item.ItemStat.ATTACK_DAMAGE, -1));
        assertTrue(items.value(net.minestom.server.item.ItemStack.of(net.minestom.server.item.Material.DIAMOND_SWORD), null,
                io.github.term4.polyp.item.ItemStat.ATTACK_DAMAGE, -1) > 0, "the rest of the registry survives");
        var fixes = out.get(MechanicsKeys.FIXES);
        assertTrue(fixes.legacyConsume().enabled(null));
        assertTrue(fixes.visuals().legacyArrowVisibility().deflectParticles(null));
        assertEquals(io.github.term4.polyp.entity.DroppedItemEntity.Model.MODERN, out.get(MechanicsKeys.ITEM_PHYSICS));
        assertNotNull(out.get(MechanicsKeys.VELOCITY).reconstructionConfig(), "a simulated rule, knobs addressable");
        var blocking = out.get(MechanicsKeys.BLOCKING);
        assertNotNull(blocking.typeConfig(net.minestom.server.item.Material.IRON_SWORD));
        assertNotNull(blocking.defaults(), "blocking/<knob> edited the defaults entry");
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

    private static java.util.Map<String, PathEdits.Step> browse(MechanicsProfile profile, String path) {
        java.util.Map<String, PathEdits.Step> byName = new java.util.LinkedHashMap<>();
        for (PathEdits.Step step : PathEdits.browse(path, profile::get)) byName.put(step.name(), step);
        return byName;
    }

    @Test
    void browsingWalksTheTree() {
        MechanicsProfile base = base();
        assertTrue(browse(base, "").keySet().containsAll(java.util.List.of("attack", "projectiles", "velocity")));
        assertTrue(browse(base, "").get("projectiles").more());

        var member = browse(base, "projectiles");
        assertTrue(member.get("minecraft:arrow").more(), "a configured entry is a way down");
        assertNotNull(member.get("speed").value(), "the defaults entry answers the two-part form");
        assertEquals("HitResponse (hit|pass_through|deflect|destroy)", member.get("selfHit").type());

        var entry = browse(base, "projectiles/minecraft:arrow");
        assertNotNull(entry.get("damage").value());
        assertEquals(java.util.List.of("damage"), browse(base, "projectiles/minecraft:arrow/damage").keySet().stream().toList());
    }

    @Test
    void browsingWithoutAProfile() {
        var member = PathEdits.browse("projectiles", key -> null);
        assertFalse(member.isEmpty());
        assertTrue(member.stream().allMatch(step -> step.value() == null), "nothing to read a value off");
        assertTrue(member.stream().noneMatch(PathEdits.Step::more), "and no entries to descend into");
    }

    @Test
    void browseRefusesUnknownPaths() {
        MechanicsProfile base = base();
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> PathEdits.browse("nosuchmember", base::get)).getMessage().contains("unknown member"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> PathEdits.browse("attack/noSuchKnob", base::get)).getMessage().contains("unknown knob"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> PathEdits.browse("projectiles/minecraft:arrow/damage/deeper", base::get)).getMessage().contains("below"));
    }
}
