package io.github.term4.polyp.mechanics.attribute.catalog;

import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.PotionContents;
import net.minestom.server.potion.CustomPotionEffect;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.potion.PotionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 1.8 lets custom effects stand in for the base potion's; later versions add both. */
class PotionContentsTest extends HeadlessServerTest {

    @Test
    void customStandsInOnLegacy() {
        ItemStack speed = ItemStack.of(Material.POTION).with(DataComponents.POTION_CONTENTS, new PotionContents(
                PotionType.FIRE_RESISTANCE, null, List.of(new CustomPotionEffect(PotionEffect.SPEED, 1, 900, false, true, true))));
        List<CustomPotionEffect> legacy = VanillaPotions.payload(speed, VanillaPotions.Contents.LEGACY);
        assertEquals(1, legacy.size());
        assertEquals(PotionEffect.SPEED, legacy.getFirst().id());
        assertEquals(2, VanillaPotions.payload(speed, VanillaPotions.Contents.MODERN).size());

        ItemStack plain = ItemStack.of(Material.POTION).with(DataComponents.POTION_CONTENTS, new PotionContents(PotionType.SWIFTNESS));
        assertEquals(PotionEffect.SPEED, VanillaPotions.payload(plain, VanillaPotions.Contents.LEGACY).getFirst().id(),
                "no custom effects: the base potion's");
    }
}
