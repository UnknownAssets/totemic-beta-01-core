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

	private static DeathProtectionSnapshot snapshot(
		double previousHealth,
		double appliedDamage,
		TotemCandidate mainHand,
		TotemCandidate offHand,
		List<TotemCandidate> storage,
		List<TotemCandidate> hotbar
	) {
		return new DeathProtectionSnapshot(
			previousHealth,
			appliedDamage,
			Optional.ofNullable(mainHand),
			Optional.ofNullable(offHand),
			storage,
			hotbar
		);
	}

	private static TotemCandidate candidate(SemanticSlot slot, double capacity) {
		return new TotemCandidate(slot, capacity, 1);
	}
}
