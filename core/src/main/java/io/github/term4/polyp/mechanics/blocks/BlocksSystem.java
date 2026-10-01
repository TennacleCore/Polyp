package io.github.term4.polyp.mechanics.blocks;

import io.github.term4.polyp.MechanicsKeys;
import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.ScopedSystem;
import io.github.term4.polyp.mechanics.blocks.BlocksConfig.ChestPlacement;
import io.github.term4.polyp.mechanics.blocks.BlocksConfigResolver.BlocksContext;
import io.github.term4.polyp.world.MechanicsWorld;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.event.player.PlayerCancelDiggingEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerFinishDiggingEvent;
import net.minestom.server.event.player.PlayerStartDiggingEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.timer.TaskSchedule;
import net.minestom.server.utils.block.BlockBreakCalculation;
import io.github.term4.polyp.util.tick.TickSystem;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockManager;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Block placement by era, resolved per WORLD. Install after the app's own placement rules: the chest rule
 * registered by then is what {@link ChestPlacement#MODERN} runs; {@link ChestPlacement#LEGACY} takes the slot.
 */
public final class BlocksSystem extends ScopedSystem<BlocksConfig> {

    private static final List<Block> CHESTS = List.of(Block.CHEST, Block.TRAPPED_CHEST);

    private record Dig(BlockVec pos, long startTick) {}

    private final EventNode<@NotNull PlayerEvent> node;
    private final Map<UUID, Dig> digs = new ConcurrentHashMap<>();
    private final Map<UUID, BlockVec> finishing = new ConcurrentHashMap<>();

    private BlocksSystem(Polyp polyp, BlocksConfig config) {
        super(polyp, MechanicsKeys.BLOCKS, config);
        node = EventNode.type("polyp:blocks", EventFilter.PLAYER);
        node.addListener(PlayerBlockPlaceEvent.class, this::onPlace);
        node.addListener(PlayerStartDiggingEvent.class, e ->
                digs.put(e.getPlayer().getUuid(), new Dig(e.getBlockPosition(), TickSystem.tick(e.getPlayer()))));
        node.addListener(PlayerCancelDiggingEvent.class, e -> digs.remove(e.getPlayer().getUuid()));
        node.addListener(PlayerFinishDiggingEvent.class, e -> finishing.put(e.getPlayer().getUuid(), e.getBlockPosition()));
        node.addListener(PlayerBlockBreakEvent.class, this::onBreak);
        node.addListener(PlayerDisconnectEvent.class, e -> {
            digs.remove(e.getPlayer().getUuid());
            finishing.remove(e.getPlayer().getUuid());
        });
    }

    public static BlocksSystem install(Polyp polyp) {
        return install(polyp, BlocksConfig.builder().build());
    }

    public static BlocksSystem install(Polyp polyp, BlocksConfig config) {
        BlocksSystem system = polyp.installModule(new BlocksSystem(polyp, config));
        BlockManager manager = MinecraftServer.getBlockManager();
        for (Block chest : CHESTS) {
            BlockPlacementRule previous = manager.getBlockPlacementRule(chest);
            if (previous instanceof ChestPlacementRule ours) previous = ours.modern; // a re-install
            manager.registerBlockPlacementRule(new ChestPlacementRule(chest, previous, system::chestPlacementOf));
        }
        return system;
    }

    @Override
    public EventNode<@NotNull PlayerEvent> node() { return node; }

    /** {@code world}'s chest rule: its profile chain, else the install config. */
    public ChestPlacement chestPlacement(@Nullable MechanicsWorld world) {
        return resolved(world).chestPlacement();
    }

    private BlocksConfigResolver.ResolvedBlocksConfig resolved(@Nullable MechanicsWorld world) {
        BlocksConfig scoped = polyp.profiles().resolveWorld(world, MechanicsKeys.BLOCKS);
        return BlocksConfigResolver.resolve(scoped != null ? scoped : config, new BlocksContext(world, services()));
    }

    // a finished dig, not an instant break or a game's own: short of the share it waits for the server's count
    private void onBreak(PlayerBlockBreakEvent event) {
        Player player = event.getPlayer();
        BlockVec at = event.getBlockPosition();
        BlockVec finished = finishing.remove(player.getUuid());
        if (finished == null || !finished.equals(at)) return;
        Dig dig = digs.remove(player.getUuid());
        var cfg = resolved(MechanicsWorld.viewed(player));
        Double share = cfg.digFinishShare();
        if (share == null || share <= 0) return;
        if (dig == null || !dig.pos().equals(at)) { // vanilla breaks nothing for a finish it saw no start of
            event.setCancelled(true);
            return;
        }
        int ticks = BlockBreakCalculation.breakTicks(event.getBlock(), player);
        if (ticks <= 0) return;
        long elapsed = TickSystem.tick(player) - dig.startTick();
        if ((elapsed + 1) / (double) ticks >= share) return;
        event.setCancelled(true);
        if (cfg.digFinishLate()) finishLater(player, at, event.getBlockFace(), event.getBlock(), Math.max(1, ticks - 1 - elapsed));
    }

    private static void finishLater(Player player, BlockVec at, BlockFace face, Block block, long delay) {
        Instance instance = player.getInstance();
        MinecraftServer.getSchedulerManager().buildTask(() -> {
            if (!player.isOnline() || player.getInstance() != instance || instance == null) return;
            if (!instance.getBlock(at).compare(block)) return;
            instance.breakBlock(player, at, face);
        }).delay(TaskSchedule.tick((int) delay)).schedule();
    }

    private ChestPlacement chestPlacementOf(Block.Getter view) {
        return chestPlacement(MechanicsWorld.ofView(view));
    }

    private void onPlace(PlayerBlockPlaceEvent event) {
        Block placing = event.getBlock();
        if (!LegacyChestPlacement.isChest(placing)) return;
        MechanicsWorld world = MechanicsWorld.viewed(event.getPlayer());
        if (chestPlacement(world) != ChestPlacement.LEGACY) return;
        if (!LegacyChestPlacement.canPlace(world, event.getBlockPosition(), placing)) event.setCancelled(true);
    }
}
