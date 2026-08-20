package io.github.unknownassets.totemic.minecraft;

import io.github.unknownassets.totemic.logic.SemanticSlot;
import io.github.unknownassets.totemic.logic.TotemicResolution;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DeathProtection;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TotemicMinecraftBridgeTest {
	@BeforeAll
	static void bootstrapMinecraftRegistries() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void formatsTheRequestedAnnouncement() {
		TotemicResolution resolution = new TotemicResolution(
			TotemicResolution.Outcome.PROTECTED,
			12.0,
			15.0,
			List.of(
				new TotemicResolution.Selection(SemanticSlot.mainHand(), 1, 5.0),
				new TotemicResolution.Selection(SemanticSlot.storage(9), 2, 5.0)
			)
		);

		assertEquals("(3 Totems) [15/12]", TotemicMinecraftBridge.announcementText(resolution));
	}

	@Test
	void keepsTheAnnouncementCompactForOneTotemAndFractionalDamage() {
		TotemicResolution resolution = new TotemicResolution(
			TotemicResolution.Outcome.PROTECTED,
			12.5,
			20.0,
			List.of(new TotemicResolution.Selection(SemanticSlot.offHand(), 1, 20.0))
		);

		assertEquals("(1 Totem) [20/12.5]", TotemicMinecraftBridge.announcementText(resolution));
	}

	@Test
	void keepsDifferentDpcsFromUnstackableCopiesOfTheSameItem() {
		DeathProtection regeneration = new DeathProtection(List.of(
			new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1))
		));
		DeathProtection resistance = new DeathProtection(List.of(
			new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 80, 5))
		));
		ItemStack mainHand = new ItemStack(Items.LARGE_FERN, 8);
		mainHand.set(DataComponents.DEATH_PROTECTION, resistance);
		ItemStack storage = new ItemStack(Items.LARGE_FERN, 8);
		storage.set(DataComponents.DEATH_PROTECTION, regeneration);

		TotemicResolution resolution = new TotemicResolution(
			TotemicResolution.Outcome.PROTECTED,
			92,
			100,
			List.of(
				new TotemicResolution.Selection(SemanticSlot.mainHand(), 8, 10),
				new TotemicResolution.Selection(SemanticSlot.storage(9), 2, 10)
			)
		);
		List<TotemicDeathEffects.Source> sources = TotemicMinecraftBridge.selectedDeathEffectSources(
			resolution,
			Map.of(SemanticSlot.mainHand(), mainHand, SemanticSlot.storage(9), storage)::get
		);

		assertEquals(2, sources.size());
		assertEquals(8, sources.get(0).units());
		assertEquals(2, sources.get(1).units());
		assertEquals(resistance, sources.get(0).stack().get(DataComponents.DEATH_PROTECTION));
		assertEquals(regeneration, sources.get(1).stack().get(DataComponents.DEATH_PROTECTION));
		assertNotEquals(
			sources.get(0).stack().get(DataComponents.DEATH_PROTECTION),
			sources.get(1).stack().get(DataComponents.DEATH_PROTECTION)
		);
	}
}
