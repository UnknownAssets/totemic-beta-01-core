package io.github.unknownassets.totemic.minecraft;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TotemicDeathEffectsTest {
	@BeforeAll
	static void bootstrapMinecraftRegistries() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void higherAmplifierWinsEvenWhenItsDurationIsShorter() {
		MobEffectInstance levelTwoForTenSeconds = new MobEffectInstance(MobEffects.REGENERATION, 200, 1);
		MobEffectInstance levelThreeForFourSeconds = new MobEffectInstance(MobEffects.REGENERATION, 80, 2);

		MobEffectInstance selected = TotemicDeathEffects.preferredStatusEffect(
			levelTwoForTenSeconds,
			levelThreeForFourSeconds
		);

		assertEquals(2, selected.getAmplifier());
		assertEquals(80, selected.getDuration());
	}

	@Test
	void longerDurationWinsWhenAmplifiersAreEqual() {
		MobEffectInstance levelTwoForFourSeconds = new MobEffectInstance(MobEffects.REGENERATION, 80, 1);
		MobEffectInstance levelTwoForTenSeconds = new MobEffectInstance(MobEffects.REGENERATION, 200, 1);

		MobEffectInstance selected = TotemicDeathEffects.preferredStatusEffect(
			levelTwoForFourSeconds,
			levelTwoForTenSeconds
		);

		assertEquals(1, selected.getAmplifier());
		assertEquals(200, selected.getDuration());
	}

	@Test
	void differentEffectTypesCannotBeMergedAsDuplicates() {
		assertThrows(IllegalArgumentException.class, () -> TotemicDeathEffects.preferredStatusEffect(
			new MobEffectInstance(MobEffects.REGENERATION, 200, 1),
			new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 1)
		));
	}
}
