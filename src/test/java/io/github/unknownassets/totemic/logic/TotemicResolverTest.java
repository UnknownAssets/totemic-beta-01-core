package io.github.unknownassets.totemic.logic;

import io.github.unknownassets.totemic.logic.TotemicResolution.Outcome;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotemicResolverTest {
	private final TotemicResolver resolver = new TotemicResolver();

	@Test
	void requiresATotemInEitherHand() {
		TotemicResolution resolution = resolver.resolve(snapshot(20, 100, null, null, List.of(), List.of()));

		assertEquals(Outcome.NOT_APPLICABLE, resolution.outcome());
		assertTrue(resolution.selections().isEmpty());
	}

	@Test
	void handsThenStorageThenHotbarAreConsumedInOrder() {
		TotemicResolution resolution = resolver.resolve(snapshot(
			20,
			85,
			candidate(SemanticSlot.mainHand(), 20),
			candidate(SemanticSlot.offHand(), 20),
			List.of(candidate(SemanticSlot.storage(9), 20)),
			List.of(candidate(SemanticSlot.hotbar(0), 20))
		));

		assertEquals(Outcome.PROTECTED, resolution.outcome());
		assertEquals(
			List.of(SemanticSlot.mainHand(), SemanticSlot.offHand(), SemanticSlot.storage(9), SemanticSlot.hotbar(0)),
			resolution.selections().stream().map(TotemicResolution.Selection::slot).toList()
		);
	}

	@Test
	void storageIsPreferredOverAMoreEfficientHotbarChoice() {
		TotemicResolution resolution = resolver.resolve(snapshot(
			20,
			45,
			candidate(SemanticSlot.mainHand(), 20),
			null,
			List.of(candidate(SemanticSlot.storage(9), 1000)),
			List.of(candidate(SemanticSlot.hotbar(0), 5))
		));

		assertEquals(Outcome.PROTECTED, resolution.outcome());
		assertEquals(List.of(SemanticSlot.mainHand(), SemanticSlot.storage(9)), resolution.selections().stream().map(TotemicResolution.Selection::slot).toList());
	}

	@Test
	void exactSolverChoosesMinimumCapacityThenMinimumUnits() {
		ExactPoolSolver solver = new ExactPoolSolver();
		List<TotemCandidate> candidates = List.of(
			candidate(SemanticSlot.storage(9), 5),
			candidate(SemanticSlot.storage(10), 5),
			candidate(SemanticSlot.storage(11), 5),
			candidate(SemanticSlot.storage(12), 1000)
		);

		ExactPoolSolver.Result fifteen = solver.solve(15, candidates);
		ExactPoolSolver.Result sixteen = solver.solve(16, candidates);
		ExactPoolSolver.Result ten = solver.solve(10, List.of(
			candidate(SemanticSlot.storage(9), 10),
			candidate(SemanticSlot.storage(10), 5),
			candidate(SemanticSlot.storage(11), 5)
		));

		assertEquals(15.0, fifteen.totalCapacity());
		assertEquals(3, fifteen.selections().size());
		assertEquals(1000.0, sixteen.totalCapacity());
		assertEquals(List.of(SemanticSlot.storage(12)), sixteen.selections().stream().map(TotemicResolution.Selection::slot).toList());
		assertEquals(List.of(SemanticSlot.storage(9)), ten.selections().stream().map(TotemicResolution.Selection::slot).toList());
	}

	@Test
	void insufficientResolutionCommitsEveryUsefulUnit() {
		TotemicResolution resolution = resolver.resolve(snapshot(
			20,
			120,
			candidate(SemanticSlot.mainHand(), 20),
			candidate(SemanticSlot.offHand(), 0),
			List.of(new TotemCandidate(SemanticSlot.storage(9), 20, 2)),
			List.of(candidate(SemanticSlot.hotbar(0), 20))
		));

		assertEquals(Outcome.INSUFFICIENT, resolution.outcome());
		assertEquals(80.0, resolution.committedCapacity());
		assertEquals(3, resolution.selections().size());
		assertEquals(2, resolution.selections().get(1).units());
		assertTrue(resolution.selections().stream().noneMatch(selection -> selection.slot().equals(SemanticSlot.offHand())));
	}

	@Test
	void exactPmdPolicyPreservesFractionalDamage() {
		TotemicResolution resolution = resolver.resolve(snapshot(
			20,
			32.4999,
			candidate(SemanticSlot.mainHand(), 12),
			null,
			List.of(candidate(SemanticSlot.storage(9), 0.5)),
			List.of()
		));

		assertEquals(12.4999, resolution.rawPmd(), 1.0e-9);
		assertEquals(12.4999, resolution.effectivePmd(), 1.0e-9);
		assertEquals(Outcome.PROTECTED, resolution.outcome());
		assertEquals(12.5, resolution.committedCapacity());
	}

	@Test
	void decimalSubtractionDoesNotInventAnExtraMilliPoint() {
		TotemicResolution resolution = resolver.resolve(snapshot(
			20,
			32.3,
			candidate(SemanticSlot.mainHand(), 12),
			null,
			List.of(new TotemCandidate(SemanticSlot.storage(9), 0.1, 3)),
			List.of()
		));

		assertEquals(Outcome.PROTECTED, resolution.outcome());
		assertEquals(3, resolution.selections().get(1).units());
		assertEquals(12.3, resolution.committedCapacity(), 1.0e-9);
	}

	@Test
	void halfUpPmdPolicyRoundsOnlyThePmd() {
		TotemicResolution below = resolver.resolve(snapshot(
			20,
			32.4999,
			candidate(SemanticSlot.mainHand(), 12),
			null,
			List.of(),
			List.of(),
			PmdRoundingPolicy.HALF_UP
		));
		TotemicResolution tie = resolver.resolve(snapshot(
			20,
			32.5,
			candidate(SemanticSlot.mainHand(), 12),
			null,
			List.of(candidate(SemanticSlot.storage(9), 1)),
			List.of(),
			PmdRoundingPolicy.HALF_UP
		));

		assertEquals(12.4999, below.rawPmd(), 1.0e-9);
		assertEquals(12.0, below.effectivePmd());
		assertTrue(below.roundingEnabled());
		assertEquals(Outcome.PROTECTED, below.outcome());
		assertEquals(12.5, tie.rawPmd());
		assertEquals(13.0, tie.effectivePmd());
		assertEquals(Outcome.PROTECTED, tie.outcome());
	}

	@Test
	void unlimitedActivatorAndSupportAreTypedAndSufficient() {
		TotemicResolution activator = resolver.resolve(snapshot(
			20,
			1_000,
			TotemCandidate.unlimited(SemanticSlot.mainHand(), 1),
			null,
			List.of(),
			List.of()
		));
		TotemicResolution support = resolver.resolve(snapshot(
			20,
			1_000,
			candidate(SemanticSlot.mainHand(), 1),
			null,
			List.of(TotemCandidate.unlimited(SemanticSlot.storage(9), 64)),
			List.of()
		));

		assertEquals(Outcome.PROTECTED, activator.outcome());
		assertTrue(activator.includesUnlimited());
		assertTrue(activator.selections().getFirst().unlimited());
		assertEquals(Outcome.PROTECTED, support.outcome());
		assertTrue(support.includesUnlimited());
		assertEquals(2, support.selections().size());
	}

	@Test
	void consumesTheWholeMainHandStackBeforeUsingStorage() {
		TotemicResolution resolution = resolver.resolve(snapshot(
			20,
			112,
			new TotemCandidate(SemanticSlot.mainHand(), 10, 8),
			null,
			List.of(new TotemCandidate(SemanticSlot.storage(9), 10, 8)),
			List.of()
		));

		assertEquals(Outcome.PROTECTED, resolution.outcome());
		assertEquals(100.0, resolution.committedCapacity());
		assertEquals(8, resolution.selections().get(0).units());
		assertEquals(SemanticSlot.mainHand(), resolution.selections().get(0).slot());
		assertEquals(2, resolution.selections().get(1).units());
		assertEquals(SemanticSlot.storage(9), resolution.selections().get(1).slot());
	}

	@Test
	void consumesOffHandStackBeforeStorageAndHotbar() {
		TotemicResolution resolution = resolver.resolve(snapshot(
			20,
			47,
			new TotemCandidate(SemanticSlot.mainHand(), 5, 3),
			new TotemCandidate(SemanticSlot.offHand(), 5, 3),
			List.of(new TotemCandidate(SemanticSlot.storage(9), 5, 3)),
			List.of(new TotemCandidate(SemanticSlot.hotbar(0), 5, 3))
		));

		assertEquals(Outcome.PROTECTED, resolution.outcome());
		assertEquals(List.of(SemanticSlot.mainHand(), SemanticSlot.offHand()),
			resolution.selections().stream().map(TotemicResolution.Selection::slot).toList());
		assertEquals(3, resolution.selections().get(0).units());
		assertEquals(3, resolution.selections().get(1).units());
	}

	@Test
	void offHandActivatorUsesItsRemainingStackBeforeInventory() {
		TotemicResolution resolution = resolver.resolve(snapshot(
			20,
			38,
			null,
			new TotemCandidate(SemanticSlot.offHand(), 5, 4),
			List.of(new TotemCandidate(SemanticSlot.storage(9), 5, 4)),
			List.of()
		));

		assertEquals(Outcome.PROTECTED, resolution.outcome());
		assertEquals(4, resolution.selections().getFirst().units());
		assertEquals(1, resolution.selections().size());
	}

	@Test
	void insufficientFailureCommitsEveryUsefulHandAndInventoryUnit() {
		TotemicResolution resolution = resolver.resolve(snapshot(
			20,
			100,
			new TotemCandidate(SemanticSlot.mainHand(), 5, 3),
			new TotemCandidate(SemanticSlot.offHand(), 5, 2),
			List.of(new TotemCandidate(SemanticSlot.storage(9), 5, 4)),
			List.of(new TotemCandidate(SemanticSlot.hotbar(0), 5, 2))
		));

		assertEquals(Outcome.INSUFFICIENT, resolution.outcome());
		assertEquals(55.0, resolution.committedCapacity());
		assertEquals(List.of(3, 2, 4, 2),
			resolution.selections().stream().map(TotemicResolution.Selection::units).toList());
	}

	@Test
	void zeroCapacityActivatorDoesNotWasteTheRestOfItsStack() {
		TotemicResolution resolution = resolver.resolve(snapshot(
			20,
			30,
			new TotemCandidate(SemanticSlot.mainHand(), 0, 64),
			null,
			List.of(new TotemCandidate(SemanticSlot.storage(9), 5, 2)),
			List.of()
		));

		assertEquals(Outcome.PROTECTED, resolution.outcome());
		assertEquals(1, resolution.selections().get(0).units());
		assertEquals(2, resolution.selections().get(1).units());
	}

	private static DeathProtectionSnapshot snapshot(
		double previousHealth,
		double appliedDamage,
		TotemCandidate mainHand,
		TotemCandidate offHand,
		List<TotemCandidate> storage,
		List<TotemCandidate> hotbar
	) {
		return snapshot(previousHealth, appliedDamage, mainHand, offHand, storage, hotbar, PmdRoundingPolicy.EXACT);
	}

	private static DeathProtectionSnapshot snapshot(
		double previousHealth,
		double appliedDamage,
		TotemCandidate mainHand,
		TotemCandidate offHand,
		List<TotemCandidate> storage,
		List<TotemCandidate> hotbar,
		PmdRoundingPolicy roundingPolicy
	) {
		return new DeathProtectionSnapshot(
			previousHealth,
			appliedDamage,
			Optional.ofNullable(mainHand),
			Optional.ofNullable(offHand),
			storage,
			hotbar,
			roundingPolicy
		);
	}

	private static TotemCandidate candidate(SemanticSlot slot, double capacity) {
		return new TotemCandidate(slot, capacity, 1);
	}
}
