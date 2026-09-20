package io.github.term4.polyp.mechanics.explosion;

import io.github.term4.polyp.mechanics.explosion.ExplosionConfigResolver.ExplosionContext;
import io.github.term4.polyp.world.MechanicsWorld;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleUnaryOperator;

/**
 * What an explosion does to blocks; absent from an {@link ExplosionConfig} = it breaks none. Reach is physics
 * ({@link Model}, {@link Resistance}, the ray knobs), breaking is policy ({@link BreakRule}; the config's
 * {@code breakRule} ANDs on top), {@link Interaction} is the consequence.
 *
 * <p>{@link Resistance} and {@link BreakRule} see the exploding entity via {@link ExplosionContext#source()},
 * so one config can break different blocks per source (a BedWars fireball eating wood but not end stone,
 * while TNT in the same world eats both).
 */
public final class BlockBreaking {

    /** How the destroyed set is chosen. Both rays are vanilla's 16³ shell; they differ only in what resists. */
    /** Which cells the blast reaches - the search itself. */
    @FunctionalInterface
    public interface Model {

        @NotNull List<Point> select(@NotNull MechanicsWorld world, @NotNull Point center, float power,
                                    @NotNull BlockBreaking cfg, @NotNull ExplosionContext ctx);

        /** 1.8 rays: fluids do not resist, no world-bounds stop. */
        Model RAY_1_8 = (world, center, power, cfg, ctx) -> ExplosionBlocks.rays(world, center, power, cfg, ctx, false);
        /** Modern rays: fluids resist like stone, the ray stops at the world's floor and ceiling. */
        Model RAY_MODERN = (world, center, power, cfg, ctx) -> ExplosionBlocks.rays(world, center, power, cfg, ctx, true);
        /** Every breakable cell within {@code power} blocks - no rays, no shadowing. */
        Model SPHERE = ExplosionBlocks::sphere;
    }

    /** How a ray pays for the cells it crosses. */
    @FunctionalInterface
    public interface Charging {

        /** The ray's intensity after a cell costing {@code cost}; {@code newCell} on the first sample in it. {@link Float#NaN} stops the ray. */
        float charge(float intensity, float cost, boolean newCell);

        /** Every sample pays (vanilla: ~3.3 samples per cell). */
        Charging PER_STEP = (intensity, cost, newCell) -> intensity - cost;
        /** Each cell pays once, however many samples land in it. */
        Charging PER_BLOCK = (intensity, cost, newCell) -> newCell ? intensity - cost : intensity;
        /** A gate, not a cost: a cell dearer than the ray's intensity stops it and shields what is behind. */
        Charging THRESHOLD = (intensity, cost, newCell) -> newCell && cost > intensity ? Float.NaN : intensity;
    }

    /** What a blast-proof block does to the cells behind it. */
    @FunctionalInterface
    public interface Shielding {

        @NotNull List<Point> apply(@NotNull List<Point> hit, @NotNull MechanicsWorld world, @NotNull Point center,
                                   float power, @NotNull BlockBreaking cfg, @NotNull ExplosionContext ctx);

        /** The rays alone decide. */
        Shielding NONE = (hit, world, center, power, cfg, ctx) -> hit;
        /** Hard shadow (Hypixel): a selected cell is dropped when the line from the blast center crosses a blast-proof block. */
        Shielding OCCLUSION = ExplosionBlocks::occlude;
    }

    /** What happens to a selected block. */
    public interface Interaction {

        boolean destroys();

        /** The stacks that land, given the block's vanilla drops. */
        @NotNull List<ItemStack> drops(@NotNull List<ItemStack> vanilla, float power, @NotNull ThreadLocalRandom rnd);

        Interaction KEEP = of(false, (vanilla, power, rnd) -> List.of());
        Interaction DESTROY_NO_DROPS = of(true, (vanilla, power, rnd) -> List.of());
        /** Vanilla: each item survives at {@code 1/power}, rolled per item rather than per stack. */
        Interaction DESTROY_WITH_DECAY = of(true, BlockBreaking::decayed);
        Interaction DESTROY_WITH_DROPS = of(true, (vanilla, power, rnd) -> vanilla);

        @FunctionalInterface
        interface Drops {
            @NotNull List<ItemStack> of(@NotNull List<ItemStack> vanilla, float power, @NotNull ThreadLocalRandom rnd);
        }

        static @NotNull Interaction of(boolean destroys, @NotNull Drops drops) {
            return new Interaction() {
                @Override public boolean destroys() { return destroys; }
                @Override public @NotNull List<ItemStack> drops(@NotNull List<ItemStack> vanilla, float power, @NotNull ThreadLocalRandom rnd) {
                    return drops.of(vanilla, power, rnd);
                }
            };
        }
    }

    static @NotNull List<ItemStack> decayed(@NotNull List<ItemStack> vanilla, float power, @NotNull ThreadLocalRandom rnd) {
        List<ItemStack> out = new java.util.ArrayList<>(vanilla.size());
        for (ItemStack stack : vanilla) {
            int kept = survivors(stack.amount(), power, rnd);
            if (kept > 0) out.add(stack.withAmount(kept));
        }
        return out;
    }

    static int survivors(int amount, float power, ThreadLocalRandom rnd) {
        float chance = 1.0F / power;
        int kept = 0;
        for (int i = 0; i < amount; i++) if (rnd.nextFloat() <= chance) kept++;
        return kept;
    }

    @FunctionalInterface
    public interface Resistance {
        double of(@NotNull Block block, @NotNull ExplosionContext ctx);
    }

    /**
     * Final say once the ray has already reached {@code pos} with power to spare. It does NOT affect propagation
     * (resistance governs what a blast punches THROUGH), and under {@link Shielding#OCCLUSION} a vetoed block casts
     * the hard shadow.
     */
    @FunctionalInterface
    public interface BreakRule {
        boolean canBreak(@NotNull Block block, @NotNull Point pos, @NotNull ExplosionContext ctx);

        /** Vetoes {@code blocks}, matched by type id, any state (Hypixel's blast-proof glass shape). */
        static BreakRule neverBreaks(@NotNull Set<Block> blocks) {
            boolean[] mask = idMask(blocks);
            return (block, pos, ctx) -> !masked(mask, block);
        }
    }

    /** Registry blast resistance - the modern value, correct for every block that still exists. */
    public static final Resistance VANILLA_RESISTANCE = (block, ctx) -> block.explosionResistance();

    // the only blocks whose blast resistance actually changed since 1.8 (148 of 155 match - see
    // docs/HANDOFF-explosion-block-breaking.md). moving_piston is not a typo: 1.8's c(-1.0F) never raises
    // durability, leaving it unbreakable by tools yet free to explosions
    private static final double[] LEGACY_OVERRIDES = overrides(Map.of(
            Block.PISTON, 0.5, Block.STICKY_PISTON, 0.5,
            Block.PISTON_HEAD, 0.5, Block.MOVING_PISTON, 0.0));

    /** 1.8 blast resistance: {@link #VANILLA_RESISTANCE} plus the piston overrides. */
    public static final Resistance LEGACY_RESISTANCE = (block, ctx) -> {
        int id = block.id();
        double override = id < LEGACY_OVERRIDES.length ? LEGACY_OVERRIDES[id] : Double.NaN;
        return Double.isNaN(override) ? VANILLA_RESISTANCE.of(block, ctx) : override;
    };

    /** Block-id indexed, {@code NaN} = none. Every sampled cell probes this, so it must not hash a key string. */
    private static double[] overrides(Map<Block, Double> byBlock) {
        double[] out = new double[byBlock.keySet().stream().mapToInt(Block::id).max().orElse(0) + 1];
        Arrays.fill(out, Double.NaN);
        byBlock.forEach((block, value) -> out[block.id()] = value);
        return out;
    }

    /** Membership by block TYPE id (all states), the same reason: an array probe, not a key hash. */
    private static boolean[] idMask(Set<Block> blocks) {
        boolean[] mask = new boolean[blocks.stream().mapToInt(Block::id).max().orElse(-1) + 1];
        for (Block block : blocks) mask[block.id()] = true;
        return mask;
    }

    private static boolean masked(boolean[] mask, Block block) {
        int id = block.id();
        return id < mask.length && mask[id];
    }

    private static final BreakRule ANY = (block, pos, ctx) -> true;

    private final Model model;
    private final Interaction interaction;
    private final Resistance resistance;
    private final BreakRule breakRule;
    private final Charging charging;
    private final Shielding shielding;
    private final int rayGrid;
    private final DoubleUnaryOperator charge;
    private final double rollMin, rollMax;
    private final boolean rollPerHeading;
    private final float[] intensityTable;
    private final float tableMax;
    private final double originLift;
    private final double intensityNoise;
    private final boolean tntChain;

    private BlockBreaking(Builder b) {
        this.model = b.model;
        this.interaction = b.interaction;
        this.resistance = b.resistance;
        this.breakRule = b.breakRule;
        this.charging = b.charging;
        this.shielding = b.shielding;
        this.rayGrid = b.rayGrid;
        this.charge = b.charge;
        this.rollMin = b.rollMin;
        this.rollMax = b.rollMax;
        this.rollPerHeading = b.rollPerHeading;
        this.intensityTable = b.intensityTable;
        this.originLift = b.originLift;
        this.intensityNoise = b.intensityNoise;
        this.tntChain = b.tntChain;
        float max = 0;
        if (intensityTable != null) {
            int shell = rayGrid * rayGrid * rayGrid - (rayGrid - 2) * (rayGrid - 2) * (rayGrid - 2);
            if (intensityTable.length != shell)
                throw new IllegalArgumentException("intensityTable length " + intensityTable.length
                        + " != " + shell + " rays of a " + rayGrid + " grid");
            for (float v : intensityTable) max = Math.max(max, v);
        }
        this.tableMax = max;
    }

    public @NotNull Model model() { return model; }
    public @NotNull Interaction interaction() { return interaction; }
    @NotNull Charging charging() { return charging; }
    @NotNull Shielding shielding() { return shielding; }
    int rayGrid() { return rayGrid; }
    double charge(double resistance) { return charge.applyAsDouble(resistance); }
    double rollIntensity(float power, ThreadLocalRandom rnd) {
        return power * (rollMin == rollMax ? rollMin : rollMin + rnd.nextDouble() * (rollMax - rollMin));
    }
    boolean rollPerHeading() { return rollPerHeading; }
    float @Nullable [] intensityTable() { return intensityTable; }
    double originLift() { return originLift; }
    double intensityNoise() { return intensityNoise; }
    boolean tntChain() { return tntChain; }
    /** Hottest possible launch intensity - bounds any reach-derived scan (seals). */
    double maxIntensity(float power) {
        return (intensityTable != null ? tableMax : power * rollMax) + intensityNoise;
    }

    double resistance(@NotNull Block block, @NotNull ExplosionContext ctx) { return resistance.of(block, ctx); }

    boolean canBreak(@NotNull Block block, @NotNull Point pos, @NotNull ExplosionContext ctx) {
        return breakRule.canBreak(block, pos, ctx);
    }

    /** The block's own item, 1x; blocks with no item form drop nothing. Loot beyond this is the app's {@link BreakRule} job. */
    static @NotNull List<ItemStack> dropsOf(@NotNull Block block) {
        Material material = Material.fromKey(block.key());
        return material == null ? List.of() : List.of(ItemStack.of(material));
    }

    public static @NotNull Builder builder() { return new Builder(); }

    public @NotNull Builder toBuilder() { return new Builder(this); }

    public static final class Builder {
        private Model model = Model.RAY_MODERN;
        private Interaction interaction = Interaction.DESTROY_WITH_DECAY;
        private Resistance resistance = VANILLA_RESISTANCE;
        private BreakRule breakRule = ANY;
        private Charging charging = Charging.PER_STEP;
        private Shielding shielding = Shielding.NONE;
        private int rayGrid = 16;
        private DoubleUnaryOperator charge = r -> (r + 0.3) * 0.3;
        private double rollMin = 0.7, rollMax = 1.3;
        private boolean rollPerHeading;
        private float[] intensityTable;
        private double originLift;
        private double intensityNoise;
        private boolean tntChain;

        private Builder() {}

        private Builder(BlockBreaking c) {
            model = c.model;
            interaction = c.interaction;
            resistance = c.resistance;
            breakRule = c.breakRule;
            charging = c.charging;
            shielding = c.shielding;
            rayGrid = c.rayGrid;
            charge = c.charge;
            rollMin = c.rollMin;
            rollMax = c.rollMax;
            rollPerHeading = c.rollPerHeading;
            intensityTable = c.intensityTable;
            originLift = c.originLift;
            intensityNoise = c.intensityNoise;
            tntChain = c.tntChain;
        }

        public Builder model(@NotNull Model v) { this.model = v; return this; }
        public Builder interaction(@NotNull Interaction v) { this.interaction = v; return this; }
        public Builder resistance(@NotNull Resistance v) { this.resistance = v; return this; }

        /** When a ray pays resistance; default {@link Charging#PER_STEP} (vanilla). */
        public Builder charging(@NotNull Charging v) { this.charging = v; return this; }

        /** Ray lattice edge; only the shell is cast. Vanilla 16 (1352 rays); MineMen 8 (296 - sparser rim, softer edge). */
        public Builder rayGrid(int v) { this.rayGrid = v; return this; }

        /** Intensity a ray pays for (or must beat, under {@link Charging#THRESHOLD}) a block, from its resistance;
         *  default vanilla {@code (r+0.3)*0.3} (MineMen TNT {@code r*0.0775}; its fireball the fitted gate law). */
        public Builder charge(@NotNull DoubleUnaryOperator v) { this.charge = v; return this; }

        /** Per-ray intensity roll bounds (x power); vanilla {@code 0.7, 1.3} (default), equal bounds = deterministic. */
        public Builder intensityRoll(double min, double max) { this.rollMin = min; this.rollMax = max; return this; }

        /** One roll per horizontal heading instead of per ray (the vertical fan moves together) - a coherent rim
         *  wiggle instead of per-ray salt-and-pepper (MineMen fireball). */
        public Builder rollPerHeading(boolean v) { this.rollPerHeading = v; return this; }

        /** Frozen per-ray launch intensities in lattice order (x-y-z shell walk), replacing power x roll entirely:
         *  the same blast at the same sub-block phase breaks the same cells. Length must match the {@link #rayGrid} shell. */
        public Builder intensityTable(float @Nullable [] v) { this.intensityTable = v; return this; }

        /** Raises the ray origin above the blast center for BLOCK selection only (KB/damage/packet unaffected);
         *  MineMen fireball 0.25 - their footprints shrink faster with standoff than the flat-origin geometry. */
        public Builder originLift(double v) { this.originLift = v; return this; }

        /** Explosion-destroyed TNT primes with the 1.8 short random fuse instead of dropping. Default off. */
        public Builder tntChain(boolean v) { this.tntChain = v; return this; }

        /** Per-shot, per-ray uniform {@code [-v, v]} added to the launch intensity - with an {@link #intensityTable},
         *  near-threshold rim cells flicker shot to shot while the core repeats (MineMen fireball). */
        public Builder intensityNoise(double v) { this.intensityNoise = v; return this; }

        /** How unbreakable blocks shield what is behind them; default {@link Shielding#NONE} (vanilla). */
        public Builder shielding(@NotNull Shielding v) { this.shielding = v; return this; }

        /** Whether a reached block breaks; presets encode their vetoes here ({@link BreakRule#neverBreaks}). */
        public Builder breakRule(@NotNull BreakRule v) { this.breakRule = v; return this; }

        // ExplosionConfig.breakRule folds in here
        Builder addBreakRule(@NotNull BreakRule v) {
            BreakRule base = this.breakRule;
            this.breakRule = (block, pos, ctx) -> base.canBreak(block, pos, ctx) && v.canBreak(block, pos, ctx);
            return this;
        }

        /**
         * Only these break, whatever their own resistance - the minigame shape (BedWars wool/wood). Matched by type,
         * any state. Implemented as RESISTANCE, not a veto: listed blocks offer none (so a blast tunnels through a
         * whitelisted wall) and everything else is infinite (so it shields, exactly like obsidian in BedWars).
         */
        public Builder onlyBreaks(@NotNull Set<Block> blocks) {
            boolean[] mask = idMask(blocks);
            return resistance((block, ctx) -> masked(mask, block) ? 0.0 : Double.POSITIVE_INFINITY);
        }

        public @NotNull BlockBreaking build() { return new BlockBreaking(this); }
    }
}
