package io.github.term4.polyp.mechanics.fluids;

import io.github.term4.polyp.api.event.fluid.FluidSpreadEvent;
import io.github.term4.polyp.config.FieldValue;
import io.github.term4.polyp.mechanics.fluids.FluidsConfigResolver.FluidContext;
import io.github.term4.polyp.vri.BlockDrops.DropRule;
import io.github.term4.polyp.world.MechanicsWorld;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.instance.block.Block;
import net.minestom.server.utils.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Random;
import java.util.Set;

/** The liquid tick (1.8 BlockDynamicLiquid.updateTick, 26.1 FlowingFluid.tick) over one fluid's knobs. */
final class Spread {

    /** One cell's fluid and its knobs, resolved once. */
    record Rules(FluidContext ctx, FluidConfig cfg, Block fluid, boolean waterlogging) {

        private <T> T knob(@Nullable FieldValue<FluidContext, T> knob, T def) {
            return FieldValue.resolve(knob, ctx, def);
        }

        boolean updates() { return knob(cfg.updates, true); }
        int tickRate() { return knob(cfg.tickRate, 5); }
        int dropOff() { return knob(cfg.dropOff, 1); }
        int slopeDistance() { return knob(cfg.slopeDistance, 4); }
        boolean infiniteSource() { return knob(cfg.infiniteSource, false); }
        int sourceNeighbors() { return knob(cfg.sourceNeighbors, 2); }
        boolean hesitates() { return knob(cfg.hesitates, false); }
        @Nullable DropRule washes() { return knob(cfg.washes, null); }
        Flow flows() { return knob(cfg.flows, Flow.ANY); }
        Passage passage() { return knob(cfg.passage, Passage.LEGACY); }
        boolean blocked(Block block) { return knob(cfg.blocked, Fluids.BLOCKED_18).test(block); }
        boolean replaces(Block block) { return knob(cfg.replaces, Fluids.NONE).test(block); }
        int fallingSideSources() { return knob(cfg.fallingSideSources, 0); }
        @Nullable Mixing mixing() { return knob(cfg.mixing, null); }

        /** The level of this fluid in {@code block}: a block holding water is a source; -1 for none of it. */
        int level(Block block) {
            if (block.compare(fluid)) return Spread.level(block);
            return holdsWater() && Fluids.waterlogged(block) ? 0 : -1;
        }

        boolean carries(Block block) { return level(block) >= 0; }

        /** A block that could take this fluid inside its own shape. */
        boolean container(Block block) { return holdsWater() && Fluids.waterloggable(block); }

        private boolean holdsWater() { return waterlogging && fluid.compare(Block.WATER); }

        boolean reacts(Mixing mixing, Block block) {
            return mixing.reacts(block) || waterlogging && mixing.other().compare(Block.WATER) && Fluids.waterlogged(block);
        }
    }

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

    private static Block withLevel(Block fluid, int level) {
        return fluid.withProperty("level", Integer.toString(level));
    }

    /** One scheduled tick of the fluid at {@code pos}. */
    void tick(MechanicsWorld world, BlockVec pos) {
        Block block = world.getBlock(pos);
        Rules r = system.rules(world, pos, block, null);
        if (r == null || !r.updates()) return;
        if (mix(world, pos, block, r)) return;
        int level = r.level(block);
        int drop = r.dropOff();
        int rate = r.tickRate();
        if (level > 0) {
            int lowest = -100;
            int sources = 0;
            Passage passage = r.passage();
            for (Direction d : Direction.HORIZONTAL) {
                Block side = world.getBlock(step(pos, d));
                if (!passage.through(r.ctx, block, d, side)) continue;
                int n = r.level(side);
                if (n < 0) continue;
                if (n == 0) ++sources;
                if (n >= 8) n = 0;
                lowest = lowest >= 0 && n >= lowest ? lowest : n;
            }
            int next = lowest + drop;
            if (next >= 8 || lowest < 0) next = -1;
            Block aboveBlock = world.getBlock(pos.add(0, 1, 0));
            int above = passage.through(r.ctx, block, Direction.UP, aboveBlock) ? r.level(aboveBlock) : -1;
            if (above >= 0) next = above >= 8 ? above : above + 8;
            if (sources >= r.sourceNeighbors() && r.infiniteSource()) {
                Block below = world.getBlock(pos.add(0, -1, 0));
                if (below.solid() || r.level(below) == 0) next = 0;
            }
            if (r.hesitates() && level < 8 && next < 8 && next > level && random.nextInt(4) != 0) rate *= 4;
            if (next != level) {
                level = next;
                if (next < 0) {
                    system.set(world, pos, Block.AIR);
                } else {
                    system.set(world, pos, withLevel(r.fluid, next));
                    system.schedule(world, pos, rate);
                }
            }
        }
        if (level < 0) return;
        BlockVec under = pos.add(0, -1, 0);
        Block underBlock = world.getBlock(under);
        Mixing mixing = r.mixing();
        boolean fallsOnto = mixing != null && r.reacts(mixing, underBlock) && mixing.underFlow() != null
                && r.flows().allowed(r.ctx, Direction.DOWN, under);
        if (fallsOnto || canFlowInto(r, block, Direction.DOWN, under, underBlock)) {
            if (fallsOnto) {
                if (underBlock.liquid()) system.set(world, under, mixing.underFlow());
                system.mixed(world, under);
                return;
            }
            flowInto(world, pos, r, block, Direction.DOWN, under, underBlock, level >= 8 ? level : level + 8, rate);
            int need = r.fallingSideSources();
            if (need > 0 && sources(world, pos, r) >= need) sides(world, pos, r, block, level, rate);
        } else if (level == 0 || !hole(r, block, underBlock)) {
            sides(world, pos, r, block, level, rate);
        }
    }

    private void sides(MechanicsWorld world, BlockVec pos, Rules r, Block block, int level, int rate) {
        int spread = level + r.dropOff();
        if (level >= 8) spread = 1;
        if (spread >= 8) return;
        for (Direction d : directions(world, pos, r, block)) {
            BlockVec to = step(pos, d);
            flowInto(world, pos, r, block, d, to, world.getBlock(to), spread, rate);
        }
    }

    // FlowingFluid.sourceNeighborCount
    private int sources(MechanicsWorld world, BlockVec pos, Rules r) {
        int n = 0;
        for (Direction d : Direction.HORIZONTAL) if (r.level(world.getBlock(step(pos, d))) == 0) ++n;
        return n;
    }

    /** 1.8 onBlockAdded: a fresh fluid reacts or gets its first tick. */
    void placed(MechanicsWorld world, BlockVec pos) {
        Block block = world.getBlock(pos);
        Rules r = system.rules(world, pos, block, null);
        if (r == null || !r.updates()) return;
        if (!mix(world, pos, block, r)) system.schedule(world, pos, r.tickRate());
    }

    /** What a fluid beside its {@link Mixing} partner turns into now, or null to stay a fluid. */
    @Nullable Block mixedInto(MechanicsWorld world, BlockVec pos, Block block, Rules r) {
        Mixing mixing = r.mixing();
        if (mixing == null) return null;
        for (Direction d : Direction.values()) {
            if (d == Direction.DOWN) continue;
            if (r.reacts(mixing, world.getBlock(step(pos, d)))) return mixing.beside(r.level(block));
        }
        return null;
    }

    private boolean mix(MechanicsWorld world, BlockVec pos, Block block, Rules r) {
        Block into = mixedInto(world, pos, block, r);
        if (into == null) return false;
        system.set(world, pos, into);
        system.mixed(world, pos);
        return true;
    }

    // 1.8 tryFlowInto / 26.1 spreadTo: a container fills, anything else is washed out or fizzed away
    private void flowInto(MechanicsWorld world, BlockVec from, Rules r, Block fromBlock, Direction d, BlockVec to, Block toBlock,
                          int level, int rate) {
        if (!canFlowInto(r, fromBlock, d, to, toBlock)) return;
        boolean container = r.container(toBlock);
        Block landing = container ? toBlock.withProperty("waterlogged", "true") : withLevel(r.fluid, level);
        FluidSpreadEvent event = new FluidSpreadEvent(world, from, to, d, landing, toBlock);
        EventDispatcher.call(event);
        if (event.isCancelled()) return;
        if (!toBlock.air() && !container) {
            if (r.mixing() != null) system.mixed(world, to);
            else system.wash(world, to, toBlock, r.washes());
        }
        system.set(world, to, landing);
        placed(world, to);
    }

    // never its own kind, another fluid only when `replaces` says, then the face, then the cell
    private boolean canFlowInto(Rules r, Block from, Direction d, BlockVec to, Block toBlock) {
        if (!r.flows().allowed(r.ctx, d, to)) return false;
        if (r.carries(toBlock)) return false;
        boolean container = r.container(toBlock);
        if (!container && toBlock.liquid() && !r.replaces(toBlock)) return false;
        if (!r.passage().through(r.ctx, from, d, toBlock)) return false;
        return container || !r.blocked(toBlock);
    }

    // 1.8 isBlocked(below), 26.1 isWaterHole: a fall the fluid would take rather than spread
    private boolean hole(Rules r, Block at, Block below) {
        if (!r.passage().through(r.ctx, at, Direction.DOWN, below)) return false;
        return r.carries(below) || r.container(below) || !r.blocked(below);
    }

    // 1.8 getPossibleFlowDirections: the sides nearest a drop, all of them on a tie
    private Set<Direction> directions(MechanicsWorld world, BlockVec pos, Rules r, Block at) {
        int best = 1000;
        Set<Direction> out = EnumSet.noneOf(Direction.class);
        int reach = r.slopeDistance();
        Flow flows = r.flows();
        Passage passage = r.passage();
        for (Direction d : Direction.HORIZONTAL) {
            BlockVec to = step(pos, d);
            Block toBlock = world.getBlock(to);
            if (!flows.allowed(r.ctx, d, to)) continue;
            if (!enterable(r, toBlock) || !passage.through(r.ctx, at, d, toBlock)) continue;
            int cost = hole(r, toBlock, world.getBlock(to.add(0, -1, 0))) ? 0 : cost(world, to, toBlock, 1, d.opposite(), r, reach);
            if (cost < best) out.clear();
            if (cost <= best) {
                out.add(d);
                best = cost;
            }
        }
        return out;
    }

    // a cell the slope search may walk: not blocked, not a source of this fluid
    private static boolean enterable(Rules r, Block block) {
        return (r.container(block) || !r.blocked(block)) && r.level(block) != 0;
    }

    // 1.8 func_176374_a
    private int cost(MechanicsWorld world, BlockVec pos, Block at, int distance, Direction from, Rules r, int reach) {
        int best = 1000;
        Passage passage = r.passage();
        for (Direction d : Direction.HORIZONTAL) {
            if (d == from) continue;
            BlockVec to = step(pos, d);
            Block toBlock = world.getBlock(to);
            if (!enterable(r, toBlock) || !passage.through(r.ctx, at, d, toBlock)) continue;
            if (hole(r, toBlock, world.getBlock(to.add(0, -1, 0)))) return distance;
            if (distance < reach) {
                int further = cost(world, to, toBlock, distance + 1, d.opposite(), r, reach);
                if (further < best) best = further;
            }
        }
        return best;
    }
}
