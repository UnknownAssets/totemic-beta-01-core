package io.github.unknownassets.totemic.minecraft;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DeathProtection;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TotemicDeathEffects {
	private TotemicDeathEffects() {
	}

	static void apply(LivingEntity entity, List<Source> sources) {
		Map<Holder<MobEffect>, MobEffectInstance> pendingStatusEffects = new LinkedHashMap<>();
		for (Source source : sources) {
			for (int unit = 0; unit < source.units; unit++) {
				ItemStack unitStack = source.stack.copyWithCount(1);
				DeathProtection deathProtection = unitStack.get(DataComponents.DEATH_PROTECTION);
				if (deathProtection == null) {
					continue;
				}
				for (ConsumeEffect effect : deathProtection.deathEffects()) {
					if (effect instanceof ApplyStatusEffectsConsumeEffect statusEffects) {
						collectStatusEffects(entity, statusEffects, pendingStatusEffects);
					} else {
						applyPendingStatusEffects(entity, pendingStatusEffects);
						effect.apply(entity.level(), unitStack, entity);
					}
				}
			}
		}
		applyPendingStatusEffects(entity, pendingStatusEffects);
	}

	private static void collectStatusEffects(
		LivingEntity entity,
		ApplyStatusEffectsConsumeEffect source,
		Map<Holder<MobEffect>, MobEffectInstance> pending
	) {
		if (entity.getRandom().nextFloat() >= source.probability()) {
			return;
		}
		for (MobEffectInstance effect : source.effects()) {
			pending.merge(effect.getEffect(), new MobEffectInstance(effect), TotemicDeathEffects::preferredStatusEffect);
		}
	}

	private static void applyPendingStatusEffects(
		LivingEntity entity,
		Map<Holder<MobEffect>, MobEffectInstance> pending
	) {
		for (MobEffectInstance effect : pending.values()) {
			entity.addEffect(new MobEffectInstance(effect));
		}
		pending.clear();
	}

	static MobEffectInstance preferredStatusEffect(MobEffectInstance current, MobEffectInstance incoming) {
		if (!current.getEffect().equals(incoming.getEffect())) {
			throw new IllegalArgumentException("effect type");
		}
		if (incoming.getAmplifier() > current.getAmplifier()) {
			return new MobEffectInstance(incoming);
		}
		if (incoming.getAmplifier() < current.getAmplifier()) {
			return current;
		}
		if (incoming.isInfiniteDuration()
			|| (!current.isInfiniteDuration() && incoming.getDuration() > current.getDuration())) {
			return new MobEffectInstance(incoming);
		}
		return current;
	}

	record Source(ItemStack stack, int units) {
		Source {
			if (stack == null || stack.isEmpty() || units < 1) {
				throw new IllegalArgumentException("effect source");
			}
			stack = stack.copy();
		}
	}
}
