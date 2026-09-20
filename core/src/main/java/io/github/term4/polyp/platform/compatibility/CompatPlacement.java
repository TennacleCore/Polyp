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
        Direction facing = across(world, at, other, join, facingOf(mine), facingOf(theirs));
        String name = facing.name().toLowerCase(Locale.ROOT);
        boolean otherOnMyLeft = join == clockwise(facing); // the left half's partner sits clockwise of the facing
        world.setBlock(at, mine.withProperty("facing", name).withProperty("type", otherOnMyLeft ? "left" : "right"));
        world.setBlock(other, theirs.withProperty("facing", name).withProperty("type", otherOnMyLeft ? "right" : "left"));
    }

    // postPlace first: the new chest's own facing, when the join crosses it - the placer's look, and final in 1.8.
    // Else the neighbor's facing when it crosses, else south or east; then away from a solid block beside either
    // half when the other side is open
    private static Direction across(MechanicsWorld world, Point at, Point other, Direction join,
                                    @Nullable Direction mine, @Nullable Direction theirs) {
        if (crosses(mine, join)) return mine;
        Direction facing = join.normalX() != 0 ? Direction.SOUTH : Direction.EAST;
        if (crosses(theirs, join)) facing = theirs;
        Direction back = facing.opposite();
        boolean frontBlocked = solid(world, at, facing) || solid(world, other, facing);
        boolean backBlocked = solid(world, at, back) || solid(world, other, back);
        return frontBlocked && !backBlocked ? back : facing;
    }

    /** Whether {@code facing} is perpendicular to the join - 1.8 pairs across a facing, never along it. */
    private static boolean crosses(@Nullable Direction facing, Direction join) {
        return facing != null && facing.normalX() * join.normalX() + facing.normalZ() * join.normalZ() == 0;
    }

    private static boolean solid(MechanicsWorld world, Point from, Direction side) {
        return world.getBlock(from.add(side.normalX(), 0, side.normalZ())).isSolid();
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
