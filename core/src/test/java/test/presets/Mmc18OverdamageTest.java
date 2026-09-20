package test.presets;

import io.github.term4.polyp.api.event.damage.FatalDamageEvent;
import io.github.term4.polyp.mechanics.attribute.catalog.enchant.Sharpness;
import io.github.term4.polyp.mechanics.damage.DamageSystem;
import io.github.term4.polyp.mechanics.damage.types.melee.MeleeDamage;
import io.github.term4.polyp.presets.Preset;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.event.EventNode;
import net.minestom.server.instance.Instance;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.tag.Tag;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MineMen's overdamage rule: inside the i-frame window a CRIT with the same item as the opening hit never replaces
 * it - the extra is the crit roll's. Anything else that exceeds the opening hit still lands, a stronger weapon or the
 * same material carrying more. A blocked replacement is no kill: the fatal seam never fires for it.
 */
class Mmc18OverdamageTest extends HeadlessServerTest {

    private static final AtomicInteger fatal = new AtomicInteger();
    private static final java.util.concurrent.atomic.AtomicReference<Float> dealt = new java.util.concurrent.atomic.AtomicReference<>(0f);
    // a global listener that cancels fatal hits outlives this class on the shared server: scope it to our victims
    private static final Tag<Boolean> MINE = Tag.Transient("test:mmc18-overdamage-victim");
    private static final EventNode<net.minestom.server.event.Event> node = EventNode.all("mmc18-overdamage");

    @BeforeAll
    static void countFatal() {
        node.addListener(FatalDamageEvent.class, e -> {
            if (!Boolean.TRUE.equals(e.target().getTag(MINE))) return;
            fatal.incrementAndGet();
            e.setCancelled(true);
        });
        node.addListener(io.github.term4.polyp.api.event.damage.DamageAppliedEvent.class, e -> {
            if (Boolean.TRUE.equals(e.target().getTag(MINE))) dealt.set(e.dealt());
        });
        MinecraftServer.getGlobalEventHandler().addChild(node);
    }

    @AfterAll
    static void dropListener() {
        MinecraftServer.getGlobalEventHandler().removeChild(node);
    }

    private static LivingEntity mob(Instance inst, double x) {
        LivingEntity mob = new LivingEntity(EntityType.ZOMBIE);
        mob.setInstance(inst, new Pos(x, 65, 100.5)).join();
        mob.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(1.0); // a player's base, so the sword numbers are a player's
        mob.setHealth(20f);
        mob.setTag(MINE, true);
        return mob;
    }

    /** Opens a window with a plain wooden sword, drops the victim to 0.1 hp, then lands {@code second} inside it: the outcome and whether it was fatal. */
    private static Object[] openThen(ItemStack second, boolean critical) {
        return openThen(second, critical, a -> {});
    }

    /** {@code between} runs on the attacker after the opening hit: a potion landing or wearing off inside the window. */
    private static Object[] openThen(ItemStack second, boolean critical, java.util.function.Consumer<LivingEntity> between) {
        Instance inst = flatInstance(Preset.MMC18.profile());
        LivingEntity attacker = mob(inst, 100.5);
        LivingEntity victim = mob(inst, 102.5);
        try {
            fatal.set(0);
            dealt.set(0f);
            DamageSystem damage = services.damage();
            DamageSystem.DamageOutcome first = damage.apply(MeleeDamage.INSTANCE.snapshot(attacker, victim, false, ItemStack.of(Material.WOODEN_SWORD), services));
            assertEquals(DamageSystem.DamageOutcome.FRESH_DAMAGE, first, "the wooden sword opens the window");
            victim.setHealth(0.1f); // the window's highwater and opening item stay; any landed delta is now lethal
            between.accept(attacker);
            DamageSystem.DamageOutcome outcome = damage.apply(MeleeDamage.INSTANCE.snapshot(attacker, victim, critical, second, services));
            return new Object[]{outcome, fatal.get(), victim.getHealth(), dealt.get()};
        } finally {
            attacker.remove();
            victim.remove();
        }
    }

    @Test
    void sameSwordCritIsNoKill() {
        Object[] r = openThen(ItemStack.of(Material.WOODEN_SWORD), true);
        assertEquals(DamageSystem.DamageOutcome.BLOCKED, r[0], "the crit's 6 over the opening 4 is the roll's, not the weapon's");
        assertEquals(0, r[1], "no fatal seam for a replacement that never lands");
        assertEquals(0.1f, (Float) r[2], 1e-4);
    }

    @Test
    void strongerSwordStillReplaces() {
        Object[] r = openThen(ItemStack.of(Material.STONE_SWORD), false);
        assertEquals(DamageSystem.DamageOutcome.OVERDAMAGE, r[0], "stone's 5 over wood's 4 is the weapon's");
        assertEquals(1, r[1], "and it kills");
    }

    /** Strength landed inside the window: the crit lands exactly the strengthened swing's excess, never the roll's. */
    @Test
    void strengthGainedLandsItsShare() {
        java.util.function.Consumer<LivingEntity> strength = a -> a.addEffect(new net.minestom.server.potion.Potion(
                net.minestom.server.potion.PotionEffect.STRENGTH, 0, 200, (byte) 0));
        Object[] plain = openThen(ItemStack.of(Material.WOODEN_SWORD), false, strength);
        Object[] crit = openThen(ItemStack.of(Material.WOODEN_SWORD), true, strength);
        assertEquals(DamageSystem.DamageOutcome.OVERDAMAGE, plain[0], "Strength alone exceeds the opening hit");
        assertEquals((Float) plain[3], (Float) crit[3], 1e-4, "the crit lands what the strengthened swing lands");
    }

    /** Weakness landed inside the window: the crit alone would exceed the opening hit, un-critted it does not. */
    @Test
    void weakenedCritLandsNothing() {
        Object[] crit = openThen(ItemStack.of(Material.WOODEN_SWORD), true, a -> a.addEffect(new net.minestom.server.potion.Potion(
                net.minestom.server.potion.PotionEffect.WEAKNESS, 0, 200, (byte) 0)));
        assertEquals(DamageSystem.DamageOutcome.BLOCKED, crit[0], "un-critted, the weakened swing is under the opening hit");
        assertEquals(0, crit[1]);
    }

    /** The sword gained Sharpness since the opening hit: a crit with it lands the Sharpness, never the roll. */
    @Test
    void sameSwordCritLandsOnlyTheWeaponsExtra() {
        ItemStack sharp = enchanted(Material.WOODEN_SWORD, Sharpness.KEY, 5);
        Object[] plain = openThen(sharp, false);
        Object[] crit = openThen(sharp, true);
        assertEquals(DamageSystem.DamageOutcome.OVERDAMAGE, crit[0], "the Sharpness exceeds the opening hit even without the roll");
        assertEquals((Float) plain[3], (Float) crit[3], 1e-4, "the crit lands exactly what the un-critted swing would");
        assertEquals(1, crit[1], "and at 0.1 hp that kills");
    }

    @Test
    void sameMaterialWithMoreStillReplaces() {
        Object[] r = openThen(enchanted(Material.WOODEN_SWORD, Sharpness.KEY, 5), false);
        assertEquals(DamageSystem.DamageOutcome.OVERDAMAGE, r[0], "the extra is Sharpness, not a crit");
        assertEquals(1, r[1]);
    }
}
