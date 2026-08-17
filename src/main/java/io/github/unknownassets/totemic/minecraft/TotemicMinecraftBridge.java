package io.github.unknownassets.totemic.minecraft;

import io.github.unknownassets.totemic.logic.DeathProtectionSnapshot;
import io.github.unknownassets.totemic.logic.SemanticSlot;
import io.github.unknownassets.totemic.logic.TotemCandidate;
import io.github.unknownassets.totemic.logic.TotemicResolution;
import io.github.unknownassets.totemic.logic.TotemicResolver;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class TotemicMinecraftBridge {
	private static final TotemicResolver RESOLVER = new TotemicResolver();

	private TotemicMinecraftBridge() {
	}

	public static Pending prepare(ServerPlayer player, float previousHealth, float appliedDamage) {
		Pending first = capture(player, previousHealth, appliedDamage);
		if (first.revalidate(player)) {
			return first;
		}

		Pending second = capture(player, previousHealth, appliedDamage);
		if (second.revalidate(player)) {
			return second;
		}

		return new Pending(TotemicResolution.aborted(second.resolution.rawPmd()), Map.of());
	}

	public static void consumeFailure(ServerPlayer player, Pending pending) {
		consume(player, pending.resolution.selections(), false);
	}

	public static void consumeSupportsAfterVanilla(ServerPlayer player, Pending pending) {
		consume(player, pending.resolution.selections(), true);
	}

	public static void announceResolution(ServerPlayer player, Pending pending) {
		player.sendSystemMessage(Component.literal(announcementText(pending.resolution)));
	}

	private static Pending capture(ServerPlayer player, float previousHealth, float appliedDamage) {
		double capacity = capacityFor(player.level().getDifficulty());
		Map<SemanticSlot, ExpectedStack> expected = new HashMap<>();

		Optional<TotemCandidate> mainHand = candidate(player.getMainHandItem(), SemanticSlot.mainHand(), capacity, expected);
		Optional<TotemCandidate> offHand = candidate(player.getOffhandItem(), SemanticSlot.offHand(), capacity, expected);

		Inventory inventory = player.getInventory();
		List<TotemCandidate> storage = new ArrayList<>();
		for (int index = 9; index < Inventory.INVENTORY_SIZE; index++) {
			candidate(inventory.getItem(index), SemanticSlot.storage(index), capacity, expected).ifPresent(storage::add);
		}

		List<TotemCandidate> hotbar = new ArrayList<>();
		for (int index = 0; index < Inventory.SELECTION_SIZE; index++) {
			if (index != inventory.selected) {
				candidate(inventory.getItem(index), SemanticSlot.hotbar(index), capacity, expected).ifPresent(hotbar::add);
			}
		}

		DeathProtectionSnapshot snapshot = new DeathProtectionSnapshot(
			previousHealth,
			appliedDamage,
			mainHand,
			offHand,
			storage,
			hotbar
		);
		return new Pending(RESOLVER.resolve(snapshot), Map.copyOf(expected));
	}

	private static Optional<TotemCandidate> candidate(
		ItemStack stack,
		SemanticSlot slot,
		double capacity,
		Map<SemanticSlot, ExpectedStack> expected
	) {
		if (stack.isEmpty() || stack.get(DataComponents.DEATH_PROTECTION) == null) {
			return Optional.empty();
		}
		expected.put(slot, new ExpectedStack(stack.copy(), stack.getCount()));
		return Optional.of(new TotemCandidate(slot, capacity, stack.getCount()));
	}

	public static double capacityFor(Difficulty difficulty) {
		return switch (difficulty) {
			case PEACEFUL, EASY -> 30.0;
			case NORMAL -> 20.0;
			case HARD -> 10.0;
		};
	}

	public static String formatPoints(double value) {
		return BigDecimal.valueOf(value)
			.setScale(3, RoundingMode.HALF_UP)
			.stripTrailingZeros()
			.toPlainString();
	}

	static String announcementText(TotemicResolution resolution) {
		int units = resolution.selections().stream().mapToInt(TotemicResolution.Selection::units).sum();
		String label = units == 1 ? "Totem" : "Totems";
		return "(" + units + " " + label + ") ["
			+ formatPoints(resolution.committedCapacity()) + "/" + formatPoints(resolution.rawPmd()) + "]";
	}

	private static void consume(ServerPlayer player, List<TotemicResolution.Selection> selections, boolean skipActivator) {
		boolean activatorSkipped = !skipActivator;
		for (TotemicResolution.Selection selection : selections) {
			int units = selection.units();
			if (!activatorSkipped) {
				units--;
				activatorSkipped = true;
			}
			if (units > 0) {
				stackAt(player, selection.slot()).shrink(units);
			}
		}
		player.getInventory().setChanged();
		player.containerMenu.broadcastChanges();
	}

	private static ItemStack stackAt(ServerPlayer player, SemanticSlot slot) {
		return switch (slot.kind()) {
			case MAIN_HAND -> player.getMainHandItem();
			case OFF_HAND -> player.getOffhandItem();
			case STORAGE, HOTBAR -> player.getInventory().getItem(slot.index());
		};
	}

	public static final class Pending {
		private final TotemicResolution resolution;
		private final Map<SemanticSlot, ExpectedStack> expected;

		private Pending(TotemicResolution resolution, Map<SemanticSlot, ExpectedStack> expected) {
			this.resolution = resolution;
			this.expected = expected;
		}

		public TotemicResolution resolution() {
			return resolution;
		}

		private boolean revalidate(ServerPlayer player) {
			for (TotemicResolution.Selection selection : resolution.selections()) {
				ExpectedStack expectedStack = expected.get(selection.slot());
				if (expectedStack == null || !expectedStack.matches(stackAt(player, selection.slot()))) {
					return false;
				}
			}
			return true;
		}
	}

	private record ExpectedStack(ItemStack stack, int count) {
		private boolean matches(ItemStack current) {
			return current.getCount() == count && ItemStack.isSameItemSameComponents(stack, current);
		}
	}
}
