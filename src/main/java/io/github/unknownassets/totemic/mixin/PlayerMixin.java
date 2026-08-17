package io.github.unknownassets.totemic.mixin;

import io.github.unknownassets.totemic.minecraft.TotemicDamageCapture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Player.class)
public abstract class PlayerMixin {
	@Redirect(
		method = "actuallyHurt",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setHealth(F)V")
	)
	private void totemic$captureAppliedDamage(
		Player instance,
		float newHealth,
		ServerLevel serverLevel,
		DamageSource damageSource,
		float amount
	) {
		if (instance instanceof ServerPlayer) {
			((TotemicDamageCapture)instance).totemic$captureAppliedDamage(instance.getHealth(), newHealth);
		}
		instance.setHealth(newHealth);
	}
}
