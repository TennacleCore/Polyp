package io.github.term4.polyp.mechanics.containers;

import io.github.term4.polyp.MechanicsKeys;
import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.ScopedSystem;
import io.github.term4.polyp.api.event.container.ContainerChangeEvent;
import io.github.term4.polyp.api.event.item.ItemSpawnEvent;
import io.github.term4.polyp.fx.Fx;
import io.github.term4.polyp.fx.FxContext;
import io.github.term4.polyp.mechanics.containers.ContainersConfigResolver.ContainerContext;
import io.github.term4.polyp.mechanics.containers.ContainersConfigResolver.ResolvedContainer;
import io.github.term4.polyp.mechanics.containers.ContainerTypeConfig.Pairing;
import io.github.term4.polyp.platform.player.CreativeInventory;
import io.github.term4.polyp.world.MechanicsWorld;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.inventory.InventoryCloseEvent;
import net.minestom.server.event.inventory.InventoryItemChangeEvent;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerBlockInteractEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.inventory.AbstractInventory;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.network.packet.server.play.BlockActionPacket;
import net.minestom.server.utils.Direction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Containers as a mechanic: a block from {@link ContainersConfig} (or a cell an app {@link #declare declares})
 * claims the click and opens the contents its kind's key names; the contents live on the world, the window
 * only while someone has it open; a break {@link Spill spills} them. Every slot change fires
 * {@link ContainerChangeEvent}, which is all a recorder needs.
 */
public final class ContainerSystem extends ScopedSystem<ContainersConfig> {

    private static final int CHEST_HALF = 27;

    private final EventNode<@NotNull Event> node;
    private final Map<AbstractInventory, ContainerStore.Window> open = new ConcurrentHashMap<>();
    /** Contents waiting for the block's own dropped item to carry them ({@link Spill#PACK}). */
    private final Map<BlockVec, ItemStack[]> packing = new ConcurrentHashMap<>();
    // a load runs on the opener's thread while another viewer's change lands on theirs
    private final ThreadLocal<Boolean> loading = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private ContainerSystem(Polyp polyp, ContainersConfig config) {
        super(polyp, MechanicsKeys.CONTAINERS, config);
        node = EventNode.all("polyp:containers");
        node.addListener(PlayerBlockInteractEvent.class, this::onInteract);
        node.addListener(InventoryItemChangeEvent.class, this::onChange);
        node.addListener(InventoryCloseEvent.class, this::onClose);
        node.addListener(PlayerBlockBreakEvent.class, e -> MinecraftServer.getSchedulerManager().scheduleEndOfTick(() -> {
            if (!e.isCancelled()) broken(MechanicsWorld.viewed(e.getPlayer()), e.getBlockPosition(), e.getBlock(), e.getPlayer());
        }));
        node.addListener(ItemSpawnEvent.class, this::onItemSpawn);
        // Player.remove pulls the viewer with no close event, so the lid would stay open and the window listed
        node.addListener(PlayerDisconnectEvent.class, e -> {
            AbstractInventory inventory = e.getPlayer().getOpenInventory();
            if (inventory != null) close(inventory, e.getPlayer());
        });
    }

    public static ContainerSystem install(Polyp polyp) {
        return install(polyp, ContainersConfig.builder().build());
    }

    public static ContainerSystem install(Polyp polyp, ContainersConfig config) {
        return polyp.installModule(new ContainerSystem(polyp, config));
    }

    @Override
    public EventNode<@NotNull Event> node() { return node; }

    /** Makes {@code pos} a container of kind {@code type} in {@code world}, whatever block stands there. */
    public void declare(@NotNull MechanicsWorld world, @NotNull Point pos, @NotNull ContainerTypeConfig type) {
        ContainerStore.of(world).declared.put(pos.asBlockVec(), type);
    }

    public void undeclare(@NotNull MechanicsWorld world, @NotNull Point pos) {
        ContainerStore store = ContainerStore.existing(world);
        if (store != null) store.declared.remove(pos.asBlockVec());
    }

    /** A copy of the contents under {@code key}, air as {@code null}; {@code null} = nothing stored. */
    public ItemStack @Nullable [] contents(@NotNull MechanicsWorld world, @NotNull String key) {
        ContainerStore store = ContainerStore.existing(world);
        ItemStack[] slots = store == null ? null : store.holdings.get(key);
        return slots == null ? null : slots.clone();
    }

    /** Sets the contents under {@code key} wholesale, silently (a branch or a load, not a change). */
    public void restore(@NotNull MechanicsWorld world, @NotNull String key, ItemStack @NotNull [] slots) {
        ContainerStore store = ContainerStore.of(world);
        ItemStack[] copy = new ItemStack[slots.length];
        boolean any = false;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null && !slots[i].isAir()) {
                copy[i] = slots[i];
                any = true;
            }
        }
        store.filled.add(key);
        if (any) store.holdings.put(key, copy);
        else store.holdings.remove(key);
    }

    public void forEach(@NotNull MechanicsWorld world, @NotNull BiConsumer<String, ItemStack[]> consumer) {
        ContainerStore store = ContainerStore.existing(world);
        if (store != null) store.holdings.forEach((key, slots) -> consumer.accept(key, slots.clone()));
    }

    /** The kind of container at {@code pos}, or {@code null}; {@code viewer} is who is opening or breaking it. */
    public @Nullable ResolvedContainer kindAt(@NotNull MechanicsWorld world, @NotNull Point pos, @NotNull Block block,
                                             @Nullable Player viewer) {
        ContainerStore store = ContainerStore.existing(world);
        ContainerTypeConfig declared = store == null ? null : store.declared.get(pos.asBlockVec());
        ContainersConfig cfg = viewer != null ? configFor(viewer) : polyp.profiles().resolveWorld(world, MechanicsKeys.CONTAINERS);
        if (cfg == null) cfg = config;
        return ContainersConfigResolver.resolve(cfg, declared, context(world, pos, block, viewer));
    }

    private ContainerContext context(MechanicsWorld world, Point pos, Block block, @Nullable Player viewer) {
        return new ContainerContext(world, pos.asBlockVec(), block, viewer, services());
    }

    private void onInteract(PlayerBlockInteractEvent e) {
        Player p = e.getPlayer();
        MechanicsWorld world = MechanicsWorld.viewed(p);
        BlockVec pos = e.getBlockPosition();
        Block block = world.getBlock(pos); // the event's block is the base map's; a virtual-world member clicks their own
        ResolvedContainer kind = kindAt(world, pos, block, p);
        if (kind == null) return;
        // vanilla: a sneaked click with something in hand places past the container instead of opening it
        if (p.isSneaking() && !p.getItemInHand(e.getHand()).isAir()) return;
        e.setBlockingItemUse(true);
        open(world, pos, block, p, kind);
    }

    private void open(MechanicsWorld world, BlockVec pos, Block block, Player p, ResolvedContainer kind) {
        ContainerContext ctx = context(world, pos, block, p);
        String key = kind.key().of(ctx);
        if (key == null) return;
        ContainerStore store = ContainerStore.of(world);
        List<BlockVec> cells = new ArrayList<>(2);
        List<String> keys = new ArrayList<>(2);
        int rows = kind.rows();
        Component title = kind.title();
        BlockVec partner = kind.pairing() != Pairing.NONE ? partnerOf(world, pos, block) : null;
        if (partner != null) {
            BlockVec first = kind.pairing() == Pairing.MODERN
                    ? ("right".equals(block.getProperty("type")) ? pos : partner)
                    : (partner.blockX() < pos.blockX() || partner.blockZ() < pos.blockZ() ? partner : pos);
            BlockVec second = first == pos ? partner : pos;
            cells.add(first);
            cells.add(second);
            keys.add(kind.key().of(context(world, first, block, p)));
            keys.add(kind.key().of(context(world, second, block, p)));
            rows *= 2;
            title = Component.translatable("container.chestDouble");
        } else {
            cells.add(pos);
            keys.add(key);
        }
        ContainerStore.Window window = null;
        for (String k : keys) window = window != null ? window : store.windows.get(k);
        if (window == null) {
            Inventory inventory = new CreativeInventory(rowsType(rows), title);
            window = new ContainerStore.Window(inventory, world, block, List.copyOf(cells), List.copyOf(keys));
            int half = window.half();
            for (int h = 0; h < keys.size(); h++) {
                String k = keys.get(h);
                ItemStack[] slots = store.holdings.get(k);
                if (slots == null && store.filled.add(k)) {
                    slots = kind.fill().of(context(world, cells.get(h), block, p), half);
                    load(inventory, h * half, half, slots, false); // a fill is a change: it lands through onChange
                } else {
                    load(inventory, h * half, half, slots, true);
                }
            }
            for (String k : keys) store.windows.put(k, window);
            open.put(inventory, window);
        }
        if (!p.openInventory(window.inventory())) return;
        if (window.inventory().getViewers().size() == 1) lid(window, true, p);
    }

    private void load(Inventory inventory, int offset, int half, ItemStack @Nullable [] slots, boolean silent) {
        if (slots == null) return;
        if (silent) loading.set(Boolean.TRUE);
        try {
            for (int i = 0; i < half && i < slots.length; i++) {
                if (slots[i] != null && !slots[i].isAir()) inventory.setItemStack(offset + i, slots[i]);
            }
        } finally {
            loading.remove();
        }
    }

    private void onChange(InventoryItemChangeEvent e) {
        ContainerStore.Window window = open.get(e.getInventory());
        if (window == null || loading.get()) return;
        int half = window.half();
        String key = window.keys().get(e.getSlot() / half);
        int slot = e.getSlot() % half;
        ContainerStore.of(window.world()).put(key, slot, half, e.getNewItem());
        EventDispatcher.call(new ContainerChangeEvent(window.world(), key, slot, e.getPreviousItem(), e.getNewItem()));
    }

    private void onClose(InventoryCloseEvent e) {
        close(e.getInventory(), e.getPlayer());
    }

    private void close(AbstractInventory inventory, Player by) {
        ContainerStore.Window window = open.get(inventory);
        if (window == null) return;
        // the leaving viewer is still counted: both events run before the removal
        if (window.inventory().getViewers().size() > 1) return;
        drop(window);
        lid(window, false, by);
    }

    private void drop(ContainerStore.Window window) {
        open.remove(window.inventory());
        ContainerStore store = ContainerStore.existing(window.world());
        if (store != null) for (String k : window.keys()) store.windows.remove(k, window);
    }

    /** The lid: a block action for every cell (a pair animates both), the sound once at the clicked one. */
    private void lid(ContainerStore.Window window, boolean opening, Player by) {
        MechanicsWorld world = window.world();
        int viewers = opening ? window.inventory().getViewers().size() : 0;
        for (BlockVec cell : window.cells()) {
            world.broadcast(new BlockActionPacket(cell, (byte) 1, (byte) viewers, world.getBlock(cell).id()));
        }
        BlockVec at = window.cells().get(0);
        Fx.play(services(), opening ? Fx.CONTAINER_OPEN : Fx.CONTAINER_CLOSE,
                FxContext.at(world, at.add(0.5, 0.5, 0.5), by).withDetail(window.block()));
    }

    /**
     * The block at {@code pos} is going: windows on it close, and its contents spill by kind. Explosions call
     * this before clearing the cell; player breaks arrive through the break event.
     */
    public void broken(@NotNull MechanicsWorld world, @NotNull Point pos, @NotNull Block block, @Nullable Player by) {
        BlockVec cell = pos.asBlockVec();
        ResolvedContainer kind = kindAt(world, cell, block, by);
        ContainerStore store = ContainerStore.existing(world);
        if (store != null) store.declared.remove(cell);
        if (kind == null || store == null) return;
        for (ContainerStore.Window window : List.copyOf(open.values())) {
            if (window.world() == world && window.cells().contains(cell)) {
                for (Player viewer : List.copyOf(window.inventory().getViewers())) viewer.closeInventory();
                drop(window);
            }
        }
        ContainerContext ctx = context(world, cell, block, by);
        String key = kind.key().of(ctx);
        if (key == null || kind.spill() == Spill.KEEP) return;
        ItemStack[] slots = store.holdings.remove(key);
        if (slots == null && store.filled.add(key)) slots = kind.fill().of(ctx, kind.rows() * 9); // unopened loot still drops
        store.filled.remove(key);
        if (slots == null) return;
        switch (kind.spill()) {
            case DROP -> Spill.scatter(world, cell, Arrays.asList(slots), by);
            case PACK -> {
                packing.put(cell, slots);
                MinecraftServer.getSchedulerManager().scheduleEndOfTick(() -> packing.remove(cell));
            }
            default -> {}
        }
    }

    // PACK: the block's own dropped item, spawned by whichever drop rule runs, carries the contents
    private void onItemSpawn(ItemSpawnEvent e) {
        if (e.cause() != ItemSpawnEvent.Cause.BLOCK_DROP || packing.isEmpty()) return;
        ItemStack[] slots = packing.remove(e.position().asBlockVec());
        if (slots == null) return;
        List<ItemStack> packed = new ArrayList<>(slots.length);
        for (ItemStack s : slots) packed.add(s == null ? ItemStack.AIR : s);
        e.item().setItemStack(e.item().getItemStack().with(DataComponents.CONTAINER, packed));
    }

    /** The other half of a modern-state pair: {@code left}'s partner sits clockwise of its facing, {@code right}'s counter. */
    private static @Nullable BlockVec partnerOf(MechanicsWorld world, BlockVec pos, Block block) {
        String type = block.getProperty("type");
        String facing = block.getProperty("facing");
        if (facing == null || type == null || "single".equals(type)) return null;
        Direction front = Direction.valueOf(facing.toUpperCase(Locale.ROOT));
        Direction side = "left".equals(type) ? clockwise(front) : clockwise(clockwise(clockwise(front)));
        BlockVec partner = pos.add(side.normalX(), 0, side.normalZ()).asBlockVec();
        return world.getBlock(partner).compare(block) ? partner : null;
    }

    private static Direction clockwise(Direction facing) {
        return switch (facing) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            default -> Direction.NORTH;
        };
    }

    private static InventoryType rowsType(int rows) {
        return switch (Math.max(1, Math.min(6, rows))) {
            case 1 -> InventoryType.CHEST_1_ROW;
            case 2 -> InventoryType.CHEST_2_ROW;
            case 3 -> InventoryType.CHEST_3_ROW;
            case 4 -> InventoryType.CHEST_4_ROW;
            case 5 -> InventoryType.CHEST_5_ROW;
            default -> InventoryType.CHEST_6_ROW;
        };
    }
}
