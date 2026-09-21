package io.github.term4.polyp.item;

import io.github.term4.polyp.entity.DroppedItemEntity;
import io.github.term4.polyp.mechanics.attribute.catalog.enchant.Sharpness;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import io.github.term4.polyp.world.MechanicsWorld;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.registry.RegistryKey;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** A registry-keyed component (an enchantment) needs the registries in the transcoder, or nothing encodes at all. */
class ItemNbtTest extends HeadlessServerTest {

    private static ItemStack enchantedSword() {
        RegistryKey<Enchantment> sharp = RegistryKey.unsafeOf(Sharpness.KEY);
        return ItemStack.of(Material.DIAMOND_SWORD).with(DataComponents.ENCHANTMENTS, new EnchantmentList(sharp, 3));
    }

    @Test
    void enchantedItemRoundTrips() {
        ItemStack item = enchantedSword();
        assertEquals(item, ItemNbt.decode(ItemNbt.encode(item)));
    }

    @Test
    void enchantedDropSavesAndRevives() {
        DroppedItemEntity drop = new DroppedItemEntity(enchantedSword(), DroppedItemEntity.Model.LEGACY);
        Supplier<CompoundBinaryTag> save = drop.getTag(MechanicsWorld.ENTITY_SAVE);
        assertNotNull(save);
        assertEquals(drop.getItemStack(), DroppedItemEntity.fromSave(save.get()).getItemStack());
    }
}
