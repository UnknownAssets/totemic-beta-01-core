package io.github.unknownassets.totemic.gametest;

import io.github.unknownassets.totemic.minecraft.TotemicMinecraftBridge;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DeathProtection;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class TotemicGameTests implements FabricGameTest {
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void consumesMainThenOffThenStorageBeforeHotbar(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		double capacity = TotemicMinecraftBridge.capacityFor(helper.getLevel().getDifficulty());

		player.setItemInHand(InteractionHand.MAIN_HAND, protectedStack(Items.LARGE_FERN, 3));
		player.setItemInHand(InteractionHand.OFF_HAND, protectedStack(Items.IRON_INGOT, 2));
		player.getInventory().setItem(9, protectedStack(Items.APPLE, 3));
		player.getInventory().setItem(1, protectedStack(Items.FEATHER, 3));

		afterSpawnProtection(helper, () -> {
			applyLethalDamage(player, capacity * 7.0);

			helper.assertTrue(player.isAlive(), "Totemic should protect the player");
			helper.assertTrue(player.getMainHandItem().isEmpty(), "Main hand should be exhausted first");
			helper.assertTrue(player.getOffhandItem().isEmpty(), "Off hand should be exhausted second");
			helper.assertValueEqual(player.getInventory().getItem(9).getCount(), 1, "Storage remainder");
			helper.assertValueEqual(player.getInventory().getItem(1).getCount(), 3, "Hotbar must remain untouched");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void combinesDifferentDpcsOnTheSameItem(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		double capacity = TotemicMinecraftBridge.capacityFor(helper.getLevel().getDifficulty());
		ItemStack main = protectedStack(
			Items.LARGE_FERN,
			1,
			new MobEffectInstance(MobEffects.REGENERATION, 200, 1)
		);
		ItemStack storage = protectedStack(
			Items.LARGE_FERN,
			1,
			new MobEffectInstance(MobEffects.REGENERATION, 80, 2),
			new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 120, 0)
		);
		player.setItemInHand(InteractionHand.MAIN_HAND, main);
		player.getInventory().setItem(9, storage);

		afterSpawnProtection(helper, () -> {
			applyLethalDamage(player, capacity + 1.0);

			MobEffectInstance regeneration = player.getEffect(MobEffects.REGENERATION);
			MobEffectInstance resistance = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
			helper.assertTrue(player.isAlive(), "Totemic should protect the player");
			helper.assertTrue(regeneration != null, "Regeneration should be applied");
			helper.assertValueEqual(regeneration.getAmplifier(), 2, "Regeneration amplifier");
			helper.assertValueEqual(regeneration.getDuration(), 80, "Higher amplifier duration");
			helper.assertTrue(resistance != null, "Resistance should be applied");
			helper.assertValueEqual(resistance.getAmplifier(), 0, "Resistance amplifier");
			helper.assertValueEqual(resistance.getDuration(), 120, "Resistance duration");
			helper.assertTrue(player.getMainHandItem().isEmpty(), "Activator should be consumed");
			helper.assertTrue(player.getInventory().getItem(9).isEmpty(), "Support should be consumed");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void insufficientCapacityConsumesCandidatesButDropsOrdinaryItems(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		double capacity = TotemicMinecraftBridge.capacityFor(helper.getLevel().getDifficulty());
		player.setItemInHand(InteractionHand.MAIN_HAND, protectedStack(Items.LARGE_FERN, 2));
		player.getInventory().setItem(9, protectedStack(Items.IRON_INGOT, 1));
		player.getInventory().setItem(10, new ItemStack(Items.OAK_FENCE));

		afterSpawnProtection(helper, () -> {
			discardItemsNear(player);
			applyLethalDamage(player, capacity * 3.0 + 1.0);

			helper.assertTrue(player.isDeadOrDying(), "Insufficient capacity must preserve death");
			int protectedDrops = itemCountNear(player, Items.LARGE_FERN) + itemCountNear(player, Items.IRON_INGOT);
			helper.assertValueEqual(protectedDrops, 0, "Consumed candidates must not drop");
			helper.assertValueEqual(itemCountNear(player, Items.OAK_FENCE), 1, "Ordinary inventory item should drop");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void emptiesSmallerStorageStackBeforeUsingTheLargerStack(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		double capacity = TotemicMinecraftBridge.capacityFor(helper.getLevel().getDifficulty());
		player.setItemInHand(InteractionHand.MAIN_HAND, protectedStack(Items.LARGE_FERN, 1));
		player.getInventory().setItem(9, protectedStack(Items.IRON_INGOT, 3));
		player.getInventory().setItem(10, protectedStack(Items.APPLE, 64));

		afterSpawnProtection(helper, () -> {
			applyLethalDamage(player, capacity * 6.0);

			helper.assertTrue(player.isAlive(), "Totemic should protect the player");
			helper.assertTrue(player.getMainHandItem().isEmpty(), "Activator should be consumed");
			helper.assertTrue(player.getInventory().getItem(9).isEmpty(), "Smaller stack should be emptied first");
			helper.assertValueEqual(player.getInventory().getItem(10).getCount(), 62, "Larger stack remainder");
			helper.succeed();
		});
	}

	@SuppressWarnings("removal")
	private static ServerPlayer survivalPlayer(GameTestHelper helper) {
		helper.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY)
			.set(false, helper.getLevel().getServer());
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.getInventory().clearContent();
		player.setHealth(player.getMaxHealth());
		return player;
	}

	private static void afterSpawnProtection(GameTestHelper helper, Runnable assertion) {
		helper.runAfterDelay(61, assertion);
	}

	private static ItemStack protectedStack(Item item, int count, MobEffectInstance... effects) {
		ItemStack stack = new ItemStack(item, count);
		List<ConsumeEffect> deathEffects = new ArrayList<>();
		Arrays.stream(effects)
			.map(ApplyStatusEffectsConsumeEffect::new)
			.forEach(deathEffects::add);
		stack.set(DataComponents.DEATH_PROTECTION, new DeathProtection(deathEffects));
		return stack;
	}

	private static void applyLethalDamage(ServerPlayer player, double pmd) {
		float damage = (float)(player.getHealth() + pmd);
		player.hurtServer(player.serverLevel(), player.damageSources().generic(), damage);
	}

	private static int itemCountNear(ServerPlayer player, Item item) {
		return player.serverLevel().getEntitiesOfClass(
			ItemEntity.class,
			player.getBoundingBox().inflate(4.0)
		).stream()
			.map(ItemEntity::getItem)
			.filter(stack -> stack.is(item))
			.mapToInt(ItemStack::getCount)
			.sum();
	}

	private static void discardItemsNear(ServerPlayer player) {
		player.serverLevel().getEntitiesOfClass(
			ItemEntity.class,
			player.getBoundingBox().inflate(4.0)
		).forEach(ItemEntity::discard);
	}
}
