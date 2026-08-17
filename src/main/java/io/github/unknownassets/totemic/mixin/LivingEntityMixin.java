package io.github.unknownassets.totemic.mixin;

import io.github.unknownassets.totemic.logic.TotemicResolution;
import io.github.unknownassets.totemic.minecraft.TotemicDamageCapture;
import io.github.unknownassets.totemic.minecraft.TotemicMinecraftBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin implements TotemicDamageCapture {
	@Unique
	private float totemic$previousHealth;
	@Unique
	private float totemic$appliedDamage;
	@Unique
	private boolean totemic$capturedDamage;
	@Unique
	private TotemicMinecraftBridge.Pending totemic$pending;

	@Override
	public void totemic$captureAppliedDamage(float previousHealth, float newHealth) {
		this.totemic$previousHealth = previousHealth;
		this.totemic$appliedDamage = Math.max(0.0F, previousHealth - newHealth);
		this.totemic$capturedDamage = true;
	}

	@Inject(method = "checkTotemDeathProtection", at = @At("HEAD"), cancellable = true)
	private void totemic$prepareResolution(DamageSource damageSource, CallbackInfoReturnable<Boolean> callback) {
		if (!((Object)this instanceof ServerPlayer player) || !this.totemic$capturedDamage) {
			return;
		}

		this.totemic$capturedDamage = false;
		if (damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return;
		}

		TotemicMinecraftBridge.Pending pending = TotemicMinecraftBridge.prepare(
			player,
			this.totemic$previousHealth,
			this.totemic$appliedDamage
		);
		TotemicResolution.Outcome outcome = pending.resolution().outcome();
		if (outcome == TotemicResolution.Outcome.PROTECTED) {
			this.totemic$pending = pending;
		} else if (outcome == TotemicResolution.Outcome.INSUFFICIENT) {
			TotemicMinecraftBridge.consumeFailure(player, pending);
			TotemicMinecraftBridge.announceResolution(player, pending);
			player.level().broadcastEntityEvent(player, (byte)35);
			callback.setReturnValue(false);
		} else if (outcome == TotemicResolution.Outcome.ABORTED_CONFLICT) {
			callback.setReturnValue(false);
		}
	}

	@Inject(method = "checkTotemDeathProtection", at = @At("RETURN"))
	private void totemic$completeResolution(DamageSource damageSource, CallbackInfoReturnable<Boolean> callback) {
		if (this.totemic$pending == null) {
			return;
		}
		if (callback.getReturnValue()) {
			ServerPlayer player = (ServerPlayer)(Object)this;
			TotemicMinecraftBridge.consumeSupportsAfterVanilla(player, this.totemic$pending);
			TotemicMinecraftBridge.announceResolution(player, this.totemic$pending);
		}
		this.totemic$pending = null;
	}

	@Inject(method = "hurtServer", at = @At("RETURN"))
	private void totemic$clearDamageCapture(
		ServerLevel serverLevel,
		DamageSource damageSource,
		float amount,
		CallbackInfoReturnable<Boolean> callback
	) {
		this.totemic$capturedDamage = false;
		this.totemic$pending = null;
	}
}
