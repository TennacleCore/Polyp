package io.github.term4.polyp.mechanics.fluids;

import io.github.term4.polyp.MechanicsKeys;
import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.ScopedSystem;
import io.github.term4.polyp.entity.DroppedItemEntity;
import io.github.term4.polyp.fx.Fx;
import io.github.term4.polyp.fx.FxContext;
import io.github.term4.polyp.mechanics.fluids.FluidsConfigResolver.FluidContext;
import io.github.term4.polyp.util.tick.TickPhase;
import io.github.term4.polyp.util.tick.TickSystem;
import io.github.term4.polyp.world.MechanicsWorld;
import net.kyori.adventure.key.Key;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockManager;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Water and lava that flow: 1.8's scheduled liquid ticks on each world's own clock, the rules per fluid as knobs, a
 * neighbor change waking a still fluid through Minestom's placement-rule hook, and buckets. Nothing ticks
 * randomly here (lava lights no fires).
 */
public final class FluidSystem extends ScopedSystem<FluidsConfig> {

    public static final Key KEY = Key.key("polyp:fluids");

    /** One world's pending ticks, 1.8's NextTickListEntry set: a cell waits once, however often it is asked. */
    private static final class Schedule {
        long now;
        final TreeMap<Long, LinkedHashSet<BlockVec>> due = new TreeMap<>();
        final Set<BlockVec> pending = new HashSet<>();

        synchronized void add(BlockVec pos, int delay) {
            if (!pending.add(pos)) return;
            due.computeIfAbsent(now + Math.max(1, delay), k -> new LinkedHashSet<>()).add(pos);
        }

        synchronized List<BlockVec> advance() {
            ++now;
            List<BlockVec> out = new ArrayList<>();
            while (!due.isEmpty() && due.firstKey() <= now) out.addAll(due.pollFirstEntry().getValue());
            pending.removeAll(out);
            return out;
        }
    }

    private final EventNode<@NotNull Event> node = EventNode.all("polyp:fluids");
    private final Map<MechanicsWorld, Schedule> schedules = new ConcurrentHashMap<>();
    private final Spread spread = new Spread(this);
    private final Buckets buckets = new Buckets(this);
    private TickSystem.Registration ticker;

    public FluidSystem(Polyp polyp, FluidsConfig config) {
        super(polyp, MechanicsKeys.FLUIDS, config);
        node.addListener(PlayerUseItemEvent.class, buckets::use);
    }

    public static FluidSystem install(Polyp polyp, FluidsConfig config) {
        FluidSystem system = polyp.installModule(new FluidSystem(polyp, config));
        BlockManager manager = MinecraftServer.getBlockManager();
        for (Key key : config.fluids.keySet()) {
            Block fluid = Block.fromKey(key);
            if (fluid == null) continue;
            BlockPlacementRule previous = manager.getBlockPlacementRule(fluid);
            if (previous instanceof FluidPlacementRule ours) previous = ours.previous; // a re-install
            manager.registerBlockPlacementRule(new FluidPlacementRule(fluid, previous, system));
        }
        system.ticker = TickSystem.register(TickPhase.DEFAULT, ctx -> system.tick(ctx.world()));
        return system;
    }

    @Override
    public EventNode<@NotNull Event> node() { return node; }

    @Override
    public void uninstall() {
        if (ticker != null) ticker.cancel();
        schedules.clear();
    }

    // ---- config

    public FluidsConfig configFor(MechanicsWorld world) {
        FluidsConfig scoped = polyp.profiles().resolveWorld(world, MechanicsKeys.FLUIDS);
        return scoped != null ? scoped : config();
    }

    FluidContext context(MechanicsWorld world, @Nullable BlockVec pos, @Nullable Block block) {
        return context(world, pos, block, null);
    }

    FluidContext context(MechanicsWorld world, @Nullable BlockVec pos, @Nullable Block block, @Nullable Entity actor) {
        return new FluidContext(world, pos, block, actor, services());
    }

    /** The context's fluid's knobs for its world, or null when the block there is no fluid the config knows. */
    @Nullable FluidConfig configFor(FluidContext ctx) {
        if (ctx.block() == null) return null;
        FluidsConfig cfg = configFor(ctx.world()).withOverlay(ctx);
        FluidConfig fluid = cfg.fluid(ctx.block());
        return fluid != null ? fluid.withOverlay(ctx) : null;
    }

    // ---- the clock

    private void tick(MechanicsWorld world) {
        Schedule schedule = schedules.get(world);
        if (schedule == null) return;
        for (BlockVec pos : schedule.advance()) {
            if (world.isChunkLoaded(pos)) spread.tick(world, pos);
        }
    }

    /** Ticks the fluid at {@code pos} in {@code delay} ticks, unless it already waits. */
    public void schedule(@NotNull MechanicsWorld world, @NotNull Point pos, int delay) {
        schedules.computeIfAbsent(world, w -> new Schedule()).add(pos.asBlockVec(), delay);
    }

    /** A fluid a caller set itself (1.8 onBlockAdded): it reacts, or gets its first tick. */
    public void placed(@NotNull MechanicsWorld world, @NotNull Point pos) {
        spread.placed(world, pos.asBlockVec());
    }

    /** Sets a source of {@code fluid} at {@code pos} and starts it. */
    public void place(@NotNull MechanicsWorld world, @NotNull Point pos, @NotNull Block fluid) {
        set(world, pos.asBlockVec(), fluid.defaultState());
        placed(world, pos);
    }

    /** A neighbor of the fluid at {@code pos} changed (1.8 onNeighborBlockChange): what it turns into now, or null and a tick. */
    @Nullable Block changed(MechanicsWorld world, BlockVec pos, Block block) {
        FluidContext ctx = context(world, pos, block);
        FluidConfig cfg = configFor(ctx);
        if (cfg == null || !io.github.term4.polyp.config.FieldValue.resolve(cfg.updates, ctx, true)) return null;
        Block mixed = spread.mixedInto(world, pos, block, cfg, ctx);
        if (mixed != null) {
            mixed(world, pos);
            return mixed;
        }
        schedule(world, pos, io.github.term4.polyp.config.FieldValue.resolve(cfg.tickRate, ctx, 5));
        return null;
    }

    // ---- the world

    void set(MechanicsWorld world, BlockVec pos, Block block) {
        world.setBlock(pos, block);
        world.applyPhysics(pos);
    }

    /** 1.8 triggerMixEffects at {@code pos}. */
    void mixed(MechanicsWorld world, BlockVec pos) {
        Fx.play(services(), Fx.FLUID_MIX, FxContext.at(world, pos.add(0.5, 0.5, 0.5)));
    }

    /** 1.8 dropBlockAsItem for what water runs over: the block itself, as a hand with no tool would get it. */
    void wash(MechanicsWorld world, BlockVec pos, Block block) {
        Material material = block.material();
        if (material == null) return;
        DroppedItemEntity.spawn(world, new Pos(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5), Vec.ZERO, ItemStack.of(material), null, 10);
    }
}
