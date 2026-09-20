package io.github.term4.polyp.platform.compatibility;

import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.platform.player.OptimizedPlayer;
import io.github.term4.polyp.util.BlockContact;
import io.github.term4.polyp.world.MechanicsWorld;
import io.github.term4.polyp.tracking.ClientInfoTracker;
import net.minestom.server.MinecraftServer;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerBlockInteractEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.utils.Direction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Locale;

/**
 * Server-side 1.8 block-placement rules, each gated by its own {@code CompatConfig} knob.
 *
 * <p><b>Reach ({@code blockPlaceReach}):</b> cancels a placement whose clicked point is farther than the reach from the
 * player's <em>server</em> eye (the 1.8 preset under {@code legacyHitbox}), closing the modern sneak-bridge over-reach
 * (the lower crouch eye out-reaches 1.8). An honest Animatium client aims from the 1.8 eye, so the check is a no-op for
 * it - kept live to cover a spoofed handshake.
 *
 * <p><b>Air placement ({@code oldPlacement}):</b> refuses a placement whose clicked cell is air - the server half of the
 * 1.8 "don't place against air" rule (Animatium enforces the client half via {@code OLD_PLACEMENT}).
 *
 * <p>Installed once; each rule is inert unless the player's config enables it.
 */
public final class CompatPlacement {

    private CompatPlacement() {}

    /**
     * The per-body placement entity check for mixed-version play (shaped for a shard router's body-check hook).
     * A LEGACY placer gets the 1.8 reference-server semantics, source-verified against Paper 1.8.8 + the 1.8.9
     * client: the placer's own body NEVER blocks their placement (Paper passes the placer into
     * {@code checkNoEntityCollision}, which excludes it - and the 1.8 client sends every attempt before its own
     * prediction runs, so the server's accept is what the player sees; stairs into your own face land), and
     * no-collision-box blocks check nobody (1.8's null-AABB skip - the ladder clutch). A slab doubling in its
     * own cell is 1.8's one exception: {@code ItemSlab} checks the merged cube through the excludes-nobody
     * {@code checkNoEntityCollision}, so the placer standing on the half they click is what refuses it. Other
     * bodies stay on the precise check; everyone else (Animatium included, for now) is precise throughout,
     * matching their own client's prediction.
     *
     * <p>{@link CompatConfig#legacySelfPlace} scopes the self-exemption through the profile: a Hypixel-style
     * world turns it off and a 1.8 client stops landing stairs in its own face, while the passable skip keeps
     * the ladder clutch. Narrower policy still cancels {@code PlayerBlockPlaceEvent} - both placement paths
     * fire it with the resolved target and resync on cancel - with {@link BlockContact#overlapsBody} as the
     * condition, composed with {@link BlockContact#isFullCube}/{@link BlockContact#isPassable}.
     */
    public static boolean placementBodyCheck(@NotNull Player placer, @NotNull Entity body, @NotNull Block placing,
                                             @NotNull Point cellRelativeBody, @NotNull BoundingBox bodyBox) {
        if (placer instanceof OptimizedPlayer op && op.compat().legacyClient()) {
            if (body == placer && op.compat().legacySelfPlace() && !doubling(placing)) return false;
            if (BlockContact.isPassable(placing)) return false;
        }
        return placing.collisionShape().intersectBox(cellRelativeBody, bodyBox);
    }

    // the shaped result of a slab merge: no slab item places one otherwise
    private static boolean doubling(Block placing) {
        return "double".equals(placing.getProperty("type"));
    }

    public static void install(Polyp polyp) {
        EventNode<@NotNull PlayerEvent> node = EventNode.type("polyp:compat-placement", EventFilter.PLAYER);
        // resolve the client-info tracker lazily - install runs before it's created in init()
        node.addListener(PlayerBlockPlaceEvent.class, e -> onPlace(e, polyp.clientInfo()));
        node.addListener(PlayerBlockInteractEvent.class, CompatPlacement::onInteract);
        polyp.install(node);
    }

    /** 1.8 BlockChest.canPlaceBlockAt: beside two, or beside an already paired one, never lands - the client would
     *  pair by adjacency and draw a half where no block is. Trapped and plain never pair. */
    static boolean legacyChestShape(MechanicsWorld world, Point at, Block placing) {
        int beside = 0;
        for (Direction side : Direction.HORIZONTAL) {
            Point next = at.add(side.normalX(), 0, side.normalZ());
            if (!world.getBlock(next).compare(placing)) continue;
            beside++;
            for (Direction far : Direction.HORIZONTAL) {
                Point partner = next.add(far.normalX(), 0, far.normalZ());
                if (!partner.sameBlock(at) && world.getBlock(partner).compare(placing)) return false;
            }
        }
        return beside <= 1;
    }

    /**
     * 1.8's pair, both of its stages. {@code BlockChest.postPlace} runs last and wins: a chest placed beside another
     * turns BOTH to the PLACER's facing, as long as the join crosses it - so the pair reads the way the last chest
     * was set, not the first. Only where that stage passes (the join runs along the new chest's facing) does
     * {@code checkForSurroundingChests} decide, off the neighbour's facing and the blocks around the pair.
     *
     * <p>The modern rule pairs equal facings alone, so the halves it left single are turned and joined here. The 1.8
     * client predicts all of this locally, which is why a server that only does the second stage reads backwards.
     */
    static void pairLike18(MechanicsWorld world, Point at, Block placing) {
        Block mine = world.getBlock(at);
        if (!mine.compare(placing) || !"single".equals(mine.getProperty("type"))) return;
        Direction join = null;
        for (Direction side : Direction.HORIZONTAL) {
            if (world.getBlock(at.add(side.normalX(), 0, side.normalZ())).compare(placing)) join = side;
        }
        if (join == null) return;
        Point other = at.add(join.normalX(), 0, join.normalZ());
        Block theirs = world.getBlock(other);
        if (!"single".equals(theirs.getProperty("type"))) return;

        // 1.8's sequence. The block lands carrying the LOOK, both halves run checkForSurroundingChests, and
        // postPlace lands last with the look's opposite - our placement rule already faced the chest at the placer,
        // so that facing IS postPlace's answer and its opposite is the look e() saw.
        Direction placed = facingOf(mine);
        if (placed == null) return;
        Direction theirsFacing = facingOf(theirs);
        Direction facing = surrounding(world, at, join, theirsFacing);
        // postPlace writes NOTHING when a chest stands there and the join does not cross the placer's facing: its
        // three branches are no-neighbour, x-facing with a chest north or south, z-facing with one west or east.
        // So the placer's look lands on both halves or on neither, and e()'s answer stands - both halves compute it
        // off the same two cells, so they always agree.
        if (crosses(placed, join)) facing = placed;

        boolean otherOnMyLeft = join == clockwise(facing); // the left half's partner sits clockwise of the facing
        world.setBlock(at, half(mine, facing, otherOnMyLeft ? "left" : "right"));
        world.setBlock(other, half(theirs, facing, otherOnMyLeft ? "right" : "left"));
    }

    private static Block half(Block chest, Direction facing, String type) {
        return chest.withProperty("facing", facing.name().toLowerCase(Locale.ROOT)).withProperty("type", type);
    }

    /**
     * {@code BlockChest.e}: the join's axis picks the default - SOUTH for a pair joined along x, EAST along z - the
     * partner's facing can pull it to the other side, and an opaque block against one side with open air against the
     * other overrides both. Reads the pair, never the chest's own facing.
     */
    private static Direction surrounding(MechanicsWorld world, Point at, Direction toPartner, @Nullable Direction partnerFacing) {
        boolean alongZ = toPartner.normalZ() != 0;
        Direction positive = alongZ ? Direction.EAST : Direction.SOUTH;
        Direction negative = alongZ ? Direction.WEST : Direction.NORTH;
        Direction facing = partnerFacing == negative ? negative : positive;
        Point partner = at.add(toPartner.normalX(), 0, toPartner.normalZ());
        boolean negativeBlocked = fullBlock(world, at, negative) || fullBlock(world, partner, negative);
        boolean positiveBlocked = fullBlock(world, at, positive) || fullBlock(world, partner, positive);
        if (negativeBlocked && !positiveBlocked) facing = positive;
        if (positiveBlocked && !negativeBlocked) facing = negative;
        return facing;
    }

    /** Whether {@code facing} is perpendicular to the join - 1.8's postPlace turns both halves only across a facing. */
    private static boolean crosses(@Nullable Direction facing, Direction join) {
        return facing != null && facing.normalX() * join.normalX() + facing.normalZ() * join.normalZ() == 0;
    }

    // 1.8 tests Block.isFullBlock, which is isOpaqueCube read once in the constructor: opaque AND a whole cube. A
    // chest, slab, stair, pane or door is none of it, and turns nothing
    private static boolean fullBlock(MechanicsWorld world, Point from, Direction side) {
        Block block = world.getBlock(from.add(side.normalX(), 0, side.normalZ()));
        return block.occludes() && BlockContact.isFullCube(block);
    }

    private static Direction clockwise(Direction facing) {
        return switch (facing) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            default -> Direction.NORTH;
        };
    }

    private static @Nullable Direction facingOf(Block chest) {
        String facing = chest.getProperty("facing");
        return facing == null ? null : Direction.valueOf(facing.toUpperCase(Locale.ROOT));
    }

    private static boolean isChest(Block block) {
        return block.compare(Block.CHEST) || block.compare(Block.TRAPPED_CHEST);
    }

    /** A live raycast never block-hits air, so an air clicked-block = the client aimed at a cell it just broke (creative quick-replace). */
    private static void onInteract(PlayerBlockInteractEvent event) {
        if (!(event.getPlayer() instanceof OptimizedPlayer op)) return;
        // the event's block is the base-map read; a virtual-world member's clicked block may exist only in their world
        if (op.compat().oldPlacement() && MechanicsWorld.viewed(op).getBlock(event.getBlockPosition()).air()) {
            event.setBlockingItemUse(true);
        }
    }

    private static void onPlace(PlayerBlockPlaceEvent event, ClientInfoTracker clientInfo) {
        Player player = event.getPlayer();
        if (!(player instanceof OptimizedPlayer op)) return;
        if (op.compat().legacyChestShapes() && isChest(event.getBlock())) {
            MechanicsWorld world = MechanicsWorld.viewed(op);
            Point at = event.getBlockPosition();
            Block placing = event.getBlock();
            if (!legacyChestShape(world, at, placing)) {
                event.setCancelled(true);
                return;
            }
            // after the placement rule has had its say
            MinecraftServer.getSchedulerManager().scheduleEndOfTick(() -> {
                if (!event.isCancelled()) pairLike18(world, at, placing);
            });
        }
        Double reach = op.compat().blockPlaceReach();
        if (reach == null) return;
        // only modern survival clients can sneak-bridge past 1.8 reach; legacy/creative/spectator already aim correctly
        if (clientInfo.isLegacy(player)
                || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        // exact clicked point = the support block (one step back along the clicked face) + the cursor offset on its face
        Point hit = event.getBlockPosition().relative(event.getBlockFace().getOppositeFace()).add(event.getCursorPosition());
        Point eye = player.getPosition().add(0, player.getEyeHeight(), 0); // value (b) server eye
        if (eye.distanceSquared(hit) > reach * reach) event.setCancelled(true);
    }
}
