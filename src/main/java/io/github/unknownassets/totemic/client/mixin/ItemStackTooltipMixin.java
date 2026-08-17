package io.github.unknownassets.totemic.client.mixin;

import io.github.unknownassets.totemic.minecraft.TotemicMinecraftBridge;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ItemStack.class)
public abstract class ItemStackTooltipMixin {
	@Inject(method = "getTooltipLines", at = @At("RETURN"))
	private void totemic$appendCapacity(
		Item.TooltipContext context,
		@Nullable Player player,
		TooltipFlag flag,
		CallbackInfoReturnable<List<Component>> callback
	) {
		ItemStack stack = (ItemStack)(Object)this;
		if (stack.isEmpty() || stack.has(DataComponents.HIDE_TOOLTIP) || stack.get(DataComponents.DEATH_PROTECTION) == null) {
			return;
		}

		Difficulty difficulty = Difficulty.NORMAL;
		if (player != null) {
			difficulty = player.level().getDifficulty();
		} else if (Minecraft.getInstance().level != null) {
			difficulty = Minecraft.getInstance().level.getDifficulty();
		}

		double capacity = TotemicMinecraftBridge.capacityFor(difficulty);
		List<Component> lines = callback.getReturnValue();
		lines.add(totemic$capacityInsertionIndex(stack, lines, flag),
			Component.literal("Capacity: " + TotemicMinecraftBridge.formatPoints(capacity))
				.withStyle(ChatFormatting.GRAY)
		);
	}

	@Unique
	private static int totemic$capacityInsertionIndex(ItemStack stack, List<Component> lines, TooltipFlag flag) {
		if (!flag.isAdvanced()) {
			return lines.size();
		}

		String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		for (int index = lines.size() - 1; index > 0; index--) {
			if (lines.get(index).getString().equals(itemId)) {
				return index;
			}
		}
		return lines.size();
	}
}
