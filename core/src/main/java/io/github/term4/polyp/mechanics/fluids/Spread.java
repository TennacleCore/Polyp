package io.github.term4.polyp.mechanics.fluids;

import io.github.term4.polyp.api.event.fluid.FluidSpreadEvent;
import io.github.term4.polyp.config.FieldValue;
import io.github.term4.polyp.mechanics.fluids.FluidsConfigResolver.FluidContext;
import io.github.term4.polyp.world.MechanicsWorld;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.instance.block.Block;
import net.minestom.server.utils.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Random;
import java.util.Set;

/** 1.8 BlockDynamicLiquid.updateTick and BlockLiquid.checkForMixing, over one fluid's knobs. */
final class Spread {

    private final FluidSystem system;
    private final Random random = new Random();

    Spread(FluidSystem system) {
        this.system = system;
    }

    static BlockVec step(BlockVec pos, Direction d) {
        return pos.add(d.normalX(), d.normalY(), d.normalZ());
    }

    static int level(Block block) {
        String level = block.getProperty("level");
        return level != null ? Integer.parseInt(level) : 0;
    }

    /** 1.8 getLevel: the level when {@code block} is this fluid, else -1. */
    private static int level(Block fluid, Block block) {
        return block.compare(fluid) ? level(block) : -1;
    }

    private static Block withLevel(Block fluid, int level) {
        return fluid.withProperty("level", Integer.toString(level));
    }

    /** One scheduled tick of the fluid at {@code pos}. */
    void tick(MechanicsWorld world, BlockVec pos) {
        Block block = world.getBlock(pos);
        FluidContext ctx = system.context(world, pos, block);
        FluidConfig cfg = system.configFor(ctx);
        if (cfg == null || !FieldValue.resolve(cfg.updates, ctx, true)) return;
        if (mix(world, pos, block, cfg, ctx)) return;
        Block fluid = block.defaultState();
        int level = level(block);
        int drop = FieldValue.resolve(cfg.dropOff, ctx, 1);
        int rate = FieldValue.resolve(cfg.tickRate, ctx, 5);
        Mixing mixing = FieldValue.resolve(cfg.mixing, ctx, null);
        if (level > 0) {
            int lowest = -100;
            int sources = 0;
            for (Direction d : Direction.HORIZONTAL) {
                int n = level(fluid, world.getBlock(step(pos, d)));
                if (n < 0) continue;
                if (n == 0) ++sources;
                if (n >= 8) n = 0;
                lowest = lowest >= 0 && n >= lowest ? lowest : n;
            }
            int next = lowest + drop;
            if (next >= 8 || lowest < 0) next = -1;
            int above = level(fluid, world.getBlock(pos.add(0, 1, 0)));
            if (above >= 0) next = above >= 8 ? above : above + 8;
            if (sources >= FieldValue.resolve(cfg.sourceNeighbors, ctx, 2) && FieldValue.resolve(cfg.infiniteSource, ctx, false)) {
                Block below = world.getBlock(pos.add(0, -1, 0));
                if (below.solid() || level(fluid, below) == 0) next = 0;
            }
            if (FieldValue.resolve(cfg.hesitates, ctx, false) && level < 8 && next < 8 && next > level && random.nextInt(4) != 0) rate *= 4;
            if (next != level) {
                level = next;
                if (next < 0) {
                    system.set(world, pos, Block.AIR);
                } else {
                    system.set(world, pos, withLevel(fluid, next));
                    system.schedule(world, pos, rate);
                }
            }
        }
        BlockVec under = pos.add(0, -1, 0);
        Block underBlock = world.getBlock(under);
        boolean fallsOnto = mixing != null && mixing.reacts(underBlock) && mixing.underFlow() != null
                && FieldValue.resolve(cfg.flows, ctx, Flow.ANY).allowed(ctx, Direction.DOWN, under);
        if (level >= 0 && (fallsOnto || canFlowInto(cfg, ctx, fluid, Direction.DOWN, under, underBlock))) {
            if (fallsOnto) {
                system.set(world, under, mixing.underFlow());
                system.mixed(world, under);
                return;
            }
            flowInto(world, pos, cfg, ctx, fluid, Direction.DOWN, under, underBlock, level >= 8 ? level : level + 8, rate);
        } else if (level >= 0 && (level == 0 || blocked(cfg, ctx, underBlock))) {
            int spread = level + drop;
            if (level >= 8) spread = 1;
            if (spread >= 8) return;
            for (Direction d : directions(world, pos, cfg, ctx, fluid)) {
                BlockVec to = step(pos, d);
                flowInto(world, pos, cfg, ctx, fluid, d, to, world.getBlock(to), spread, rate);
            }
        }
    }

    /** 1.8 onBlockAdded: a fresh fluid reacts or gets its first tick. */
    void placed(MechanicsWorld world, BlockVec pos) {
        Block block = world.getBlock(pos);
        FluidContext ctx = system.context(world, pos, block);
        FluidConfig cfg = system.configFor(ctx);
        if (cfg == null || !FieldValue.resolve(cfg.updates, ctx, true)) return;
        if (!mix(world, pos, block, cfg, ctx)) system.schedule(world, pos, FieldValue.resolve(cfg.tickRate, ctx, 5));
    }

    /** What a fluid beside its {@link Mixing} partner turns into now, or null to stay a fluid. */
    @Nullable Block mixedInto(MechanicsWorld world, BlockVec pos, Block block, FluidConfig cfg, FluidContext ctx) {
        Mixing mixing = FieldValue.resolve(cfg.mixing, ctx, null);
        if (mixing == null) return null;
        for (Direction d : Direction.values()) {
            if (d == Direction.DOWN) continue;
            if (mixing.reacts(world.getBlock(step(pos, d)))) return mixing.beside(level(block));
        }
        return null;
    }

    private boolean mix(MechanicsWorld world, BlockVec pos, Block block, FluidConfig cfg, FluidContext ctx) {
        Block into = mixedInto(world, pos, block, cfg, ctx);
        if (into == null) return false;
        system.set(world, pos, into);
        system.mixed(world, pos);
        return true;
    }

    // 1.8 tryFlowInto
    private void flowInto(MechanicsWorld world, BlockVec from, FluidConfig cfg, FluidContext ctx, Block fluid, Direction d,
                          BlockVec to, Block toBlock, int level, int rate) {
        if (!canFlowInto(cfg, ctx, fluid, d, to, toBlock)) return;
        Block landing = withLevel(fluid, level);
        FluidSpreadEvent event = new FluidSpreadEvent(world, from, to, d, landing, toBlock);
        EventDispatcher.call(event);
        if (event.isCancelled()) return;
        if (!toBlock.air()) {
            if (FieldValue.resolve(cfg.mixing, ctx, null) != null) system.mixed(world, to);
            else if (FieldValue.resolve(cfg.washes, ctx, true)) system.wash(world, to, toBlock);
        }
        system.set(world, to, landing);
        placed(world, to);
    }

    // 1.8 canFlowInto over the knobs: never its own kind, another fluid only when `replaces` says (1.8 lava takes
    // water, water never takes lava), never a blocked cell
    private boolean canFlowInto(FluidConfig cfg, FluidContext ctx, Block fluid, Direction d, BlockVec to, Block toBlock) {
        if (!FieldValue.resolve(cfg.flows, ctx, Flow.ANY).allowed(ctx, d, to)) return false;
        if (toBlock.compare(fluid)) return false;
        if (toBlock.liquid() && !FieldValue.resolve(cfg.replaces, ctx, Fluids.NONE).test(toBlock)) return false;
        return !blocked(cfg, ctx, toBlock);
    }

    private static boolean blocked(FluidConfig cfg, FluidContext ctx, Block block) {
        return FieldValue.resolve(cfg.blocked, ctx, Fluids.BLOCKED_18).test(block);
    }

    // 1.8 getPossibleFlowDirections: the sides nearest a drop, all of them on a tie
    private Set<Direction> directions(MechanicsWorld world, BlockVec pos, FluidConfig cfg, FluidContext ctx, Block fluid) {
        int best = 1000;
        Set<Direction> out = EnumSet.noneOf(Direction.class);
        int reach = FieldValue.resolve(cfg.slopeDistance, ctx, 4);
        Flow flows = FieldValue.resolve(cfg.flows, ctx, Flow.ANY);
        for (Direction d : Direction.HORIZONTAL) {
            BlockVec to = step(pos, d);
            Block toBlock = world.getBlock(to);
            if (!flows.allowed(ctx, d, to)) continue;
            if (blocked(cfg, ctx, toBlock) || (toBlock.compare(fluid) && level(toBlock) == 0)) continue;
            int cost = blocked(cfg, ctx, world.getBlock(to.add(0, -1, 0))) ? cost(world, to, 1, d.opposite(), cfg, ctx, fluid, reach) : 0;
            if (cost < best) out.clear();
            if (cost <= best) {
                out.add(d);
                best = cost;
            }
        }
        return out;
    }

    // 1.8 func_176374_a
    private int cost(MechanicsWorld world, BlockVec pos, int distance, Direction from, FluidConfig cfg, FluidContext ctx,
                     Block fluid, int reach) {
        int best = 1000;
        for (Direction d : Direction.HORIZONTAL) {
            if (d == from) continue;
            BlockVec to = step(pos, d);
            Block toBlock = world.getBlock(to);
            if (blocked(cfg, ctx, toBlock) || (toBlock.compare(fluid) && level(toBlock) == 0)) continue;
            if (!blocked(cfg, ctx, world.getBlock(to.add(0, -1, 0)))) return distance;
            if (distance < reach) {
                int further = cost(world, to, distance + 1, d.opposite(), cfg, ctx, fluid, reach);
                if (further < best) best = further;
            }
        }
        return best;
    }
}
