package io.github.term4.polyp.vri;

import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import io.github.term4.polyp.vri.BlockDrops.DropContext;
import io.github.term4.polyp.vri.BlockDrops.DropRule;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The vanilla tables read for a break: the tool gate, silk touch, fortune, the block's state, and 1.8's own drops. */
class BlockLootTest extends HeadlessServerTest {

    private static FakePlayer miner;

    @BeforeAll
    static void connect() {
        miner = FakePlayer.connect(instance, new Pos(3.5, 43, 3.5), "LootMiner");
    }

    @AfterAll
    static void leave() {
        miner.player.remove();
    }

    private static List<ItemStack> drops(DropRule rule, Block block, ItemStack tool) {
        DropContext ctx = BlockDrops.context(miner.player, block, tool);
        List<ItemStack> out = rule.drops(ctx);
        return out == null ? List.of() : out;
    }

    private static ItemStack silk(Material tool) {
        return ItemStack.of(tool).with(DataComponents.ENCHANTMENTS, new EnchantmentList(Map.of(Enchantment.SILK_TOUCH, 1)));
    }

    @Test
    void theToolGateHolds() {
        assertTrue(drops(BlockDrops.VANILLA, Block.END_STONE, ItemStack.AIR).isEmpty(), "end stone by hand");
        assertEquals(Material.END_STONE, drops(BlockDrops.VANILLA, Block.END_STONE, ItemStack.of(Material.WOODEN_PICKAXE)).getFirst().material());
        assertTrue(drops(BlockDrops.VANILLA, Block.OBSIDIAN, ItemStack.of(Material.IRON_PICKAXE)).isEmpty(), "obsidian wants diamond");
        assertEquals(Material.OBSIDIAN, drops(BlockDrops.VANILLA, Block.OBSIDIAN, ItemStack.of(Material.DIAMOND_PICKAXE)).getFirst().material());
        assertTrue(drops(BlockDrops.VANILLA, Block.TERRACOTTA, ItemStack.of(Material.DIAMOND_SWORD)).isEmpty(), "a sword harvests no clay");
        assertEquals(Material.WHITE_WOOL, drops(BlockDrops.VANILLA, Block.WHITE_WOOL, ItemStack.AIR).getFirst().material(), "wool by hand");
    }

    @Test
    void theTablesSayWhatFalls() {
        assertTrue(drops(BlockDrops.VANILLA, Block.GLASS, ItemStack.of(Material.DIAMOND_PICKAXE)).isEmpty(), "glass");
        assertEquals(Material.GLASS, drops(BlockDrops.VANILLA, Block.GLASS, silk(Material.DIAMOND_PICKAXE)).getFirst().material(), "with silk touch");
        assertTrue(drops(BlockDrops.VANILLA, Block.PACKED_ICE, ItemStack.of(Material.DIAMOND_PICKAXE)).isEmpty(), "packed ice");
        assertEquals(Material.COBBLESTONE, drops(BlockDrops.VANILLA, Block.STONE, ItemStack.of(Material.WOODEN_PICKAXE)).getFirst().material());
        assertEquals(Material.OAK_LOG, drops(BlockDrops.VANILLA, Block.OAK_LOG, ItemStack.AIR).getFirst().material());
        assertTrue(drops(BlockDrops.VANILLA, Block.BEDROCK, ItemStack.of(Material.DIAMOND_PICKAXE)).isEmpty(), "no table, nothing");
        assertEquals(2, drops(BlockDrops.VANILLA, Block.OAK_SLAB.withProperty("type", "double"), ItemStack.AIR).getFirst().amount(), "a double slab");
        assertEquals(Material.TORCH, drops(BlockDrops.VANILLA, Block.WALL_TORCH, ItemStack.AIR).getFirst().material(), "a wall block drops as its standing one");
        List<ItemStack> books = drops(BlockDrops.VANILLA, Block.BOOKSHELF, ItemStack.AIR);
        assertEquals(3, books.stream().mapToInt(ItemStack::amount).sum());
    }

    @Test
    void legacyDropsDiffer() {
        for (int i = 0; i < 200; i++) {
            int lapis = drops(BlockDrops.VANILLA_18, Block.LAPIS_ORE, ItemStack.of(Material.STONE_PICKAXE)).stream().mapToInt(ItemStack::amount).sum();
            assertTrue(lapis >= 4 && lapis <= 8, "1.8 lapis: " + lapis);
        }
        assertEquals(Material.IRON_ORE, drops(BlockDrops.VANILLA_18, Block.IRON_ORE, ItemStack.of(Material.STONE_PICKAXE)).getFirst().material(), "the ore itself");
        assertEquals(Material.RAW_IRON, drops(BlockDrops.VANILLA, Block.IRON_ORE, ItemStack.of(Material.STONE_PICKAXE)).getFirst().material(), "today's raw iron");
        for (int i = 0; i < 50; i++) {
            assertTrue(drops(BlockDrops.VANILLA_18, Block.DEAD_BUSH, ItemStack.AIR).isEmpty(), "1.8: no sticks");
            assertTrue(drops(BlockDrops.VANILLA_18, Block.OAK_LEAVES, ItemStack.AIR).stream().noneMatch(s -> s.material() == Material.STICK));
        }
        assertEquals(3, drops(BlockDrops.VANILLA_18, Block.SNOW.withProperty("layers", "2"), ItemStack.of(Material.WOODEN_SHOVEL)).getFirst().amount(), "layers + 1");
        assertEquals(Material.COBBLESTONE, drops(BlockDrops.VANILLA_18, Block.STONE, ItemStack.of(Material.WOODEN_PICKAXE)).getFirst().material(), "the rest is the tables'");
    }
}
