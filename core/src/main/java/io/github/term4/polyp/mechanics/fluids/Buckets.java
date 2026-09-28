package io.github.term4.polyp.mechanics.fluids;

import io.github.term4.polyp.config.FieldValue;
import io.github.term4.polyp.fx.Fx;
import io.github.term4.polyp.fx.FxContext;
import io.github.term4.polyp.mechanics.fluids.FluidsConfigResolver.FluidContext;
import io.github.term4.polyp.world.MechanicsWorld;
import net.minestom.server.collision.Shape;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.inventory.TransactionOption;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.Nullable;

/**
 * 1.8 ItemBucket: a use is a ray from the eyes, five blocks; a full bucket pours into the cell past the hit face, an
 * empty one scoops the source it hits. Both go through the player's own place and break events, so a game's rules
 * see them as any placement.
 */
final class Buckets {

    private static final double EPS = 1e-7;

    private final FluidSystem system;

    Buckets(FluidSystem system) {
        this.system = system;
    }

    void use(PlayerUseItemEvent e) {
        Player player = e.getPlayer();
        Material held = e.getItemStack().material();
        Block fluid = fluidOf(held);
        boolean empty = held == Material.BUCKET;
        if (fluid == null && !empty) return;
        if (player.getInstance() == null) return;
        MechanicsWorld world = MechanicsWorld.viewed(player);
        FluidContext ctx = system.context(world, null, null, player);
        FluidsConfig cfg = system.configFor(world);
        if (!FieldValue.resolve(cfg.buckets, ctx, true)) return;
        double reach = FieldValue.resolve(cfg.bucketReach, ctx, 5.0);
        if (reach <= 0) reach = player.getAttributeValue(Attribute.BLOCK_INTERACTION_RANGE);
        Hit hit = trace(world, player, reach, empty);
        if (hit == null) return;
        if (empty) scoop(world, player, e.getHand(), hit);
        else pour(world, player, e.getHand(), fluid, hit);
    }

    private static @Nullable Block fluidOf(Material material) {
        if (material == Material.WATER_BUCKET) return Block.WATER;
        if (material == Material.LAVA_BUCKET) return Block.LAVA;
        return null;
    }

    // 1.8 tryPlaceContainedLiquid: only into air or a block with nothing solid to it
    private void pour(MechanicsWorld world, Player player, PlayerHand hand, Block fluid, Hit hit) {
        BlockVec at = hit.cell().relative(hit.face());
        Block there = world.getBlock(at);
        if (!there.air() && there.solid()) return;
        var event = new PlayerBlockPlaceEvent(player, player.getInstance(), fluid, hit.face(), at,
                new Pos(0.5, 0.5, 0.5), hand);
        EventDispatcher.call(event);
        if (event.isCancelled()) return;
        if (!there.air() && !there.liquid()) system.wash(world, at, there);
        system.set(world, at, fluid);
        system.placed(world, at);
        Fx.play(system.services(), Fx.BUCKET_EMPTY, FxContext.at(world, at.add(0.5, 0.5, 0.5), player));
        if (player.getGameMode() != GameMode.CREATIVE) hold(player, hand, ItemStack.of(Material.BUCKET));
    }

    private void scoop(MechanicsWorld world, Player player, PlayerHand hand, Hit hit) {
        Block source = world.getBlock(hit.cell());
        if (!source.liquid() || Spread.level(source) != 0) return;
        Material filled = source.compare(Block.LAVA) ? Material.LAVA_BUCKET : Material.WATER_BUCKET;
        var event = new PlayerBlockBreakEvent(player, player.getInstance(), source, Block.AIR, hit.cell(), hit.face());
        EventDispatcher.call(event);
        if (event.isCancelled()) return;
        system.set(world, hit.cell(), Block.AIR);
        Fx.play(system.services(), Fx.BUCKET_FILL, FxContext.at(world, hit.cell().add(0.5, 0.5, 0.5), player));
        if (player.getGameMode() == GameMode.CREATIVE) return;
        ItemStack held = hand == PlayerHand.MAIN ? player.getItemInMainHand() : player.getItemInOffHand();
        if (held.amount() <= 1) {
            hold(player, hand, ItemStack.of(filled));
            return;
        }
        hold(player, hand, held.withAmount(held.amount() - 1));
        ItemStack left = player.getInventory().addItemStack(ItemStack.of(filled), TransactionOption.ALL);
        if (!left.isAir()) player.dropItem(left);
    }

    private static void hold(Player player, PlayerHand hand, ItemStack stack) {
        if (hand == PlayerHand.MAIN) player.setItemInMainHand(stack);
        else player.setItemInOffHand(stack);
    }

    record Hit(BlockVec cell, BlockFace face) {}

    /** 1.8 Item.getMovingObjectPositionFromPlayer: the first cell with something to hit; a full bucket looks past the boxless. */
    static @Nullable Hit trace(MechanicsWorld world, Player player, double reach, boolean liquids) {
        Pos eye = player.getPosition().add(0, player.getEyeHeight(), 0);
        Vec dir = player.getPosition().direction();
        Vec end = eye.asVec().add(dir.mul(reach));
        double x0 = eye.x(), y0 = eye.y(), z0 = eye.z();
        double x1 = end.x(), y1 = end.y(), z1 = end.z();
        int ex = (int) Math.floor(x1), ey = (int) Math.floor(y1), ez = (int) Math.floor(z1);
        int cx = (int) Math.floor(x0), cy = (int) Math.floor(y0), cz = (int) Math.floor(z0);
        Hit hit = test(world, cx, cy, cz, eye, end, liquids);
        if (hit != null) return hit;
        int steps = 200;
        while (steps-- >= 0) {
            if (cx == ex && cy == ey && cz == ez) return null;
            boolean stepX = true, stepY = true, stepZ = true;
            double nx = 999.0, ny = 999.0, nz = 999.0;
            if (ex > cx) nx = cx + 1.0;
            else if (ex < cx) nx = cx + 0.0;
            else stepX = false;
            if (ey > cy) ny = cy + 1.0;
            else if (ey < cy) ny = cy + 0.0;
            else stepY = false;
            if (ez > cz) nz = cz + 1.0;
            else if (ez < cz) nz = cz + 0.0;
            else stepZ = false;
            double tx = 999.0, ty = 999.0, tz = 999.0;
            double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
            if (stepX) tx = (nx - x0) / dx;
            if (stepY) ty = (ny - y0) / dy;
            if (stepZ) tz = (nz - z0) / dz;
            if (tx == -0.0) tx = -1.0E-4;
            if (ty == -0.0) ty = -1.0E-4;
            if (tz == -0.0) tz = -1.0E-4;
            int side;
            if (tx < ty && tx < tz) {
                side = ex > cx ? 4 : 5;
                x0 = nx;
                y0 += dy * tx;
                z0 += dz * tx;
            } else if (ty < tz) {
                side = ey > cy ? 0 : 1;
                x0 += dx * ty;
                y0 = ny;
                z0 += dz * ty;
            } else {
                side = ez > cz ? 2 : 3;
                x0 += dx * tz;
                y0 += dy * tz;
                z0 = nz;
            }
            cx = (int) Math.floor(x0) - (side == 5 ? 1 : 0);
            cy = (int) Math.floor(y0) - (side == 1 ? 1 : 0);
            cz = (int) Math.floor(z0) - (side == 3 ? 1 : 0);
            hit = test(world, cx, cy, cz, eye, end, liquids);
            if (hit != null) return hit;
        }
        return null;
    }

    // 1.8 canCollideCheck + collisionRayTrace: a source counts for an empty bucket; a boxless block only for it too
    private static @Nullable Hit test(MechanicsWorld world, int x, int y, int z, Point from, Point to, boolean liquids) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return null;
        Block block = world.getBlock(x, y, z, Block.Getter.Condition.TYPE);
        if (block == null || block.air()) return null;
        double minX = x, minY = y, minZ = z, maxX = x + 1, maxY = y + 1, maxZ = z + 1;
        if (block.liquid()) {
            if (!liquids || Spread.level(block) != 0) return null;
        } else {
            Shape shape = block.collisionShape();
            Point s = shape.relativeStart(), e = shape.relativeEnd();
            boolean boxless = e.x() - s.x() <= 0 || e.y() - s.y() <= 0 || e.z() - s.z() <= 0;
            if (boxless) {
                if (!liquids) return null;
            } else {
                minX = x + s.x();
                minY = y + s.y();
                minZ = z + s.z();
                maxX = x + e.x();
                maxY = y + e.y();
                maxZ = z + e.z();
            }
        }
        int axis = clip(from, to, minX, minY, minZ, maxX, maxY, maxZ);
        if (axis < 0) return null;
        Vec v = new Vec(to.x() - from.x(), to.y() - from.y(), to.z() - from.z());
        BlockFace face = switch (axis) {
            case 0 -> v.x() > 0 ? BlockFace.WEST : BlockFace.EAST;
            case 1 -> v.y() > 0 ? BlockFace.BOTTOM : BlockFace.TOP;
            default -> v.z() > 0 ? BlockFace.NORTH : BlockFace.SOUTH;
        };
        return new Hit(new BlockVec(x, y, z), face);
    }

    /** The axis of the face the segment enters the box through, or -1 for a miss or a start inside. */
    private static int clip(Point from, Point to, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double tEnter = 0, tExit = 1;
        int axis = -1;
        double[] p = {from.x(), from.y(), from.z()};
        double[] v = {to.x() - from.x(), to.y() - from.y(), to.z() - from.z()};
        double[] lo = {minX, minY, minZ};
        double[] hi = {maxX, maxY, maxZ};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(v[i]) < EPS) {
                if (p[i] <= lo[i] || p[i] >= hi[i]) return -1;
                continue;
            }
            double t1 = (lo[i] - p[i]) / v[i], t2 = (hi[i] - p[i]) / v[i];
            double near = Math.min(t1, t2), far = Math.max(t1, t2);
            if (near > tEnter) {
                tEnter = near;
                axis = i;
            }
            tExit = Math.min(tExit, far);
            if (tEnter > tExit) return -1;
        }
        return axis;
    }
}
