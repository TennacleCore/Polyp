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
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockManager;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Block placement by era, resolved per WORLD. Install after the app's own placement rules: the chest rule
 * registered by then is what {@link ChestPlacement#MODERN} runs; {@link ChestPlacement#LEGACY} takes the slot.
 */
public final class BlocksSystem extends ScopedSystem<BlocksConfig> {

    private static final List<Block> CHESTS = List.of(Block.CHEST, Block.TRAPPED_CHEST);

    private final EventNode<@NotNull PlayerEvent> node;

    private BlocksSystem(Polyp polyp, BlocksConfig config) {
        super(polyp, MechanicsKeys.BLOCKS, config);
        node = EventNode.type("polyp:blocks", EventFilter.PLAYER);
        node.addListener(PlayerBlockPlaceEvent.class, this::onPlace);
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
        BlocksConfig scoped = polyp.profiles().resolveWorld(world, MechanicsKeys.BLOCKS);
        return BlocksConfigResolver.resolve(scoped != null ? scoped : config, new BlocksContext(world, services()))
                .chestPlacement();
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
